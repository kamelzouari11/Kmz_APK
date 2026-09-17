package com.kmz.shazamplayer.network

import android.content.Context
import com.kmz.shazamplayer.model.Track
import java.security.MessageDigest
import java.util.Locale
import org.json.JSONObject

/** Durable association between a stable Shazam track identity and the selected YouTube video. */
class YouTubeMappingStore(context: Context) {
    private val preferences =
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun restore(track: Track): Boolean {
        val raw = preferences.getString(storageKey(track), null) ?: return false
        val mapping = runCatching { JSONObject(raw) }.getOrNull() ?: return false
        val videoId = mapping.optString("videoId").takeIf { VIDEO_ID.matches(it) } ?: return false
        track.youtubeVideoId = videoId
        track.youtubeChannel = mapping.optString("channel").takeIf { it.isNotBlank() }
        if (track.officialCoverHD.isNullOrBlank() && track.artworkUrl.isNullOrBlank()) {
            track.artworkUrl =
                    mapping.optString("artworkUrl").takeIf { it.isNotBlank() }
                            ?: youtubeThumbnail(videoId)
        }
        return true
    }

    fun save(track: Track, videoId: String, channel: String?, artworkUrl: String? = null) {
        if (!VIDEO_ID.matches(videoId)) return
        val resolvedArtwork =
                track.officialCoverHD
                        ?: artworkUrl?.takeIf { it.isNotBlank() }
                        ?: track.artworkUrl?.takeIf { it.isNotBlank() }
                        ?: youtubeThumbnail(videoId)
        val mapping =
                JSONObject()
                        .put("identity", identity(track))
                        .put("artist", track.artist)
                        .put("title", track.title)
                        .put("videoId", videoId)
                        .put("channel", channel.orEmpty())
                        .put("artworkUrl", resolvedArtwork)
        preferences.edit().putString(storageKey(track), mapping.toString()).apply()
        track.youtubeVideoId = videoId
        track.youtubeChannel = channel
        if (track.officialCoverHD.isNullOrBlank()) track.artworkUrl = resolvedArtwork
    }

    private fun youtubeThumbnail(videoId: String): String =
            "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

    private fun storageKey(track: Track): String = "mapping:${sha256(identity(track))}"

    private fun identity(track: Track): String =
            track.trackKey.takeIf { it.isNotBlank() }?.let { "shazam:$it" }
                    ?: "metadata:${track.artist.trim().lowercase(Locale.ROOT)}|${track.title.trim().lowercase(Locale.ROOT)}"

    private fun sha256(value: String): String =
            MessageDigest.getInstance("SHA-256")
                    .digest(value.toByteArray(Charsets.UTF_8))
                    .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private companion object {
        const val PREFERENCES_NAME = "YouTubeTrackMappingsV1"
        val VIDEO_ID = "[A-Za-z0-9_-]{11}".toRegex()
    }
}
