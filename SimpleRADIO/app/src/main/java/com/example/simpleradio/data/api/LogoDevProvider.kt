package com.example.simpleradio.data.api

import com.example.simpleradio.BuildConfig
import android.graphics.BitmapFactory
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext

/** Resolves one station logo through Logo.dev without exposing a secret API key. */
object LogoDevProvider {
    private const val CONNECTION_TIMEOUT_MS = 1_500
    private const val READ_TIMEOUT_MS = 1_500
    private const val TOTAL_SEARCH_TIMEOUT_MS = 3_000L
    private const val MIN_NATIVE_WIDTH = 512
    private const val MIN_NATIVE_HEIGHT = 512
    private val logoCache = ConcurrentHashMap<String, String>()
    private val failedSearches = ConcurrentHashMap.newKeySet<String>()
    private val nativeQualityCache = ConcurrentHashMap<String, ImageInfo>()
    private val rejectedPathMarkers =
            listOf("banner", "/ads/", "advert", "avatar", "favicon-16", "favicon16")

    data class ImageInfo(
            val url: String,
            val width: Int,
            val height: Int,
            val isSvg: Boolean = false
    )

    suspend fun findLogo(
            radioName: String,
            country: String?,
            streamUrl: String,
            excludedUrls: Set<String> = emptySet()
    ): String? {
        val token = BuildConfig.LOGO_DEV_PUBLISHABLE_KEY.trim()
        if (token.isBlank() || radioName.isBlank()) return null

        val cacheKey = "${normalize(radioName)}|${normalize(country.orEmpty())}"
        logoCache[cacheKey]?.takeUnless { it in excludedUrls }?.let { return it }
        if (cacheKey in failedSearches && excludedUrls.isEmpty()) return null

        val candidates =
                buildCandidateUrls(radioName, country, streamUrl, token)
                        .filterNot { it in excludedUrls }
        val logoUrl =
                withTimeoutOrNull(TOTAL_SEARCH_TIMEOUT_MS) {
                    candidates.firstNotNullOfOrNull { inspectLogo(it) }?.url
                }

        if (logoUrl != null) {
            logoCache[cacheKey] = logoUrl
            failedSearches.remove(cacheKey)
        } else if (excludedUrls.isEmpty()) {
            // Do not retry an unavailable service on every recomposition during this session.
            failedSearches += cacheKey
        }
        return logoUrl
    }

    /** A native icon is kept when it is large enough not to pixelate in the player. */
    suspend fun isNativeLogoLargeEnough(url: String): Boolean {
        return inspectLogo(url) != null
    }

    /** Returns validated dimensions; SVG logos are accepted because they scale without pixels. */
    suspend fun inspectLogo(url: String): ImageInfo? {
        if (url.isBlank()) return null
        if (rejectedPathMarkers.any { url.contains(it, ignoreCase = true) }) return null
        nativeQualityCache[url]?.let { return it }

        val result =
                withTimeoutOrNull(TOTAL_SEARCH_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) { readImageInfo(url) }
                } ?: return null
        nativeQualityCache[url] = result
        return result
    }

    private fun buildCandidateUrls(
            radioName: String,
            country: String?,
            streamUrl: String,
            token: String
    ): List<String> {
        val identifiers = mutableListOf<String>()
        val domain = domainFromUrl(streamUrl)
        if (domain != null && domainMatchesRadioName(domain, radioName)) {
            identifiers += domain
        }

        val searchName = radioName.removePrefix("Radio ").removePrefix("radio ").trim()
        if (!country.isNullOrBlank()) {
            identifiers += "name/${encodePath("radio $searchName $country")}"
        }
        identifiers += "name/${encodePath("radio $searchName")}"

        val encodedToken = URLEncoder.encode(token, Charsets.UTF_8.name())
        return identifiers.distinct().map { identifier ->
            "https://img.logo.dev/$identifier" +
                    "?token=$encodedToken&size=400&retina=true&format=png&fallback=404"
        }
    }

    private fun readImageInfo(url: String): ImageInfo? {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = CONNECTION_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("Accept", "image/*")

        return try {
            if (connection.responseCode !in 200..299) return null
            val contentType = connection.contentType.orEmpty().lowercase(Locale.ROOT)
            if (!contentType.startsWith("image/")) return null
            if (contentType.contains("gif")) return null

            if (contentType.contains("svg") || connection.url.path.endsWith(".svg", true)) {
                // Vector logos remain sharp at every player size.
                connection.inputStream.use { if (it.read() < 0) return null }
                return ImageInfo(
                        url = url,
                        width = MIN_NATIVE_WIDTH,
                        height = MIN_NATIVE_HEIGHT,
                        isSvg = true
                )
            }

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            connection.inputStream.use { BitmapFactory.decodeStream(it, null, options) }
            if (options.outWidth >= MIN_NATIVE_WIDTH && options.outHeight >= MIN_NATIVE_HEIGHT) {
                ImageInfo(url = url, width = options.outWidth, height = options.outHeight)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    fun domainMatchesRadioName(domain: String, radioName: String): Boolean {
        val normalizedDomain = normalize(domain).replace(" ", "")
        return significantTokens(radioName).any { token ->
            normalizedDomain.contains(token.replace(" ", ""))
        }
    }

    private fun significantTokens(value: String): List<String> =
            normalize(value)
                    .split(' ')
                    .filter { it.length >= 3 && it !in setOf("radio", "fm", "am", "the") }

    fun domainFromUrl(streamUrl: String): String? {
        val host =
                try {
                    URL(streamUrl).host.lowercase(Locale.ROOT).removePrefix("www.")
                } catch (_: Exception) {
                    return null
                }
        val parts = host.split('.').filter { it.isNotBlank() }
        if (parts.size <= 2) return parts.joinToString(".").takeIf { it.isNotBlank() }

        val lastThree = parts.takeLast(3)
        return if (lastThree[1] in setOf("co", "com", "org", "net") &&
                        lastThree[2].length == 2
        ) {
            lastThree.joinToString(".")
        } else {
            parts.takeLast(2).joinToString(".")
        }
    }

    private fun encodePath(value: String): String =
            URLEncoder.encode(value.trim(), Charsets.UTF_8.name()).replace("+", "%20")

    private fun normalize(value: String): String =
            Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
                    .replace("\\p{Mn}+".toRegex(), "")
                    .replace("[^a-z0-9]+".toRegex(), " ")
                    .trim()
}
