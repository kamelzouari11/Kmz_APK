package com.example.simpleradio.data.api

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ImageScraper {

    /** Tente de trouver des logos HD via plusieurs sources en cascade. */
    suspend fun findLogos(radioName: String, country: String?, streamUrl: String?): List<String> =
            withContext(Dispatchers.IO) {
                val logos = mutableListOf<String>()

                // 1. Priorité 1 : Recherche par domaine (Clearbit)
                val domain = streamUrl?.let { extractDomain(it) }
                if (domain != null) {
                    val clearbitUrl = "https://logo.clearbit.com/$domain"
                    if (isUrlValid(clearbitUrl)) {
                        logos.add(clearbitUrl)
                    }
                }

                // 2. Priorité 2 : Recherche Google Images (Fallback)
                val query = "$radioName logo ${country ?: ""}"
                val googleLogos = searchGoogleLogos(query)
                logos.addAll(googleLogos)

                return@withContext logos.distinct().take(3)
            }

    suspend fun findBestLogo(radioName: String, country: String?, streamUrl: String?): String? {
        return findLogos(radioName, country, streamUrl).firstOrNull()
    }

    private fun extractDomain(url: String): String? {
        return try {
            val uri = URL(url)
            val host = uri.host.lowercase()
            // On enlève les sous-domaines techniques type 'streaming', 'edge', 'shoutcast'
            val parts = host.split(".")
            if (parts.size >= 2) {
                // On garde les deux derniers segments (ex: mosaiquefm.net)
                parts.takeLast(2).joinToString(".")
            } else host
        } catch (e: Exception) {
            null
        }
    }

    private fun isUrlValid(urlStr: String): Boolean {
        return try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.requestMethod = "HEAD"
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            val code = conn.responseCode
            code == 200
        } catch (e: Exception) {
            false
        }
    }

    suspend fun searchGoogleLogos(query: String): List<String> =
            withContext(Dispatchers.IO) {
                val results = mutableListOf<String>()
                results.addAll(fetchGoogleImages(query, "l"))
                if (results.size < 3) {
                    results.addAll(fetchGoogleImages(query, null))
                }
                return@withContext results.distinct().take(3)
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
