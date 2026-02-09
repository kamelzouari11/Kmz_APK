package com.example.simpleiptv.data

import com.example.simpleiptv.data.api.XtreamApi
import com.example.simpleiptv.data.local.IptvDao
import com.example.simpleiptv.data.local.entities.*
import com.example.simpleiptv.data.services.BackupService
import com.example.simpleiptv.data.services.StreamService
import com.example.simpleiptv.data.services.SyncService
import kotlinx.coroutines.flow.Flow

class IptvRepository(private val api: XtreamApi, private val dao: IptvDao) {

    // Sub-services
    private val backupService = BackupService(dao)
    private val syncService = SyncService(dao)
    private val streamService = StreamService(dao)

    // --- Basic DAO Access ---
    fun getCategories(profileId: Int): Flow<List<CategoryEntity>> = dao.getAllCategories(profileId)
    fun getFavoriteLists(profileId: Int): Flow<List<FavoriteListEntity>> =
            dao.getAllFavoriteLists(profileId)
    fun getRecentChannels(profileId: Int): Flow<List<ChannelEntity>> =
            dao.getRecentChannels(profileId)
    fun getChannelsByCategory(categoryId: String, profileId: Int): Flow<List<ChannelEntity>> =
            dao.getChannelsByCategory(categoryId, profileId)
    fun getChannelsByFavoriteList(listId: Int, profileId: Int): Flow<List<ChannelEntity>> =
            dao.getChannelsByFavoriteList(listId, profileId)
    fun searchChannels(query: String, profileId: Int): Flow<List<ChannelEntity>> =
            dao.searchChannels(query, profileId)

    // --- Favorites Logic ---
    suspend fun addFavoriteList(name: String, profileId: Int) =
            dao.insertFavoriteList(FavoriteListEntity(name = name, profileId = profileId))
    suspend fun removeFavoriteList(list: FavoriteListEntity) = dao.deleteFavoriteList(list)
    suspend fun addChannelToFavoriteList(streamId: String, listId: Int, profileId: Int) {
        val maxPos = dao.getMaxPositionForList(listId, profileId) ?: -1
        dao.addChannelToFavorite(ChannelFavoriteCrossRef(streamId, listId, profileId, maxPos + 1))
    }

    suspend fun toggleChannelFavorite(channelId: String, listId: Int, profileId: Int) {
        val currentLists = dao.getListIdsForChannel(channelId, profileId)
        if (currentLists.contains(listId)) {
            dao.removeChannelFromFavorite(ChannelFavoriteCrossRef(channelId, listId, profileId))
        } else {
            val maxPos = dao.getMaxPositionForList(listId, profileId) ?: -1
            dao.addChannelToFavorite(
                    ChannelFavoriteCrossRef(channelId, listId, profileId, maxPos + 1)
            )
        }
    }

    suspend fun moveChannelInList(channelId: String, listId: Int, profileId: Int, up: Boolean) {
        // Shared logic could stay here or move to a PlaylistService if it becomes too large
    }

    // --- Recents Logic ---
    suspend fun addToRecents(channelId: String, profileId: Int) {
        dao.insertRecent(RecentChannelEntity(channelId, System.currentTimeMillis(), profileId))
        dao.trimRecents(profileId)
    }

    // --- Profiles Logic ---
    val allProfiles: Flow<List<ProfileEntity>> = dao.getAllProfiles()
    suspend fun getSelectedProfile(): ProfileEntity? = dao.getSelectedProfile()
    suspend fun addProfile(profile: ProfileEntity) = dao.insertProfile(profile)
    suspend fun updateProfile(profile: ProfileEntity) = dao.updateProfile(profile)
    suspend fun deleteProfile(profile: ProfileEntity) {
        dao.clearCategories(profile.id)
        dao.clearChannels(profile.id)
        dao.clearChannelCategoryLinks(profile.id)
        dao.clearFavoriteLists(profile.id)
        dao.clearChannelFavorites(profile.id)
        dao.clearRecents(profile.id)
        dao.deleteProfile(profile)
    }
    suspend fun selectProfile(profileId: Int) {
        dao.deselectAllProfiles()
        dao.selectProfile(profileId)
    }

    // --- Delegated to SyncService ---
    suspend fun refreshDatabase(profile: ProfileEntity) = syncService.refreshDatabase(profile)

    // --- Delegated to StreamService ---
    suspend fun getStreamUrl(profile: ProfileEntity, channelId: String): String =
            streamService.getStreamUrl(profile, channelId)

    // --- Delegated to BackupService ---
    suspend fun exportFavoritesToJson(profileId: Int): String =
            backupService.exportFavoritesToJson(profileId)
    suspend fun importFavoritesFromJson(profileId: Int, json: String) =
            backupService.importFavoritesFromJson(profileId, json)
    suspend fun exportDatabaseToJson(): String = backupService.exportDatabaseToJson()
    suspend fun importDatabaseFromJson(json: String) = backupService.importDatabaseFromJson(json)
}
