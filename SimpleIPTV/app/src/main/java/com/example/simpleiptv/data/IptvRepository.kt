package com.example.simpleiptv.data

import com.example.simpleiptv.data.api.XtreamApi
import com.example.simpleiptv.data.api.XtreamClient
import com.example.simpleiptv.data.local.IptvDao
import com.example.simpleiptv.data.local.entities.*
import com.example.simpleiptv.data.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

    suspend fun getListIdsForChannel(channelId: String, profileId: Int): List<Int> {
        return dao.getListIdsForChannel(channelId, profileId)
    }

    suspend fun addToRecents(channelId: String, profileId: Int) {
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

    suspend fun refreshDatabase(profile: ProfileEntity) {
        val baseUrl = profile.url
        val user = profile.username
        val pass = profile.password

        if (profile.type == "stalker") {
            refreshStalker(profile)
        } else {
            refreshXtream(profile.id, baseUrl, user, pass)
        }
    }

    private suspend fun refreshXtream(profileId: Int, baseUrl: String, user: String, pass: String) {
        val dynamicApi = XtreamClient.create(baseUrl)
        val apiCategories = dynamicApi.getLiveCategories(user, pass)
        val apiChannels = dynamicApi.getLiveStreams(user, pass)

        val categoryEntities =
                apiCategories.map { cat ->
                    CategoryEntity(cat.category_id ?: "0", cat.category_name, profileId)
                }
        val channelEntitiesMap = mutableMapOf<String, ChannelEntity>()
        val linkEntities = mutableListOf<ChannelCategoryCrossRef>()

        apiChannels.forEach { ch ->
            val id = ch.stream_id.toString()
            channelEntitiesMap[id] = ChannelEntity(id, ch.name, ch.stream_icon, profileId)
            linkEntities.add(ChannelCategoryCrossRef(id, ch.category_id ?: "0", profileId))
        }

        saveToDao(profileId, categoryEntities, channelEntitiesMap.values.toList(), linkEntities)
    }

    private suspend fun refreshStalker(profile: ProfileEntity) {
        val mac =
                profile.macAddress?.trim()?.uppercase()
                        ?: throw IllegalArgumentException("MAC Address required for Stalker")
        val api = com.example.simpleiptv.data.api.StalkerClient.create(profile.url, mac)

        // 1. Handshake
        val handshake = api.handshake(mac)
        val token = "Bearer " + handshake.js.token

        // 2. Genres
        val genresResponse = api.getGenres(token)
        val categoryEntities =
                genresResponse.js.map { genre -> CategoryEntity(genre.id, genre.title, profile.id) }

        val channelEntitiesMap = mutableMapOf<String, ChannelEntity>()
        val linkEntities = mutableListOf<ChannelCategoryCrossRef>()

        // 3. Channels
        val channelsData = mutableListOf<Map<String, Any?>>()
        var globalFetchSuccess = false

        try {
            val allChannelsResponse = api.getAllChannels(token)
            val rawData = allChannelsResponse.js

            val allList =
                    when (rawData) {
                        is List<*> -> rawData.filterIsInstance<Map<String, Any?>>()
                        is Map<*, *> -> {
                            if (rawData.containsKey("data") && rawData["data"] is List<*>) {
                                (rawData["data"] as List<*>).filterIsInstance<Map<String, Any?>>()
                            } else {
                                rawData.values.filterIsInstance<Map<String, Any?>>()
                            }
                        }
                        else -> emptyList()
                    }

            if (allList.isNotEmpty()) {
                // Check quality of data - do we have genre IDs?
                val firstWithGenre = allList.find { it["tv_genre_id"] != null }
                if (firstWithGenre == null && categoryEntities.isNotEmpty()) {

                    globalFetchSuccess = false
                } else {

                    channelsData.addAll(allList)
                    globalFetchSuccess = true
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("StalkerRepo", "get_all_channels failed", e)
        }

        // If global fetch failed or data was poor, iterate per genre
        if (!globalFetchSuccess) {

            // ... (existing per-genre loop) ...
            // OPTIMIZED: Parallel fetching of categories
            // Instead of fetching one by one, we launch all requests simultaneously
            kotlinx.coroutines.coroutineScope {
                val deferredResults =
                        categoryEntities.map { category ->
                            async {
                                try {
                                    val channelsResponse =
                                            api.getChannels(token, category.category_id)
                                    val rawData = channelsResponse.js
                                    val genreList =
                                            when (rawData) {
                                                is List<*> ->
                                                        rawData.filterIsInstance<
                                                                Map<String, Any?>>()
                                                is Map<*, *> ->
                                                        rawData.values.filterIsInstance<
                                                                Map<String, Any?>>()
                                                else -> emptyList()
                                            }

                                    if (genreList.isNotEmpty()) {
                                        // Enrich with genre ID
                                        genreList.map {
                                            it + ("tv_genre_id" to category.category_id)
                                        }
                                    } else {
                                        emptyList()
                                    }
                                } catch (e: Exception) {
                                    emptyList<Map<String, Any?>>()
                                }
                            }
                        }
                // Wait for all requests to finish and merge results
                val allResults: List<List<Map<String, Any?>>> = deferredResults.awaitAll()
                val allFetched = allResults.flatten()
                channelsData.addAll(allFetched)
            }
        } else {}

        // Process all gathered channels
        channelsData.forEach { chMap ->
            val id = chMap["id"]?.toString() ?: return@forEach
            val name = chMap["name"]?.toString() ?: "Unknown"
            val logoUrl = chMap["logo"]?.toString()
            val cmd = chMap["cmd"]?.toString() ?: ""
            // Use provided tv_genre_id or fallback to trying to find it
            val genreId = chMap["tv_genre_id"]?.toString()

            val icon =
                    if (!logoUrl.isNullOrEmpty()) {
                        if (logoUrl.startsWith("http")) logoUrl
                        else "${profile.url}/${logoUrl.removePrefix("/")}"
                    } else null

            if (!channelEntitiesMap.containsKey(id)) {
                channelEntitiesMap[id] = ChannelEntity(id, name, icon, profile.id, cmd)
            }

            // Link to category if we have a genre ID
            if (genreId != null) {
                linkEntities.add(ChannelCategoryCrossRef(id, genreId, profile.id))
            } else {
                // Fallback: If we fetched per-genre, we already injected the ID.
                // If we came from get_all_channels but no tv_genre_id field, we might lose mapping.
                // Most 'get_all_channels' include 'tv_genre_id'.

            }
        }

        saveToDao(profile.id, categoryEntities, channelEntitiesMap.values.toList(), linkEntities)
    }

    private suspend fun saveToDao(
            profileId: Int,
            categories: List<CategoryEntity>,
            channels: List<ChannelEntity>,
            links: List<ChannelCategoryCrossRef>
    ) {
        // 1. Purge Categories and Links (Full Reset for these to handle renaming/moving)
        dao.clearChannelCategoryLinks(profileId)
        dao.clearCategories(profileId)
        dao.insertCategories(categories)

        // 2. Smart Sync Channels: Delete only orphaned channels (ghosts), Upsert the rest
        // This preserves favorites if the channel ID still exists in the new scan
        val currentIds = dao.getChannelIds(profileId).toSet()
        val newIds = channels.map { it.stream_id }.toSet()
        val idsToDelete = currentIds.minus(newIds).toList()

        if (idsToDelete.isNotEmpty()) {
            // Chunking to avoid SQLite parameter limit (999 usually)
            idsToDelete.chunked(900).forEach { chunk -> dao.deleteChannelsByIds(profileId, chunk) }
        }

        dao.insertChannels(channels)
        dao.insertChannelCategoryLinks(links)
    }

    suspend fun getStreamUrl(profile: ProfileEntity, channelId: String): String {
        return if (profile.type == "stalker") {
            // Retrieve a fresh link from Stalker API
            val mac = profile.macAddress ?: return ""
            val api = com.example.simpleiptv.data.api.StalkerClient.create(profile.url, mac)
            val handshake = api.handshake(mac)
            val token = "Bearer " + handshake.js.token

            val channel = dao.getChannelById(channelId, profile.id)
            val rawCmd = channel?.extraParams

            // Fix for 444 Error (Stalker):
            // The server fails to parse complex URLs passed as 'cmd'.
            // It expects the Channel/Stream ID for lookup.
            // If the command looks like a URL with stream ID, we rely on the channelId.
            // We observed that sending the full URL resulted in 'stream=' (empty) in the response.
            val cmdToSend =
                    if (!rawCmd.isNullOrEmpty() && rawCmd.contains("stream=")) {
                        channelId
                    } else {
                        if (rawCmd?.startsWith("ffmpeg ") == true) {
                            rawCmd.substringAfter("ffmpeg ").trim()
                        } else {
                            rawCmd ?: channelId
                        }
                    }

            // Try creating link
            val linkResponse = api.createLink(token, cmdToSend)
            var url = linkResponse.js.cmd

            // Cleanup: Strip "ffmpeg " prefix if present in the response
            if (url.startsWith("ffmpeg ")) {
                url = url.substringAfter("ffmpeg ").trim()
            }

            // Fix for Empty Stream ID (Stalker Server Bug):
            // The server is returning URLs with "stream=&" even when a valid Channel ID is sent.
            // This causes Error 444. We manually inject the Channel ID back into the URL.
            if (url.contains("stream=&")) {
                android.util.Log.w(
                        "StalkerRepo",
                        "Server returned empty stream ID. Patching URL with ID: $channelId"
                )
                url = url.replace("stream=&", "stream=$channelId&")
            } else if (url.endsWith("stream=")) {
                android.util.Log.w(
                        "StalkerRepo",
                        "Server returned empty stream ID. Patching URL with ID: $channelId"
                )
                url = url + channelId
            }

            url
        } else {
            val baseUrl = if (profile.url.endsWith("/")) profile.url else "${profile.url}/"
            "${baseUrl}live/${profile.username}/${profile.password}/${channelId}.ts"
        }
    }
}
