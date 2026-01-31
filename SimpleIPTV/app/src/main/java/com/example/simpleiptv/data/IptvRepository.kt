package com.example.simpleiptv.data

import com.example.simpleiptv.data.api.XtreamApi
import com.example.simpleiptv.data.api.XtreamClient
import com.example.simpleiptv.data.local.IptvDao
import com.example.simpleiptv.data.local.entities.*
import com.example.simpleiptv.data.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class IptvRepository(private val api: XtreamApi, private val dao: IptvDao) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    fun getCategories(profileId: Int): Flow<List<CategoryEntity>> = dao.getAllCategories(profileId)
    fun getFavoriteLists(profileId: Int): Flow<List<FavoriteListEntity>> =
            dao.getAllFavoriteLists(profileId)
    fun getRecentChannels(profileId: Int): Flow<List<ChannelEntity>> =
            dao.getRecentChannels(profileId)

    // --- SAUVEGARDE / EXPORT ---
    suspend fun exportFavoritesToJson(profileId: Int): String {
        val lists = dao.getAllFavoriteLists(profileId).first()
        val backupLists =
                lists.map { list ->
                    val channels = dao.getChannelsByFavoriteList(list.id, profileId).first()
                    BackupFavoriteList(name = list.name, channels = channels)
                }
        val backup = IptvBackup(favoriteLists = backupLists)
        val adapter = moshi.adapter(IptvBackup::class.java)
        return adapter.toJson(backup)
    }

    suspend fun importFavoritesFromJson(profileId: Int, json: String) {
        val adapter = moshi.adapter(IptvBackup::class.java)
        val backup = adapter.fromJson(json) ?: return
        importBackupData(profileId, backup.favoriteLists)
    }

    private suspend fun importBackupData(profileId: Int, favoriteLists: List<BackupFavoriteList>) {
        favoriteLists.forEach { backupList ->
            dao.insertFavoriteList(
                    FavoriteListEntity(name = backupList.name, profileId = profileId)
            )
            val allLists = dao.getAllFavoriteLists(profileId).first()
            val targetList = allLists.find { it.name == backupList.name }

            if (targetList != null) {
                backupList.channels.forEach { channel ->
                    dao.insertChannel(channel.copy(profileId = profileId))
                }
                backupList.channels.forEachIndexed { index, channel ->
                    dao.addChannelToFavorite(
                            ChannelFavoriteCrossRef(
                                    channel.stream_id,
                                    targetList.id,
                                    profileId,
                                    index
                            )
                    )
                }
            }
        }
    }

    // --- FULL DATABASE BACKUP ---
    suspend fun exportDatabaseToJson(): String {
        val profiles = dao.getAllProfiles().first()
        val profileBackups =
                profiles.map { profile ->
                    val lists = dao.getAllFavoriteLists(profile.id).first()
                    val backupLists =
                            lists.map { list ->
                                val channels =
                                        dao.getChannelsByFavoriteList(list.id, profile.id).first()
                                BackupFavoriteList(name = list.name, channels = channels)
                            }
                    ProfileBackup(
                            profile = profile.copy(id = 0, isSelected = false),
                            favoriteLists = backupLists
                    )
                }
        val backup = FullDatabaseBackup(profileBackups = profileBackups)
        return moshi.adapter(FullDatabaseBackup::class.java).toJson(backup)
    }

    suspend fun importDatabaseFromJson(json: String) {
        val adapter = moshi.adapter(FullDatabaseBackup::class.java)
        val backup = adapter.fromJson(json) ?: return

        backup.profileBackups.forEach { profileBackup ->
            dao.insertProfile(profileBackup.profile)
            // Get the newly created profile ID (by matching name/url)
            val allProfiles = dao.getAllProfiles().first()
            val newProfile =
                    allProfiles.find {
                        it.profileName == profileBackup.profile.profileName &&
                                it.url == profileBackup.profile.url
                    }

            if (newProfile != null) {
                importBackupData(newProfile.id, profileBackup.favoriteLists)
            }
        }
    }

    fun getChannelsByCategory(categoryId: String, profileId: Int): Flow<List<ChannelEntity>> {
        return dao.getChannelsByCategory(categoryId, profileId)
    }

    fun getChannelsByFavoriteList(listId: Int, profileId: Int): Flow<List<ChannelEntity>> {
        return dao.getChannelsByFavoriteList(listId, profileId)
    }

    fun searchChannels(query: String, profileId: Int): Flow<List<ChannelEntity>> {
        return dao.searchChannels(query, profileId)
    }

    suspend fun addFavoriteList(name: String, profileId: Int) {
        dao.insertFavoriteList(FavoriteListEntity(name = name, profileId = profileId))
    }

    suspend fun removeFavoriteList(list: FavoriteListEntity) {
        dao.deleteFavoriteList(list)
    }

    suspend fun toggleChannelFavorite(channelId: Int, listId: Int, profileId: Int) {
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

    suspend fun moveChannelInList(channelId: Int, listId: Int, profileId: Int, up: Boolean) {
        val channels = dao.getChannelsByFavoriteList(listId, profileId).first()
        val index = channels.indexOfFirst { it.stream_id == channelId }
        if (index == -1) return

        val targetIndex = if (up) index - 1 else index + 1
        if (targetIndex < 0 || targetIndex >= channels.size) return

        val currentRef = dao.getFavoriteCrossRef(channelId, listId, profileId) ?: return
        val targetChannel = channels[targetIndex]
        val targetRef =
                dao.getFavoriteCrossRef(targetChannel.stream_id, listId, profileId) ?: return

        // Swap positions
        val tempPos = currentRef.sortPosition
        dao.updateFavoriteCrossRef(currentRef.copy(sortPosition = targetRef.sortPosition))
        dao.updateFavoriteCrossRef(targetRef.copy(sortPosition = tempPos))
    }

    suspend fun getListIdsForChannel(channelId: Int, profileId: Int): List<Int> {
        return dao.getListIdsForChannel(channelId, profileId)
    }

    suspend fun addToRecents(channelId: Int, profileId: Int) {
        dao.insertRecent(RecentChannelEntity(channelId, System.currentTimeMillis(), profileId))
        dao.trimRecents(profileId)
    }

    // --- PROFILES ---
    val allProfiles: Flow<List<ProfileEntity>> = dao.getAllProfiles()

    suspend fun getSelectedProfile(): ProfileEntity? = dao.getSelectedProfile()

    suspend fun addProfile(profile: ProfileEntity) {
        dao.insertProfile(profile)
    }

    suspend fun updateProfile(profile: ProfileEntity) {
        dao.updateProfile(profile)
    }

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

    suspend fun refreshDatabase(profileId: Int, baseUrl: String, user: String, pass: String) {
        val dynamicApi = XtreamClient.create(baseUrl)
        val apiCategories = dynamicApi.getLiveCategories(user, pass)
        val apiChannels = dynamicApi.getLiveStreams(user, pass)

        val categoryEntities =
                apiCategories.map { cat ->
                    CategoryEntity(cat.category_id, cat.category_name, profileId)
                }
        val channelEntitiesMap = mutableMapOf<Int, ChannelEntity>()
        val linkEntities = mutableListOf<ChannelCategoryCrossRef>()

        apiChannels.forEach { ch ->
            channelEntitiesMap[ch.stream_id] =
                    ChannelEntity(ch.stream_id, ch.name, ch.stream_icon, profileId)
            linkEntities.add(ChannelCategoryCrossRef(ch.stream_id, ch.category_id, profileId))
        }

        dao.clearChannelCategoryLinks(profileId)
        dao.insertCategories(categoryEntities)
        dao.insertChannels(channelEntitiesMap.values.toList())
        dao.insertChannelCategoryLinks(linkEntities)
    }
}
