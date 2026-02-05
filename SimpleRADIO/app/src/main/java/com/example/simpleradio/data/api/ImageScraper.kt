package com.example.simpleradio.data.api

import android.graphics.BitmapFactory
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ImageScraper {

    private const val DEFAULT_MIN_SIZE = 300
    private const val METADATA_MIN_SIZE = 600
    private const val MAX_FILE_SIZE = 4 * 600 * 600 // 4MB maximum
    private const val CONNECTION_TIMEOUT = 5000
    private const val READ_TIMEOUT = 5000

    /** Trouve des logos radio (600p) avec validation. */
    suspend fun findLogos(radioName: String, country: String?, streamUrl: String?): List<String> =
            withContext(Dispatchers.IO) {
                val query = "$radioName ${country ?: ""} logo radio (all format images)"
                val googleLogos =
                        searchGoogleLogos(query, null) // Pas de filtre 'Large' pour les logos
                val filteredLogos =
                        googleLogos.filter { !it.endsWith(".svg", ignoreCase = true) }.distinct()

                val validatedLogos = mutableListOf<String>()
                for (url in filteredLogos) {
                    if (validatedLogos.size >= 5) break
                    if (isValidImage(url, DEFAULT_MIN_SIZE, DEFAULT_MIN_SIZE)) {
                        validatedLogos.add(url)
                    }
                }
                return@withContext validatedLogos
            }

    /** Trouve une liste de pochettes d'albums HD (1024p) avec validation. */
    suspend fun findArtworks(artist: String, title: String?): List<String> =
            withContext(Dispatchers.IO) {
                val query = "$artist ${title ?: ""} album cover 1024p"
                val candidates = searchGoogleLogos(query, "l")
                val validated = mutableListOf<String>()
                for (url in candidates) {
                    if (validated.size >= 5) break
                    if (isValidImage(url, METADATA_MIN_SIZE, METADATA_MIN_SIZE)) {
                        validated.add(url)
                    }
                }
                return@withContext validated
            }

    suspend fun findArtwork(artist: String, title: String?): String? =
            findArtworks(artist, title).firstOrNull()

    suspend fun findBestLogo(radioName: String, country: String?, streamUrl: String?): String? {
        return findLogos(radioName, country, streamUrl).firstOrNull()
    }

    suspend fun searchGoogleLogos(query: String, size: String? = null): List<String> =
            withContext(Dispatchers.IO) {
                val results = mutableListOf<String>()
                results.addAll(fetchGoogleImages(query, size))
                return@withContext results.distinct()
            }

    /** Valide qu'une image respecte les dimensions minimales. */
    private suspend fun isValidImage(imageUrl: String, minWidth: Int, minHeight: Int): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    val url = URL(imageUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.instanceFollowRedirects = true
                    connection.requestMethod = "GET"
                    connection.connectTimeout = CONNECTION_TIMEOUT
                    connection.readTimeout = READ_TIMEOUT
                    connection.setRequestProperty(
                            "User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    )

                    if (connection.responseCode != 200) {
                        connection.disconnect()
                        return@withContext false
                    }

                    val contentType = connection.contentType?.lowercase() ?: ""
                    if (!contentType.startsWith("image/")) {
                        connection.disconnect()
                        return@withContext false
                    }

                    val contentLength = connection.contentLength
                    if (contentLength > MAX_FILE_SIZE) {
                        connection.disconnect()
                        return@withContext false
                    }

                    val inputStream = java.io.BufferedInputStream(connection.inputStream)
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }

                    BitmapFactory.decodeStream(inputStream, null, options)
                    inputStream.close()
                    connection.disconnect()

                    val width = options.outWidth
                    val height = options.outHeight

                    if (width <= 0 || height <= 0) {
                        return@withContext false
                    }

                    return@withContext width >= minWidth && height >= minHeight
                } catch (e: Exception) {
                    return@withContext false
                }
            }

    private fun fetchGoogleImages(query: String, size: String?): List<String> {
        val images = mutableListOf<String>()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            // udm=2 active la recherche d'images pure (plus pertinente en 2024/2025)
            val sizeParam = if (size != null) "&tbs=isz:$size" else ""
            val urlStr =
                    "https://www.google.com/search?q=$encodedQuery&tbm=isch&udm=2$sizeParam&safe=active"

            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            conn.connectTimeout = CONNECTION_TIMEOUT
            conn.readTimeout = READ_TIMEOUT

            if (conn.responseCode == 200) {
                val html = conn.inputStream.bufferedReader().use { it.readText() }
                val cleanHtml =
                        html.replace("\\/", "/").replace("\\u003d", "=").replace("\\u0026", "&")

                // Regex pour capturer les URLs HD dans le JSON de Google
                val jsonImageUrlPattern =
                        "[\"'](https?://[^\"'<>\\s,]+?\\.(?:png|jpg|jpeg|webp|svg)[^\"'<>\\s,]*?)[\"']\\s*,\\s*\\d+\\s*,\\s*\\d+".toRegex(
                                RegexOption.IGNORE_CASE
                        )

                val matches = jsonImageUrlPattern.findAll(cleanHtml)
                for (match in matches) {
                    val url = match.groups[1]?.value ?: continue
                    if (isRealContentImage(url)) {
                        images.add(url)
                        if (images.size >= 20)
                                break // Collecter plus de candidats pour la validation
                    }
                }

                if (images.size < 10) {
                    // Fallback direct links
                    val directImagePattern =
                            "[\"'](https?://[^\"'<>\\s,]+?\\.(?:png|jpg|jpeg|webp|svg)[^\"'<>\\s,]*?)[\"']".toRegex(
                                    RegexOption.IGNORE_CASE
                            )
                    val directMatches = directImagePattern.findAll(cleanHtml)
                    for (match in directMatches) {
                        val url = match.groups[1]?.value ?: continue
                        if (isRealContentImage(url)) {
                            images.add(url)
                            if (images.size >= 20) break
                        }
                    }
                }

                if (images.isEmpty()) {
                    // Fallback Thumbnail gstatic (toujours fiable)
                    val thumbPattern =
                            "https://encrypted-tbn0.gstatic.com/images\\?q=tbn:[^\"'&\\s]+".toRegex()
                    val thumbMatch = thumbPattern.find(html)
                    if (thumbMatch != null) images.add(thumbMatch.value)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return images
    }

    private fun isRealContentImage(url: String): Boolean {
        val u = url.lowercase()
        // On évite les sites connus pour bloquer le hotlinking ou les images parasites
        return !u.contains("google.") &&
                !u.contains("gstatic.com") &&
                !u.contains("favicon") &&
                !u.contains("schema.org") &&
                !u.contains("facebook.com/tr") && // Pixel de tracking
                (u.startsWith("http://") || u.startsWith("https://"))
    }
}
