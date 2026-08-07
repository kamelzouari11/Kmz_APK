package com.kmz.shazamplayer.network

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class YouTubeResult(
        val videoId: String,
        val title: String,
        val channelTitle: String,
        val artworkUrl: String?,
        val durationMs: Long,
        val score: Int
)

class YouTubeApiException(message: String) : Exception(message)

/**
 * Official YouTube Data API client.
 *
 * It only resolves video IDs and metadata. Playback is deliberately delegated to the official
 * embedded player so no audio URL is extracted or downloaded.
 */
class YouTubeManager(context: Context, private val apiKey: String) {
    private val applicationPackage = context.packageName
    private val signingCertificateSha1 = signingCertificateSha1(context)
    private val cache =
            context.getSharedPreferences("YouTubeTrackCache", Context.MODE_PRIVATE)
    private val client =
            OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()

    suspend fun searchTracks(
            artist: String,
            title: String,
            expectedDurationMs: Long?
    ): List<YouTubeResult> =
            withContext(Dispatchers.IO) {
                readCache(artist, title)?.let { return@withContext it }

                if (apiKey.isBlank()) {
                    throw YouTubeApiException(
                            "Clé YouTube absente. Ajoutez YOUTUBE_API_KEY dans local.properties."
                    )
                }

                val searchUrl =
                        Uri.parse("https://www.googleapis.com/youtube/v3/search")
                                .buildUpon()
                                .appendQueryParameter("part", "snippet")
                                .appendQueryParameter("q", "$artist $title official audio")
                                .appendQueryParameter("type", "video")
                                .appendQueryParameter("videoCategoryId", "10")
                                .appendQueryParameter("videoEmbeddable", "true")
                                .appendQueryParameter("videoSyndicated", "true")
                                .appendQueryParameter("maxResults", "10")
                                .appendQueryParameter("safeSearch", "none")
                                .appendQueryParameter("key", apiKey)
                                .build()
                                .toString()

                val searchJson = executeJson(searchUrl)
                val searchItems = searchJson.optJSONArray("items") ?: JSONArray()
                val snippets = linkedMapOf<String, JSONObject>()
                for (index in 0 until searchItems.length()) {
                    val item = searchItems.optJSONObject(index) ?: continue
                    val videoId = item.optJSONObject("id")?.optString("videoId").orEmpty()
                    if (VIDEO_ID.matches(videoId)) {
                        snippets[videoId] = item.optJSONObject("snippet") ?: JSONObject()
                    }
                }

                if (snippets.isEmpty()) return@withContext emptyList()

                val detailsUrl =
                        Uri.parse("https://www.googleapis.com/youtube/v3/videos")
                                .buildUpon()
                                .appendQueryParameter("part", "contentDetails,status,snippet")
                                .appendQueryParameter("id", snippets.keys.joinToString(","))
                                .appendQueryParameter("key", apiKey)
                                .build()
                                .toString()

                val details = executeJson(detailsUrl).optJSONArray("items") ?: JSONArray()
                val results = mutableListOf<YouTubeResult>()
                for (index in 0 until details.length()) {
                    val item = details.optJSONObject(index) ?: continue
                    val status = item.optJSONObject("status") ?: continue
                    if (!status.optBoolean("embeddable", false)) continue

                    val videoId = item.optString("id")
                    val snippet = item.optJSONObject("snippet") ?: snippets[videoId] ?: continue
                    val candidateTitle = decodeHtml(snippet.optString("title"))
                    val channelTitle = decodeHtml(snippet.optString("channelTitle"))
                    val description = decodeHtml(snippet.optString("description"))
                    val durationMs =
                            parseIsoDurationMs(
                                    item.optJSONObject("contentDetails")?.optString("duration").orEmpty()
                            )
                    val artwork =
                            snippet.optJSONObject("thumbnails")?.let { thumbnails ->
                                listOf("maxres", "standard", "high", "medium", "default")
                                        .firstNotNullOfOrNull { size ->
                                            thumbnails
                                                    .optJSONObject(size)
                                                    ?.optString("url")
                                                    ?.takeIf { it.isNotBlank() }
                                        }
                            }

                    results +=
                            YouTubeResult(
                                    videoId = videoId,
                                    title = candidateTitle,
                                    channelTitle = channelTitle,
                                    artworkUrl = artwork,
                                    durationMs = durationMs,
                                    score =
                                            score(
                                                    artist = artist,
                                                    title = title,
                                                    candidateTitle = candidateTitle,
                                                    channelTitle = channelTitle,
                                                    description = description,
                                                    durationMs = durationMs,
                                                    expectedDurationMs = expectedDurationMs
                                            )
                            )
                }

                results.sortedByDescending { it.score }.take(5).also { sorted ->
                    if (sorted.isNotEmpty()) writeCache(artist, title, sorted)
                }
            }

    /** Returns embeddable popular music videos for an artist, ordered by YouTube view count. */
    suspend fun searchArtistTopTracks(artist: String): List<YouTubeResult> =
            withContext(Dispatchers.IO) {
                readCache(artist, ARTIST_RADIO_CACHE_TOKEN)?.let { return@withContext it }

                if (apiKey.isBlank()) {
                    throw YouTubeApiException(
                            "Clé YouTube absente. Ajoutez YOUTUBE_API_KEY dans local.properties."
                    )
                }

                val searchUrl =
                        Uri.parse("https://www.googleapis.com/youtube/v3/search")
                                .buildUpon()
                                .appendQueryParameter("part", "snippet")
                                .appendQueryParameter("q", "$artist official music")
                                .appendQueryParameter("type", "video")
                                .appendQueryParameter("videoCategoryId", "10")
                                .appendQueryParameter("videoEmbeddable", "true")
                                .appendQueryParameter("videoSyndicated", "true")
                                .appendQueryParameter("order", "viewCount")
                                .appendQueryParameter("maxResults", "25")
                                .appendQueryParameter("safeSearch", "none")
                                .appendQueryParameter("key", apiKey)
                                .build()
                                .toString()

                val searchItems =
                        executeJson(searchUrl).optJSONArray("items") ?: return@withContext emptyList()
                val orderedIds = buildList {
                    for (index in 0 until searchItems.length()) {
                        val videoId =
                                searchItems
                                        .optJSONObject(index)
                                        ?.optJSONObject("id")
                                        ?.optString("videoId")
                                        .orEmpty()
                        if (VIDEO_ID.matches(videoId)) add(videoId)
                    }
                }
                if (orderedIds.isEmpty()) return@withContext emptyList()

                val detailsUrl =
                        Uri.parse("https://www.googleapis.com/youtube/v3/videos")
                                .buildUpon()
                                .appendQueryParameter(
                                        "part",
                                        "contentDetails,status,snippet,statistics"
                                )
                                .appendQueryParameter("id", orderedIds.joinToString(","))
                                .appendQueryParameter("key", apiKey)
                                .build()
                                .toString()
                val detailItems =
                        executeJson(detailsUrl).optJSONArray("items") ?: return@withContext emptyList()
                val candidates = mutableListOf<ArtistRadioCandidate>()
                val wantedArtist = normalize(artist)

                for (index in 0 until detailItems.length()) {
                    val item = detailItems.optJSONObject(index) ?: continue
                    val status = item.optJSONObject("status") ?: continue
                    if (!status.optBoolean("embeddable", false)) continue

                    val videoId = item.optString("id")
                    val snippet = item.optJSONObject("snippet") ?: continue
                    val candidateTitle = decodeHtml(snippet.optString("title"))
                    val channelTitle = decodeHtml(snippet.optString("channelTitle"))
                    val description = decodeHtml(snippet.optString("description"))
                    val normalizedIdentity = normalize("$candidateTitle $channelTitle")
                    val normalizedContext = normalize("$normalizedIdentity $description")
                    if (!normalizedIdentity.contains(wantedArtist) &&
                                    !normalizedContext.contains(wantedArtist)
                    ) {
                        continue
                    }

                    val normalizedTitle = " ${normalize(candidateTitle)} "
                    if (ARTIST_RADIO_EXCLUSIONS.any { normalizedTitle.contains(it) }) continue

                    val durationMs =
                            parseIsoDurationMs(
                                    item.optJSONObject("contentDetails")
                                            ?.optString("duration")
                                            .orEmpty()
                            )
                    if (durationMs !in MIN_ARTIST_TRACK_DURATION_MS..MAX_ARTIST_TRACK_DURATION_MS) {
                        continue
                    }

                    val artwork =
                            snippet.optJSONObject("thumbnails")?.let { thumbnails ->
                                listOf("maxres", "standard", "high", "medium", "default")
                                        .firstNotNullOfOrNull { size ->
                                            thumbnails
                                                    .optJSONObject(size)
                                                    ?.optString("url")
                                                    ?.takeIf { it.isNotBlank() }
                                        }
                            }
                    val cleanTitle = cleanArtistTrackTitle(artist, candidateTitle)
                    val viewCount =
                            item.optJSONObject("statistics")?.optLong("viewCount", 0L) ?: 0L
                    candidates +=
                            ArtistRadioCandidate(
                                    result =
                                            YouTubeResult(
                                                    videoId = videoId,
                                                    title = cleanTitle,
                                                    channelTitle = channelTitle,
                                                    artworkUrl = artwork,
                                                    durationMs = durationMs,
                                                    score = 0
                                            ),
                                    viewCount = viewCount,
                                    canonicalTitle = normalize(cleanTitle)
                            )
                }

                candidates
                        .sortedByDescending { it.viewCount }
                        .distinctBy { it.canonicalTitle }
                        .map { it.result }
                        .take(MAX_ARTIST_RADIO_TRACKS)
                        .also { results ->
                            if (results.isNotEmpty()) {
                                writeCache(artist, ARTIST_RADIO_CACHE_TOKEN, results)
                            }
                        }
            }

    fun clearCachedTrack(artist: String, title: String) {
        cache.edit().remove(cacheKey(artist, title)).apply()
    }

    private fun cleanArtistTrackTitle(artist: String, rawTitle: String): String {
        var title = rawTitle.replace(VIDEO_TITLE_QUALIFIER, " ").replace("\\s+".toRegex(), " ").trim()
        val separator = listOf(" - ", " – ", " — ", " | ").firstOrNull { title.contains(it) }
        if (separator != null) {
            val parts = title.split(separator, limit = 2).map { it.trim() }
            if (parts.size == 2) {
                val wantedArtist = normalize(artist)
                title =
                        when {
                            normalize(parts[0]).contains(wantedArtist) -> parts[1]
                            normalize(parts[1]).contains(wantedArtist) -> parts[0]
                            else -> title
                        }
            }
        }
        return title.trim(' ', '-', '|').takeIf { it.isNotBlank() } ?: rawTitle
    }

    private fun executeJson(url: String): JSONObject {
        val request =
                Request.Builder()
                        .url(url)
                        .header("X-Android-Package", applicationPackage)
                        .apply {
                            signingCertificateSha1?.let {
                                header("X-Android-Cert", it)
                            }
                        }
                        .get()
                        .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val apiMessage =
                        runCatching {
                                    JSONObject(body)
                                            .optJSONObject("error")
                                            ?.optString("message")
                                            .orEmpty()
                                }
                                .getOrDefault("")
                val message =
                        when (response.code) {
                            400 -> "Clé YouTube invalide ou requête refusée."
                            403 -> "API YouTube refusée : vérifiez la clé, les restrictions et le quota."
                            429 -> "Quota YouTube atteint. Réessayez plus tard."
                            else -> "Erreur YouTube ${response.code}."
                        }
                throw YouTubeApiException(
                        if (apiMessage.isBlank()) message else "$message $apiMessage"
                )
            }
            return JSONObject(body)
        }
    }

    private fun score(
            artist: String,
            title: String,
            candidateTitle: String,
            channelTitle: String,
            description: String,
            durationMs: Long,
            expectedDurationMs: Long?
    ): Int {
        val wantedTitle = normalize(title)
        val wantedArtist = normalize(artist)
        val candidate = normalize(candidateTitle)
        val channel = normalize(channelTitle)
        val normalizedDescription = normalize(description)
        var value = 0

        if (candidate.contains(wantedTitle)) value += 55
        value += tokenSimilarity(wantedTitle, candidate) * 35 / 100

        if (candidate.contains(wantedArtist)) value += 30
        if (channel.contains(wantedArtist)) value += 25
        value += tokenSimilarity(wantedArtist, "$candidate $channel") * 20 / 100

        if (channelTitle.endsWith(" - Topic", ignoreCase = true)) value += 35
        if (channelTitle.endsWith("VEVO", ignoreCase = true)) value += 30
        if (candidate.contains("official audio")) value += 20
        if (normalizedDescription.contains("provided to youtube by")) value += 30

        val unwanted =
                listOf(
                        "remix",
                        "cover",
                        "live",
                        "slowed",
                        "sped up",
                        "nightcore",
                        "karaoke",
                        "instrumental",
                        "reverb"
                )
        unwanted.forEach { qualifier ->
            if (candidate.contains(qualifier) && !wantedTitle.contains(qualifier)) value -= 35
        }

        if (expectedDurationMs != null && expectedDurationMs > 0 && durationMs > 0) {
            val difference = kotlin.math.abs(durationMs - expectedDurationMs)
            value +=
                    when {
                        difference <= 8_000 -> 35
                        difference <= 20_000 -> 20
                        difference <= 35_000 -> 5
                        else -> -40
                    }
        }
        return value
    }

    private fun tokenSimilarity(expected: String, actual: String): Int {
        val expectedTokens = expected.split(' ').filter { it.length > 1 }.toSet()
        if (expectedTokens.isEmpty()) return 0
        return ((expectedTokens.count { actual.contains(it) }.toDouble() / expectedTokens.size) * 100)
                .toInt()
                .coerceIn(0, 100)
    }

    private fun writeCache(artist: String, title: String, results: List<YouTubeResult>) {
        val array = JSONArray()
        results.forEach { result ->
            array.put(
                    JSONObject()
                            .put("videoId", result.videoId)
                            .put("title", result.title)
                            .put("channelTitle", result.channelTitle)
                            .put("artworkUrl", result.artworkUrl)
                            .put("durationMs", result.durationMs)
                            .put("score", result.score)
            )
        }
        cache.edit().putString(cacheKey(artist, title), array.toString()).apply()
    }

    private fun readCache(artist: String, title: String): List<YouTubeResult>? {
        val raw = cache.getString(cacheKey(artist, title), null) ?: return null
        return runCatching {
                    val array = JSONArray(raw)
                    buildList {
                        for (index in 0 until array.length()) {
                            val item = array.getJSONObject(index)
                            val videoId = item.getString("videoId")
                            if (!VIDEO_ID.matches(videoId)) continue
                            add(
                                    YouTubeResult(
                                            videoId = videoId,
                                            title = item.getString("title"),
                                            channelTitle = item.getString("channelTitle"),
                                            artworkUrl =
                                                    item.optString("artworkUrl")
                                                            .takeIf { it.isNotBlank() && it != "null" },
                                            durationMs = item.optLong("durationMs"),
                                            score = item.optInt("score")
                                    )
                            )
                        }
                    }
                }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
    }

    private fun cacheKey(artist: String, title: String): String =
            "v1:${normalize(artist)}:${normalize(title)}"

    private fun normalize(value: String): String =
            Normalizer.normalize(value, Normalizer.Form.NFD)
                    .replace("\\p{M}+".toRegex(), "")
                    .lowercase(Locale.ROOT)
                    .replace("&", " and ")
                    .replace("[^a-z0-9]+".toRegex(), " ")
                    .trim()

    private fun parseIsoDurationMs(value: String): Long {
        val match = ISO_DURATION.matchEntire(value) ?: return 0L
        val hours = match.groupValues[1].toLongOrNull() ?: 0L
        val minutes = match.groupValues[2].toLongOrNull() ?: 0L
        val seconds = match.groupValues[3].toLongOrNull() ?: 0L
        return ((hours * 3600) + (minutes * 60) + seconds) * 1000
    }

    private fun decodeHtml(value: String): String =
            value.replace("&amp;", "&")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")

    @Suppress("DEPRECATION")
    private fun signingCertificateSha1(context: Context): String? =
            runCatching {
                        val packageInfo =
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    context.packageManager.getPackageInfo(
                                            context.packageName,
                                            PackageManager.GET_SIGNING_CERTIFICATES
                                    )
                                } else {
                                    context.packageManager.getPackageInfo(
                                            context.packageName,
                                            PackageManager.GET_SIGNATURES
                                    )
                                }
                        val signature =
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    packageInfo.signingInfo
                                            ?.apkContentsSigners
                                            ?.firstOrNull()
                                } else {
                                    packageInfo.signatures?.firstOrNull()
                                } ?: return@runCatching null
                        MessageDigest.getInstance("SHA-1")
                                .digest(signature.toByteArray())
                                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
                    }
                    .getOrNull()

    companion object {
        private data class ArtistRadioCandidate(
                val result: YouTubeResult,
                val viewCount: Long,
                val canonicalTitle: String
        )

        private const val ARTIST_RADIO_CACHE_TOKEN = "artist-radio-v1"
        private const val MAX_ARTIST_RADIO_TRACKS = 20
        private const val MIN_ARTIST_TRACK_DURATION_MS = 45_000L
        private const val MAX_ARTIST_TRACK_DURATION_MS = 15 * 60_000L
        private val VIDEO_ID = "[A-Za-z0-9_-]{11}".toRegex()
        private val ISO_DURATION = "PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?".toRegex()
        private val VIDEO_TITLE_QUALIFIER =
                """\s*[\[(](?:(?:official|music|officiel|clip)\s+)*(?:video|audio|lyrics?|visuali[sz]er|hd|4k|clip)[^)\]]*[)\]]"""
                        .toRegex(RegexOption.IGNORE_CASE)
        private val ARTIST_RADIO_EXCLUSIONS =
                listOf(
                        " live at ",
                        " live from ",
                        " live performance ",
                        " live session ",
                        " ao vivo ",
                        " cover by ",
                        " cover version ",
                        " karaoke ",
                        " reaction ",
                        " interview ",
                        " tutorial ",
                        " slowed ",
                        " sped up ",
                        " nightcore ",
                        " remix ",
                        " 1 hour "
                )
    }
}
