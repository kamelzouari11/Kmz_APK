package com.example.myiptv.data

import com.example.myiptv.BuildConfig
import java.io.IOException
import java.text.Normalizer
import java.util.Locale
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

internal data class EpgArtworkImage(
    val url: String,
    val title: String,
    val sourceUrl: String,
    val sourceName: String,
)
internal data class EpgArtwork(val images: List<EpgArtworkImage>)

/** Fetches artwork only for large EPG cards, without IPTV credentials. */
internal object EpgArtworkRepository {
    private val client = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS).build()
    private val cacheMutex = Mutex()
    private data class Cached(val result: EpgArtwork?, val expiry: Long)
    private val cache = LinkedHashMap<String, Cached>(128, 0.75f, true)
    private data class CachedText(val result: String?, val expiry: Long)
    private val actorCache = LinkedHashMap<String, CachedText>(512, 0.75f, true)
    private val actorFilmographyCache = LinkedHashMap<String, Set<String>>(128, 0.75f, true)
    private val actorRankedMovieCache = LinkedHashMap<String, List<String>>(128, 0.75f, true)

    suspend fun find(originalTitle: String, country: String): EpgArtwork? = withTimeout(25_000L) {
        val query = artworkQuery(originalTitle)
        if (query.length < 3) return@withTimeout null
        cacheMutex.withLock {
            cache[query]?.takeIf { it.expiry > System.currentTimeMillis() }?.let { return@withLock it.result }
            val result = (findShow(query) ?: findImdb(query))?.let { EpgArtwork(listOf(it)) }
            cache[query] = Cached(result, System.currentTimeMillis() + TimeUnit.HOURS.toMillis(if (result == null) 1 else 24))
            while (cache.size > 128) cache.remove(cache.keys.first())
            result
        }
    }

    private suspend fun findShow(query: String): EpgArtworkImage? {
        val url = "https://api.tvmaze.com/search/shows".toHttpUrl().newBuilder()
            .addQueryParameter("q", query).build()
        val entries = JSONArray(get(url))
        return (0 until entries.length()).mapNotNull { index ->
            val show = entries.getJSONObject(index).optJSONObject("show") ?: return@mapNotNull null
            val title = show.optString("name")
            val image = show.optJSONObject("image")?.optString("original")
                ?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            val score = artworkMatchScore(query, title)
            if (score < 0.75) null else score to EpgArtworkImage(
                image, title, show.optString("url"), "TVMaze",
            )
        }.maxByOrNull { it.first }?.second
    }

    /** Cinema cards only accept matching movies, never the first similarly named series. */
    suspend fun findMovie(title: String): EpgArtwork? = withTimeout(25_000L) {
        val query = artworkQuery(title)
        if (query.length < 3) return@withTimeout null
        val key = "movie|$query"
        cacheMutex.withLock {
            val previous = cache[key]
            if (previous != null && previous.expiry > System.currentTimeMillis()) return@withLock previous.result
            val result = findImdb(query, movieOnly = true)?.let { EpgArtwork(listOf(it)) }
            cache[key] = Cached(result, System.currentTimeMillis() + TimeUnit.HOURS.toMillis(if (result == null) 1 else 24))
            while (cache.size > 128) cache.remove(cache.keys.first())
            result
        }
    }

    /** Fallback synopsis for VOD entries whose provider plot is empty or too short. */
    suspend fun findMovieSummary(title: String, year: String?, country: String): String? = withTimeout(12_000L) {
        val query = artworkQuery(title)
        if (query.length < 3) return@withTimeout null
        val language = when (country.uppercase(Locale.ROOT)) {
            "FR" -> "fr"
            "DE" -> "de"
            "ES" -> "es"
            "IT" -> "it"
            else -> "en"
        }
        val key = "summary|$language|$query"
        cacheMutex.withLock {
            val candidates = listOfNotNull(
                year?.takeIf { it.length == 4 }?.let { "$query ($it)" },
                query,
            )
            candidates.firstNotNullOfOrNull { candidate ->
                runCatching {
                    val url = "https://$language.wikipedia.org/api/rest_v1/page/summary/" +
                        URLEncoder.encode(candidate, StandardCharsets.UTF_8.name()).replace("+", "%20")
                    val json = JSONObject(get(url.toHttpUrl()))
                    json.optString("extract").trim().takeIf { it.length >= 80 }
                }.getOrNull()
            }
        }
    }

    /** IMDb suggestion data is used only to complete missing provider cast data. */
    suspend fun findMovieActors(title: String, year: String?): String? = withTimeout(12_000L) {
        val query = artworkQuery(title)
        if (query.length < 3) return@withTimeout null
        val key = "actors|$query|${year.orEmpty()}"
        cacheMutex.withLock {
            actorCache[key]?.takeIf { it.expiry > System.currentTimeMillis() }?.let { return@withLock it.result }
        }?.let { return@withTimeout it }
        val result = runCatching {
            val url = "https://v3.sg.media-imdb.com".toHttpUrl().newBuilder()
                .addPathSegment("suggestion").addPathSegment("x").addPathSegment("$query.json").build()
            val entries = JSONObject(get(url)).optJSONArray("d") ?: return@runCatching null
            (0 until entries.length()).asSequence()
                .map { entries.getJSONObject(it) }
                .filter { it.optString("q") == "feature" }
                .filter { year.isNullOrBlank() || it.optInt("y", -1).toString() == year }
                .map { it.optString("s").trim() }
                .firstOrNull { it.isNotBlank() }
        }.getOrNull()
        cacheMutex.withLock {
            actorCache[key] = CachedText(result, System.currentTimeMillis() + TimeUnit.DAYS.toMillis(30))
            while (actorCache.size > 512) actorCache.remove(actorCache.keys.first())
        }
        result
    }

    /** Returns film titles for an actor, using one Wikidata query instead of one request per film. */
    suspend fun findActorFilmography(actor: String): Set<String> = withTimeout(20_000L) {
        val query = actor.trim().replace(Regex("\\s+"), " ")
        if (query.length < 2) return@withTimeout emptySet()
        val key = query.lowercase(Locale.ROOT)
        cacheMutex.withLock { actorFilmographyCache[key] }?.let { return@withTimeout it }
        val sparqlActor = query.replace("\\", "\\\\").replace("\"", "\\\"")
        val sparql = """
            SELECT DISTINCT ?workLabel WHERE {
              ?person rdfs:label "$sparqlActor"@en.
              ?work wdt:P161 ?person.
              ?work rdfs:label ?workLabel.
              FILTER(LANG(?workLabel) = "en")
            }
            LIMIT 1000
        """.trimIndent()
        val url = "https://query.wikidata.org/sparql".toHttpUrl().newBuilder()
            .addQueryParameter("query", sparql)
            .addQueryParameter("format", "json")
            .build()
        val result = runCatching {
            val bindings = JSONObject(get(url)).optJSONObject("results")
                ?.optJSONArray("bindings") ?: JSONArray()
            (0 until bindings.length()).mapNotNull { index ->
                bindings.getJSONObject(index).optJSONObject("workLabel")?.optString("value")
                    ?.trim()?.takeIf(String::isNotBlank)
            }.toSet()
        }.getOrDefault(emptySet())
        // Une réponse vide peut venir d'un timeout ou d'une indisponibilité
        // Wikidata : ne pas la mémoriser définitivement.
        if (result.isNotEmpty()) {
            cacheMutex.withLock {
                actorFilmographyCache[key] = result
                while (actorFilmographyCache.size > 128) actorFilmographyCache.remove(actorFilmographyCache.keys.first())
            }
        }
        result
    }

    /** Returns the actor's best movies in Internet order, independently of the IPTV provider. */
    suspend fun findActorTopMovies(actor: String, limit: Int = 40): List<String> = withTimeout(20_000L) {
        val query = actor.trim().replace(Regex("\\s+"), " ")
        if (query.length < 2 || BuildConfig.TMDB_API_KEY.isBlank()) return@withTimeout emptyList()
        // Version de cache modifiée : les anciennes réponses limitées à 40
        // titres ne doivent pas être réutilisées.
        val key = "tmdb-v2|${query.lowercase(Locale.ROOT)}"
        cacheMutex.withLock { actorRankedMovieCache[key] }?.let { return@withTimeout it.take(limit) }

        val result = runCatching {
            val personSearchUrl = "https://api.themoviedb.org/3/search/person".toHttpUrl().newBuilder()
                .addQueryParameter("api_key", BuildConfig.TMDB_API_KEY)
                .addQueryParameter("query", query)
                .addQueryParameter("language", "en-US")
                .addQueryParameter("include_adult", "false")
                .build()
            val people = JSONObject(get(personSearchUrl)).optJSONArray("results") ?: JSONArray()
            val person = (0 until people.length()).asSequence()
                .map { people.getJSONObject(it) }
                .firstOrNull { it.optString("name").equals(query, ignoreCase = true) }
                ?: people.optJSONObject(0)
                ?: return@runCatching emptyList()
            val personId = person.optInt("id", 0)
            if (personId == 0) return@runCatching emptyList()

            val creditsUrl = "https://api.themoviedb.org/3/person/$personId/movie_credits".toHttpUrl().newBuilder()
                .addQueryParameter("api_key", BuildConfig.TMDB_API_KEY)
                .addQueryParameter("language", "en-US")
                .build()
            val cast = JSONObject(get(creditsUrl)).optJSONArray("cast") ?: JSONArray()
            (0 until cast.length()).flatMap { index ->
                val movie = cast.getJSONObject(index)
                val title = movie.optString("title").trim()
                if (title.isBlank()) return@flatMap emptyList()
                val originalTitle = movie.optString("original_title").trim()
                val score = movie.optDouble("vote_average", 0.0)
                listOf(title to score) + listOf(originalTitle)
                    .takeIf { originalTitle.isNotBlank() && !originalTitle.equals(title, ignoreCase = true) }
                    .orEmpty()
                    .map { it to score }
            }.sortedByDescending { it.second }
                .map { it.first }
                .distinctBy { it.lowercase(Locale.ROOT) }
                // On garde un large ensemble classé pour pouvoir trouver les
                // titres réellement présents chez le provider.
                .take(200)
        }.getOrDefault(emptyList())

        if (result.isNotEmpty()) {
            cacheMutex.withLock {
                actorRankedMovieCache[key] = result
                while (actorRankedMovieCache.size > 128) actorRankedMovieCache.remove(actorRankedMovieCache.keys.first())
            }
        }
        result.take(limit)
    }

    /** Tries the broadcast title first, then a French translation for artwork lookup. */
    internal suspend fun findTranslated(title: String, country: String, movieOnly: Boolean): EpgArtwork? {
        val original = if (movieOnly) findMovie(title) else find(title, country)
        if (original != null) return original
        val translated = runCatching { translateEpgToFrench(title, "", country).first }.getOrNull()
            ?.takeIf { it.isNotBlank() && !it.equals(title, ignoreCase = true) }
            ?: return null
        return if (movieOnly) findMovie(translated) else find(translated, country)
    }

    private suspend fun findImdb(query: String, movieOnly: Boolean = false): EpgArtworkImage? {
        val url = "https://v3.sg.media-imdb.com".toHttpUrl().newBuilder()
            .addPathSegment("suggestion").addPathSegment("x").addPathSegment("$query.json").build()
        val entries = JSONObject(get(url)).optJSONArray("d") ?: return null
        val candidates = (0 until entries.length()).mapNotNull { index ->
            val item = entries.getJSONObject(index)
            val type = item.optString("q")
            val allowed = if (movieOnly) setOf("feature", "TV movie") else setOf("feature", "TV series", "TV episode", "TV movie")
            if (type !in allowed) return@mapNotNull null
            val image = item.optJSONObject("i")?.optString("imageUrl")
                ?.takeIf { it.startsWith("https://m.media-amazon.com/") } ?: return@mapNotNull null
            val title = item.optString("l")
            val id = item.optString("id")
            val score = artworkMatchScore(query, title)
            // IMDb can return the localized query under an unrelated translated title
            // (for example "La casa di famiglia" -> "The Family House"). Its first
            // movie suggestion is still the strongest title-only match in that case.
            val translatedFirstMovie = movieOnly && index == 0
            if (title.isBlank() || id.isBlank() || (score < 0.85 && !translatedFirstMovie)) null else
                (if (translatedFirstMovie && score < 0.85) 0.84 else score) to EpgArtworkImage(
                image, title, "https://www.imdb.com/title/$id/", "IMDb",
            )
        }.sortedByDescending { it.first }
        return candidates.firstOrNull()?.second
    }

    private suspend fun get(url: okhttp3.HttpUrl): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(okhttp3.Request.Builder().url(url).header("User-Agent", "MyIPTV/0.2").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        if (!it.isSuccessful) throw IOException("Artwork HTTP ${it.code}")
                        it.body?.string() ?: throw IOException("Empty artwork response")
                    }
                }
                if (continuation.isActive) result.fold({ continuation.resume(it) }, { continuation.resumeWithException(it) })
            }
        })
    }
}

internal fun artworkQuery(title: String): String = title
    .replace(Regex("\\[[^]]*]"), " ")
    .replace(Regex("\\b(?:HD|FHD|UHD|4K|HEVC|H265)\\b", RegexOption.IGNORE_CASE), " ")
    .replace(Regex("^(?:(?:LIVE|DIRECT|EN DIRECT|FOOTBALL|SOCCER|CALCIO)\\s*[:|–-]\\s*)+", RegexOption.IGNORE_CASE), "")
    .replace(Regex("\\s+"), " ").trim().take(200)

private fun artworkWords(text: String): Set<String> = Normalizer.normalize(text, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
    .split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 2 }.toSet()

internal fun artworkMatchScore(query: String, title: String): Double {
    val requested = artworkWords(query)
    val candidate = artworkWords(title)
    if (requested.isEmpty() || candidate.any { it in setOf("soundtrack", "album", "podcast") }) return 0.0
    if (requested == candidate) return 2.0
    return requested.intersect(candidate).size.toDouble() / requested.size
}
