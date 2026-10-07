package com.example.myiptv.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE isActive = 1 ORDER BY id LIMIT 1")
    fun observeActive(): Flow<XtreamProfile?>

    @Query("SELECT * FROM profile ORDER BY name COLLATE NOCASE, id")
    fun observeAll(): Flow<List<XtreamProfile>>

    @Query("SELECT * FROM profile ORDER BY name COLLATE NOCASE, id")
    suspend fun getAll(): List<XtreamProfile>

    @Query("SELECT * FROM profile WHERE isActive = 1 ORDER BY id LIMIT 1")
    suspend fun getActive(): XtreamProfile?

    @Query("SELECT * FROM profile WHERE id = :profileId LIMIT 1")
    suspend fun get(profileId: Int): XtreamProfile?

    @Insert
    suspend fun insert(profile: XtreamProfile): Long

    @Query("SELECT COALESCE(MAX(id), 0) + 1 FROM profile")
    suspend fun nextId(): Int

    @Update
    suspend fun update(profile: XtreamProfile)

    @Query("UPDATE profile SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE profile SET isActive = 1 WHERE id = :profileId")
    suspend fun activate(profileId: Int)

    @Query("UPDATE profile SET lastSyncedAt = :syncedAt WHERE id = :profileId")
    suspend fun updateLastSyncedAt(profileId: Int, syncedAt: Long)

    @Query("SELECT COUNT(*) FROM profile")
    suspend fun count(): Int

    @Query("SELECT * FROM profile WHERE id != :profileId ORDER BY id LIMIT 1")
    suspend fun getAnother(profileId: Int): XtreamProfile?

    @Delete
    suspend fun delete(profile: XtreamProfile)
}

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE profileId = :profileId AND countryCode = :countryCode ORDER BY providerOrder")
    suspend fun getByCountry(profileId: Int, countryCode: String): List<SavedChannel>

    @Query(
        "SELECT * FROM channels WHERE profileId = :profileId " +
            "AND epgChannelId = :epgChannelId ORDER BY providerOrder",
    )
    suspend fun getByEpgChannelId(profileId: Int, epgChannelId: String): List<SavedChannel>

    @Query(
        "SELECT profileId, streamId, name, countryCode, epgChannelId, providerOrder " +
            "FROM channels WHERE profileId = :profileId AND epgChannelId IS NOT NULL " +
            "ORDER BY providerOrder",
    )
    suspend fun getEpgRows(profileId: Int): List<EpgChannelRow>

    @Query("SELECT * FROM channels WHERE profileId = :profileId ORDER BY providerOrder")
    suspend fun getForEpg(profileId: Int): List<SavedChannel>

    @Query("SELECT * FROM channels WHERE profileId = :profileId ORDER BY providerOrder")
    fun observeAll(profileId: Int): Flow<List<SavedChannel>>

    @Upsert
    suspend fun saveAll(channels: List<SavedChannel>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(channels: List<SavedChannel>)

    @Query("UPDATE channels SET providerOrder = -1 WHERE profileId = :profileId")
    suspend fun markAllStale(profileId: Int)

    @Query("DELETE FROM channels WHERE profileId = :profileId AND providerOrder = -1")
    suspend fun deleteStale(profileId: Int)

    @Query("DELETE FROM channels WHERE profileId = :profileId")
    suspend fun clear(profileId: Int)
}

@Dao
interface EpgChannelCacheDao {
    @Query(
        "SELECT epgChannelId FROM epg_channel_cache " +
            "WHERE profileId = :profileId AND programsJson <> '[]'",
    )
    suspend fun getIds(profileId: Int): List<String>

    @Query("SELECT * FROM epg_channel_cache WHERE profileId = :profileId")
    suspend fun getAll(profileId: Int): List<EpgChannelCache>

    @Query(
        "SELECT * FROM epg_channel_cache " +
            "WHERE profileId = :profileId AND epgChannelId IN (:epgChannelIds)",
    )
    suspend fun getForIds(profileId: Int, epgChannelIds: List<String>): List<EpgChannelCache>

    @Query(
        "SELECT * FROM epg_channel_cache " +
            "WHERE profileId = :profileId AND epgChannelId = :epgChannelId LIMIT 1",
    )
    suspend fun get(profileId: Int, epgChannelId: String): EpgChannelCache?

    @Upsert
    suspend fun save(entry: EpgChannelCache)

    @Upsert
    suspend fun saveAll(entries: List<EpgChannelCache>)

    @Query("DELETE FROM epg_channel_cache WHERE profileId = :profileId")
    suspend fun clear(profileId: Int)
}

@Dao
interface RecentDao {
    @Query(
        """
        SELECT channels.* FROM channels
        INNER JOIN recent_channels
            ON channels.profileId = recent_channels.profileId
            AND channels.streamId = recent_channels.streamId
        WHERE channels.profileId = :profileId
        ORDER BY recent_channels.lastWatchedAt DESC
        """,
    )
    fun observeChannels(profileId: Int): Flow<List<SavedChannel>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun mark(recent: RecentChannel)

    @Query(
        """
        DELETE FROM recent_channels
        WHERE profileId = :profileId AND streamId NOT IN (
            SELECT streamId FROM recent_channels
            WHERE profileId = :profileId
            ORDER BY lastWatchedAt DESC LIMIT :limit
        )
        """,
    )
    suspend fun trim(profileId: Int, limit: Int)

    @Query("DELETE FROM recent_channels WHERE profileId = :profileId")
    suspend fun clear(profileId: Int)
}

@Dao
interface SearchHistoryDao {
    @Query(
        "SELECT query FROM search_history " +
            "WHERE profileId = :profileId ORDER BY searchedAt DESC LIMIT :limit",
    )
    fun observeRecent(profileId: Int, limit: Int): Flow<List<String>>

    @Query(
        "DELETE FROM search_history " +
            "WHERE profileId = :profileId AND query = :query COLLATE NOCASE",
    )
    suspend fun deleteCaseInsensitive(profileId: Int, query: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entry: SearchHistoryEntry)

    @Query(
        """
        DELETE FROM search_history
        WHERE profileId = :profileId AND query IN (
            SELECT query FROM search_history
            WHERE profileId = :profileId
            ORDER BY searchedAt DESC LIMIT -1 OFFSET :limit
        )
        """,
    )
    suspend fun trim(profileId: Int, limit: Int)

    @Query("DELETE FROM search_history WHERE profileId = :profileId")
    suspend fun clear(profileId: Int)
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite_groups WHERE profileId = :profileId ORDER BY name COLLATE NOCASE")
    fun observeGroups(profileId: Int): Flow<List<FavoriteGroup>>

    @Query("SELECT * FROM favorite_memberships WHERE profileId = :profileId")
    fun observeMemberships(profileId: Int): Flow<List<FavoriteMembership>>

    @Query("SELECT * FROM favorite_groups WHERE profileId = :profileId ORDER BY name COLLATE NOCASE")
    suspend fun getGroups(profileId: Int): List<FavoriteGroup>

    @Query(
        """
        SELECT channels.* FROM channels
        INNER JOIN favorite_memberships
            ON channels.profileId = favorite_memberships.profileId
            AND channels.streamId = favorite_memberships.streamId
        WHERE favorite_memberships.profileId = :profileId
            AND favorite_memberships.groupId = :groupId
        ORDER BY channels.providerOrder
        """,
    )
    fun observeChannels(profileId: Int, groupId: Long): Flow<List<SavedChannel>>

    @Query(
        """
        SELECT channels.* FROM channels
        INNER JOIN favorite_memberships
            ON channels.profileId = favorite_memberships.profileId
            AND channels.streamId = favorite_memberships.streamId
        WHERE favorite_memberships.profileId = :profileId
            AND favorite_memberships.groupId = :groupId
        ORDER BY channels.providerOrder
        """,
    )
    suspend fun getChannels(profileId: Int, groupId: Long): List<SavedChannel>

    @Query(
        "SELECT groupId FROM favorite_memberships " +
            "WHERE profileId = :profileId AND streamId = :streamId",
    )
    fun observeGroupIds(profileId: Int, streamId: Int): Flow<List<Long>>

    @Insert
    suspend fun createGroup(group: FavoriteGroup): Long

    @Query(
        "UPDATE favorite_groups SET name = :name " +
            "WHERE profileId = :profileId AND id = :groupId",
    )
    suspend fun renameGroup(profileId: Int, groupId: Long, name: String)

    @Query(
        "SELECT COUNT(*) FROM favorite_groups WHERE profileId = :profileId " +
            "AND name = :name COLLATE NOCASE AND id != :excludedGroupId",
    )
    suspend fun countGroupsNamed(profileId: Int, name: String, excludedGroupId: Long): Int

    @Delete
    suspend fun deleteGroup(group: FavoriteGroup)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(membership: FavoriteMembership)

    @Query(
        "DELETE FROM favorite_memberships " +
            "WHERE profileId = :profileId AND groupId = :groupId AND streamId = :streamId",
    )
    suspend fun remove(profileId: Int, groupId: Long, streamId: Int)

    @Query(
        "SELECT COUNT(*) FROM favorite_memberships " +
            "WHERE profileId = :profileId AND groupId = :groupId AND streamId = :streamId",
    )
    suspend fun contains(profileId: Int, groupId: Long, streamId: Int): Int

    @Query("DELETE FROM favorite_memberships WHERE profileId = :profileId")
    suspend fun clearMemberships(profileId: Int)

    @Query("DELETE FROM favorite_groups WHERE profileId = :profileId")
    suspend fun clearGroups(profileId: Int)
}

@Dao
interface VodDao {
    @Query("SELECT * FROM vod_categories WHERE profileId = :profileId ORDER BY categoryOrder, categoryName")
    suspend fun getStoredCategories(profileId: Int): List<VodCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCategories(categories: List<VodCategoryEntity>)

    @Query("DELETE FROM vod_categories WHERE profileId = :profileId")
    suspend fun clearCategories(profileId: Int)

    @Query("SELECT * FROM vod_movies WHERE profileId = :profileId ORDER BY providerOrder")
    suspend fun getMovies(profileId: Int): List<VodMovieEntity>

    @Query(
        "SELECT categoryId, categoryName, MIN(categoryOrder) AS categoryOrder " +
            "FROM vod_movies WHERE profileId = :profileId " +
            "GROUP BY categoryId, categoryName ORDER BY categoryOrder, categoryName",
    )
    suspend fun getCategories(profileId: Int): List<VodCategoryRow>

    @Query("SELECT streamId, name FROM vod_movies WHERE profileId = :profileId ORDER BY providerOrder")
    suspend fun getTitleIndex(profileId: Int): List<VodTitleRow>

    @Query("SELECT * FROM vod_movies WHERE profileId = :profileId AND streamId IN (:streamIds) ORDER BY providerOrder")
    suspend fun getMoviesByIds(profileId: Int, streamIds: List<Int>): List<VodMovieEntity>

    @Query(
        "SELECT * FROM vod_movies WHERE profileId = :profileId AND categoryId = :categoryId " +
            "ORDER BY providerOrder LIMIT :limit OFFSET :offset",
    )
    suspend fun getMoviesPage(profileId: Int, categoryId: String, limit: Int, offset: Int): List<VodMovieEntity>

    @Query(
        "SELECT * FROM vod_movies " +
            "WHERE profileId = :profileId AND name LIKE '%' || :query || '%' " +
            "ORDER BY providerOrder",
    )
    suspend fun searchMoviesByTitle(profileId: Int, query: String): List<VodMovieEntity>

    @Query("SELECT * FROM vod_movies WHERE profileId = :profileId AND streamId = :streamId LIMIT 1")
    suspend fun getMovie(profileId: Int, streamId: Int): VodMovieEntity?

    @Query(
        """
        SELECT vod_movies.* FROM vod_movies
        INNER JOIN vod_actor_index
            ON vod_movies.profileId = vod_actor_index.profileId
            AND vod_movies.streamId = vod_actor_index.streamId
        WHERE vod_actor_index.profileId = :profileId
            AND vod_actor_index.actorKey LIKE '%' || :actorKey || '%'
        ORDER BY vod_movies.providerOrder
        """,
    )
    suspend fun getMoviesByActor(profileId: Int, actorKey: String): List<VodMovieEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMovies(movies: List<VodMovieEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMovie(movie: VodMovieEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveActorIndex(rows: List<VodActorIndexEntity>)

    @Query("DELETE FROM vod_actor_index WHERE profileId = :profileId")
    suspend fun clearActorIndex(profileId: Int)

    @Query("SELECT COUNT(*) FROM vod_actor_index WHERE profileId = :profileId")
    suspend fun countActorIndex(profileId: Int): Int

    @Query("DELETE FROM vod_movies WHERE profileId = :profileId")
    suspend fun clearMovies(profileId: Int)

    @Query("SELECT * FROM vod_catalog_meta WHERE profileId = :profileId LIMIT 1")
    suspend fun getMeta(profileId: Int): VodCatalogMeta?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMeta(meta: VodCatalogMeta)

    @Query("DELETE FROM vod_catalog_meta WHERE profileId = :profileId")
    suspend fun clearMeta(profileId: Int)
}
