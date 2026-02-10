package com.kamel.ott750

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Environment
import android.util.Log
import java.io.File

/**
 * Charge les chaînes depuis database.db (enrichi avec providers)
 * Fichier attendu dans : /storage/emulated/0/Download/database.db
 */
class DatabaseLoader(private val context: Context) {
    
    companion object {
        private const val TAG = "DatabaseLoader"
        private const val DB_NAME = "database.db"
    }
    
    data class DbChannel(
        val id: Int,
        val name: String,
        val provider: String,
        val satellite: String,
        val frequency: Int,
        val isHD: Boolean,
        val programType: Int  // 0=Radio, 1=TV
    )
    
    /**
     * Retourne le chemin vers database.db
     */
    fun getDatabasePath(): File {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(downloads, DB_NAME)
    }
    
    /**
     * Vérifie si database.db existe
     */
    fun isDatabaseAvailable(): Boolean {
        val dbFile = getDatabasePath()
        val exists = dbFile.exists() && dbFile.canRead()
        Log.d(TAG, "Database at ${dbFile.absolutePath}: exists=$exists")
        return exists
    }
    
    /**
     * Charge toutes les chaînes depuis database.db
     */
    fun loadChannels(): List<DbChannel> {
        val channels = mutableListOf<DbChannel>()
        val dbFile = getDatabasePath()
        
        if (!dbFile.exists()) {
            Log.e(TAG, "Database not found at ${dbFile.absolutePath}")
            return channels
        }
        
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            
            val query = """
                SELECT p.id, p.name, COALESCE(p.provider, 'Other') as provider, 
                       s.name as satellite, t.freq, p.tv_type, p.vid_type
                FROM program_table p
                JOIN satellite_transponder_table t ON p.tp_id = t.id
                JOIN satellite_table s ON t.sat_id = s.id
                WHERE p.name != '' AND p.name != 'Unname'
                ORDER BY p.disp_order
            """.trimIndent()
            
            val cursor = db.rawQuery(query, null)
            
            while (cursor.moveToNext()) {
                val id = cursor.getInt(0)
                val name = cursor.getString(1) ?: ""
                val provider = cursor.getString(2) ?: "Other"
                val satellite = cursor.getString(3) ?: ""
                val frequency = cursor.getInt(4)
                val tvType = cursor.getInt(5)  // 0=SD, 1=HD
                
                // Déterminer si HD basé sur le nom ou vidType
                val isHD = name.contains("HD", ignoreCase = true) || 
                           name.contains("UHD", ignoreCase = true) ||
                           name.contains("4K", ignoreCase = true) ||
                           tvType == 1
                
                // programType: on utilise une heuristique basée sur le nom pour radio
                val isRadio = name.contains("Radio", ignoreCase = true) ||
                              name.contains("FM", ignoreCase = true) ||
                              name.endsWith(" R")
                
                channels.add(DbChannel(
                    id = id,
                    name = name,
                    provider = provider,
                    satellite = satellite,
                    frequency = frequency,
                    isHD = isHD,
                    programType = if (isRadio) 0 else 1
                ))
            }
            
            cursor.close()
            Log.d(TAG, "Loaded ${channels.size} channels from database")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading database", e)
        } finally {
            db?.close()
        }
        
        return channels
    }
    
    /**
     * Charge les noms des groupes favoris
     */
    fun loadFavoriteGroups(): Map<Int, String> {
        val groups = mutableMapOf<Int, String>()
        val dbFile = getDatabasePath()
        
        if (!dbFile.exists()) return groups
        
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            
            val cursor = db.rawQuery("SELECT id, fav_name FROM fav_name_table", null)
            while (cursor.moveToNext()) {
                groups[cursor.getInt(0)] = cursor.getString(1) ?: "FAV ${cursor.getInt(0)}"
            }
            cursor.close()
            
            Log.d(TAG, "Loaded ${groups.size} favorite groups")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading favorite groups", e)
        } finally {
            db?.close()
        }
        
        return groups
    }
    
    /**
     * Charge les favoris actuels (channel_id -> set of group_ids)
     */
    fun loadFavorites(): Map<Int, Set<Int>> {
        val favorites = mutableMapOf<Int, MutableSet<Int>>()
        val dbFile = getDatabasePath()
        
        if (!dbFile.exists()) return favorites
        
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            
            val cursor = db.rawQuery("SELECT prog_id, fav_group_id FROM fav_prog_table", null)
            while (cursor.moveToNext()) {
                val progId = cursor.getInt(0)
                val groupId = cursor.getInt(1)
                favorites.getOrPut(progId) { mutableSetOf() }.add(groupId)
            }
            cursor.close()
            
            Log.d(TAG, "Loaded favorites for ${favorites.size} channels")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading favorites", e)
        } finally {
            db?.close()
        }
        
        return favorites
    }
    
    /**
     * Retourne la liste des providers uniques
     */
    fun loadProviders(): List<String> {
        val providers = mutableListOf<String>()
        val dbFile = getDatabasePath()
        
        if (!dbFile.exists()) return providers
        
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            
            val cursor = db.rawQuery("""
                SELECT DISTINCT provider FROM program_table 
                WHERE provider != '' 
                ORDER BY provider
            """.trimIndent(), null)
            
            while (cursor.moveToNext()) {
                providers.add(cursor.getString(0) ?: "Other")
            }
            cursor.close()
            
            Log.d(TAG, "Loaded ${providers.size} providers")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading providers", e)
        } finally {
            db?.close()
        }
        
        return providers
    }
    
    /**
     * Retourne la liste des satellites uniques
     */
    fun loadSatellites(): List<String> {
        val satellites = mutableListOf<String>()
        val dbFile = getDatabasePath()
        
        if (!dbFile.exists()) return satellites
        
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            
            val cursor = db.rawQuery("""
                SELECT DISTINCT s.name FROM satellite_table s
                JOIN satellite_transponder_table t ON t.sat_id = s.id
                JOIN program_table p ON p.tp_id = t.id
                WHERE p.name != '' AND p.name != 'Unname'
                ORDER BY s.name
            """.trimIndent(), null)
            
            while (cursor.moveToNext()) {
                satellites.add(cursor.getString(0) ?: "")
            }
            cursor.close()
            
            Log.d(TAG, "Loaded ${satellites.size} satellites")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading satellites", e)
        } finally {
            db?.close()
        }
        
        return satellites
    }
}
