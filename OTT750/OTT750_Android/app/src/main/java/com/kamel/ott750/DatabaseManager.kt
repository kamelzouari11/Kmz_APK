package com.kamel.ott750

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class Channel(
    val id: Int,
    val name: String,
    val favs: MutableSet<Int> = mutableSetOf(),
    var isSelected: Boolean = false
)

class DatabaseManager(private val context: Context) {

    private val dbPath = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "database.db")
    private val newDbPath = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "database_new.db")

    fun getChannelsForSat(satId: Int): List<Channel> {
        val channels = mutableListOf<Channel>()
        
        if (!dbPath.exists()) {
            Log.e("DB", "Database not found at $dbPath")
            return channels
        }

        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openDatabase(dbPath.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            
            val query = """
                SELECT p.id, p.name 
                FROM program_table p
                JOIN satellite_transponder_table tp ON p.tp_id = tp.id
                WHERE tp.sat_id = ?
                ORDER BY p.name
            """
            
            val cursor = db.rawQuery(query, arrayOf(satId.toString()))
            if (cursor.moveToFirst()) {
                do {
                    val pid = cursor.getInt(0)
                    val name = cursor.getString(1)
                    
                    // Get Favs
                    val favs = mutableSetOf<Int>()
                    val favCursor = db.rawQuery("SELECT fav_group_id FROM fav_prog_table WHERE prog_id=?", arrayOf(pid.toString()))
                    if (favCursor.moveToFirst()) {
                        do {
                            favs.add(favCursor.getInt(0))
                        } while (favCursor.moveToNext())
                    }
                    favCursor.close()
                    
                    channels.add(Channel(pid, name, favs))
                    
                } while (cursor.moveToNext())
            }
            cursor.close()
            
        } catch (e: Exception) {
            Log.e("DB", "Error reading DB", e)
        } finally {
            db?.close()
        }
        
        return channels
    }

    fun saveNewDb(allChannels: Map<Int, List<Channel>>, favMap: Map<String, Int>) {
        try {
            // 1. Copy original to new
            copyFile(dbPath, newDbPath)
            
            // 2. Open new DB
            val db = SQLiteDatabase.openDatabase(newDbPath.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            db.beginTransaction()
            
            try {
                // 3. Update Group Names
                for ((label, id) in favMap) {
                    db.execSQL("UPDATE fav_name_table SET fav_name=? WHERE id=?", arrayOf(label, id))
                }
                
                // 4. Update Channels
                // We need to process all loaded channels. 
                // Note: If the user didn't load a satellite, we won't touch its data, which is correct (preserved from copy).
                
                for ((_, channels) in allChannels) {
                    for (ch in channels) {
                         // Delete existing managed favs for this channel
                         for (favId in favMap.values) {
                             db.execSQL("DELETE FROM fav_prog_table WHERE prog_id=? AND fav_group_id=?", arrayOf(ch.id, favId))
                         }
                         
                         // Insert new favs
                         for (favId in ch.favs) {
                             if (favMap.values.contains(favId)) {
                                 db.execSQL("INSERT INTO fav_prog_table (prog_id, fav_group_id, disp_order, tv_type) VALUES (?, ?, 0, 0)", arrayOf(ch.id, favId))
                             }
                         }
                    }
                }
                
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
                db.close()
            }
            
        } catch (e: Exception) {
            Log.e("DB", "Error saving DB", e)
            throw e
        }
    }

    private fun copyFile(src: File, dst: File) {
        FileInputStream(src).use { `in` ->
            FileOutputStream(dst).use { out ->
                val buf = ByteArray(1024)
                var len: Int
                while (`in`.read(buf).also { len = it } > 0) {
                    out.write(buf, 0, len)
                }
            }
        }
    }

    // ==========================================
    // Internal STB Cache (StbChannel persistence)
    // ==========================================

    private val INTERNAL_DB_NAME = "stb_cache.db"
    private val INTERNAL_DB_VERSION = 3
    private val TAG = "DatabaseManager"

    private val TABLE_CHANNELS = "channels"
    private val COL_PROG_ID = "programId"
    private val COL_NAME = "name"
    private val COL_PROG_INDEX = "programIndex"
    private val COL_PROG_TYPE = "programType"
    private val COL_IS_HD = "isHD"
    private val COL_FAV_MARK = "favMark"
    private val COL_FAV_GROUPS = "favorGroupIds"
    private val COL_IS_LOCKED = "isLocked"
    private val COL_CHANNEL_TYPE = "channelType"
    private val COL_PROVIDER = "provider"
    
    private val TABLE_GROUPS = "groups"
    private val COL_GROUP_ID = "id"
    private val COL_GROUP_NAME = "name"

    private inner class InternalDbHelper(context: Context) : android.database.sqlite.SQLiteOpenHelper(context, INTERNAL_DB_NAME, null, INTERNAL_DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            val createTable = """
                CREATE TABLE $TABLE_CHANNELS (
                    $COL_PROG_ID TEXT PRIMARY KEY,
                    $COL_NAME TEXT,
                    $COL_PROG_INDEX INTEGER,
                    $COL_PROG_TYPE INTEGER,
                    $COL_IS_HD INTEGER,
                    $COL_FAV_MARK INTEGER,
                    $COL_FAV_GROUPS TEXT,
                    $COL_IS_LOCKED INTEGER,
                    $COL_CHANNEL_TYPE INTEGER,
                    $COL_PROVIDER TEXT
                )
            """.trimIndent()
            db.execSQL(createTable)
            
            val createGroupsTable = """
                CREATE TABLE $TABLE_GROUPS (
                    $COL_GROUP_ID INTEGER PRIMARY KEY,
                    $COL_GROUP_NAME TEXT
                )
            """.trimIndent()
            db.execSQL(createGroupsTable)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_CHANNELS")
             db.execSQL("DROP TABLE IF EXISTS $TABLE_GROUPS")
            onCreate(db)
        }
    }

    private val internalHelper by lazy { InternalDbHelper(context) }

    suspend fun saveStbChannels(channels: List<StbChannel>) = withContext(Dispatchers.IO) {
        val db = internalHelper.writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_CHANNELS, null, null) // Simple replacement policy
            val stmt = db.compileStatement("INSERT INTO $TABLE_CHANNELS VALUES (?,?,?,?,?,?,?,?,?,?)")

            for (ch in channels) {
                stmt.clearBindings()
                stmt.bindString(1, ch.programId)
                stmt.bindString(2, ch.name)
                stmt.bindLong(3, ch.programIndex.toLong())
                stmt.bindLong(4, ch.programType.toLong())
                stmt.bindLong(5, if (ch.isHD) 1 else 0)
                stmt.bindLong(6, ch.favMark.toLong())
                stmt.bindString(7, ch.getFavorGroupIdString())
                stmt.bindLong(8, if (ch.isLocked) 1 else 0)
                stmt.bindLong(9, ch.channelType.toLong())
                stmt.bindString(10, ch.provider)
                stmt.executeInsert()
            }
            db.setTransactionSuccessful()
            Log.d(TAG, "Saved ${channels.size} channels to internal DB")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving channels to internal DB", e)
        } finally {
            db.endTransaction()
        }
    }

    suspend fun loadStbChannels(): MutableList<StbChannel> = withContext(Dispatchers.IO) {
        val list = mutableListOf<StbChannel>()
        try {
            val db = internalHelper.readableDatabase
            val cursor = db.rawQuery("SELECT * FROM $TABLE_CHANNELS ORDER BY $COL_PROG_INDEX ASC", null)

            if (cursor.moveToFirst()) {
                do {
                    val pId = cursor.getString(0)
                    val name = cursor.getString(1)
                    val pIndex = cursor.getInt(2)
                    val pType = cursor.getInt(3)
                    val isHd = cursor.getInt(4) == 1
                    val favMark = cursor.getInt(5)
                    val favGroupsStr = cursor.getString(6)
                    val isLocked = cursor.getInt(7) == 1
                    val chType = cursor.getInt(8)
                    val provider = cursor.getString(9) ?: ""

                    val ch = StbChannel(
                        pId, name, pIndex, pType, isHd, favMark,
                        StbChannel.parseFavorGroupIds(favGroupsStr),
                        isLocked, chType, provider
                    )
                    list.add(ch)
                } while (cursor.moveToNext())
            }
            cursor.close()
            Log.d(TAG, "Loaded ${list.size} channels from internal DB")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading internal channels", e)
        }
        return@withContext list
    }

    suspend fun updateFavorite(channel: StbChannel) = withContext(Dispatchers.IO) {
        try {
            val db = internalHelper.writableDatabase
            val values = android.content.ContentValues().apply {
                put(COL_FAV_MARK, channel.favMark)
                put(COL_FAV_GROUPS, channel.getFavorGroupIdString())
            }
            db.update(TABLE_CHANNELS, values, "$COL_PROG_ID = ?", arrayOf(channel.programId))
        } catch (e: Exception) {
            Log.e(TAG, "Error updating favorite", e)
        }
    }

    suspend fun saveFavoriteGroups(groups: List<FavoriteGroup>) = withContext(Dispatchers.IO) {
        val db = internalHelper.writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_GROUPS, null, null)
            val stmt = db.compileStatement("INSERT INTO $TABLE_GROUPS VALUES (?,?)")
            for (g in groups) {
                stmt.clearBindings()
                stmt.bindLong(1, g.id.toLong())
                stmt.bindString(2, g.name)
                stmt.executeInsert()
            }
            db.setTransactionSuccessful()
            Log.d(TAG, "Saved ${groups.size} groups")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving groups", e)
        } finally {
            db.endTransaction()
        }
    }

    suspend fun loadFavoriteGroups(): MutableList<FavoriteGroup> = withContext(Dispatchers.IO) {
        val list = mutableListOf<FavoriteGroup>()
        try {
            val db = internalHelper.readableDatabase
            val cursor = db.rawQuery("SELECT * FROM $TABLE_GROUPS", null)
            if (cursor.moveToFirst()) {
                do {
                    val id = cursor.getInt(0)
                    val name = cursor.getString(1)
                    list.add(FavoriteGroup(id, name))
                } while (cursor.moveToNext())
            }
            cursor.close()
            Log.d(TAG, "Loaded ${list.size} groups")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading groups", e)
        }
        return@withContext list
    }
}
