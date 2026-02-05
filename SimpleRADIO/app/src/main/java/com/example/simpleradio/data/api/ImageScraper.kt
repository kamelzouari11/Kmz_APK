package com.example.simpleradio.data.api

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ImageScraper {

    /** Trouve des logos HD via Google Images Large. */
    suspend fun findLogos(radioName: String, country: String?, streamUrl: String?): List<String> =
            withContext(Dispatchers.IO) {
                // Recherche exclusive sur Google Images en taille LARGE
                val query = "$radioName logo ${country ?: ""}"
                val googleLogos = searchGoogleLogos(query)
                // On retire les SVG car souvent problématiques à l'affichage direct dans certaines
                // vues
                return@withContext googleLogos
                        .filter { !it.endsWith(".svg", ignoreCase = true) }
                        .distinct()
                        .take(5)
            }

    suspend fun findBestLogo(radioName: String, country: String?, streamUrl: String?): String? {
        return findLogos(radioName, country, streamUrl).firstOrNull()
    }

    suspend fun searchGoogleLogos(query: String): List<String> =
            withContext(Dispatchers.IO) {
                val results = mutableListOf<String>()
                results.addAll(fetchGoogleImages(query, "l"))
                return@withContext results.distinct().take(5)
            }

    private fun fetchGoogleImages(query: String, size: String?): List<String> {
        val images = mutableListOf<String>()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val sizeParam = if (size != null) "&tbs=isz:$size" else ""
            val urlStr =
                    "https://www.google.com/search?q=$encodedQuery&tbm=isch$sizeParam&safe=active"

            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

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
                        if (images.size >= 5)
                                break // On en prend un peu plus pour filtrer les doublons après
                    }
                }

                if (images.size < 3) {
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
                            if (images.size >= 5) break
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
