package com.example.simpleradio.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.simpleradio.data.local.entities.*

@Database(
        entities =
                [
                        RadioStationEntity::class,
                        RadioFavoriteListEntity::class,
                        RadioFavoriteCrossRef::class,
                        RadioRecentEntity::class,
                        StationLogoCacheEntity::class],
        version = 6,
        exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun radioDao(): RadioDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_3_4 =
                object : androidx.room.migration.Migration(3, 4) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL(
                                "ALTER TABLE radio_favorites ADD COLUMN position INTEGER NOT NULL DEFAULT 0"
                        )
                    }
                }

        private val MIGRATION_4_5 =
                object : androidx.room.migration.Migration(4, 5) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE radio_stations ADD COLUMN homepage TEXT")
                    }
                }

        private val MIGRATION_5_6 =
                object : androidx.room.migration.Migration(5, 6) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL(
                                """
                                CREATE TABLE IF NOT EXISTS station_logo_cache (
                                    stationuuid TEXT NOT NULL PRIMARY KEY,
                                    logoUrl TEXT NOT NULL,
                                    source TEXT NOT NULL,
                                    width INTEGER NOT NULL,
                                    height INTEGER NOT NULL,
                                    checkedAt INTEGER NOT NULL
                                )
                                """.trimIndent()
                        )
                    }
                }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE
                    ?: synchronized(this) {
                        val instance =
                                Room.databaseBuilder(
                                                context.applicationContext,
                                                AppDatabase::class.java,
                                                "radio_database"
                                        )
                                        .addMigrations(
                                                MIGRATION_3_4,
                                                MIGRATION_4_5,
                                                MIGRATION_5_6
                                        )
                                        .fallbackToDestructiveMigration()
                                        .build()
                        INSTANCE = instance
                        instance
                    }
        }
    }
}
