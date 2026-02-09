package com.example.simpleiptv.data.services

import android.util.Log
import com.example.simpleiptv.data.api.StalkerClient
import com.example.simpleiptv.data.local.IptvDao
import com.example.simpleiptv.data.local.entities.ProfileEntity

class StreamService(private val dao: IptvDao) {

    suspend fun getStreamUrl(profile: ProfileEntity, channelId: String): String {
        return if (profile.type == "stalker") {
            val mac = profile.macAddress ?: return ""
            val api = StalkerClient.create(profile.url, mac)
            val handshake = api.handshake(mac)
            val token = "Bearer " + handshake.js.token

            val channel = dao.getChannelById(channelId, profile.id)
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
            url
        } else {
            val baseUrl = if (profile.url.endsWith("/")) profile.url else "${profile.url}/"
            "${baseUrl}live/${profile.username}/${profile.password}/${channelId}.ts"
        }
    }
}
