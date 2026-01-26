package com.example.simpleiptv.data.local

import androidx.room.*
import com.example.simpleiptv.data.local.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface IptvDao {

    // --- IPTV Categories ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Query("SELECT * FROM categories")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Query("DELETE FROM categories")
    suspend fun clearCategories()

    // --- IPTV Channels ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannelCategoryLinks(links: List<ChannelCategoryCrossRef>)

    @Query("""
        SELECT channels.* FROM channels 
        INNER JOIN channel_category_links ON channels.stream_id = channel_category_links.channelId 
        WHERE channel_category_links.categoryId = :categoryId
    """)
    fun getChannelsByCategory(categoryId: String): Flow<List<ChannelEntity>>

    @Query("DELETE FROM channels")
    suspend fun clearChannels()

    @Query("DELETE FROM channel_category_links")
    suspend fun clearChannelCategoryLinks()

    @Query("SELECT * FROM channels WHERE name LIKE '%' || :query || '%'")
    fun searchChannels(query: String): Flow<List<ChannelEntity>>

    // --- IPTV Favorite Lists ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFavoriteList(list: FavoriteListEntity)

    @Query("SELECT * FROM favorite_lists")
    fun getAllFavoriteLists(): Flow<List<FavoriteListEntity>>

    @Delete
    suspend fun deleteFavoriteList(list: FavoriteListEntity)

    // --- IPTV Channel Favorites ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addChannelToFavorite(crossRef: ChannelFavoriteCrossRef)

    @Delete
    suspend fun removeChannelFromFavorite(crossRef: ChannelFavoriteCrossRef)

    @Query("""
        SELECT channels.* FROM channels 
        INNER JOIN channel_favorites ON channels.stream_id = channel_favorites.channelId 
        WHERE channel_favorites.listId = :listId
    """)
    fun getChannelsByFavoriteList(listId: Int): Flow<List<ChannelEntity>>

    @Query("SELECT listId FROM channel_favorites WHERE channelId = :channelId")
    suspend fun getListIdsForChannel(channelId: Int): List<Int>

    // --- IPTV Recents ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecent(recent: RecentChannelEntity)

    @Query("""
        SELECT channels.* FROM channels 
        INNER JOIN recent_channels ON channels.stream_id = recent_channels.channelId 
        ORDER BY recent_channels.timestamp DESC 
        LIMIT 20
    """)
    fun getRecentChannels(): Flow<List<ChannelEntity>>

    @Query("""
        DELETE FROM recent_channels WHERE channelId NOT IN (
            SELECT channelId FROM recent_channels ORDER BY timestamp DESC LIMIT 20
        )
    """)
    suspend fun trimRecents()
}
