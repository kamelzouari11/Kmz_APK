package com.example.myiptv.data

import android.util.Base64
import androidx.room.withTransaction
import com.squareup.moshi.Moshi
import okhttp3.Dispatcher
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    val favoriteMemberships = profile.flatMapLatest { active ->
        active?.let { database.favoriteDao().observeMemberships(it.id) } ?: flowOf(emptyList())
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build(),
            )
        }
        .dispatcher(
            Dispatcher().apply {
                maxRequests = 32
                maxRequestsPerHost = 24
            },
        )
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val epgCacheMutex = Mutex()
    private val apiCache = mutableMapOf<String, XtreamApi>()
    private val vodTitleIndexCache = mutableMapOf<Int, List<VodTitleRow>>()
    private val moshi = Moshi.Builder().build()

    suspend fun saveProfile(
        profileId: Int?,
        name: String,
        serverUrl: String,
        username: String,
        password: String,
        protocol: ProfileProtocol,
        macAddress: String?,
        countryGroupingEnabled: Boolean,
    ) {
        val normalized = normalizeServerUrl(serverUrl)
        require(name.isNotBlank()) { "Le nom du profil est obligatoire." }
        if (protocol == ProfileProtocol.XTREAM) {
            require(username.isNotBlank()) { "Le nom d’utilisateur est obligatoire." }
            require(password.isNotBlank()) { "Le mot de passe est obligatoire." }
        } else {
            require(macAddress?.matches(Regex("(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$")) == true) {
                "L’adresse MAC doit avoir le format AA:BB:CC:DD:EE:FF."
            }
        }

        database.withTransaction {
            val dao = database.profileDao()
            val existing = profileId?.let { dao.get(it) }
            require(profileId == null || existing != null) { "Profil Xtream introuvable." }
            val id = existing?.id ?: dao.nextId()
            val shouldActivate = existing?.isActive ?: true
            val credentialsChanged = existing != null && (
                    normalizeServerUrl(existing.serverUrl) != normalized ||
                    existing.username != username.trim() ||
                    existing.password != password ||
                    existing.protocol != protocol.name ||
                    existing.macAddress != macAddress?.trim()?.uppercase(Locale.ROOT)
                )
            val saved = XtreamProfile(
                id = id,
                name = name.trim(),
                serverUrl = normalized,
                username = username.trim(),
                password = password,
                protocol = protocol.name,
                macAddress = macAddress?.trim()?.uppercase(Locale.ROOT),
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
        if (activeProfile.protocol == ProfileProtocol.STALKER.name) {
            refreshStalker(activeProfile)
            return
        }
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
                    // Live Xtream endpoints are served as /live/.../{id}.ts by
                    // the reference client, even when the provider reports a
                    // different container_extension.
                    extension = "ts",
                    epgChannelId = dto.epgChannelId?.takeIf(String::isNotBlank),
                    categoryOrder = metadata.order,
                    providerOrder = providerOrder,
                )
            }
        }
        database.withTransaction {
            if (mappedChannels.isNotEmpty()) {
                database.channelDao().markAllStale(activeProfile.id)
                database.channelDao().saveAll(mappedChannels.map { channel ->
                    channel.copy(
                        epgChannelId = channel.epgChannelId?.trim()?.takeIf(String::isNotEmpty),
                    )
                })
                database.channelDao().deleteStale(activeProfile.id)
            }
            database.profileDao().updateLastSyncedAt(activeProfile.id, System.currentTimeMillis())
        }
    }

    /** Synchronises MAG/Stalker portals. Portals commonly return either an object or a JS envelope. */
    private suspend fun refreshStalker(profile: XtreamProfile) {
        val mac = requireNotNull(profile.macAddress?.trim()?.uppercase(Locale.ROOT)) {
            "Adresse MAC manquante pour le profil Stalker."
        }
        val base = normalizeServerUrl(profile.serverUrl).toHttpUrl()
        var token: String? = null
        suspend fun request(action: String, type: String = "stb", extra: Map<String, String> = emptyMap()): JSONObject = withContext(Dispatchers.IO) {
            val url = base.newBuilder().addPathSegments("server/load.php")
                .addQueryParameter("type", type)
                .addQueryParameter("action", action)
                .addQueryParameter("JsHttpRequest", "1-xml")
                .apply { extra.forEach { (key, value) -> addQueryParameter(key, value) } }
                .build()
            val builder = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (MAG254; Linux; Android 4.4.2)")
                .header("X-User-Agent", "Model: MAG254; Link: WiFi")
                .header("Cookie", "mac=$mac; stb_lang=en; timezone=Europe/Paris;")
            token?.let { builder.header("Authorization", "Bearer $it") }
            client.newCall(builder.build()).execute().use { response ->
                check(response.isSuccessful) { "Portail Stalker HTTP ${response.code}" }
                JSONObject(response.body?.string().orEmpty())
            }
        }
        val handshake = request("handshake", extra = mapOf("mac" to mac))
        token = handshake.optJSONObject("js")?.optString("token")?.takeIf { it.isNotBlank() }
        check(!token.isNullOrBlank()) { "Le portail Stalker n’a pas fourni de token (MAC refusée ou portail incompatible)." }
        val categoriesResponse = request("get_genres", "itv", mapOf("p" to "1", "JsHttpRequest" to "1-xml"))
        val categoryObjects = stalkerArray(categoriesResponse)
        val categories = categoryObjects.mapIndexed { index, item ->
            (item.optString("id").ifBlank { item.optString("genre_id") }) to
                (item.optString("title").ifBlank { item.optString("name") }.ifBlank { "Sans catégorie" } to index)
        }.toMap()
        val channelsResponse = request("get_all_channels", "itv", mapOf("p" to "1", "JsHttpRequest" to "1-xml"))
        val channelObjects = stalkerArray(channelsResponse).ifEmpty {
            categories.keys.filter(String::isNotBlank).flatMap { genre ->
                stalkerArray(request("get_ordered_list", "itv", mapOf("genre" to genre)))
            }
        }
        val mapped = channelObjects.mapIndexedNotNull { index, item ->
            val rawId = item.optString("id").ifBlank { item.optString("ch_id") }
            val streamId = rawId.toIntOrNull() ?: rawId.hashCode().and(0x7fffffff)
            if (streamId == 0) return@mapIndexedNotNull null
            val name = item.optString("name").ifBlank { item.optString("title") }.trim()
            if (name.isBlank()) return@mapIndexedNotNull null
            val categoryId = item.optString("tv_genre_id").ifBlank { item.optString("genre_id") }
            val metadata = categories[categoryId] ?: ("Sans catégorie" to Int.MAX_VALUE)
            SavedChannel(profile.id, name, categoryId, metadata.first,
                countryFromCategoryName(metadata.first),
                item.optString("logo").takeIf { it.isNotBlank() },
                item.optString("cmd").takeIf { it.isNotBlank() } ?: rawId,
                item.optString("xmltv_id").takeIf { it.isNotBlank() }, metadata.second, index, streamId)
        }
        database.withTransaction {
            if (mapped.isNotEmpty()) {
                database.channelDao().markAllStale(profile.id)
                database.channelDao().saveAll(mapped.map { channel ->
                    channel.copy(
                        epgChannelId = channel.epgChannelId?.trim()?.takeIf(String::isNotEmpty),
                    )
                })
                database.channelDao().deleteStale(profile.id)
            }
            database.profileDao().updateLastSyncedAt(profile.id, System.currentTimeMillis())
        }
        check(mapped.isNotEmpty()) { "Le portail Stalker a répondu sans chaîne." }
    }

    private fun stalkerArray(response: JSONObject): List<JSONObject> {
        val js = response.opt("js")
        val array = when (js) {
            is JSONArray -> js
            is JSONObject -> js.optJSONArray("data") ?: js.optJSONArray("channels") ?: JSONArray()
            else -> response.optJSONArray("data") ?: JSONArray()
        }
        if (array.length() > 0) return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        val payload = when (js) {
            is JSONObject -> js.optJSONObject("data") ?: js
            else -> response.optJSONObject("data")
        } ?: return emptyList()
        return payload.keys().asSequence().mapNotNull { payload.optJSONObject(it) }.toList()
    }

    suspend fun loadVodMovies(profile: XtreamProfile, forceRefresh: Boolean = false): List<VodMovie> {
        val vodDao = database.vodDao()
        val cached = vodDao.getMovies(profile.id)
        // Les lignes Room constituent déjà un cache exploitable. Cela permet
        // aussi de servir les anciennes bases qui n'avaient pas encore de
        // ligne vod_catalog_meta, sans refaire un appel provider.
        if (!forceRefresh && vodDao.getMeta(profile.id) != null) {
            return cached.map { it.toVodMovie() }
        }
        val service = api(profile)
        val categoryNames = service.vodCategories(profile.username, profile.password)
            .mapIndexed { index, category ->
                category.categoryId.orEmpty() to (category.categoryName.orEmpty().ifBlank { "Sans catégorie" } to index)
            }.toMap()
        val remote = service.vodMovies(profile.username, profile.password).mapIndexedNotNull { providerOrder, dto ->
            val name = dto.name?.trim()?.takeIf(String::isNotBlank) ?: return@mapIndexedNotNull null
            val categoryId = dto.categoryId.orEmpty()
            val metadata = categoryNames[categoryId]
            val providerCategory = metadata?.first ?: "Sans catégorie"
            val country = vodCountry(dto.country, providerCategory)
            VodMovie(
                profileId = profile.id,
                streamId = dto.streamId,
                name = name,
                categoryId = categoryId,
                categoryName = vodCategoryName(providerCategory, country),
                country = country,
                posterUrl = dto.streamIcon?.trim()?.takeIf(String::isNotBlank),
                extension = dto.containerExtension?.trim('.')?.takeIf(String::isNotBlank) ?: "mp4",
                description = dto.plot?.trim().orEmpty(),
                releaseDate = dto.releasedate?.trim()?.takeIf(String::isNotBlank),
                duration = dto.duration?.trim()?.takeIf(String::isNotBlank),
                durationSecs = dto.durationSecs,
                rating = dto.rating?.trim()?.takeIf(String::isNotBlank),
                actors = dto.cast?.trim()?.takeIf(String::isNotBlank),
                director = dto.director?.trim()?.takeIf(String::isNotBlank),
                genre = dto.genre?.trim()?.takeIf(String::isNotBlank),
                categoryOrder = metadata?.second ?: Int.MAX_VALUE,
                providerOrder = providerOrder,
            )
        }
        database.withTransaction {
            vodDao.clearMovies(profile.id)
            vodDao.clearActorIndex(profile.id)
            vodDao.clearMeta(profile.id)
            vodTitleIndexCache.remove(profile.id)
        }
        // Les paquets sont validés séparément : une interruption ne détruit
        // pas les paquets déjà écrits en Room.
        remote.map { it.toEntity() }.chunked(VOD_SAVE_BATCH_SIZE).forEach { batch ->
            database.withTransaction {
                vodDao.saveMovies(batch)
                vodDao.saveActorIndex(batch.flatMap(::actorIndexRows))
            }
        }
        database.withTransaction {
            vodDao.saveMeta(VodCatalogMeta(profile.id, System.currentTimeMillis()))
        }
        return remote
    }

    suspend fun loadVodCategories(profile: XtreamProfile, forceRefresh: Boolean = false): List<VodCategoryRow> {
        val dao = database.vodDao()
        val stored = dao.getStoredCategories(profile.id)
        if (!forceRefresh && stored.isNotEmpty()) {
            return stored.map { VodCategoryRow(it.categoryId, it.categoryName, it.categoryOrder) }
        }
        val remote = api(profile).vodCategories(profile.username, profile.password)
            .mapIndexedNotNull { index, category ->
                val id = category.categoryId?.trim().takeIf { !it.isNullOrBlank() } ?: return@mapIndexedNotNull null
                VodCategoryEntity(
                    profileId = profile.id,
                    categoryId = id,
                    categoryName = category.categoryName?.trim().takeIf { !it.isNullOrBlank() } ?: "Sans catégorie",
                    categoryOrder = index,
                )
            }
        database.withTransaction {
            dao.clearCategories(profile.id)
            dao.saveCategories(remote)
        }
        return remote.map { VodCategoryRow(it.categoryId, it.categoryName, it.categoryOrder) }
    }

    suspend fun loadVodPage(
        profile: XtreamProfile,
        categoryId: String,
        limit: Int = 20,
        offset: Int = 0,
    ): List<VodMovie> = database.vodDao()
        .let { dao ->
            if (dao.getMeta(profile.id) == null) loadVodMovies(profile)
            dao.getMoviesPage(profile.id, categoryId, limit, offset)
        }
        .map { it.toVodMovie() }

    suspend fun loadVodTitleIndex(profile: XtreamProfile): List<VodTitleRow> =
        vodTitleIndexCache[profile.id] ?: database.vodDao().getTitleIndex(profile.id).also {
            vodTitleIndexCache[profile.id] = it
        }

    suspend fun searchVodMoviesByActor(profile: XtreamProfile, actor: String): List<VodMovie> {
        val actorKey = normalizeActorKey(actor)
        if (actorKey.isBlank()) return emptyList()
        return database.vodDao().getMoviesByActor(profile.id, actorKey).map { it.toVodMovie() }
    }

    suspend fun ensureVodActorIndex(profile: XtreamProfile) {
        val dao = database.vodDao()
        if (dao.countActorIndex(profile.id) > 0) return
        val movies = dao.getMovies(profile.id)
        movies.map { movie -> actorIndexRows(movie) }
            .flatten()
            .chunked(VOD_SAVE_BATCH_SIZE)
            .forEach { batch -> dao.saveActorIndex(batch) }
    }

    suspend fun buildVodActorIndex(
        profile: XtreamProfile,
        categoryIds: Set<String>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ) {
        val dao = database.vodDao()
        val selectedMovies = dao.getMovies(profile.id).filter { it.categoryId in categoryIds }
        dao.clearActorIndex(profile.id)
        val total = selectedMovies.size
        var done = 0
        selectedMovies.map(::actorIndexRows)
            .flatten()
            .chunked(VOD_SAVE_BATCH_SIZE)
            .forEach { batch ->
                dao.saveActorIndex(batch)
                done = (done + batch.map { it.streamId }.distinct().size).coerceAtMost(total)
                onProgress(done, total)
            }
        if (total == 0) onProgress(0, 0)
    }

    suspend fun loadVodMoviesByIds(profile: XtreamProfile, streamIds: List<Int>): List<VodMovie> {
        if (streamIds.isEmpty()) return emptyList()
        return database.vodDao().getMoviesByIds(profile.id, streamIds).map { it.toVodMovie() }
    }

    suspend fun searchVodMoviesByTitle(profile: XtreamProfile, query: String): List<VodMovie> {
        val terms = normalizeSearchText(query)
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)
        if (terms.isEmpty()) return emptyList()

        // Chaque mot doit être présent, sans imposer la saisie du titre entier
        // ni son ordre exact (ex. « ouest » trouve « Il était une fois dans
        // l'Ouest », accents et casse ignorés).
        return database.vodDao().getMovies(profile.id)
            .filter { movie ->
                val title = normalizeSearchText(movie.name)
                terms.all { term -> title.contains(term) }
            }
            .map { it.toVodMovie() }
    }

    suspend fun loadVodDetails(
        profile: XtreamProfile,
        movie: VodMovie,
        forceRefresh: Boolean = false,
    ): VodMovie {
        val vodDao = database.vodDao()
        if (!forceRefresh) {
            vodDao.getMovie(profile.id, movie.streamId)
                ?.takeIf { it.detailsLoaded }
                ?.let { return it.toVodMovie() }
        }
        val info = api(profile).vodInfo(profile.username, profile.password, movie.streamId).info
        val detailed = movie.copy(
            posterUrl = info?.movieImage?.trim()?.takeIf(String::isNotBlank) ?: movie.posterUrl,
            description = info?.plot?.trim()?.takeIf(String::isNotBlank) ?: movie.description,
            releaseDate = info?.releasedate?.trim()?.takeIf(String::isNotBlank) ?: movie.releaseDate,
            duration = info?.duration?.trim()?.takeIf(String::isNotBlank) ?: movie.duration,
            durationSecs = info?.durationSecs ?: movie.durationSecs,
            rating = info?.rating?.trim()?.takeIf(String::isNotBlank) ?: movie.rating,
            actors = (info?.cast ?: info?.actors)?.trim()?.takeIf(String::isNotBlank) ?: movie.actors,
            director = info?.director?.trim()?.takeIf(String::isNotBlank) ?: movie.director,
            genre = info?.genre?.trim()?.takeIf(String::isNotBlank) ?: movie.genre,
            detailsLoaded = true,
        )
        vodDao.saveMovie(detailed.toEntity())
        return detailed
    }

    suspend fun cacheVodMovie(movie: VodMovie) {
        database.vodDao().saveMovie(movie.toEntity())
    }

    suspend fun cacheVodMovies(movies: List<VodMovie>) {
        if (movies.isEmpty()) return
        database.withTransaction {
            val entities = movies.map { it.toEntity() }
            database.vodDao().saveMovies(entities)
            database.vodDao().saveActorIndex(entities.flatMap(::actorIndexRows))
        }
    }

    private fun VodMovie.toEntity() = VodMovieEntity(
        profileId, streamId, name, categoryId, categoryName, country, posterUrl, extension,
        description, releaseDate, duration, durationSecs, rating, actors, director, genre,
        categoryOrder, providerOrder, detailsLoaded,
    )

    private fun VodMovieEntity.toVodMovie() = VodMovie(
        profileId, streamId, name, categoryId, categoryName, country, posterUrl, extension,
        description, releaseDate, duration, durationSecs, rating, actors, director, genre,
        categoryOrder, providerOrder, detailsLoaded,
    )

    private fun actorIndexRows(movie: VodMovieEntity): List<VodActorIndexEntity> =
        movie.actors.orEmpty()
            .split(Regex("\\s*(?:,|;|\\||/|\\band\\b)\\s*", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.length >= 2 }
            .mapNotNull { actorName ->
                normalizeActorKey(actorName).takeIf { it.isNotBlank() }?.let { actorKey ->
                    VodActorIndexEntity(movie.profileId, actorKey, actorName, movie.streamId)
                }
            }
            .distinctBy { it.actorKey }

    private fun normalizeActorKey(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length > 1 }
            .sorted()
            .joinToString(" ")

    private fun normalizeSearchText(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    private fun vodCountry(rawCountry: String?, categoryName: String): String {
        val aliases = mapOf(
            "ARGENTINA" to "AR", "ALLEMAGNE" to "DE", "GERMANY" to "DE",
            "FRANCE" to "FR", "ESPAGNE" to "ES", "SPAIN" to "ES",
            "ITALIE" to "IT", "ITALY" to "IT", "MAROC" to "MA",
            "TUNISIE" to "TN", "TURQUIE" to "TR", "TURKEY" to "TR",
            "ROYAUME UNI" to "GB", "UNITED KINGDOM" to "GB",
            "ETATS UNIS" to "US", "UNITED STATES" to "US", "USA" to "US",
        )
        val source = listOf(rawCountry.orEmpty(), categoryName).joinToString(" ")
            .uppercase(Locale.ROOT)
            .replace('-', ' ')
            .replace('_', ' ')
        val knownCodes = setOf("AR", "DE", "FR", "ES", "IT", "MA", "TN", "TR", "GB", "US", "CA", "BE", "CH", "NL", "PT", "BR", "IN", "JP", "KR", "CN", "MX")
        Regex("\\b[A-Z]{2}\\b").find(source)?.value?.takeIf { it in knownCodes }?.let { return it }
        aliases.entries.firstOrNull { source.contains(it.key) }?.value?.let { return it }
        return "Autres"
    }

    private fun vodCategoryName(categoryName: String, country: String): String =
        categoryName.trim()
            .replace(Regex("^${Regex.escape(country)}\\s*[-|:/]+\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^[A-Z]{2}\\s*[-|:/]+\\s*"), "")
            .trim()
            .ifBlank { "Sans catégorie" }

    suspend fun loadEpg(
        channel: SavedChannel,
        refresh: Boolean = false,
    ): List<EpgProgram> {
        val activeProfile = database.profileDao().getActive() ?: return emptyList()
        if (activeProfile.id != channel.profileId) return emptyList()
        return loadEpg(activeProfile, channel, refresh)
    }

    internal suspend fun loadCachedEpg(channel: SavedChannel): List<EpgProgram> = withContext(Dispatchers.IO) {
        loadCachedEpg(listOf(channel))[channel.streamId].orEmpty()
    }

    /** Resolve streamId -> epgChannelId -> cached EPG in one indexed query per profile. */
    internal suspend fun loadCachedEpg(channels: List<SavedChannel>): Map<Int, List<EpgProgram>> = withContext(Dispatchers.IO) {
        if (channels.isEmpty()) return@withContext emptyMap()
        val result = linkedMapOf<Int, List<EpgProgram>>()
        channels.groupBy(SavedChannel::profileId).forEach { (profileId, group) ->
            val byEpgId = group.mapNotNull { channel ->
                channel.epgChannelId?.trim()?.takeIf(String::isNotEmpty)?.let { it to channel }
            }.groupBy({ it.first }, { it.second })
            if (byEpgId.isEmpty()) return@forEach
            val cached = database.epgChannelCacheDao().getForIds(profileId, byEpgId.keys.toList())
                .associate { it.epgChannelId to decodeEpgCache(it.programsJson) }
            byEpgId.forEach { (epgId, matchingChannels) ->
                val programs = cached[epgId].orEmpty()
                if (programs.isNotEmpty()) matchingChannels.forEach { result[it.streamId] = programs }
            }
        }
        result
    }

    internal suspend fun loadEpg(
        profile: XtreamProfile,
        channel: SavedChannel,
        refresh: Boolean = false,
    ): List<EpgProgram> = withContext(Dispatchers.IO) {
        if (profile.id != channel.profileId) return@withContext emptyList()
        val epgId = channel.epgChannelId?.trim()?.takeIf(String::isNotEmpty)
            ?: return@withContext emptyList()
        epgCacheMutex.withLock {
            val previous = database.epgChannelCacheDao().get(profile.id, epgId)
            val cached = previous?.let { decodeEpgCache(it.programsJson) }.orEmpty()
            if (!refresh && cached.isNotEmpty()) return@withLock cached
            if (!refresh && previous != null && previous.programsJson == "[]" &&
                System.currentTimeMillis() - previous.attemptedAt < EPG_ID_RETRY_MS
            ) return@withLock emptyList()

            val variants = database.channelDao().getByEpgChannelId(profile.id, epgId)
            val ordered = (listOf(channel) + variants)
                .distinctBy(SavedChannel::streamId)
                .sortedWith(compareByDescending<SavedChannel> { it.streamId == channel.streamId }
                    .thenBy(SavedChannel::providerOrder))
            val after = previous?.sourceStreamId?.let { source ->
                ordered.indexOfFirst { it.streamId == source }.takeIf { it >= 0 }
            } ?: -1
            val candidates = if (cached.isEmpty() && after >= 0) {
                ordered.drop(after + 1) + ordered.take(after + 1)
            } else {
                ordered
            }
            val service = api(profile)
            val now = Instant.now().epochSecond
            var lastAttempted: Int? = null
            for (candidate in candidates.take(EPG_ID_MAX_VARIANTS)) {
                lastAttempted = candidate.streamId
                val programs = try {
                    val validPrograms = mapEpg(
                        service.shortEpg(profile.username, profile.password, candidate.streamId),
                        sourceZone = epgSourceZone(candidate),
                    ).filter { program ->
                        val start = program.startEpochSeconds
                        val stop = program.stopEpochSeconds
                        start != null && stop != null && stop > start && stop > now
                    }
                    normalEpgPrograms(validPrograms)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    emptyList()
                }
                if (programs.isNotEmpty()) {
                    database.epgChannelCacheDao().save(
                        EpgChannelCache(profile.id, epgId, encodeEpgCache(programs), System.currentTimeMillis(), candidate.streamId),
                    )
                    return@withLock programs
                }
            }
            database.epgChannelCacheDao().save(
                EpgChannelCache(profile.id, epgId, "[]", System.currentTimeMillis(), lastAttempted),
            )
            emptyList()
        }
    }

    private fun encodeEpgCache(programs: List<EpgProgram>): String = JSONArray().apply {
        programs.forEach { program ->
            put(JSONObject().apply {
                put("title", program.title)
                put("description", program.description)
                put("timeRange", program.timeRange)
                put("start", program.startEpochSeconds)
                put("stop", program.stopEpochSeconds)
            })
        }
    }.toString()

    internal suspend fun saveDownloadedEpg(
        profileId: Int,
        guides: Map<String, Pair<List<EpgProgram>, Int?>>,
    ) {
        if (guides.isEmpty()) return
        val downloadedAt = System.currentTimeMillis()
        database.epgChannelCacheDao().saveAll(guides.map { (epgId, value) ->
            EpgChannelCache(
                profileId = profileId,
                epgChannelId = epgId,
                programsJson = encodeEpgCache(value.first),
                attemptedAt = downloadedAt,
                sourceStreamId = value.second,
            )
        })
    }

    internal suspend fun cachedEpgIds(profileId: Int): Set<String> =
        database.epgChannelCacheDao().getIds(profileId).toSet()

    internal suspend fun clearEpgCache(profileId: Int) {
        database.epgChannelCacheDao().clear(profileId)
    }

    private fun decodeEpgCache(json: String): List<EpgProgram> = runCatching {
        val rows = JSONArray(json)
        val now = Instant.now().epochSecond
        buildList {
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                val start = row.getLong("start")
                val stop = row.getLong("stop")
                if (stop <= start || stop <= now) continue
                add(EpgProgram(
                    title = row.getString("title"),
                    description = row.optString("description"),
                    timeRange = row.optString("timeRange"),
                    startEpochSeconds = start,
                    stopEpochSeconds = stop,
                ))
            }
        }
    }.getOrDefault(emptyList())

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

    suspend fun exportBackupJson(remoteJson: String? = null): String {
        val activeProfile = database.profileDao().getActive()
            ?: error("Aucun profil Xtream à sauvegarder.")
        val profileBackups = database.profileDao().getAll().map { profile ->
            BackupProfile(
                profile = profile,
                favoriteGroups = backupFavoriteGroups(profile.id),
            )
        }
        val remote = remoteJson?.let { moshi.adapter(MyIptvBackup::class.java).fromJson(it) }
        val remoteProfiles = when (remote?.version) {
            null -> emptyList()
            1 -> remote?.profile?.let { listOf(BackupProfile(it, remote?.favoriteGroups.orEmpty())) }.orEmpty()
            2, 3, 4 -> remote?.profiles.orEmpty()
            else -> error("Version de sauvegarde GitHub non prise en charge.")
        }
        val combined = profileBackups + remoteProfiles.filter { remoteProfile ->
            profileBackups.none { profileIdentity(it.profile) == profileIdentity(remoteProfile.profile) }
        }
        val backup = MyIptvBackup(
            version = 4,
            profiles = combined,
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
            2, 3, 4 -> backup.profiles
            else -> error("Version de sauvegarde non prise en charge.")
        }
        require(rawProfiles.isNotEmpty()) { "La sauvegarde GitHub ne contient aucun profil." }

        val normalizedEntries = rawProfiles.map { entry ->
            val normalizedProfile = entry.profile.copy(
                serverUrl = normalizeServerUrl(entry.profile.serverUrl),
                username = entry.profile.username.trim(),
                name = entry.profile.name.ifBlank { "Profil GitHub" }.trim(),
            )
            if (normalizedProfile.protocol == ProfileProtocol.STALKER.name) {
                require(normalizedProfile.macAddress?.matches(Regex("(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$")) == true) {
                    "Profil GitHub Stalker sans adresse MAC valide."
                }
            } else {
                require(normalizedProfile.username.isNotBlank()) { "Profil GitHub sans utilisateur." }
                require(normalizedProfile.password.isNotBlank()) { "Profil GitHub sans mot de passe." }
            }
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
                if (credentialsChanged) database.epgChannelCacheDao().clear(profileId)

                val favoriteDao = database.favoriteDao()
                favoriteDao.clearGroups(profileId)
                val favoriteChannels = entry.favoriteGroups
                    .flatMap(BackupFavoriteGroup::channels)
                    .distinctBy(SavedChannel::streamId)
                    .map { channel ->
                        channel.copy(
                            profileId = profileId,
                            epgChannelId = channel.epgChannelId?.trim()?.takeIf(String::isNotEmpty),
                        )
                    }
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
        profile.protocol + '\u0000' +
            normalizeServerUrl(profile.serverUrl).lowercase(Locale.ROOT) + '\u0000' +
            profile.username.trim() + '\u0000' + profile.macAddress.orEmpty().uppercase(Locale.ROOT)

    private data class NormalizedBackupProfile(
        val profile: XtreamProfile,
        val favoriteGroups: List<BackupFavoriteGroup>,
        val active: Boolean,
    )

    suspend fun streamUrl(profile: XtreamProfile, channel: SavedChannel): String {
        if (profile.protocol == ProfileProtocol.STALKER.name) {
            return stalkerStreamUrl(profile, channel.extension)
        }
        return buildString {
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
    }

    private suspend fun stalkerStreamUrl(profile: XtreamProfile, cmd: String): String = withContext(Dispatchers.IO) {
        val mac = requireNotNull(profile.macAddress?.trim()?.uppercase(Locale.ROOT))
        val base = normalizeServerUrl(profile.serverUrl).toHttpUrl()
        val sessionCookies = mutableMapOf<String, MutableList<Cookie>>()
        val stalkerClient = client.newBuilder()
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<Cookie>) {
                    val stored = sessionCookies.getOrPut(url.host) { mutableListOf() }
                    cookies.forEach { cookie ->
                        stored.removeAll { it.name == cookie.name }
                        stored += cookie
                    }
                }

                override fun loadForRequest(url: okhttp3.HttpUrl): List<Cookie> =
                    sessionCookies[url.host].orEmpty()
            })
            .build()
        val portalCookie = "mac=$mac; stb_lang=en; timezone=Europe/Paris;"
        fun call(action: String, extra: Map<String, String> = emptyMap(), token: String? = null, type: String = "stb"): JSONObject {
            val url = base.newBuilder().addPathSegments("server/load.php")
                .addQueryParameter("type", type)
                .addQueryParameter("action", action)
                .addQueryParameter("JsHttpRequest", "1-xml")
                .apply { extra.forEach { (key, value) -> addQueryParameter(key, value) } }
                .build()
            val request = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (MAG254; Linux; Android 4.4.2)")
                .header("X-User-Agent", "Model: MAG254; Link: WiFi")
                .header("Cookie", portalCookie)
                .apply { token?.let { header("Authorization", "Bearer $it") } }
                .build()
            return stalkerClient.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Portail Stalker HTTP ${response.code}" }
                JSONObject(response.body?.string().orEmpty())
            }
        }
        val token = call("handshake", mapOf("mac" to mac))
            .optJSONObject("js")?.optString("token")?.takeIf { it.isNotBlank() }
            ?: error("Le portail Stalker n’a pas fourni de token.")
        var link = call("create_link", mapOf("cmd" to cmd), token, type = "itv")
            .optJSONObject("js")?.optString("cmd").orEmpty()
        if (link.startsWith("ffmpeg ")) link = link.removePrefix("ffmpeg ").trim()
        check(link.isNotBlank()) { "Le portail Stalker n’a pas fourni de lien de lecture." }
        link.replace("stream=&", "stream=${cmd.substringAfterLast('/').substringBefore('?')}&")
    }

    fun vodStreamUrl(profile: XtreamProfile, movie: VodMovie): String = buildString {
        append(profile.serverUrl.trimEnd('/'))
        append("/movie/")
        append(profile.username)
        append('/')
        append(profile.password)
        append('/')
        append(movie.streamId)
        append('.')
        append(movie.extension)
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
        return epgSourceZone(channel.epgChannelId, channel.countryCode)
    }

    private fun epgSourceZone(channel: EpgChannelRow): ZoneId {
        return epgSourceZone(channel.epgChannelId, channel.countryCode)
    }

    private fun epgSourceZone(epgChannelId: String?, countryCode: String): ZoneId {
        val epgSuffix = epgChannelId
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.uppercase(Locale.ROOT)
        val country = epgSuffix?.takeIf { it.length in 2..3 } ?: countryCode
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

    companion object {
        private const val SEARCH_HISTORY_LIMIT = 20
        private const val VOD_SAVE_BATCH_SIZE = 10_000
        private const val NORMAL_EPG_PROGRAM_LIMIT = 4
        private const val EPG_ID_MAX_VARIANTS = 6
        private const val EPG_ID_RETRY_MS = 15 * 60 * 1_000L
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

}
