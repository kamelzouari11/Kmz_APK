package com.example.simpleradio.data.api

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** Extracts and validates logo-specific signals from a station's official website. */
object OfficialSiteLogoProvider {
    private const val CONNECTION_TIMEOUT_MS = 1_500
    private const val READ_TIMEOUT_MS = 1_500
    private const val TOTAL_TIMEOUT_MS = 5_000L
    private const val MAX_HTML_CHARS = 600_000
    private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                    "Chrome/120.0 Mobile Safari/537.36 SimpleRADIO/1.0"

    data class OfficialLogoResult(
            val url: String,
            val source: String,
            val width: Int,
            val height: Int
    )

    private data class Candidate(val url: String, val source: String)

    private val successfulCache = ConcurrentHashMap<String, OfficialLogoResult>()
    private val failedSites = ConcurrentHashMap.newKeySet<String>()
    private val tagRegex = Regex("<(?:meta|link)\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val attributeRegex =
            Regex("""([:\w-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+))""")
    private val jsonLogoRegex =
            Regex("""["']logo["']\s*:\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val jsonLogoObjectRegex =
            Regex(
                    """["']logo["']\s*:\s*\{.*?["']url["']\s*:\s*["']([^"']+)["']""",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )

    suspend fun findLogo(
            homepage: String?,
            rejectedUrls: Set<String>
    ): OfficialLogoResult? {
        val siteUrl = normalizeHomepage(homepage) ?: return null
        successfulCache[siteUrl]?.takeUnless { it.url in rejectedUrls }?.let { return it }
        if (siteUrl in failedSites && rejectedUrls.isEmpty()) return null

        val result =
                withTimeoutOrNull(TOTAL_TIMEOUT_MS) {
                    val candidates =
                            withContext(Dispatchers.IO) { extractCandidates(siteUrl) }
                                    .filterNot { it.url in rejectedUrls }
                                    .take(12)

                    supervisorScope {
                        candidates
                                .map { candidate ->
                                    async {
                                        val info = LogoDevProvider.inspectLogo(candidate.url)
                                        if (info != null && isPlausibleCandidate(candidate, info)) {
                                            OfficialLogoResult(
                                                    url = candidate.url,
                                                    source = candidate.source,
                                                    width = info.width,
                                                    height = info.height
                                            )
                                        } else {
                                            null
                                        }
                                    }
                                }
                                .awaitAll()
                                .firstOrNull { it != null }
                    }
                }

        if (result != null) {
            successfulCache[siteUrl] = result
            failedSites.remove(siteUrl)
        } else if (rejectedUrls.isEmpty()) {
            failedSites += siteUrl
        }
        return result
    }

    private fun isPlausibleCandidate(
            candidate: Candidate,
            info: LogoDevProvider.ImageInfo
    ): Boolean {
        if (candidate.source != "og_image") return true
        val looksNamedAsLogo =
                listOf("logo", "icon", "brand", "radio").any {
                    candidate.url.contains(it, ignoreCase = true)
                }
        val ratio = info.width.toDouble() / info.height.coerceAtLeast(1)
        return looksNamedAsLogo || ratio in 0.8..1.25
    }

    private fun extractCandidates(homepage: String): List<Candidate> {
        val connection = openHtmlConnection(homepage)
        return try {
            if (connection.responseCode !in 200..299) return emptyList()
            val baseUrl = connection.url.toString()
            val html = connection.inputStream.bufferedReader().use(::readLimited)
            val tags = tagRegex.findAll(html).map { parseAttributes(it.value) }.toList()

            val schemaLogos =
                    (jsonLogoObjectRegex.findAll(html) + jsonLogoRegex.findAll(html))
                            .mapNotNull { resolveUrl(baseUrl, it.groupValues[1]) }
                            .map { Candidate(it, "schema_logo") }
                            .toList()
            val openGraphLogos = metaImages(tags, baseUrl, "og:logo", "og_logo")
            val appleTouchIcons = iconCandidates(tags, baseUrl, "apple-touch-icon", "apple_touch")
            val manifestIcons = manifestCandidates(tags, baseUrl)
            val regularIcons = iconCandidates(tags, baseUrl, "icon", "html_icon", excludeApple = true)
            val favicon = listOfNotNull(resolveUrl(baseUrl, "/favicon.ico"))
                    .map { Candidate(it, "favicon") }
            // og:image is deliberately last: on news sites it is often a photo, not a logo.
            val openGraphImages = metaImages(tags, baseUrl, "og:image", "og_image")

            (schemaLogos +
                            openGraphLogos +
                            appleTouchIcons +
                            manifestIcons +
                            regularIcons +
                            favicon +
                            openGraphImages)
                    .distinctBy { it.url }
        } catch (_: Exception) {
            emptyList()
        } finally {
            connection.disconnect()
        }
    }

    private fun metaImages(
            tags: List<Map<String, String>>,
            baseUrl: String,
            propertyName: String,
            source: String
    ): List<Candidate> =
            tags.mapNotNull { attributes ->
                val property = attributes["property"] ?: attributes["name"]
                if (property.equals(propertyName, ignoreCase = true)) {
                    attributes["content"]?.let { resolveUrl(baseUrl, it) }
                } else {
                    null
                }
            }.map { Candidate(it, source) }

    private fun iconCandidates(
            tags: List<Map<String, String>>,
            baseUrl: String,
            relation: String,
            source: String,
            excludeApple: Boolean = false
    ): List<Candidate> =
            tags.mapNotNull { attributes ->
                val rel = attributes["rel"].orEmpty().lowercase(Locale.ROOT)
                val matches = rel.split(Regex("\\s+")).contains(relation)
                if (matches && (!excludeApple || !rel.contains("apple-touch-icon"))) {
                    attributes["href"]?.let { resolveUrl(baseUrl, it) }
                } else {
                    null
                }
            }.map { Candidate(it, source) }

    private fun manifestCandidates(
            tags: List<Map<String, String>>,
            baseUrl: String
    ): List<Candidate> {
        val manifestUrl =
                tags.firstNotNullOfOrNull { attributes ->
                    if (attributes["rel"].orEmpty().contains("manifest", ignoreCase = true)) {
                        attributes["href"]?.let { resolveUrl(baseUrl, it) }
                    } else {
                        null
                    }
                } ?: return emptyList()
        val connection = openHtmlConnection(manifestUrl)
        return try {
            if (connection.responseCode !in 200..299) return emptyList()
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val icons = json.optJSONArray("icons") ?: return emptyList()
            buildList {
                for (index in 0 until icons.length()) {
                    val src = icons.optJSONObject(index)?.optString("src").orEmpty()
                    resolveUrl(manifestUrl, src)?.let { add(Candidate(it, "manifest_icon")) }
                }
            }
        } catch (_: Exception) {
            emptyList()
        } finally {
            connection.disconnect()
        }
    }

    private fun openHtmlConnection(url: String): HttpURLConnection =
            (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = CONNECTION_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "text/html,application/json,application/xhtml+xml")
            }

    private fun parseAttributes(tag: String): Map<String, String> =
            attributeRegex.findAll(tag).associate { match ->
                val value = match.groupValues.drop(2).firstOrNull { it.isNotEmpty() }.orEmpty()
                match.groupValues[1].lowercase(Locale.ROOT) to decodeHtml(value)
            }

    private fun readLimited(reader: java.io.BufferedReader): String {
        val result = StringBuilder()
        val buffer = CharArray(8_192)
        while (result.length < MAX_HTML_CHARS) {
            val count = reader.read(buffer, 0, minOf(buffer.size, MAX_HTML_CHARS - result.length))
            if (count <= 0) break
            result.append(buffer, 0, count)
        }
        return result.toString()
    }

    private fun resolveUrl(baseUrl: String, value: String): String? =
            try {
                URI(baseUrl)
                        .resolve(decodeHtml(value.trim()))
                        .toString()
                        .takeIf { it.startsWith("https://") || it.startsWith("http://") }
            } catch (_: Exception) {
                null
            }

    private fun normalizeHomepage(value: String?): String? {
        val raw = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val withScheme = if (raw.contains("://")) raw else "https://$raw"
        return try {
            URL(withScheme).toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeHtml(value: String): String =
            value.replace("&amp;", "&")
                    .replace("&#39;", "'")
                    .replace("&quot;", "\"")
}
