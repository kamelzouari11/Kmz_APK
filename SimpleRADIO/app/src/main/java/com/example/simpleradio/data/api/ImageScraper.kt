package com.example.simpleradio.data.api

import android.graphics.BitmapFactory
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ImageScraper {

    private const val DEFAULT_MIN_SIZE = 500
    private const val METADATA_MIN_SIZE = 600
    private const val MAX_FILE_SIZE = 4 * 600 * 600 // 4MB maximum
    private const val CONNECTION_TIMEOUT = 5000
    private const val READ_TIMEOUT = 5000

    /** Trouve des logos radio avec stratégie progressive (zéro résultat vide). */
    suspend fun findLogos(radioName: String, country: String?, streamUrl: String?): List<String> =
            withContext(Dispatchers.IO) {
                // Stratégie progressive en 4 niveaux (s'arrête dès qu'on a ≥1 résultat valide)
                val queries = buildProgressiveQueries(radioName, country)

                for (query in queries) {
                    val googleLogos = searchGoogleLogos(query, null)
                    val filteredLogos = googleLogos.distinct()

                    val validatedLogos = mutableListOf<String>()
                    for (url in filteredLogos) {
                        if (validatedLogos.size >= 5) break
                        // Accepter tous les formats (SVG, PNG, WEBP, JPG, ICO)
                        // Filtrage après download : taille ≥ 256px (Android TV minimum)
                        if (isValidImage(url, 256, 256)) {
                            validatedLogos.add(url)
                        }
                    }

                    // Dès qu'on a au moins 1 logo valide, on s'arrête
                    if (validatedLogos.isNotEmpty()) {
                        return@withContext validatedLogos
                    }
                }

                // Si aucun résultat après tous les niveaux, retourner liste vide
                return@withContext emptyList()
            }

    /** Construit les requêtes progressives basées sur les recommandations (précis -> large). */
    private fun buildProgressiveQueries(radioName: String, country: String?): List<String> {
        val queries = mutableListOf<String>()
        val countryPart = if (!country.isNullOrBlank()) "\"$country\"" else ""

        // Niveau 1 – Recommandation ChatGPT : Recherche précise de "logo radio" avec formats
        queries.add(
                "\"$radioName\" $countryPart \"logo radio\" (filetype:png OR filetype:svg OR filetype:jpg)"
        )

        // Niveau 2 – Recommandation ChatGPT : Recherche de "logo radio" (tous formats)
        queries.add("\"$radioName\" $countryPart logo radio")

        // Niveau 3 – Branding et icône (très efficace pour les radios internationales)
        queries.add("\"$radioName\" $countryPart (branding OR icon OR \"station logo\")")

        // Niveau 4 – Full open (si vraiment rien trouvé)
        queries.add("\"$radioName\" logo")

        return queries
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

    /**
     * Valide qu'une image respecte les dimensions minimales (accepte SVG sans validation de
     * taille).
     */
    private suspend fun isValidImage(imageUrl: String, minWidth: Int, minHeight: Int): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    // SVG : accepter directement (vectoriel, pas de contrainte de taille)
                    if (imageUrl.endsWith(".svg", ignoreCase = true) ||
                                    imageUrl.contains("/svg", ignoreCase = true)
                    ) {
                        return@withContext true
                    }

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

                    // Accepter SVG via content-type
                    if (contentType.contains("svg")) {
                        connection.disconnect()
                        return@withContext true
                    }

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

                // Regex pour capturer les URLs HD dans le JSON de Google (tous formats)
                val jsonImageUrlPattern =
                        "[\"'](https?://[^\"'<>\\s,]+?\\.(?:png|jpg|jpeg|webp|svg|ico|avif|gif)[^\"'<>\\s,]*?)[\"']\\s*,\\s*\\d+\\s*,\\s*\\d+".toRegex(
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
                    // Fallback direct links (tous formats)
                    val directImagePattern =
                            "[\"'](https?://[^\"'<>\\s,]+?\\.(?:png|jpg|jpeg|webp|svg|ico|avif|gif)[^\"'<>\\s,]*?)[\"']".toRegex(
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
