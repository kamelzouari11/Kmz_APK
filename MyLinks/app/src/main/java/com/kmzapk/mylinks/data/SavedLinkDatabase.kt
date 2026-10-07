package com.kmzapk.mylinks.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SavedLink::class], version = 10, exportSchema = false)
abstract class SavedLinkDatabase : RoomDatabase() {
    abstract fun savedLinkDao(): SavedLinkDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_links ADD COLUMN address TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_links ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE saved_links ADD COLUMN profile_url TEXT")
                db.execSQL("ALTER TABLE saved_links ADD COLUMN profile_image_url TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_links ADD COLUMN import_collection TEXT")
            }
        }
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_links ADD COLUMN import_context TEXT")
            }
        }
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_links ADD COLUMN ai_checked_at INTEGER")
            }
        }
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Keep any older duplicate rows available for recovery before enforcing uniqueness.
                db.execSQL("""CREATE TABLE saved_links_duplicate_backup AS
                    SELECT * FROM saved_links WHERE id NOT IN
                    (SELECT MIN(id) FROM saved_links GROUP BY original_url)""")
                db.execSQL("DELETE FROM saved_links WHERE id IN (SELECT id FROM saved_links_duplicate_backup)")
                db.execSQL("CREATE UNIQUE INDEX index_saved_links_original_url ON saved_links(original_url)")
            }
        }
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_links ADD COLUMN ai_check_status TEXT")
            }
        }
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_links ADD COLUMN published_at INTEGER")
            }
        }
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE saved_links SET title = '' WHERE title = 'Shared item'")
                db.execSQL("""UPDATE saved_links SET title = ''
                    WHERE title = original_url AND source IN ('Facebook', 'Instagram')""")
                db.execSQL("""UPDATE saved_links SET category = ''
                    WHERE category IN ('À classer', 'à classer', 'Suggested: General', 'General')""")
                db.execSQL("""UPDATE saved_links SET source = ''
                    WHERE source IN ('Shared from app', 'Shared from Android')""")
            }
        }


        @Volatile
        private var INSTANCE: SavedLinkDatabase? = null

        fun getDatabase(context: Context): SavedLinkDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SavedLinkDatabase::class.java,
                    "mylinks_db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                    MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
                    MIGRATION_8_9, MIGRATION_9_10).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
