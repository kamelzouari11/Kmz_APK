package com.example.simpleiptv.data.services

import android.util.Log
import com.example.simpleiptv.data.api.StalkerClient
import com.example.simpleiptv.data.local.IptvDao
import com.example.simpleiptv.data.local.entities.ChannelEntity
import com.example.simpleiptv.data.local.entities.ProfileEntity
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.coroutines.resume

class StreamService(private val dao: IptvDao) {

    private data class ChannelTestResult(val isWorking: Boolean, val checkedAt: Long)

    private val channelTestCache = ConcurrentHashMap<String, ChannelTestResult>()
    private val channelTestClient =
            OkHttpClient.Builder()
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(8, TimeUnit.SECONDS)
                    .callTimeout(12, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build()

    /**
     * Vérifie qu'un flux répond réellement et fournit des données. Les résultats
     * positifs restent valides 30 minutes, les échecs 5 minutes.
     */
    suspend fun isChannelWorking(profile: ProfileEntity, channel: ChannelEntity): Boolean {
        val cacheKey = listOf(
                profile.id,
                profile.url,
                profile.username,
                profile.password,
                profile.macAddress,
                channel.type,
                channel.stream_id,
                channel.extraParams
        ).joinToString("|")
        val now = System.currentTimeMillis()
        channelTestCache[cacheKey]?.let { cached ->
            val ttl = if (cached.isWorking) 30 * 60_000L else 5 * 60_000L
            if (now - cached.checkedAt < ttl) return cached.isWorking
        }

        val isWorking = try {
            val streamUrl = getStreamUrl(profile, channel)
            streamUrl.isNotBlank() && probeStream(streamUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("StreamService", "Channel '${channel.name}' failed its stream test", e)
            false
        }

        channelTestCache[cacheKey] = ChannelTestResult(isWorking, now)
        return isWorking
    }

    fun invalidateChannelTests(profileId: Int) {
        val prefix = "$profileId|"
        channelTestCache.keys.filter { it.startsWith(prefix) }.forEach { channelTestCache.remove(it) }
    }

    private suspend fun probeStream(streamUrl: String): Boolean = suspendCancellableCoroutine { continuation ->
        val request =
                Request.Builder()
                        .url(streamUrl)
                        .header(
                                "User-Agent",
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                        )
                        .header("Range", "bytes=0-0")
                        .get()
                        .build()

        val call = channelTestClient.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resume(false)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val isWorking = response.use {
                            if (!it.isSuccessful) return@use false
                            val body = it.body ?: return@use false
                            val contentType = body.contentType()
                            if (contentType?.type == "text" && contentType.subtype == "html") {
                                return@use false
                            }
                            try {
                                body.byteStream().read() != -1
                            } catch (e: IOException) {
                                false
                            }
                        }
                        if (continuation.isActive) continuation.resume(isWorking)
                    }
                }
        )
    }

    suspend fun getStreamUrl(profile: ProfileEntity, channel: ChannelEntity): String {
        // Fetch the channel to know its type (LIVE or VOD)
        val channelId = channel.stream_id

        if (profile.type == "stalker") {
            val mac = profile.macAddress ?: return ""
            val api = StalkerClient.create(profile.url, mac)
            val handshake = api.handshake(mac)
            val token = "Bearer " + handshake.js.token

            val rawCmd = channel?.extraParams

            val cmdToSend =
                    if (!rawCmd.isNullOrEmpty() && rawCmd.contains("stream=")) {
                        channelId
                    } else {
                        if (rawCmd?.startsWith("ffmpeg ") == true) {
                            rawCmd.substringAfter("ffmpeg ").trim()
                        } else {
                            rawCmd ?: channelId
                        }
                    }

            val linkResponse = api.createLink(token, cmdToSend)
            var url = linkResponse.js.cmd

            if (url.startsWith("ffmpeg ")) {
                url = url.substringAfter("ffmpeg ").trim()
            }

            if (url.contains("stream=&")) {
                Log.w(
                        "StreamService",
                        "Server returned empty stream ID. Patching URL with ID: $channelId"
                )
                url = url.replace("stream=&", "stream=$channelId&")
            } else if (url.endsWith("stream=")) {
                Log.w(
                        "StreamService",
                        "Server returned empty stream ID. Patching URL with ID: $channelId"
                )
                url += channelId
            }
            return url
        } else {
            val baseUrl = if (profile.url.endsWith("/")) profile.url else "${profile.url}/"
            return if (channel?.type == "VOD") {
                val ext = channel.extraParams ?: "mp4"
                "${baseUrl}movie/${profile.username}/${profile.password}/${channelId}.${ext}"
            } else {
                "${baseUrl}live/${profile.username}/${profile.password}/${channelId}.ts"
            }
        }
    }
}
