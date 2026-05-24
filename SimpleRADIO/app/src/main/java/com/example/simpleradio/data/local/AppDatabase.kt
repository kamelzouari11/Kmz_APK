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
                        RadioRecentEntity::class],
        version = 4,
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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE
                    ?: synchronized(this) {
                        val instance =
                                Room.databaseBuilder(
                                                context.applicationContext,
                                                AppDatabase::class.java,
                                                "radio_database"
                                        )
                                        .addMigrations(MIGRATION_3_4)
                                        .fallbackToDestructiveMigration()
                                        .build()
                        INSTANCE = instance
                        instance
                    }
        }
    }
}
