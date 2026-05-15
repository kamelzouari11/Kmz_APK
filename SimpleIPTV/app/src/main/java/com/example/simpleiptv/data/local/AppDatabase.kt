package com.example.simpleiptv.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.simpleiptv.data.local.entities.*

@Database(
        entities =
                [
                        CategoryEntity::class,
                        ChannelEntity::class,
                        ChannelFtsEntity::class,
                        ChannelCategoryCrossRef::class,
                        FavoriteListEntity::class,
                        ChannelFavoriteCrossRef::class,
                        RecentChannelEntity::class,
                        ProfileEntity::class,
                        SearchHistoryEntity::class],
        version = 15,
        exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun iptvDao(): IptvDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // Ajoute isEnabled (défaut 1 = ON) sans toucher aux données existantes
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE profiles ADD COLUMN isEnabled INTEGER NOT NULL DEFAULT 1"
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
                                                "iptv_database"
                                        )
                                        .addMigrations(MIGRATION_14_15)
                                        .fallbackToDestructiveMigration()
                                        .build()
                        INSTANCE = instance
                        instance
                    }
        }
    }
}
