package com.example.simpleradio.data

import com.example.simpleradio.data.api.RadioBrowserApi
import com.example.simpleradio.data.api.HunterLogoProvider
import com.example.simpleradio.data.api.LogoDevProvider
import com.example.simpleradio.data.api.OfficialSiteLogoProvider
import com.example.simpleradio.data.api.SerperImageProvider
import com.example.simpleradio.data.local.RadioDao
import com.example.simpleradio.data.local.entities.*
import com.example.simpleradio.data.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class RadioRepository(
        private val api: RadioBrowserApi,
        private val dao: RadioDao,
        private val csvDataLoader: CsvDataLoader
) {
    data class StationLogoResult(
            val url: String?,
            val needsValidation: Boolean = false,
            val candidates: List<String> = emptyList(),
            val thumbnailUrls: List<String?> = emptyList()
    )

    private companion object {
        const val LOGO_CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1_000
    }

    private val homepageCache = ConcurrentHashMap<String, String>()
    private val missingHomepages = ConcurrentHashMap.newKeySet<String>()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val listAdapter =
            moshi.adapter<List<RadioStationEntity>>(
                    com.squareup.moshi.Types.newParameterizedType(
                            List::class.java,
                            RadioStationEntity::class.java
                    )
            )

    suspend fun saveCacheList(file: java.io.File, list: List<RadioStationEntity>) {
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            val json = listAdapter.toJson(list)
            file.writeText(json)
        }
    }

    suspend fun loadCacheList(file: java.io.File): List<RadioStationEntity> {
        return withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (!file.exists()) return@withContext emptyList()
            try {
                val json = file.readText()
                listAdapter.fromJson(json) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    val allFavoriteLists: Flow<List<RadioFavoriteListEntity>> = dao.getAllRadioFavoriteLists()
    val recentRadios: Flow<List<RadioStationEntity>> = dao.getRecentRadios()
    val allFavoriteUuids: Flow<Set<String>> = dao.getAllFavoriteUuids().map { it.toSet() }

    // --- SAUVEGARDE / EXPORT ---
    suspend fun exportFavoritesToJson(
            confirmedLogos: Map<String, String> = emptyMap()
    ): String {
        val lists = dao.getAllRadioFavoriteLists().first()
        val backupLists =
                lists.map { list ->
                    val stations = dao.getRadiosByFavoriteList(list.id).first()
                    BackupFavoriteList(name = list.name, stations = stations)
                }
        val backup =
                RadioBackup(
                        favoriteLists = backupLists,
                        confirmedLogos =
                                confirmedLogos.mapNotNull { (stationUuid, logoUrl) ->
                                    val cleanUuid = stationUuid.trim()
                                    val cleanUrl = logoUrl.trim()
                                    if (cleanUuid.isBlank() || cleanUrl.isBlank()) null
                                    else cleanUuid to cleanUrl
                                }.toMap()
                )
        val adapter = moshi.adapter(RadioBackup::class.java)
        return adapter.toJson(backup)
    }

    suspend fun importFavoritesFromJson(json: String): Map<String, String> {
        val adapter = moshi.adapter(RadioBackup::class.java)
        val backup = adapter.fromJson(json) ?: error("Backup GitHub vide")

        // GitHub is authoritative for favorite lists and for logo UUIDs present in the backup.
        // Normalize first so malformed or duplicate favorite data cannot replace valid local data.
        val normalizedLists =
                backup.favoriteLists
                        .filter { it.name.isNotBlank() }
                        .groupBy { it.name.trim().lowercase() }
                        .map { (_, sameNameLists) ->
                            val displayName = sameNameLists.first().name.trim()
                            val stations =
                                    sameNameLists
                                            .flatMap { it.stations }
                                            .filter {
                                                it.stationuuid.isNotBlank() && it.url.isNotBlank()
                                            }
                                            .distinctBy { it.stationuuid }
                            displayName to stations
                        }

        val normalizedConfirmedLogos =
                backup.confirmedLogos.mapNotNull { (stationUuid, logoUrl) ->
                    val cleanUuid = stationUuid.trim()
                    val cleanUrl = logoUrl.trim()
                    if (cleanUuid.isBlank() || cleanUrl.isBlank()) null
                    else cleanUuid to cleanUrl
                }.toMap()

        dao.replaceRadioFavorites(normalizedLists)
        return normalizedConfirmedLogos
    }

    suspend fun getCountries(): List<RadioCountry> =
            withContext(kotlinx.coroutines.Dispatchers.IO) { csvDataLoader.loadCountries() }

    suspend fun getTags(): List<RadioTag> =
            withContext(kotlinx.coroutines.Dispatchers.IO) { csvDataLoader.loadGenres() }

    suspend fun getTagsFiltered(filter: String): List<RadioTag> =
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                csvDataLoader.loadGenres().filter { it.name.contains(filter, ignoreCase = true) }
            }

    suspend fun searchStations(
            countryCode: String? = null,
            tag: String? = null,
            query: String? = null,
            bitrateMin: Int? = null,
            bitrateMax: Int? = null,
            order: String = "clickcount",
            limit: Int = 200
    ): List<RadioStationEntity> {
        val stations =
                api.searchStations(
                        countryCode = countryCode,
                        tag = tag,
                        name = query,
                        bitrateMin = bitrateMin,
                        bitrateMax = bitrateMax,
                        order = order
                )
        val entities =
                stations.take(limit).map {
                    RadioStationEntity(
                            stationuuid = it.stationuuid,
                            name = it.name,
                            url = it.url_resolved.ifBlank { it.url },
                            favicon = it.favicon,
                            homepage = it.homepage,
                            country = it.country,
                            tags = it.tags,
                            bitrate = it.bitrate
                    )
                }
        dao.insertRadioStations(entities)
        return entities
    }

    suspend fun findStationLogo(
            stationUuid: String,
            name: String,
            country: String?,
            streamUrl: String,
            homepage: String?,
            fallback: String?,
            confirmedLogo: String?
    ): StationLogoResult {
        confirmedLogo?.takeIf { it.isNotBlank() }?.let {
            return StationLogoResult(url = it)
        }

        val now = System.currentTimeMillis()
        val cachedLogo = dao.getStationLogoCache(stationUuid)
        if (cachedLogo != null &&
                        now - cachedLogo.checkedAt <= LOGO_CACHE_TTL_MS &&
                        !SerperImageProvider.isConfigured()
        ) {
            return StationLogoResult(
                    url = cachedLogo.logoUrl,
                    needsValidation = cachedLogo.source != "radio_browser",
                    candidates = listOf(cachedLogo.logoUrl)
            )
        }
        if (cachedLogo != null) dao.deleteStationLogoCache(stationUuid)

        val nativeLogo = fallback?.takeIf { it.isNotBlank() }
        val resolvedHomepage =
                resolveHomepage(stationUuid, homepage)
                        ?: nativeLogo
                                ?.let(LogoDevProvider::domainFromUrl)
                                ?.takeIf { LogoDevProvider.domainMatchesRadioName(it, name) }
                                ?.let { "https://$it" }

        val serperCandidates =
                SerperImageProvider.findLogos(
                        radioName = name,
                        country = country
                )
        if (serperCandidates.isNotEmpty()) {
            val candidates = serperCandidates.map { it.imageUrl }
            val thumbnailUrls = serperCandidates.map { it.thumbnailUrl }
            return StationLogoResult(
                    url = candidates.first(),
                    needsValidation = true,
                    candidates = candidates,
                    thumbnailUrls = thumbnailUrls
            )
        }

        return withTimeoutOrNull(7_000L) {
            val nativeInfo = nativeLogo?.let { LogoDevProvider.inspectLogo(it) }
            if (nativeInfo != null) {
                cacheStationLogo(stationUuid, nativeInfo, "radio_browser", now)
                return@withTimeoutOrNull StationLogoResult(url = nativeLogo)
            }

            supervisorScope {
                val officialRequest =
                        async {
                            OfficialSiteLogoProvider.findLogo(
                                    homepage = resolvedHomepage,
                                    rejectedUrls = emptySet()
                            )
                        }
                val hunterRequest =
                        async {
                            HunterLogoProvider.findLogo(
                                    officialHomepage = resolvedHomepage,
                                    rejectedUrls = emptySet()
                            )
                        }
                val logoDevRequest =
                        async {
                            LogoDevProvider.findLogo(
                                    radioName = name,
                                    country = country,
                                    streamUrl = streamUrl,
                                    excludedUrls = emptySet()
                            )
                        }

                val officialSiteLogo = officialRequest.await()
                if (officialSiteLogo != null && officialSiteLogo.source != "og_image") {
                    hunterRequest.cancel()
                    logoDevRequest.cancel()
                    cacheStationLogo(
                            stationUuid,
                            LogoDevProvider.ImageInfo(
                                    officialSiteLogo.url,
                                    officialSiteLogo.width,
                                    officialSiteLogo.height,
                                    officialSiteLogo.url.endsWith(".svg", true)
                            ),
                            officialSiteLogo.source,
                            now
                    )
                    return@supervisorScope StationLogoResult(
                            url = officialSiteLogo.url,
                            needsValidation = true
                    )
                }

                val hunterLogo = hunterRequest.await()
                if (hunterLogo != null) {
                    logoDevRequest.cancel()
                    cacheStationLogo(stationUuid, hunterLogo, "hunter", now)
                    return@supervisorScope StationLogoResult(
                            url = hunterLogo.url,
                            needsValidation = true
                    )
                }

                val searchedLogo = logoDevRequest.await()
                if (searchedLogo != null) {
                    LogoDevProvider.inspectLogo(searchedLogo)?.let {
                        cacheStationLogo(stationUuid, it, "logo_dev", now)
                    }
                    StationLogoResult(url = searchedLogo, needsValidation = true)
                } else if (officialSiteLogo != null) {
                    cacheStationLogo(
                            stationUuid,
                            LogoDevProvider.ImageInfo(
                                    officialSiteLogo.url,
                                    officialSiteLogo.width,
                                    officialSiteLogo.height,
                                    officialSiteLogo.url.endsWith(".svg", true)
                            ),
                            officialSiteLogo.source,
                            now
                    )
                    StationLogoResult(
                            url = officialSiteLogo.url,
                            needsValidation = true
                    )
                } else {
                    StationLogoResult(url = null)
                }
            }
        }
                ?: StationLogoResult(url = null)
    }

    private suspend fun cacheStationLogo(
            stationUuid: String,
            info: LogoDevProvider.ImageInfo,
            source: String,
            checkedAt: Long
    ) {
        dao.upsertStationLogoCache(
                StationLogoCacheEntity(
                        stationuuid = stationUuid,
                        logoUrl = info.url,
                        source = source,
                        width = info.width,
                        height = info.height,
                        checkedAt = checkedAt
                )
        )
    }

    suspend fun invalidateStationLogo(stationUuid: String) {
        dao.deleteStationLogoCache(stationUuid)
    }

    private suspend fun resolveHomepage(stationUuid: String, storedHomepage: String?): String? {
        storedHomepage?.takeIf { it.isNotBlank() }?.let {
            homepageCache[stationUuid] = it
            return it
        }
        homepageCache[stationUuid]?.let { return it }
        if (stationUuid in missingHomepages) return null

        val remoteHomepage =
                withTimeoutOrNull(1_500L) {
                    try {
                        api.getStationByUuid(stationUuid)
                                .firstOrNull()
                                ?.homepage
                                ?.takeIf { it.isNotBlank() }
                    } catch (_: Exception) {
                        null
                    }
                }
        if (remoteHomepage != null) {
            homepageCache[stationUuid] = remoteHomepage
        } else {
            missingHomepages += stationUuid
        }
        return remoteHomepage
    }

    fun getRadiosByFavoriteList(listId: Int): Flow<List<RadioStationEntity>> {
        return dao.getRadiosByFavoriteList(listId)
    }

    suspend fun addFavoriteList(name: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        val alreadyExists =
                dao.getAllRadioFavoriteLists().first().any {
                    it.name.equals(cleanName, ignoreCase = true)
                }
        if (!alreadyExists) {
            dao.insertRadioFavoriteList(RadioFavoriteListEntity(name = cleanName))
        }
    }

    suspend fun removeFavoriteList(list: RadioFavoriteListEntity) {
        dao.deleteRadioFavoriteList(list)
    }

    suspend fun toggleRadioFavorite(uuid: String, listId: Int) {
        val currentLists = dao.getListIdsForRadio(uuid)
        if (currentLists.contains(listId)) {
            dao.removeRadioFromFavorite(RadioFavoriteCrossRef(uuid, listId))
        } else {
            val maxPos = dao.getMaxPositionForList(listId) ?: -1
            dao.addRadioToFavorite(RadioFavoriteCrossRef(uuid, listId, position = maxPos + 1))
        }
    }

    suspend fun moveRadio(
            listId: Int,
            fromIndex: Int,
            toIndex: Int,
            currentList: List<RadioStationEntity>
    ) {
        val mutableList = currentList.toMutableList()
        if (fromIndex !in mutableList.indices || toIndex !in mutableList.indices) return

        val item = mutableList.removeAt(fromIndex)
        mutableList.add(toIndex, item)

        mutableList.forEachIndexed { index, station ->
            dao.updateRadioPosition(station.stationuuid, listId, index)
        }
    }

    suspend fun getListIdsForRadio(uuid: String): List<Int> {
        return dao.getListIdsForRadio(uuid)
    }

    suspend fun addToRecents(uuid: String) {
        dao.insertRadioRecent(RadioRecentEntity(uuid, System.currentTimeMillis()))
        dao.trimRadioRecents()
    }

    suspend fun getStationByUuid(uuid: String): RadioStationEntity? {
        return dao.getStationByUuid(uuid)
    }

    suspend fun addCustomRadio(name: String, url: String) {
        val listName = "mes urls"
        val allLists = dao.getAllRadioFavoriteLists().first()
        var customList = allLists.find { it.name.equals(listName, ignoreCase = true) }
        if (customList == null) {
            val insertedId =
                    dao.insertRadioFavoriteList(RadioFavoriteListEntity(name = listName)).toInt()
            customList = RadioFavoriteListEntity(id = insertedId, name = listName)
        }

        val uuid = java.util.UUID.randomUUID().toString()
        val station =
                RadioStationEntity(
                        stationuuid = uuid,
                        name = name,
                        url = url,
                        favicon = null,
                        homepage = null,
                        country = "Custom",
                        tags = "custom",
                        bitrate = 0
                )
        dao.insertRadioStations(listOf(station))
        dao.addRadioToFavorite(RadioFavoriteCrossRef(uuid, customList.id))
    }
}
