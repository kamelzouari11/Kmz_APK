package com.example.simpleradio.data.api

import android.os.SystemClock
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * Resolves one cover from Apple Search, then MusicBrainz and the Cover Art Archive.
 *
 * Every result must match the normalized artist/title pair. Apple fills common catalog gaps while
 * MusicBrainz and the Cover Art Archive remain the independent fallback.
 */
object CoverArtProvider {
    private const val CONNECTION_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000
    private const val MUSICBRAINZ_INTERVAL_MS = 1_100L
    private const val TOTAL_SEARCH_TIMEOUT_MS = 9_000L
    private const val USER_AGENT = "SimpleRADIO/1.0 (kamel@example.com)"

    private val trailingYearRegex =
            Regex("""\s*(?:\(|\[)(?:19|20)\d{2}(?:\)|\])\s*$""")
    private val trailingVersionRegex =
            Regex(
                    """\s*(?:[-–—]\s*)?(?:\(|\[)(?:(?:\d{4}\s+)?remaster(?:ed)?(?:\s+\d{4})?|""" +
                            """radio\s+edit|single\s+version|album\s+version)(?:\)|\])\s*$""",
                    RegexOption.IGNORE_CASE
            )
    private val versionFollowedByAlbumRegex =
            Regex(
                    """^(.+?)\s*(?:\(|\[)(?:(?:\d{4}\s+)?remaster(?:ed)?(?:\s+\d{4})?|""" +
                            """radio\s+edit|single\s+version|album\s+version)(?:\)|\])""" +
                            """\s+(?:\(|\[).+(?:\)|\])\s*$""",
                    RegexOption.IGNORE_CASE
            )
    private val ignoredTitleWords =
            setOf("the", "and", "for", "from", "with", "feat", "featuring", "remaster", "remastered")

    private val coverCache = ConcurrentHashMap<String, String>()
    private val missingCoverCache = ConcurrentHashMap.newKeySet<String>()
    private val musicBrainzMutex = Mutex()
    private var lastMusicBrainzRequestAt = 0L

    suspend fun findCover(artist: String, title: String): String? {
        val cleanArtist = artist.trim()
        val cleanTitle = catalogTitle(title)
        if (cleanArtist.isBlank() || cleanTitle.isBlank()) return null

        val cacheKey = "${canonicalArtist(cleanArtist)}|${canonicalTitle(cleanTitle)}"
        coverCache[cacheKey]?.let { return it }
        if (cacheKey in missingCoverCache) return null

        val coverUrl =
                withTimeoutOrNull(TOTAL_SEARCH_TIMEOUT_MS) {
                    val appleCover =
                            try {
                                searchItunesCover(cleanArtist, cleanTitle)
                            } catch (_: Exception) {
                                null
                            }
                    appleCover
                            ?: run {
                                val releaseGroupIds =
                                        searchOfficialReleaseGroups(cleanArtist, cleanTitle)
                                withContext(Dispatchers.IO) {
                                    releaseGroupIds.firstNotNullOfOrNull { releaseGroupId ->
                                        val candidate =
                                                "https://coverartarchive.org/release-group/" +
                                                        "$releaseGroupId/front-1200"
                                        candidate.takeIf { isReachableImage(it) }
                                    }
                                }
                            }
                }

        if (coverUrl != null) {
            coverCache[cacheKey] = coverUrl
        } else {
            // Avoid repeating a slow lookup on every metadata refresh for the same track.
            missingCoverCache += cacheKey
        }
        return coverUrl
    }

    /**
     * Artist-only fallback. Apple does not expose an artist portrait consistently, so this returns
     * a high-resolution album artwork only when the catalog artist matches the requested artist.
     */
    suspend fun findArtistArtwork(artist: String): String? {
        val cleanArtist = artist.trim()
        if (cleanArtist.isBlank()) return null

        val cacheKey = "artist|${canonicalArtist(cleanArtist)}"
        coverCache[cacheKey]?.let { return it }
        if (cacheKey in missingCoverCache) return null

        val artwork =
                withTimeoutOrNull(5_000L) {
                    try {
                        searchItunesArtistArtwork(cleanArtist)
                    } catch (_: Exception) {
                        null
                    }
                }

        if (artwork != null) {
            coverCache[cacheKey] = artwork
        } else {
            missingCoverCache += cacheKey
        }
        return artwork
    }

    /** Apple Search is free, needs no key and often fills Cover Art Archive gaps. */
    private suspend fun searchItunesCover(artist: String, title: String): String? =
            withContext(Dispatchers.IO) {
                val term = URLEncoder.encode("$artist $title", Charsets.UTF_8.name())
                val endpoint =
                        "https://itunes.apple.com/search?term=$term&media=music&entity=song&limit=25"
                val connection = openConnection(endpoint, "GET")
                try {
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        return@withContext null
                    }
                    val results =
                            JSONObject(
                                            connection.inputStream.bufferedReader().use {
                                                it.readText()
                                            }
                                    )
                                    .optJSONArray("results") ?: return@withContext null
                    val expectedArtist = canonicalArtist(artist)
                    val expectedTitle = canonicalTitle(title)
                    val appendedAlbum = splitTrailingGroup(title)

                    for (index in 0 until results.length()) {
                        val item = results.optJSONObject(index) ?: continue
                        val foundArtist = canonicalArtist(item.optString("artistName"))
                        val foundTitle = canonicalTitle(item.optString("trackName"))
                        val artistMatches =
                                foundArtist.isNotBlank() &&
                                        (foundArtist == expectedArtist ||
                                        foundArtist.contains(expectedArtist) ||
                                        expectedArtist.contains(foundArtist))
                        val titleMatch = titlesMatch(expectedTitle, foundTitle)
                        val titleAndAlbumMatch =
                                appendedAlbum?.let { (trackPart, albumPart) ->
                                    titlesMatch(canonicalTitle(trackPart), foundTitle) &&
                                            normalize(
                                                    catalogTitle(
                                                            item.optString("collectionName")
                                                    )
                                            ) == normalize(catalogTitle(albumPart))
                                } ?: false
                        if (!artistMatches || (!titleMatch && !titleAndAlbumMatch)) continue

                        val artwork = item.optString("artworkUrl100")
                        if (artwork.isNotBlank()) {
                            return@withContext artwork.replace(
                                    Regex("\\d+x\\d+bb"),
                                    "1200x1200bb"
                            )
                        }
                    }
                    null
                } finally {
                    connection.disconnect()
                }
            }

    private suspend fun searchItunesArtistArtwork(artist: String): String? =
            withContext(Dispatchers.IO) {
                val term = URLEncoder.encode(artist, Charsets.UTF_8.name())
                val endpoint =
                        "https://itunes.apple.com/search?term=$term&media=music&entity=album&limit=25"
                val connection = openConnection(endpoint, "GET")
                try {
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        return@withContext null
                    }
                    val results =
                            JSONObject(
                                            connection.inputStream.bufferedReader().use {
                                                it.readText()
                                            }
                                    )
                                    .optJSONArray("results") ?: return@withContext null
                    val expectedArtist = canonicalArtist(artist)

                    for (index in 0 until results.length()) {
                        val item = results.optJSONObject(index) ?: continue
                        val foundArtist = canonicalArtist(item.optString("artistName"))
                        val artistMatches =
                                foundArtist.isNotBlank() &&
                                        foundArtist == expectedArtist
                        if (!artistMatches) continue

                        val artwork = item.optString("artworkUrl100")
                        if (artwork.isNotBlank()) {
                            return@withContext artwork.replace(
                                    Regex("\\d+x\\d+bb"),
                                    "1200x1200bb"
                            )
                        }
                    }
                    null
                } finally {
                    connection.disconnect()
                }
            }

    private suspend fun searchOfficialReleaseGroups(artist: String, title: String): List<String> =
            musicBrainzMutex.withLock {
                val elapsed = SystemClock.elapsedRealtime() - lastMusicBrainzRequestAt
                if (elapsed < MUSICBRAINZ_INTERVAL_MS) {
                    delay(MUSICBRAINZ_INTERVAL_MS - elapsed)
                }
                lastMusicBrainzRequestAt = SystemClock.elapsedRealtime()

                withContext(Dispatchers.IO) {
                    val query =
                            "recording:\"${escapeLucene(title)}\" AND " +
                                    "artist:\"${escapeLucene(artist)}\" AND status:official"
                    val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8.name())
                    val endpoint =
                            "https://musicbrainz.org/ws/2/recording/" +
                                    "?query=$encodedQuery&fmt=json&limit=10"
                    val connection = openConnection(endpoint, "GET")

                    try {
                        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                            return@withContext emptyList()
                        }
                        val json =
                                JSONObject(
                                        connection.inputStream.bufferedReader().use { it.readText() }
                                )
                        extractMatchingReleaseGroups(
                                recordings = json.optJSONArray("recordings") ?: JSONArray(),
                                artist = artist,
                                title = title
                        )
                    } finally {
                        connection.disconnect()
                    }
                }
            }

    private fun extractMatchingReleaseGroups(
            recordings: JSONArray,
            artist: String,
            title: String
    ): List<String> {
        val expectedArtist = canonicalArtist(artist)
        val expectedTitle = canonicalTitle(title)
        val result = mutableListOf<String>()

        for (index in 0 until recordings.length()) {
            val recording = recordings.optJSONObject(index) ?: continue
            if (recording.optInt("score", 0) < 90) continue
            if (!titlesMatch(expectedTitle, canonicalTitle(recording.optString("title")))) continue

            val creditedArtist = extractArtistCredit(recording.optJSONArray("artist-credit"))
            val normalizedCredit = canonicalArtist(creditedArtist)
            if (normalizedCredit.isBlank() ||
                            (!normalizedCredit.contains(expectedArtist) &&
                            !expectedArtist.contains(normalizedCredit)
                            )
            ) {
                continue
            }

            val releases = recording.optJSONArray("releases") ?: continue
            for (releaseIndex in 0 until releases.length()) {
                val release = releases.optJSONObject(releaseIndex) ?: continue
                if (!release.optString("status").equals("Official", ignoreCase = true)) continue
                val releaseGroupId =
                        release.optJSONObject("release-group")
                                ?.optString("id")
                                ?.takeIf { it.isNotBlank() }
                                ?: continue
                if (releaseGroupId !in result) result += releaseGroupId
            }
        }
        return result.take(5)
    }

    private fun extractArtistCredit(credits: JSONArray?): String {
        if (credits == null) return ""
        return buildString {
            for (index in 0 until credits.length()) {
                val name = credits.optJSONObject(index)?.optString("name").orEmpty()
                if (name.isNotBlank()) {
                    if (isNotEmpty()) append(' ')
                    append(name)
                }
            }
        }
    }

    private fun isReachableImage(url: String): Boolean {
        val connection = openConnection(url, "HEAD")
        connection.instanceFollowRedirects = true
        return try {
            val code = connection.responseCode
            val contentType = connection.contentType.orEmpty().lowercase(Locale.ROOT)
            code in 200..299 && contentType.startsWith("image/")
        } catch (_: Exception) {
            false
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String, method: String): HttpURLConnection =
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = CONNECTION_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", if (method == "GET") "application/json" else "image/*")
            }

    private fun escapeLucene(value: String): String =
            value.replace("\\", "\\\\").replace("\"", "\\\"")

    /** Removes radio annotations that are not part of the catalog title. */
    private fun catalogTitle(value: String): String {
        var result = value.trim()
        // Some streams append the album after the track version, for example:
        // "At Seventeen (Remastered) (Between the Lines (Remastered))".
        result =
                versionFollowedByAlbumRegex.matchEntire(result)?.groupValues?.get(1)?.trim()
                        ?: result

        while (result.isNotBlank()) {
            val stripped =
                    result.replace(trailingYearRegex, "").replace(trailingVersionRegex, "").trim()
            if (stripped == result) break
            result = stripped
        }
        return result.trim()
    }

    /** Splits a final balanced '(album)' or '[album]' group without damaging song parentheses. */
    private fun splitTrailingGroup(value: String): Pair<String, String>? {
        val trimmed = value.trim()
        val closing = trimmed.lastOrNull() ?: return null
        val opening = when (closing) {
            ')' -> '('
            ']' -> '['
            else -> return null
        }
        var depth = 0
        for (index in trimmed.indices.reversed()) {
            when (trimmed[index]) {
                closing -> depth++
                opening -> {
                    depth--
                    if (depth == 0) {
                        val track = trimmed.substring(0, index).trim()
                        val album = trimmed.substring(index + 1, trimmed.lastIndex).trim()
                        return if (track.isNotBlank() && album.isNotBlank()) {
                            track to album
                        } else {
                            null
                        }
                    }
                }
            }
        }
        return null
    }

    private fun canonicalArtist(value: String): String =
            normalize(value).removePrefix("the ").trim()

    /** Treats an optional leading "The" as a catalog variation, not as a fuzzy title match. */
    private fun canonicalTitle(value: String): String =
            normalize(catalogTitle(value)).removePrefix("the ").trim()

    /**
     * Accepts exact catalog titles first, then a conservative two-word containment match.
     * The shorter title must be entirely represented in the longer one.
     */
    private fun titlesMatch(expected: String, found: String): Boolean {
        if (expected.isBlank() || found.isBlank()) return false
        if (expected == found) return true

        val expectedWords = significantTitleWords(expected)
        val foundWords = significantTitleWords(found)
        val shorter = if (expectedWords.size <= foundWords.size) expectedWords else foundWords
        val longer = if (expectedWords.size <= foundWords.size) foundWords else expectedWords
        return shorter.size >= 2 && longer.containsAll(shorter)
    }

    private fun significantTitleWords(value: String): Set<String> =
            value.split(' ')
                    .asSequence()
                    .filter { word ->
                        word.length >= 3 &&
                                word !in ignoredTitleWords &&
                                word.toIntOrNull() == null
                    }
                    .toSet()

    private fun normalize(value: String): String =
            Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
                    .replace("\\p{Mn}+".toRegex(), "")
                    .replace("[^a-z0-9]+".toRegex(), " ")
                    .trim()
}
