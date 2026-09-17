package com.example.myiptv.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        XtreamProfile::class,
        SavedChannel::class,
        RecentChannel::class,
        SearchHistoryEntry::class,
        FavoriteGroup::class,
        FavoriteMembership::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class MyIptvDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun channelDao(): ChannelDao
    abstract fun recentDao(): RecentDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        @Volatile
        private var instance: MyIptvDatabase? = null

        fun get(context: Context): MyIptvDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                MyIptvDatabase::class.java,
                "myiptv.db",
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
                .also { instance = it }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE channels ADD COLUMN categoryOrder INTEGER NOT NULL DEFAULT -1",
                )
                db.execSQL(
                    "ALTER TABLE channels ADD COLUMN providerOrder INTEGER NOT NULL DEFAULT -1",
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS search_history (
                        query TEXT NOT NULL,
                        searchedAt INTEGER NOT NULL,
                        PRIMARY KEY(query)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_search_history_searchedAt " +
                        "ON search_history(searchedAt)",
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE profile ADD COLUMN name TEXT NOT NULL DEFAULT 'Profil principal'",
                )
                db.execSQL(
                    "ALTER TABLE profile ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1",
                )
                db.execSQL("ALTER TABLE profile ADD COLUMN lastSyncedAt INTEGER")

                db.execSQL(
                    """
                    CREATE TABLE channels_new (
                        profileId INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        categoryId TEXT NOT NULL,
                        categoryName TEXT NOT NULL,
                        countryCode TEXT NOT NULL,
                        iconUrl TEXT,
                        extension TEXT NOT NULL,
                        epgChannelId TEXT,
                        categoryOrder INTEGER NOT NULL DEFAULT -1,
                        providerOrder INTEGER NOT NULL DEFAULT -1,
                        streamId INTEGER NOT NULL,
                        PRIMARY KEY(profileId, streamId),
                        FOREIGN KEY(profileId) REFERENCES profile(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO channels_new (
                        profileId, name, categoryId, categoryName, countryCode, iconUrl,
                        extension, epgChannelId, categoryOrder, providerOrder, streamId
                    )
                    SELECT 1, name, categoryId, categoryName, countryCode, iconUrl,
                        extension, epgChannelId, categoryOrder, providerOrder, streamId
                    FROM channels
                    """.trimIndent(),
                )

                db.execSQL(
                    """
                    CREATE TABLE recent_channels_new (
                        profileId INTEGER NOT NULL,
                        streamId INTEGER NOT NULL,
                        lastWatchedAt INTEGER NOT NULL,
                        PRIMARY KEY(profileId, streamId)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "INSERT INTO recent_channels_new SELECT 1, streamId, lastWatchedAt FROM recent_channels",
                )

                db.execSQL(
                    """
                    CREATE TABLE search_history_new (
                        profileId INTEGER NOT NULL,
                        query TEXT NOT NULL,
                        searchedAt INTEGER NOT NULL,
                        PRIMARY KEY(profileId, query),
                        FOREIGN KEY(profileId) REFERENCES profile(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "INSERT INTO search_history_new SELECT 1, query, searchedAt FROM search_history",
                )

                db.execSQL(
                    """
                    CREATE TABLE favorite_groups_new (
                        name TEXT NOT NULL,
                        profileId INTEGER NOT NULL,
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        FOREIGN KEY(profileId) REFERENCES profile(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "INSERT INTO favorite_groups_new (name, profileId, id) SELECT name, 1, id FROM favorite_groups",
                )

                db.execSQL(
                    """
                    CREATE TABLE favorite_memberships_new (
                        profileId INTEGER NOT NULL,
                        groupId INTEGER NOT NULL,
                        streamId INTEGER NOT NULL,
                        PRIMARY KEY(profileId, groupId, streamId)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "INSERT INTO favorite_memberships_new SELECT 1, groupId, streamId FROM favorite_memberships",
                )

                db.execSQL("DROP TABLE favorite_memberships")
                db.execSQL("DROP TABLE recent_channels")
                db.execSQL("DROP TABLE channels")
                db.execSQL("DROP TABLE favorite_groups")
                db.execSQL("DROP TABLE search_history")
                db.execSQL("ALTER TABLE channels_new RENAME TO channels")
                db.execSQL("ALTER TABLE search_history_new RENAME TO search_history")
                db.execSQL("ALTER TABLE favorite_groups_new RENAME TO favorite_groups")

                db.execSQL(
                    """
                    CREATE TABLE recent_channels (
                        profileId INTEGER NOT NULL,
                        streamId INTEGER NOT NULL,
                        lastWatchedAt INTEGER NOT NULL,
                        PRIMARY KEY(profileId, streamId),
                        FOREIGN KEY(profileId, streamId)
                            REFERENCES channels(profileId, streamId) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "INSERT INTO recent_channels SELECT profileId, streamId, lastWatchedAt " +
                        "FROM recent_channels_new",
                )
                db.execSQL("DROP TABLE recent_channels_new")

                db.execSQL(
                    """
                    CREATE TABLE favorite_memberships (
                        profileId INTEGER NOT NULL,
                        groupId INTEGER NOT NULL,
                        streamId INTEGER NOT NULL,
                        PRIMARY KEY(profileId, groupId, streamId),
                        FOREIGN KEY(groupId) REFERENCES favorite_groups(id) ON DELETE CASCADE,
                        FOREIGN KEY(profileId, streamId)
                            REFERENCES channels(profileId, streamId) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "INSERT INTO favorite_memberships SELECT profileId, groupId, streamId " +
                        "FROM favorite_memberships_new",
                )
                db.execSQL("DROP TABLE favorite_memberships_new")

                db.execSQL("CREATE INDEX index_channels_profileId ON channels(profileId)")
                db.execSQL("CREATE INDEX index_recent_channels_profileId ON recent_channels(profileId)")
                db.execSQL(
                    "CREATE INDEX index_recent_channels_profileId_lastWatchedAt " +
                        "ON recent_channels(profileId, lastWatchedAt)",
                )
                db.execSQL("CREATE INDEX index_search_history_profileId ON search_history(profileId)")
                db.execSQL(
                    "CREATE INDEX index_search_history_profileId_searchedAt " +
                        "ON search_history(profileId, searchedAt)",
                )
                db.execSQL("CREATE INDEX index_favorite_groups_profileId ON favorite_groups(profileId)")
                db.execSQL("CREATE INDEX index_favorite_memberships_groupId ON favorite_memberships(groupId)")
                db.execSQL(
                    "CREATE INDEX index_favorite_memberships_profileId_streamId " +
                        "ON favorite_memberships(profileId, streamId)",
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE profile ADD COLUMN countryGroupingEnabled " +
                        "INTEGER NOT NULL DEFAULT 1",
                )
            }
        }
    }
}
