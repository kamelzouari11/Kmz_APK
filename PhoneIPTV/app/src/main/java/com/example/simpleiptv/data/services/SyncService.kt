package com.example.simpleiptv.data.services

import android.util.Log
import com.example.simpleiptv.data.api.StalkerClient
import com.example.simpleiptv.data.api.XtreamClient
import com.example.simpleiptv.data.local.IptvDao
import com.example.simpleiptv.data.local.entities.*
import com.example.simpleiptv.data.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class SyncService(private val dao: IptvDao) {

    suspend fun refreshDatabase(profile: ProfileEntity) {
        if (profile.type == "stalker") {
            refreshStalker(profile)
        } else {
            refreshXtream(profile)
            refreshVodXtream(profile)
        }
    }

    private suspend fun refreshXtream(profile: ProfileEntity) {
        val dynamicApi = XtreamClient.create(profile.url)
        val apiCategories = dynamicApi.getLiveCategories(profile.username, profile.password)
        val apiChannels = dynamicApi.getLiveStreams(profile.username, profile.password)

        val categoryEntities =
                apiCategories.mapIndexed { index, cat ->
                    CategoryEntity(
                            cat.category_id ?: "0",
                            cat.category_name,
                            profile.id,
                            type = "LIVE",
                            sortOrder = index
                    )
                }
        val channelEntitiesMap = mutableMapOf<String, ChannelEntity>()
        val linkEntities = mutableListOf<ChannelCategoryCrossRef>()

        apiChannels.forEachIndexed { index, ch ->
            val id = ch.stream_id.toString()
            channelEntitiesMap[id] =
                    ChannelEntity(
                            id,
                            ch.name,
                            ch.stream_icon,
                            profile.id,
                            type = "LIVE",
                            sortOrder = index
                    )
            linkEntities.add(
                    ChannelCategoryCrossRef(id, ch.category_id ?: "0", profile.id, type = "LIVE")
            )
        }

        saveToDao(
                profile.id,
                categoryEntities,
                channelEntitiesMap.values.toList(),
                linkEntities,
                type = "LIVE"
        )
    }

    private suspend fun refreshVodXtream(profile: ProfileEntity) {
        try {
            val dynamicApi = XtreamClient.create(profile.url)
            val apiCategories = dynamicApi.getVodCategories(profile.username, profile.password)
            val apiStreams = dynamicApi.getVodStreams(profile.username, profile.password)

            val categoryEntities =
                    apiCategories.mapIndexed { index, cat ->
                        CategoryEntity(
                                cat.category_id ?: "0",
                                cat.category_name,
                                profile.id,
                                type = "VOD",
                                sortOrder = index
                        )
                    }
            val channelEntitiesMap = mutableMapOf<String, ChannelEntity>()
            val linkEntities = mutableListOf<ChannelCategoryCrossRef>()

            apiStreams.forEachIndexed { index, ch ->
                val id = ch.stream_id.toString()
                channelEntitiesMap[id] =
                        ChannelEntity(
                                id,
                                ch.name,
                                ch.stream_icon,
                                profile.id,
                                type = "VOD",
                                extraParams = ch.container_extension,
                                sortOrder = index
                        )
                linkEntities.add(
                        ChannelCategoryCrossRef(id, ch.category_id ?: "0", profile.id, type = "VOD")
                )
            }

            saveToDao(
                    profile.id,
                    categoryEntities,
                    channelEntitiesMap.values.toList(),
                    linkEntities,
                    type = "VOD"
            )
        } catch (e: Exception) {
            Log.e("SyncService", "VOD Sync failed", e)
        }
    }

    private suspend fun refreshStalker(profile: ProfileEntity) {
        val mac =
                profile.macAddress?.trim()?.uppercase()
                        ?: throw IllegalArgumentException("MAC Address required for Stalker")
        val api = StalkerClient.create(profile.url, mac)

        val handshake = api.handshake(mac)
        val token = "Bearer " + handshake.js.token

        val genresResponse = api.getGenres(token)
        val categoryEntities =
                genresResponse.js.mapIndexed { index, genre ->
                    CategoryEntity(
                            genre.id,
                            genre.title,
                            profile.id,
                            type = "LIVE",
                            sortOrder = index
                    )
                }

        val channelEntitiesMap = mutableMapOf<String, ChannelEntity>()
        val linkEntities = mutableListOf<ChannelCategoryCrossRef>()
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
                val firstWithGenre = allList.find { it["tv_genre_id"] != null }
                if (firstWithGenre != null || categoryEntities.isEmpty()) {
                    channelsData.addAll(allList)
                    globalFetchSuccess = true
                }
            }
        } catch (e: Exception) {
            Log.w("SyncService", "get_all_channels failed", e)
        }

        if (!globalFetchSuccess) {
            coroutineScope {
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
                                    genreList.map { it + ("tv_genre_id" to category.category_id) }
                                } catch (e: Exception) {
                                    emptyList<Map<String, Any?>>()
                                }
                            }
                        }
                channelsData.addAll(deferredResults.awaitAll().flatten())
            }
        }

        channelsData.forEachIndexed { index, chMap ->
            val id = chMap["id"]?.toString() ?: return@forEachIndexed
            val name = chMap["name"]?.toString() ?: "Unknown"
            val logoUrl = chMap["logo"]?.toString()
            val cmd = chMap["cmd"]?.toString() ?: ""
            val genreId = chMap["tv_genre_id"]?.toString()

            val icon =
                    if (!logoUrl.isNullOrEmpty()) {
                        if (logoUrl.startsWith("http")) logoUrl
                        else "${profile.url}/${logoUrl.removePrefix("/")}"
                    } else null

            if (!channelEntitiesMap.containsKey(id)) {
                channelEntitiesMap[id] =
                        ChannelEntity(
                                id,
                                name,
                                icon,
                                profile.id,
                                type = "LIVE",
                                extraParams = cmd,
                                sortOrder = index
                        )
            }
            if (genreId != null) {
                linkEntities.add(ChannelCategoryCrossRef(id, genreId, profile.id, type = "LIVE"))
            }
        }

        saveToDao(
                profile.id,
                categoryEntities,
                channelEntitiesMap.values.toList(),
                linkEntities,
                type = "LIVE"
        )
    }

    private suspend fun saveToDao(
            profileId: Int,
            categories: List<CategoryEntity>,
            channels: List<ChannelEntity>,
            links: List<ChannelCategoryCrossRef>,
            type: String = "LIVE"
    ) {
        dao.syncProfileData(profileId, categories, channels, links, type)
    }
}
