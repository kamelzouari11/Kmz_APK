package com.example.simpleradio.data.api

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object ImageScraper {

    private const val CONNECTION_TIMEOUT = 8000
    private const val READ_TIMEOUT = 8000
    private val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    // ─────────────────────────────────────────────────────────────────────────
    //  LOGOS DE RADIO (via Bing Images)
    // ─────────────────────────────────────────────────────────────────────────

    /** Trouve des logos radio avec stratégie progressive. */
    suspend fun findLogos(radioName: String, country: String?, streamUrl: String?): List<String> =
            withContext(Dispatchers.IO) {
                val queries = buildProgressiveQueries(radioName, country)
                for (query in queries) {
                    val logos = scrapeBingImages(query).filter { isRealContentImage(it) }.distinct().take(5)
                    if (logos.isNotEmpty()) return@withContext logos
                }
                return@withContext emptyList()
            }

    suspend fun findBestLogo(radioName: String, country: String?, streamUrl: String?): String? =
            findLogos(radioName, country, streamUrl).firstOrNull()

    private fun buildProgressiveQueries(radioName: String, country: String?): List<String> {
        val cp = if (!country.isNullOrBlank()) country else ""
        return listOf(
                "$radioName $cp logo radio station",
                "$radioName logo radio",
                "$radioName $cp icon",
                "$radioName logo"
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  POCHETTES D'ALBUMS (via Deezer → iTunes → Bing fallback)
    // ─────────────────────────────────────────────────────────────────────────

    /** Trouve une liste de pochettes d'albums HD. */
    suspend fun findArtworks(artist: String, title: String?): List<String> =
            withContext(Dispatchers.IO) {
                val results = mutableListOf<String>()

                // 1. Deezer (meilleure qualité, API ouverte)
                val deezerUrl = fetchDeezerCover(artist, title)
                if (deezerUrl != null) results.add(deezerUrl)

                // 2. iTunes / Apple Music
                val itunesUrl = fetchItunesCover(artist, title)
                if (itunesUrl != null && !results.contains(itunesUrl)) results.add(itunesUrl)

                // 3. Fallback Bing Images si aucune pochette trouvée
                if (results.isEmpty()) {
                    val query = "$artist ${title ?: ""} album cover"
                    val bingResults = scrapeBingImages(query).filter { isRealContentImage(it) }.take(5)
                    results.addAll(bingResults)
                }

                return@withContext results.distinct().take(10)
            }

    suspend fun findArtwork(artist: String, title: String?): String? =
            findArtworks(artist, title).firstOrNull()

    // ─────────────────────────────────────────────────────────────────────────
    //  DEEZER API
    // ─────────────────────────────────────────────────────────────────────────

    private fun fetchDeezerCover(artist: String, title: String?): String? {
        return try {
            val query = URLEncoder.encode("$artist ${title ?: ""}", "UTF-8")
            val url = URL("https://api.deezer.com/search?q=$query&limit=5")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.connectTimeout = CONNECTION_TIMEOUT
            conn.readTimeout = READ_TIMEOUT

            if (conn.responseCode == 200) {
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val data = json.optJSONArray("data") ?: return null
                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    val album = item.optJSONObject("album") ?: continue
                    val cover = album.optString("cover_xl").ifBlank { album.optString("cover_big") }
                    if (cover.isNotBlank()) return cover
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ITUNES API
    // ─────────────────────────────────────────────────────────────────────────

    private fun fetchItunesCover(artist: String, title: String?): String? {
        return try {
            val query = URLEncoder.encode("$artist ${title ?: ""}", "UTF-8")
            val url = URL("https://itunes.apple.com/search?term=$query&media=music&limit=5")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.connectTimeout = CONNECTION_TIMEOUT
            conn.readTimeout = READ_TIMEOUT

            if (conn.responseCode == 200) {
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val results = json.optJSONArray("results") ?: return null
                for (i in 0 until results.length()) {
                    val item = results.getJSONObject(i)
                    val artwork = item.optString("artworkUrl100").ifBlank { null } ?: continue
                    // Remplacer 100x100 par 1000x1000
                    return artwork.replace("100x100bb", "1000x1000bb").replace("100x100", "1000x1000")
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  BING IMAGES SCRAPER
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun searchGoogleLogos(query: String, size: String? = null): List<String> =
            withContext(Dispatchers.IO) { scrapeBingImages(query).distinct() }

    private fun scrapeBingImages(query: String): List<String> {
        val images = mutableListOf<String>()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val urlStr = "https://www.bing.com/images/search?q=$encodedQuery&form=HDRSC2&first=1"

            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
            conn.setRequestProperty("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.8")
            conn.connectTimeout = CONNECTION_TIMEOUT
            conn.readTimeout = READ_TIMEOUT

            if (conn.responseCode == 200) {
                val html = conn.inputStream.bufferedReader().use { it.readText() }

                // Bing stocke les URLs réelles dans le paramètre "murl"
                val murlPattern = """murl&quot;:&quot;(https?://[^&]+?)&quot;""".toRegex(RegexOption.IGNORE_CASE)
                murlPattern.findAll(html).forEach { images.add(it.groups[1]?.value ?: "") }

                // Fallback : chercher les imgurl dans les attributs data-src / m
                if (images.size < 5) {
                    val imgUrlPattern = """"murl":"(https?://[^"]+?)"""".toRegex(RegexOption.IGNORE_CASE)
                    imgUrlPattern.findAll(html).forEach {
                        val u = it.groups[1]?.value ?: ""
                        if (u.isNotBlank() && !images.contains(u)) images.add(u)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return images.distinct()
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  FILTRE D'IMAGES
    // ─────────────────────────────────────────────────────────────────────────

    private fun isRealContentImage(url: String): Boolean {
        val u = url.lowercase()
        return (u.startsWith("http://") || u.startsWith("https://")) &&
                !u.contains("favicon") &&
                !u.contains("pixel") &&
                !u.contains("tracker") &&
                !u.contains("ads.") &&
                !u.contains("schema.org")
    }
}
