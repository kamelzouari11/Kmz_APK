package com.kamel.iptvscrapper.data.tester

import com.kamel.iptvscrapper.data.local.entities.LinkEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class IptvTester {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3")
                .build()
            chain.proceed(request)
        }
        .build()

    suspend fun testLink(link: LinkEntity): LinkEntity = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var errorMsg: String? = null
        try {
            val cleanUrl = link.url.trim()
            val isWorking = when (link.type) {
                "XTREAM" -> testXtream(link.copy(url = cleanUrl))
                "STALKER" -> testStalker(link.copy(url = cleanUrl))
                "M3U" -> testM3u(link.copy(url = cleanUrl))
                else -> false
            }
            val endTime = System.currentTimeMillis()
            link.copy(
                status = if (isWorking) "WORKING" else "DEAD",
                lastTested = System.currentTimeMillis(),
                latency = endTime - startTime,
                error = if (isWorking) null else "Server response invalid"
            )
        } catch (e: Exception) {
            val endTime = System.currentTimeMillis()
            link.copy(
                status = "DEAD",
                lastTested = System.currentTimeMillis(),
                latency = endTime - startTime,
                error = e.message ?: "Connection failed"
            )
        }
    }

    private fun testXtream(link: LinkEntity): Boolean {
        val testUrl = "${link.url.ensureTrailingSlash()}player_api.php?username=${link.username}&password=${link.password}"
        val request = Request.Builder().url(testUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return false
            val body = response.body?.string() ?: ""
            return body.contains("user_info")
        }
    }

    private fun testStalker(link: LinkEntity): Boolean {
        val cleanUrl = link.url.trim().removeSuffix("/")
        
        // Potential bases to try
        val bases = mutableListOf(cleanUrl)
        if (cleanUrl.endsWith("/c")) {
            bases.add(cleanUrl.substringBeforeLast("/c"))
        }
        
        // Potential relative paths
        val relativePaths = listOf(
            "server/load.php",
            "stalker_portal/server/load.php",
            "portal/server/load.php"
        )
        
        for (base in bases) {
            val normalizedBase = if (base.endsWith("/")) base else "$base/"
            for (path in relativePaths) {
                val testUrl = "$normalizedBase$path?type=stb&action=handshake&JsHttpRequest=1-xml"
                try {
                    val request = Request.Builder()
                        .url(testUrl)
                        .header("Cookie", "mac=${link.mac}; stb_lang=en; timezone=Europe/Paris;")
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: ""
                            if (body.contains("token") || body.contains("js")) return true
                        }
                    }
                } catch (e: Exception) {
                    // Try next combination
                }
            }
        }
        return false
    }

    private fun testM3u(link: LinkEntity): Boolean {
        val request = Request.Builder().url(link.url).head().build()
        client.newCall(request).execute().use { response ->
            return response.isSuccessful
        }
    }

    private fun String.ensureTrailingSlash(): String {
        return if (this.endsWith("/")) this else "$this/"
    }
}
