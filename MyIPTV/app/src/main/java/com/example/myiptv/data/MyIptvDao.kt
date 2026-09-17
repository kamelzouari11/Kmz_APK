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
