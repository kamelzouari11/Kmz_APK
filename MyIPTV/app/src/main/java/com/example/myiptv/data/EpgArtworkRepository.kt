package com.example.myiptv.data

import java.io.IOException
import java.text.Normalizer
import java.util.Locale
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
internal data class EpgArtwork(val images: List<EpgArtworkImage>, val clubs: Boolean = false)

/** Fetches artwork only for large EPG cards, without IPTV credentials. */
internal object EpgArtworkRepository {
    private val client = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS).build()
    private val cacheMutex = Mutex()
    private data class Cached(val result: EpgArtwork?, val expiry: Long)
    private val cache = LinkedHashMap<String, Cached>(128, 0.75f, true)

    suspend fun find(originalTitle: String, country: String): EpgArtwork? = withTimeout(25_000L) {
        val query = artworkQuery(originalTitle)
        if (query.length < 3) return@withTimeout null
        cacheMutex.withLock {
            cache[query]?.takeIf { it.expiry > System.currentTimeMillis() }?.let { return@withLock it.result }
            val result = (findShow(query) ?: findImdb(query))?.let { EpgArtwork(listOf(it)) }
                ?: findMatchTeams(query)
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

    private suspend fun findMatchTeams(query: String): EpgArtwork? {
        val teams = query.substringAfterLast(':').trim()
            .split(Regex("\\s+(?:vs\\.?|v\\.?|[-–—])\\s+", RegexOption.IGNORE_CASE))
        if (teams.size != 2 || teams.any { it.length < 3 }) return null
        val images = buildList {
            teams.forEach { team -> findTeam(team)?.let(::add) }
        }
        return EpgArtwork(images, clubs = true).takeIf { images.size == 2 }
    }

    private suspend fun findTeam(name: String): EpgArtworkImage? {
        val url = "https://www.thesportsdb.com/api/v1/json/3/searchteams.php".toHttpUrl().newBuilder()
            .addQueryParameter("t", name).build()
        val candidates = JSONObject(get(url)).optJSONArray("teams") ?: return null
        return (0 until candidates.length()).mapNotNull { index ->
            val team = candidates.getJSONObject(index)
            val title = team.optString("strTeam")
            val badge = team.optString("strBadge").takeIf { it.startsWith("https://") }
                ?: return@mapNotNull null
            val score = artworkMatchScore(name, title)
            if (score < 0.75) null else score to EpgArtworkImage(
                badge, title, "https://www.thesportsdb.com/team/${team.optString("idTeam")}", "TheSportsDB",
            )
        }.maxByOrNull { it.first }?.second
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
