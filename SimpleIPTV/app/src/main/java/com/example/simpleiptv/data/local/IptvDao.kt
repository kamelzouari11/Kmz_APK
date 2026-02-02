package com.example.simpleiptv.data.local

import androidx.room.*
import com.example.simpleiptv.data.local.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface IptvDao {

    // --- IPTV Categories ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Query("SELECT * FROM categories WHERE profileId = :profileId")
    fun getAllCategories(profileId: Int): Flow<List<CategoryEntity>>

    @Query("DELETE FROM categories WHERE profileId = :profileId")
    suspend fun clearCategories(profileId: Int)

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
            channels.profileId = channel_category_links.profileId
        WHERE channel_category_links.categoryId = :categoryId AND channels.profileId = :profileId
    """
    )
    fun getChannelsByCategory(categoryId: String, profileId: Int): Flow<List<ChannelEntity>>

    @Query("DELETE FROM channels WHERE profileId = :profileId")
    suspend fun clearChannels(profileId: Int)

    @Query("DELETE FROM channel_category_links WHERE profileId = :profileId")
    suspend fun clearChannelCategoryLinks(profileId: Int)

    @Query("SELECT * FROM channels WHERE profileId = :profileId AND name LIKE '%' || :query || '%'")
    fun searchChannels(query: String, profileId: Int): Flow<List<ChannelEntity>>

    // --- IPTV Favorite Lists ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFavoriteList(list: FavoriteListEntity)

    @Query("SELECT * FROM favorite_lists WHERE profileId = :profileId")
    fun getAllFavoriteLists(profileId: Int): Flow<List<FavoriteListEntity>>

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
            channels.profileId = channel_favorites.profileId
        WHERE channel_favorites.listId = :listId AND channels.profileId = :profileId
        ORDER BY channel_favorites.sortPosition ASC
    """
    )
    fun getChannelsByFavoriteList(listId: Int, profileId: Int): Flow<List<ChannelEntity>>

    @Query(
            "SELECT MAX(sortPosition) FROM channel_favorites WHERE listId = :listId AND profileId = :profileId"
    )
    suspend fun getMaxPositionForList(listId: Int, profileId: Int): Int?

    @Query(
            "SELECT * FROM channel_favorites WHERE channelId = :channelId AND listId = :listId AND profileId = :profileId"
    )
    suspend fun getFavoriteCrossRef(
            channelId: String,
            listId: Int,
            profileId: Int
    ): ChannelFavoriteCrossRef?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateFavoriteCrossRef(crossRef: ChannelFavoriteCrossRef)

    @Query(
            "SELECT listId FROM channel_favorites WHERE channelId = :channelId AND profileId = :profileId"
    )
    suspend fun getListIdsForChannel(channelId: String, profileId: Int): List<Int>

    @Query("SELECT * FROM channels WHERE stream_id = :channelId AND profileId = :profileId")
    suspend fun getChannelById(channelId: String, profileId: Int): ChannelEntity?

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
            channels.profileId = recent_channels.profileId
        WHERE channels.profileId = :profileId
        ORDER BY recent_channels.timestamp DESC 
        LIMIT 20
    """
    )
    fun getRecentChannels(profileId: Int): Flow<List<ChannelEntity>>

    @Query(
            """
        DELETE FROM recent_channels WHERE profileId = :profileId AND channelId NOT IN (
            SELECT channelId FROM recent_channels WHERE profileId = :profileId ORDER BY timestamp DESC LIMIT 20
        )
    """
    )
    suspend fun trimRecents(profileId: Int)

    @Query("DELETE FROM favorite_lists WHERE profileId = :profileId")
    suspend fun clearFavoriteLists(profileId: Int)

    @Query("DELETE FROM channel_favorites WHERE profileId = :profileId")
    suspend fun clearChannelFavorites(profileId: Int)

    @Query("DELETE FROM recent_channels WHERE profileId = :profileId")
    suspend fun clearRecents(profileId: Int)

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
}
