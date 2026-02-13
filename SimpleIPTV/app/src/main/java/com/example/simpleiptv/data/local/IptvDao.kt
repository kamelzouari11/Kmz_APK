package com.example.simpleiptv.data.local

import androidx.room.*
import com.example.simpleiptv.data.local.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface IptvDao {

    // --- IPTV Categories ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Query(
            "SELECT * FROM categories WHERE profileId = :profileId AND type = :type ORDER BY sortOrder ASC"
    )
    fun getAllCategories(profileId: Int, type: String = "LIVE"): Flow<List<CategoryEntity>>

    @Query("DELETE FROM categories WHERE profileId = :profileId AND type = :type")
    suspend fun clearCategories(profileId: Int, type: String = "LIVE")

    // --- IPTV Channels ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannelCategoryLinks(links: List<ChannelCategoryCrossRef>)

    @Query(
            """
        SELECT channels.* FROM channels 
        INNER JOIN channel_category_links ON 
            channels.stream_id = channel_category_links.channelId AND 
            channels.profileId = channel_category_links.profileId AND
            channels.type = channel_category_links.type
        WHERE channel_category_links.categoryId = :categoryId 
          AND channels.profileId = :profileId 
          AND channels.type = :type
        ORDER BY channels.sortOrder ASC
    """
    )
    fun getChannelsByCategory(
            categoryId: String,
            profileId: Int,
            type: String = "LIVE"
    ): Flow<List<ChannelEntity>>

    @Query("DELETE FROM channels WHERE profileId = :profileId AND type = :type")
    suspend fun clearChannels(profileId: Int, type: String = "LIVE")

    @Query("SELECT stream_id FROM channels WHERE profileId = :profileId AND type = :type")
    suspend fun getChannelIds(profileId: Int, type: String = "LIVE"): List<String>

    @Query("SELECT COUNT(*) FROM channels WHERE profileId = :profileId AND type = :type")
    suspend fun getChannelCount(profileId: Int, type: String = "LIVE"): Int

    @Query(
            "DELETE FROM channels WHERE profileId = :profileId AND type = :type AND stream_id IN (:ids)"
    )
    suspend fun deleteChannelsByIds(profileId: Int, type: String, ids: List<String>)

    @Query("DELETE FROM channel_category_links WHERE profileId = :profileId AND type = :type")
    suspend fun clearChannelCategoryLinks(profileId: Int, type: String = "LIVE")

    @Query(
            "SELECT * FROM channels WHERE profileId = :profileId AND type = :type AND name LIKE '%' || :query || '%' ORDER BY sortOrder ASC"
    )
    fun searchChannels(
            query: String,
            profileId: Int,
            type: String = "LIVE"
    ): Flow<List<ChannelEntity>>

    // --- IPTV Favorite Lists ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFavoriteList(list: FavoriteListEntity)

    @Query(
            "SELECT * FROM favorite_lists WHERE profileId = :profileId AND type = :type ORDER BY id ASC"
    )
    fun getAllFavoriteLists(profileId: Int, type: String = "LIVE"): Flow<List<FavoriteListEntity>>

    @Delete suspend fun deleteFavoriteList(list: FavoriteListEntity)

    // --- IPTV Channel Favorites ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addChannelToFavorite(crossRef: ChannelFavoriteCrossRef)

    @Delete suspend fun removeChannelFromFavorite(crossRef: ChannelFavoriteCrossRef)

    @Query(
            """
        SELECT channels.* FROM channels 
        INNER JOIN channel_favorites ON 
            channels.stream_id = channel_favorites.channelId AND 
            channels.profileId = channel_favorites.profileId AND
            channels.type = channel_favorites.type
        WHERE channel_favorites.listId = :listId 
          AND channels.profileId = :profileId 
          AND channels.type = :type
        ORDER BY channels.sortOrder ASC
    """
    )
    fun getChannelsByFavoriteList(
            listId: Int,
            profileId: Int,
            type: String = "LIVE"
    ): Flow<List<ChannelEntity>>

    @Query(
            "SELECT MAX(sortPosition) FROM channel_favorites WHERE listId = :listId AND profileId = :profileId AND type = :type"
    )
    suspend fun getMaxPositionForList(listId: Int, profileId: Int, type: String = "LIVE"): Int?

    @Query(
            "SELECT * FROM channel_favorites WHERE channelId = :channelId AND listId = :listId AND profileId = :profileId AND type = :type"
    )
    suspend fun getFavoriteCrossRef(
            channelId: String,
            listId: Int,
            profileId: Int,
            type: String = "LIVE"
    ): ChannelFavoriteCrossRef?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateFavoriteCrossRef(crossRef: ChannelFavoriteCrossRef)

    @Query(
            "SELECT listId FROM channel_favorites WHERE channelId = :channelId AND profileId = :profileId AND type = :type"
    )
    suspend fun getListIdsForChannel(
            channelId: String,
            profileId: Int,
            type: String = "LIVE"
    ): List<Int>

    @Query(
            "SELECT * FROM channels WHERE stream_id = :channelId AND profileId = :profileId AND type = :type"
    )
    suspend fun getChannelById(
            channelId: String,
            profileId: Int,
            type: String = "LIVE"
    ): ChannelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannel(channel: ChannelEntity)

    // --- IPTV Recents ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecent(recent: RecentChannelEntity)

    @Query(
            """
        SELECT channels.* FROM channels 
        INNER JOIN recent_channels ON 
            channels.stream_id = recent_channels.channelId AND 
            channels.profileId = recent_channels.profileId AND
            channels.type = recent_channels.type
        WHERE channels.profileId = :profileId AND channels.type = :type
        ORDER BY recent_channels.timestamp DESC 
        LIMIT 20
    """
    )
    fun getRecentChannels(profileId: Int, type: String = "LIVE"): Flow<List<ChannelEntity>>

    @Query(
            """
        DELETE FROM recent_channels WHERE profileId = :profileId AND type = :type AND channelId NOT IN (
            SELECT channelId FROM recent_channels WHERE profileId = :profileId AND type = :type ORDER BY timestamp DESC LIMIT 20
        )
    """
    )
    suspend fun trimRecents(profileId: Int, type: String = "LIVE")

    @Query("DELETE FROM favorite_lists WHERE profileId = :profileId AND type = :type")
    suspend fun clearFavoriteLists(profileId: Int, type: String = "LIVE")

    @Query("DELETE FROM channel_favorites WHERE profileId = :profileId AND type = :type")
    suspend fun clearChannelFavorites(profileId: Int, type: String = "LIVE")

    @Query("DELETE FROM recent_channels WHERE profileId = :profileId AND type = :type")
    suspend fun clearRecents(profileId: Int, type: String = "LIVE")

    // --- IPTV Profiles ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity)

    @Update suspend fun updateProfile(profile: ProfileEntity)

    @Delete suspend fun deleteProfile(profile: ProfileEntity)

    @Query("SELECT * FROM profiles") fun getAllProfiles(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedProfile(): ProfileEntity?

    @Query("UPDATE profiles SET isSelected = 0") suspend fun deselectAllProfiles()

    @Query("UPDATE profiles SET isSelected = 1 WHERE id = :id") suspend fun selectProfile(id: Int)

    @Transaction
    suspend fun syncProfileData(
            profileId: Int,
            categories: List<CategoryEntity>,
            channels: List<ChannelEntity>,
            links: List<ChannelCategoryCrossRef>,
            type: String = "LIVE"
    ) {
        clearChannelCategoryLinks(profileId, type)
        clearCategories(profileId, type)
        insertCategories(categories)

        val currentIds = getChannelIds(profileId, type).toSet()
        val newIds = channels.map { it.stream_id }.toSet()
        val idsToDelete = currentIds.minus(newIds).toList()

        if (idsToDelete.isNotEmpty()) {
            idsToDelete.chunked(900).forEach { chunk ->
                deleteChannelsByIds(profileId, type, chunk)
            }
        }

        insertChannels(channels)
        insertChannelCategoryLinks(links)
    }
}
