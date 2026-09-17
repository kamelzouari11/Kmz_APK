package com.example.simpleradio.data.api

import com.example.simpleradio.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** Returns Google Images results supplied by Serper, ordered by decreasing pixel area. */
object SerperImageProvider {
    data class ImageCandidate(
            val imageUrl: String,
            val thumbnailUrl: String?,
            val width: Int,
            val height: Int
    )

    private const val ENDPOINT = "https://google.serper.dev/images"
    private const val CONNECTION_TIMEOUT_MS = 2_000
    private const val READ_TIMEOUT_MS = 4_000
    private const val TOTAL_TIMEOUT_MS = 6_000L
    private const val MAX_RESPONSE_CHARS = 1_000_000
    private const val MAX_CANDIDATES = 10
    private const val MIN_WIDTH = 256
    private const val MIN_HEIGHT = 256

    private val resultCache = ConcurrentHashMap<String, List<ImageCandidate>>()

    fun isConfigured(): Boolean = BuildConfig.SERPER_API_KEY.isNotBlank()

    suspend fun findLogos(
            radioName: String,
            country: String?
    ): List<ImageCandidate> {
        val apiKey = BuildConfig.SERPER_API_KEY.trim()
        if (apiKey.isBlank() || radioName.isBlank()) return emptyList()

        val cacheKey = "${radioName.trim().lowercase()}|${country.orEmpty().trim().lowercase()}"
        resultCache[cacheKey]?.let { cached ->
            return cached
        }

        val query = buildString {
            append(radioName.trim())
            append(" logo svg png")
            country?.takeIf { it.isNotBlank() }?.let { append(' ').append(it.trim()) }
        }
        val urls = requestImageUrls(apiKey, query) ?: return emptyList()
        resultCache[cacheKey] = urls
        return urls
    }

    private suspend fun requestImageUrls(apiKey: String, query: String): List<ImageCandidate>? =
            withTimeoutOrNull(TOTAL_TIMEOUT_MS) {
                withContext(Dispatchers.IO) {
                    val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
                    try {
                        connection.requestMethod = "POST"
                        connection.doOutput = true
                        connection.instanceFollowRedirects = true
                        connection.connectTimeout = CONNECTION_TIMEOUT_MS
                        connection.readTimeout = READ_TIMEOUT_MS
                        connection.setRequestProperty("X-API-KEY", apiKey)
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.setRequestProperty("Accept", "application/json")
                        val body = JSONObject().put("q", query).toString()
                        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                        if (connection.responseCode !in 200..299) return@withContext null

                        val json =
                                connection.inputStream.bufferedReader().use { reader ->
                                    val buffer = CharArray(8_192)
                                    val result = StringBuilder()
                                    while (result.length < MAX_RESPONSE_CHARS) {
                                        val read = reader.read(buffer, 0, minOf(buffer.size, MAX_RESPONSE_CHARS - result.length))
                                        if (read < 0) break
                                        result.append(buffer, 0, read)
                                    }
                                    result.toString()
                                }
                        parseImageUrls(json)
                    } catch (_: Exception) {
                        null
                    } finally {
                        connection.disconnect()
                    }
                }
            }

    private fun parseImageUrls(json: String): List<ImageCandidate> {
        val images = JSONObject(json).optJSONArray("images") ?: return emptyList()
        return (0 until images.length())
                .mapNotNull { index ->
                    val image = images.optJSONObject(index) ?: return@mapNotNull null
                    val width = image.optInt("imageWidth")
                    val height = image.optInt("imageHeight")
                    if (width < MIN_WIDTH || height < MIN_HEIGHT) return@mapNotNull null

                    val imageUrl = image.optString("imageUrl").takeIf { it.isNotBlank() }
                    val thumbnailUrl =
                            image.optString("thumbnailUrl").takeIf { it.isNotBlank() }
                    val initialUrl = imageUrl ?: thumbnailUrl ?: return@mapNotNull null
                    ImageCandidate(
                            imageUrl = initialUrl,
                            thumbnailUrl = thumbnailUrl?.takeUnless { it == initialUrl },
                            width = width,
                            height = height
                    )
                }
                .sortedByDescending { it.width.toLong() * it.height.toLong() }
                .take(MAX_CANDIDATES)
    }

}
