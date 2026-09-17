package com.example.myiptv.data

import android.util.Base64
import androidx.room.withTransaction
import com.squareup.moshi.Moshi
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalCoroutinesApi::class)
class MyIptvRepository(
    private val database: MyIptvDatabase,
) {
    val profiles = database.profileDao().observeAll()
    val profile = database.profileDao().observeActive()
    val channels = profile.flatMapLatest { active ->
        active?.let { database.channelDao().observeAll(it.id) } ?: flowOf(emptyList())
    }
    val recentChannels = profile.flatMapLatest { active ->
        active?.let { database.recentDao().observeChannels(it.id) } ?: flowOf(emptyList())
    }
    val recentSearches = profile.flatMapLatest { active ->
        active?.let {
            database.searchHistoryDao().observeRecent(it.id, SEARCH_HISTORY_LIMIT)
        } ?: flowOf(emptyList())
    }
    val favoriteGroups = profile.flatMapLatest { active ->
        active?.let { database.favoriteDao().observeGroups(it.id) } ?: flowOf(emptyList())
    }

    private val client = OkHttpClient.Builder()
        .dispatcher(
            Dispatcher().apply {
                maxRequests = 32
                maxRequestsPerHost = 24
            },
        )
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val normalEpgCache = LinkedHashMap<EpgCacheKey, CachedEpg>(
        NORMAL_EPG_CACHE_SIZE,
        0.75f,
        true,
    )
    private val apiCache = mutableMapOf<String, XtreamApi>()
    private val moshi = Moshi.Builder().build()

    suspend fun saveProfile(
        profileId: Int?,
        name: String,
        serverUrl: String,
        username: String,
        password: String,
        countryGroupingEnabled: Boolean,
    ) {
        val normalized = normalizeServerUrl(serverUrl)
        require(name.isNotBlank()) { "Le nom du profil est obligatoire." }
        require(username.isNotBlank()) { "Le nom d’utilisateur est obligatoire." }
        require(password.isNotBlank()) { "Le mot de passe est obligatoire." }

        database.withTransaction {
            val dao = database.profileDao()
            val existing = profileId?.let { dao.get(it) }
            require(profileId == null || existing != null) { "Profil Xtream introuvable." }
            val id = existing?.id ?: dao.nextId()
            val shouldActivate = existing?.isActive ?: true
            val credentialsChanged = existing != null && (
                normalizeServerUrl(existing.serverUrl) != normalized ||
                    existing.username != username.trim() ||
                    existing.password != password
                )
            val saved = XtreamProfile(
                id = id,
                name = name.trim(),
                serverUrl = normalized,
                username = username.trim(),
                password = password,
                countryGroupingEnabled = countryGroupingEnabled,
                isActive = shouldActivate,
                lastSyncedAt = existing?.lastSyncedAt?.takeUnless { credentialsChanged },
            )
            if (existing == null) dao.insert(saved) else dao.update(saved)
            if (shouldActivate) {
                dao.deactivateAll()
                dao.activate(id)
            }
        }
    }

    suspend fun activateProfile(profileId: Int) {
        database.withTransaction {
            val dao = database.profileDao()
            require(dao.get(profileId) != null) { "Profil Xtream introuvable." }
            dao.deactivateAll()
            dao.activate(profileId)
        }
    }

    suspend fun deleteProfile(profile: XtreamProfile) {
        database.withTransaction {
            val dao = database.profileDao()
            val replacement = if (profile.isActive) dao.getAnother(profile.id) else null
            dao.delete(profile)
            replacement?.let {
                dao.deactivateAll()
                dao.activate(it.id)
            }
        }
    }

    suspend fun refresh(profileOverride: XtreamProfile? = null) {
        val activeProfile = profileOverride ?: database.profileDao().getActive()
            ?: error("Configurez d’abord le profil Xtream.")
        val api = api(activeProfile)
        val categories = api.liveCategories(activeProfile.username, activeProfile.password)
        val categoryMetadata = categories.mapIndexed { index, dto ->
            dto.categoryId.orEmpty() to CategoryMetadata(
                name = dto.categoryName.orEmpty().ifBlank { "Sans catégorie" },
                order = index,
            )
        }.toMap()
        val remoteChannels = api.liveChannels(activeProfile.username, activeProfile.password)
        val mappedChannels = remoteChannels.mapIndexedNotNull { providerOrder, dto ->
            val categoryId = dto.categoryId.orEmpty()
            val metadata = categoryMetadata[categoryId] ?: CategoryMetadata(
                name = "Sans catégorie",
                order = Int.MAX_VALUE,
            )
            dto.name?.takeIf { it.isNotBlank() }?.let { rawName ->
                val name = rawName.trim()
                SavedChannel(
                    profileId = activeProfile.id,
                    streamId = dto.streamId,
                    name = name,
                    categoryId = categoryId,
                    categoryName = metadata.name,
                    countryCode = countryFromCategoryName(metadata.name),
                    iconUrl = dto.streamIcon?.takeIf(String::isNotBlank),
                    extension = dto.containerExtension?.trim('.')?.takeIf(String::isNotBlank) ?: "ts",
                    epgChannelId = dto.epgChannelId?.takeIf(String::isNotBlank),
                    categoryOrder = metadata.order,
                    providerOrder = providerOrder,
                )
            }
        }
        database.withTransaction {
            if (mappedChannels.isNotEmpty()) {
                database.channelDao().markAllStale(activeProfile.id)
                database.channelDao().saveAll(mappedChannels)
                database.channelDao().deleteStale(activeProfile.id)
            }
            database.profileDao().updateLastSyncedAt(activeProfile.id, System.currentTimeMillis())
        }
    }

    suspend fun loadEpg(
        channel: SavedChannel,
        refresh: Boolean = false,
    ): List<EpgProgram> {
        if (channel.epgChannelId.isNullOrBlank()) return emptyList()
        val activeProfile = database.profileDao().getActive() ?: return emptyList()
        if (activeProfile.id != channel.profileId) return emptyList()
        return loadEpg(activeProfile, channel, refresh)
    }

    internal suspend fun loadEpg(
        profile: XtreamProfile,
        channel: SavedChannel,
        refresh: Boolean = false,
    ): List<EpgProgram> {
        if (channel.epgChannelId.isNullOrBlank() || profile.id != channel.profileId) return emptyList()
        val cacheKey = EpgCacheKey(profile.id, channel.streamId)
        if (!refresh) readNormalEpgCache(cacheKey)?.let { return it }

        val service = api(profile)
        val sourceZone = epgSourceZone(channel)
        val programs = mapEpg(
            service.shortEpg(
                profile.username,
                profile.password,
                channel.streamId,
            ),
            sourceZone = sourceZone,
        )
        return normalEpgPrograms(programs).also { summary ->
            writeNormalEpgCache(cacheKey, summary)
        }
    }

    /** Resolve the service once; bulk imports have their own persistent cache. */
    internal fun shortEpgLoader(profile: XtreamProfile): suspend (SavedChannel) -> List<EpgProgram> {
        val service = api(profile)
        return { channel ->
            normalEpgPrograms(
                mapEpg(
                    service.shortEpg(profile.username, profile.password, channel.streamId),
                    sourceZone = epgSourceZone(channel),
                    includeTimeRange = false,
                ),
            )
        }
    }

    suspend fun markRecent(channel: SavedChannel) {
        val profileId = activeProfileId()
        database.recentDao().mark(
            RecentChannel(profileId, channel.streamId, System.currentTimeMillis()),
        )
        database.recentDao().trim(profileId, 50)
    }

    suspend fun clearRecentHistory() {
        database.recentDao().clear(activeProfileId())
    }

    suspend fun rememberSearch(query: String) {
        val normalizedQuery = query.trim().replace(Regex("\\s+"), " ")
        if (normalizedQuery.isBlank()) return
        database.withTransaction {
            val profileId = activeProfileId()
            val dao = database.searchHistoryDao()
            dao.deleteCaseInsensitive(profileId, normalizedQuery)
            dao.save(SearchHistoryEntry(profileId, normalizedQuery, System.currentTimeMillis()))
            dao.trim(profileId, SEARCH_HISTORY_LIMIT)
        }
    }

    suspend fun clearSearchHistory() {
        database.searchHistoryDao().clear(activeProfileId())
    }

    suspend fun favoriteChannels(groupId: Long) =
        database.favoriteDao().observeChannels(activeProfileId(), groupId)

    suspend fun favoriteGroupIds(streamId: Int) =
        database.favoriteDao().observeGroupIds(activeProfileId(), streamId)

    suspend fun createFavoriteGroup(name: String): Long {
        val normalizedName = name.trim()
        require(normalizedName.isNotBlank()) { "Le nom du groupe est obligatoire." }
        val profileId = activeProfileId()
        val dao = database.favoriteDao()
        require(dao.countGroupsNamed(profileId, normalizedName, excludedGroupId = -1) == 0) {
            "Un groupe nommé « $normalizedName » existe déjà."
        }
        return dao.createGroup(FavoriteGroup(normalizedName, profileId))
    }

    suspend fun renameFavoriteGroup(group: FavoriteGroup, name: String) {
        val normalizedName = name.trim()
        require(normalizedName.isNotBlank()) { "Le nom du groupe est obligatoire." }
        val dao = database.favoriteDao()
        require(dao.countGroupsNamed(group.profileId, normalizedName, group.id) == 0) {
            "Un groupe nommé « $normalizedName » existe déjà."
        }
        dao.renameGroup(group.profileId, group.id, normalizedName)
    }

    suspend fun toggleFavorite(groupId: Long, streamId: Int) {
        val profileId = activeProfileId()
        val dao = database.favoriteDao()
        if (dao.contains(profileId, groupId, streamId) > 0) {
            dao.remove(profileId, groupId, streamId)
        } else {
            dao.add(FavoriteMembership(profileId, groupId, streamId))
        }
    }

    suspend fun deleteFavoriteGroup(group: FavoriteGroup) {
        database.favoriteDao().deleteGroup(group)
    }

    suspend fun exportBackupJson(): String {
        val activeProfile = database.profileDao().getActive()
            ?: error("Aucun profil Xtream à sauvegarder.")
        val profileBackups = database.profileDao().getAll().map { profile ->
            BackupProfile(
                profile = profile,
                favoriteGroups = backupFavoriteGroups(profile.id),
            )
        }
        val backup = MyIptvBackup(
            version = 2,
            profiles = profileBackups,
            activeProfileId = activeProfile.id,
        )
        return moshi.adapter(MyIptvBackup::class.java).toJson(backup)
    }

    suspend fun importBackupJson(json: String) {
        val backup = moshi.adapter(MyIptvBackup::class.java).fromJson(json)
            ?: error("Sauvegarde GitHub vide ou invalide.")
        val rawProfiles = when (backup.version) {
            1 -> listOf(
                BackupProfile(
                    profile = backup.profile
                        ?: error("Sauvegarde GitHub V1 sans profil Xtream."),
                    favoriteGroups = backup.favoriteGroups,
                ),
            )
            2 -> backup.profiles
            else -> error("Version de sauvegarde non prise en charge.")
        }
        require(rawProfiles.isNotEmpty()) { "La sauvegarde GitHub ne contient aucun profil." }

        val normalizedEntries = rawProfiles.map { entry ->
            val normalizedProfile = entry.profile.copy(
                serverUrl = normalizeServerUrl(entry.profile.serverUrl),
                username = entry.profile.username.trim(),
                name = entry.profile.name.ifBlank { "Profil GitHub" }.trim(),
            )
            require(normalizedProfile.username.isNotBlank()) { "Profil GitHub sans utilisateur." }
            require(normalizedProfile.password.isNotBlank()) { "Profil GitHub sans mot de passe." }
            NormalizedBackupProfile(
                profile = normalizedProfile,
                favoriteGroups = normalizeBackupGroups(entry.favoriteGroups),
                active = backup.version == 1 ||
                    entry.profile.id == backup.activeProfileId ||
                    entry.profile.isActive,
            )
        }
        val mergedEntries = normalizedEntries
            .groupBy { profileIdentity(it.profile) }
            .map { (_, duplicates) ->
                val primary = duplicates.first()
                primary.copy(
                    favoriteGroups = normalizeBackupGroups(
                        duplicates.flatMap(NormalizedBackupProfile::favoriteGroups),
                    ),
                    active = duplicates.any(NormalizedBackupProfile::active),
                )
            }

        database.withTransaction {
            val profileDao = database.profileDao()
            val originalActiveId = profileDao.getActive()?.id
            val localProfiles = profileDao.getAll().toMutableList()
            var importedActiveId: Int? = null
            mergedEntries.forEach { entry ->
                val existing = localProfiles.firstOrNull {
                    profileIdentity(it) == profileIdentity(entry.profile)
                }
                val profileId = existing?.id ?: profileDao.nextId()
                val credentialsChanged = existing != null &&
                    existing.password != entry.profile.password
                val importedProfile = entry.profile.copy(
                    id = profileId,
                    isActive = false,
                    lastSyncedAt = existing?.lastSyncedAt?.takeUnless { credentialsChanged },
                )
                if (existing == null) {
                    profileDao.insert(importedProfile)
                    localProfiles += importedProfile
                } else {
                    profileDao.update(importedProfile)
                    localProfiles[localProfiles.indexOf(existing)] = importedProfile
                }

                val favoriteDao = database.favoriteDao()
                favoriteDao.clearGroups(profileId)
                val favoriteChannels = entry.favoriteGroups
                    .flatMap(BackupFavoriteGroup::channels)
                    .distinctBy(SavedChannel::streamId)
                    .map { it.copy(profileId = profileId) }
                if (favoriteChannels.isNotEmpty()) {
                    database.channelDao().insertMissing(favoriteChannels)
                }
                entry.favoriteGroups.forEach { group ->
                    val groupId = favoriteDao.createGroup(FavoriteGroup(group.name, profileId))
                    group.channels.forEach { channel ->
                        favoriteDao.add(
                            FavoriteMembership(profileId, groupId, channel.streamId),
                        )
                    }
                }
                if (entry.active) importedActiveId = profileId
            }
            val activeId = importedActiveId ?: originalActiveId
                ?: localProfiles.firstOrNull()?.id
            if (activeId != null) {
                profileDao.deactivateAll()
                profileDao.activate(activeId)
            }
        }
    }

    private suspend fun backupFavoriteGroups(profileId: Int): List<BackupFavoriteGroup> {
        val favoriteDao = database.favoriteDao()
        return favoriteDao.getGroups(profileId)
            .filter { it.name.isNotBlank() }
            .groupBy { it.name.trim().lowercase(Locale.ROOT) }
            .map { (_, sameNameGroups) ->
                BackupFavoriteGroup(
                    name = sameNameGroups.first().name.trim(),
                    channels = sameNameGroups
                        .flatMap { favoriteDao.getChannels(profileId, it.id) }
                        .distinctBy(SavedChannel::streamId),
                )
            }
    }

    private fun normalizeBackupGroups(
        groups: List<BackupFavoriteGroup>,
    ): List<BackupFavoriteGroup> = groups
        .filter { it.name.isNotBlank() }
        .groupBy { it.name.trim().lowercase(Locale.ROOT) }
        .map { (_, sameNameGroups) ->
            BackupFavoriteGroup(
                name = sameNameGroups.first().name.trim(),
                channels = sameNameGroups
                    .flatMap(BackupFavoriteGroup::channels)
                    .filter { it.name.isNotBlank() }
                    .distinctBy(SavedChannel::streamId),
            )
        }

    private fun profileIdentity(profile: XtreamProfile): String =
        normalizeServerUrl(profile.serverUrl).lowercase(Locale.ROOT) + '\u0000' +
            profile.username.trim()

    private data class NormalizedBackupProfile(
        val profile: XtreamProfile,
        val favoriteGroups: List<BackupFavoriteGroup>,
        val active: Boolean,
    )

    fun streamUrl(profile: XtreamProfile, channel: SavedChannel): String = buildString {
        append(profile.serverUrl.trimEnd('/'))
        append("/live/")
        append(profile.username)
        append('/')
        append(profile.password)
        append('/')
        append(channel.streamId)
        append('.')
        append(channel.extension)
    }

    private fun api(profile: XtreamProfile): XtreamApi = synchronized(apiCache) {
        val serverUrl = normalizeServerUrl(profile.serverUrl)
        apiCache.getOrPut(serverUrl) {
            Retrofit.Builder()
                .baseUrl(serverUrl)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(XtreamApi::class.java)
        }
    }

    private fun normalizeServerUrl(value: String): String {
        val trimmed = value.trim().trimEnd('/')
        require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            "L’adresse doit commencer par http:// ou https://."
        }
        return "$trimmed/"
    }

    private suspend fun activeProfileId(): Int = database.profileDao().getActive()?.id
        ?: error("Configurez d’abord le profil Xtream.")

    private fun countryFromCategoryName(categoryName: String): String {
        val country = categoryName.trimStart().take(2)
        return country.uppercase(Locale.ROOT).ifBlank { "--" }
    }

    private fun decodeXtreamText(value: String?): String {
        val text = value.orEmpty().trim()
        if (text.length < 4 || text.length % 4 != 0 || !text.matches(BASE64_PATTERN)) return text
        return runCatching {
            String(Base64.decode(text, Base64.DEFAULT), StandardCharsets.UTF_8)
                .takeIf { decoded -> decoded.all { it == '\n' || it == '\r' || it == '\t' || !it.isISOControl() } }
                ?: text
        }.getOrDefault(text)
    }

    private fun formatRange(entry: XtreamEpgDto, startSeconds: Long?, stopSeconds: Long?): String {
        val start = startSeconds?.let { seconds ->
            TIME_FORMATTER.format(Instant.ofEpochSecond(seconds).atZone(EPG_TARGET_ZONE))
        }
        val end = stopSeconds?.let { seconds ->
            TIME_FORMATTER.format(Instant.ofEpochSecond(seconds).atZone(EPG_TARGET_ZONE))
        }
        if (start != null || end != null) return listOfNotNull(start, end).joinToString(" – ")
        return listOfNotNull(entry.start?.takeLast(8)?.take(5), entry.end?.takeLast(8)?.take(5))
            .joinToString(" – ")
    }

    private fun mapEpg(
        response: XtreamEpgResponse,
        sourceZone: ZoneId,
        includeTimeRange: Boolean = true,
    ): List<EpgProgram> = response.listings.map { entry ->
        val start = epgTimestamp(entry.startTimestamp, entry.start, sourceZone)
        val stop = epgTimestamp(entry.stopTimestamp, entry.end, sourceZone)
        EpgProgram(
            title = decodeXtreamText(entry.title).ifBlank { "Programme sans titre" },
            description = decodeXtreamText(entry.description),
            timeRange = if (includeTimeRange) formatRange(entry, start, stop) else "",
            startEpochSeconds = start,
            stopEpochSeconds = stop,
        )
    }

    private fun epgTimestamp(
        timestamp: Any?,
        localDateTime: String?,
        sourceZone: ZoneId,
    ): Long? = localDateTime?.let { value ->
        runCatching {
            LocalDateTime.parse(value, EPG_DATE_TIME_FORMATTER)
                .atZone(sourceZone)
                .toEpochSecond()
        }.getOrNull()
    } ?: timestamp?.toString()?.substringBefore('.')?.toLongOrNull()

    private fun epgSourceZone(channel: SavedChannel): ZoneId {
        val epgSuffix = channel.epgChannelId
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.uppercase(Locale.ROOT)
        val country = epgSuffix?.takeIf { it.length in 2..3 } ?: channel.countryCode
        val zone = when (country.uppercase(Locale.ROOT)) {
            "FR" -> "Europe/Paris"
            "ES" -> "Europe/Madrid"
            "IT" -> "Europe/Rome"
            "DE" -> "Europe/Berlin"
            "UK", "GB" -> "Europe/London"
            "CH" -> "Europe/Zurich"
            "BE" -> "Europe/Brussels"
            "NL" -> "Europe/Amsterdam"
            "DK" -> "Europe/Copenhagen"
            "SE" -> "Europe/Stockholm"
            "NO" -> "Europe/Oslo"
            "IE" -> "Europe/Dublin"
            "PT" -> "Europe/Lisbon"
            "PL" -> "Europe/Warsaw"
            "RO" -> "Europe/Bucharest"
            "RS" -> "Europe/Belgrade"
            "HR" -> "Europe/Zagreb"
            "SI" -> "Europe/Ljubljana"
            "QA" -> "Asia/Qatar"
            "SG" -> "Asia/Singapore"
            "AU" -> "Australia/Sydney"
            "US", "USA" -> "America/New_York"
            "CA" -> "America/Toronto"
            "ZA" -> "Africa/Johannesburg"
            else -> "UTC"
        }
        return ZoneId.of(zone)
    }

    private fun normalEpgPrograms(
        programs: List<EpgProgram>,
        nowEpochSeconds: Long = Instant.now().epochSecond,
    ): List<EpgProgram> = programs
        .sortedBy { it.startEpochSeconds ?: Long.MAX_VALUE }
        .filter { program ->
            when {
                program.stopEpochSeconds != null -> program.stopEpochSeconds > nowEpochSeconds
                program.startEpochSeconds != null -> program.startEpochSeconds >= nowEpochSeconds
                else -> true
            }
        }
        .take(NORMAL_EPG_PROGRAM_LIMIT)

    private fun readNormalEpgCache(key: EpgCacheKey): List<EpgProgram>? =
        synchronized(normalEpgCache) {
            val cached = normalEpgCache[key] ?: return@synchronized null
            if (System.currentTimeMillis() - cached.loadedAtMillis <= NORMAL_EPG_CACHE_DURATION_MS) {
                normalEpgPrograms(cached.programs)
            } else {
                normalEpgCache.remove(key)
                null
            }
        }

    private fun writeNormalEpgCache(key: EpgCacheKey, programs: List<EpgProgram>) {
        synchronized(normalEpgCache) {
            normalEpgCache[key] = CachedEpg(
                loadedAtMillis = System.currentTimeMillis(),
                programs = programs,
            )
            while (normalEpgCache.size > NORMAL_EPG_CACHE_SIZE) {
                val oldestKey = normalEpgCache.entries.firstOrNull()?.key ?: break
                normalEpgCache.remove(oldestKey)
            }
        }
    }

    companion object {
        private const val SEARCH_HISTORY_LIMIT = 20
        private const val NORMAL_EPG_PROGRAM_LIMIT = 4
        private const val NORMAL_EPG_CACHE_SIZE = 24
        private const val NORMAL_EPG_CACHE_DURATION_MS = 3 * 60 * 60 * 1_000L
        private val BASE64_PATTERN = Regex("^[A-Za-z0-9+/]+={0,2}$")
        private val EPG_TARGET_ZONE: ZoneId = ZoneId.of("Africa/Tunis")
        private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private val EPG_DATE_TIME_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }

    private data class CategoryMetadata(
        val name: String,
        val order: Int,
    )

    private data class EpgCacheKey(
        val profileId: Int,
        val streamId: Int,
    )

    private data class CachedEpg(
        val loadedAtMillis: Long,
        val programs: List<EpgProgram>,
    )
}
