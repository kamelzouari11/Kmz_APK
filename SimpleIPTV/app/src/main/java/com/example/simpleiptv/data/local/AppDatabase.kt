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
        version = 17,
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

        private fun mergeFavoriteListsAsGlobal(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO channel_favorites (
                        channelId,
                        listId,
                        profileId,
                        type,
                        sortPosition
                    )
                    SELECT
                        cf.channelId,
                        keep.keepId,
                        cf.profileId,
                        cf.type,
                        cf.sortPosition
                    FROM channel_favorites cf
                    INNER JOIN favorite_lists fl ON fl.id = cf.listId
                    INNER JOIN (
                        SELECT
                            MIN(id) AS keepId,
                            name,
                            type
                        FROM favorite_lists
                        GROUP BY name, type
                    ) keep ON keep.name = fl.name
                        AND keep.type = fl.type
                    WHERE cf.listId != keep.keepId
                    """
                )
                db.execSQL(
                    """
                    DELETE FROM channel_favorites
                    WHERE listId IN (
                        SELECT fl.id
                        FROM favorite_lists fl
                        INNER JOIN (
                            SELECT
                                MIN(id) AS keepId,
                                name,
                                type
                            FROM favorite_lists
                            GROUP BY name, type
                        ) keep ON keep.name = fl.name
                            AND keep.type = fl.type
                        WHERE fl.id != keep.keepId
                    )
                    """
                )
                db.execSQL(
                    """
                    DELETE FROM favorite_lists
                    WHERE id NOT IN (
                        SELECT MIN(id)
                        FROM favorite_lists
                        GROUP BY name, type
                    )
                    """
                )
                db.execSQL("UPDATE favorite_lists SET profileId = NULL")
                db.execSQL("DROP INDEX IF EXISTS index_favorite_lists_name_profileId_type")
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_favorite_lists_name_type
                    ON favorite_lists(name, type)
                    """
                )
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                mergeFavoriteListsAsGlobal(db)
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                mergeFavoriteListsAsGlobal(db)
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
                                        .addMigrations(MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17)
                                        .fallbackToDestructiveMigration(dropAllTables = false)
                                        .build()
                        INSTANCE = instance
                        instance
                    }
        }
    }
}
