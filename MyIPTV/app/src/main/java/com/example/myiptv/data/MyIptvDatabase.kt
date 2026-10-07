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
        EpgChannelCache::class,
        RecentChannel::class,
        SearchHistoryEntry::class,
        FavoriteGroup::class,
        FavoriteMembership::class,
        VodMovieEntity::class,
        VodCatalogMeta::class,
        VodActorIndexEntity::class,
        VodCategoryEntity::class,
    ],
    version = 15,
    exportSchema = false,
)
abstract class MyIptvDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun channelDao(): ChannelDao
    abstract fun epgChannelCacheDao(): EpgChannelCacheDao
    abstract fun recentDao(): RecentDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun vodDao(): VodDao

    companion object {
        @Volatile
        private var instance: MyIptvDatabase? = null

        fun get(context: Context): MyIptvDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                MyIptvDatabase::class.java,
                "myiptv.db",
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
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

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS vod_movies (
                        profileId INTEGER NOT NULL,
                        streamId INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        categoryId TEXT NOT NULL,
                        categoryName TEXT NOT NULL,
                        country TEXT NOT NULL,
                        posterUrl TEXT,
                        extension TEXT NOT NULL,
                        description TEXT NOT NULL,
                        releaseDate TEXT,
                        duration TEXT,
                        durationSecs INTEGER,
                        rating TEXT,
                        actors TEXT,
                        director TEXT,
                        genre TEXT,
                        categoryOrder INTEGER NOT NULL,
                        providerOrder INTEGER NOT NULL,
                        PRIMARY KEY(profileId, streamId),
                        FOREIGN KEY(profileId) REFERENCES profile(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vod_movies_profileId ON vod_movies(profileId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vod_movies_profileId_categoryOrder ON vod_movies(profileId, categoryOrder)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS vod_catalog_meta (
                        profileId INTEGER NOT NULL PRIMARY KEY,
                        syncedAt INTEGER NOT NULL,
                        FOREIGN KEY(profileId) REFERENCES profile(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE vod_movies ADD COLUMN detailsLoaded INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_vod_movies_profileId_name " +
                        "ON vod_movies(profileId, name)",
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS vod_actor_index (
                        profileId INTEGER NOT NULL,
                        actorKey TEXT NOT NULL,
                        actorName TEXT NOT NULL,
                        streamId INTEGER NOT NULL,
                        PRIMARY KEY(profileId, actorKey, streamId),
                        FOREIGN KEY(profileId, streamId) REFERENCES vod_movies(profileId, streamId) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_vod_actor_index_profileId_actorKey " +
                        "ON vod_actor_index(profileId, actorKey)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_vod_actor_index_profileId_streamId " +
                        "ON vod_actor_index(profileId, streamId)",
                )
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS vod_categories (
                        profileId INTEGER NOT NULL,
                        categoryId TEXT NOT NULL,
                        categoryName TEXT NOT NULL,
                        categoryOrder INTEGER NOT NULL,
                        PRIMARY KEY(profileId, categoryId),
                        FOREIGN KEY(profileId) REFERENCES profile(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_vod_categories_profileId_categoryOrder " +
                        "ON vod_categories(profileId, categoryOrder)",
                )
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profile ADD COLUMN protocol TEXT NOT NULL DEFAULT 'XTREAM'")
                db.execSQL("ALTER TABLE profile ADD COLUMN macAddress TEXT")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE channels ADD COLUMN parent_id INTEGER")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_channels_profileId_parent_id " +
                        "ON channels(profileId, parent_id)",
                )
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS family_epg (" +
                        "profileId INTEGER NOT NULL, parentId INTEGER NOT NULL, " +
                        "programsJson TEXT NOT NULL, attemptedAt INTEGER NOT NULL, " +
                        "sourceStreamId INTEGER, PRIMARY KEY(profileId, parentId), " +
                        "FOREIGN KEY(profileId) REFERENCES profile(id) ON DELETE CASCADE)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_family_epg_profileId ON family_epg(profileId)",
                )
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS family_members (" +
                        "profileId INTEGER NOT NULL, streamId INTEGER NOT NULL, " +
                        "nameKey TEXT NOT NULL, parentId INTEGER NOT NULL, " +
                        "PRIMARY KEY(profileId, streamId), " +
                        "FOREIGN KEY(profileId) REFERENCES profile(id) ON DELETE CASCADE)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_family_members_profileId_nameKey " +
                        "ON family_members(profileId, nameKey)",
                )
                db.execSQL(
                    "INSERT INTO family_members(profileId, streamId, nameKey, parentId) " +
                        "SELECT profileId, streamId, '', parent_id FROM channels WHERE parent_id IS NOT NULL",
                )
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Rebuild channels to physically remove the obsolete parent_id relation.
                // Dependent rows are staged first so favorites and recents survive intact.
                db.execSQL(
                    "CREATE TABLE recent_channels_v15 (" +
                        "profileId INTEGER NOT NULL, streamId INTEGER NOT NULL, " +
                        "lastWatchedAt INTEGER NOT NULL, PRIMARY KEY(profileId, streamId))",
                )
                db.execSQL(
                    "INSERT INTO recent_channels_v15 SELECT profileId,streamId,lastWatchedAt FROM recent_channels",
                )
                db.execSQL(
                    "CREATE TABLE favorite_memberships_v15 (" +
                        "profileId INTEGER NOT NULL, groupId INTEGER NOT NULL, streamId INTEGER NOT NULL, " +
                        "PRIMARY KEY(profileId, groupId, streamId))",
                )
                db.execSQL(
                    "INSERT INTO favorite_memberships_v15 " +
                        "SELECT profileId,groupId,streamId FROM favorite_memberships",
                )
                db.execSQL("DROP TABLE recent_channels")
                db.execSQL("DROP TABLE favorite_memberships")
                db.execSQL(
                    "CREATE TABLE channels_v15 (" +
                        "profileId INTEGER NOT NULL, name TEXT NOT NULL, categoryId TEXT NOT NULL, " +
                        "categoryName TEXT NOT NULL, countryCode TEXT NOT NULL, iconUrl TEXT, " +
                        "extension TEXT NOT NULL, epgChannelId TEXT, " +
                        "categoryOrder INTEGER NOT NULL DEFAULT -1, " +
                        "providerOrder INTEGER NOT NULL DEFAULT -1, streamId INTEGER NOT NULL, " +
                        "PRIMARY KEY(profileId, streamId), " +
                        "FOREIGN KEY(profileId) REFERENCES profile(id) ON DELETE CASCADE)",
                )
                db.execSQL(
                    "INSERT INTO channels_v15(" +
                        "profileId,name,categoryId,categoryName,countryCode,iconUrl,extension," +
                        "epgChannelId,categoryOrder,providerOrder,streamId) " +
                        "SELECT profileId,name,categoryId,categoryName,countryCode,iconUrl,extension," +
                        "NULLIF(TRIM(epgChannelId),''),categoryOrder,providerOrder,streamId FROM channels",
                )
                db.execSQL("DROP TABLE channels")
                db.execSQL("ALTER TABLE channels_v15 RENAME TO channels")
                db.execSQL(
                    "CREATE UNIQUE INDEX index_favorite_groups_profileId_id " +
                        "ON favorite_groups(profileId, id)",
                )
                db.execSQL(
                    "CREATE TABLE recent_channels (" +
                        "profileId INTEGER NOT NULL, streamId INTEGER NOT NULL, " +
                        "lastWatchedAt INTEGER NOT NULL, PRIMARY KEY(profileId, streamId), " +
                        "FOREIGN KEY(profileId, streamId) REFERENCES channels(profileId, streamId) " +
                        "ON DELETE CASCADE)",
                )
                db.execSQL(
                    "INSERT INTO recent_channels SELECT profileId,streamId,lastWatchedAt " +
                        "FROM recent_channels_v15",
                )
                db.execSQL("DROP TABLE recent_channels_v15")
                db.execSQL(
                    "CREATE TABLE favorite_memberships (" +
                        "profileId INTEGER NOT NULL, groupId INTEGER NOT NULL, streamId INTEGER NOT NULL, " +
                        "PRIMARY KEY(profileId, groupId, streamId), " +
                        "FOREIGN KEY(profileId, groupId) REFERENCES favorite_groups(profileId, id) " +
                        "ON DELETE CASCADE, " +
                        "FOREIGN KEY(profileId, streamId) REFERENCES channels(profileId, streamId) " +
                        "ON DELETE CASCADE)",
                )
                db.execSQL(
                    "INSERT INTO favorite_memberships SELECT profileId,groupId,streamId " +
                        "FROM favorite_memberships_v15",
                )
                db.execSQL("DROP TABLE favorite_memberships_v15")
                db.execSQL(
                    "CREATE INDEX index_channels_profileId_providerOrder " +
                        "ON channels(profileId, providerOrder)",
                )
                db.execSQL(
                    "CREATE INDEX index_channels_profileId_countryCode_providerOrder " +
                        "ON channels(profileId, countryCode, providerOrder)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_channels_profileId_epgChannelId_providerOrder " +
                        "ON channels(profileId, epgChannelId, providerOrder)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_vod_movies_profileId_providerOrder " +
                        "ON vod_movies(profileId, providerOrder)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_vod_movies_profileId_categoryId_providerOrder " +
                        "ON vod_movies(profileId, categoryId, providerOrder)",
                )
                db.execSQL(
                    "CREATE INDEX index_recent_channels_profileId_lastWatchedAt " +
                        "ON recent_channels(profileId, lastWatchedAt)",
                )
                db.execSQL(
                    "CREATE INDEX index_favorite_memberships_profileId_streamId " +
                        "ON favorite_memberships(profileId, streamId)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS epg_channel_cache (" +
                        "profileId INTEGER NOT NULL, epgChannelId TEXT NOT NULL, " +
                        "programsJson TEXT NOT NULL, attemptedAt INTEGER NOT NULL, " +
                        "sourceStreamId INTEGER, PRIMARY KEY(profileId, epgChannelId), " +
                        "FOREIGN KEY(profileId) REFERENCES profile(id) ON DELETE CASCADE)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_epg_channel_cache_profileId " +
                        "ON epg_channel_cache(profileId)",
                )
                db.execSQL("DROP INDEX IF EXISTS index_search_history_profileId")
                db.execSQL("DROP INDEX IF EXISTS index_favorite_groups_profileId")
                db.execSQL("DROP INDEX IF EXISTS index_vod_movies_profileId")
                db.execSQL("DROP TABLE IF EXISTS family_members")
                db.execSQL("DROP TABLE IF EXISTS family_epg")
            }
        }
    }
}
