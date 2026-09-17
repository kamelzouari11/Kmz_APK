package com.kmz.shazamplayer.network

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class NewPipePlaylistException(message: String) : Exception(message)

/** Creates the anonymous YouTube playlist URL that NewPipe can import as a complete queue. */
class NewPipeManager {
    private val client =
            OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .build()

    suspend fun createTemporaryPlaylistUrl(videoIds: List<String>): String =
            withContext(Dispatchers.IO) {
                val ids = videoIds.take(MAX_PLAYLIST_SIZE)
                if (ids.isEmpty() || ids.any { !VIDEO_ID.matches(it) }) {
                    throw NewPipePlaylistException("La playlist contient un identifiant YouTube invalide.")
                }

                val request =
                        Request.Builder()
                                .url(
                                        "https://www.youtube.com/watch_videos?video_ids=" +
                                                ids.joinToString(",")
                                )
                                .header(
                                        "User-Agent",
                                        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Mobile Safari/537.36"
                                )
                                .get()
                                .build()

                client.newCall(request).execute().use { response ->
                    // Some regions append a consent redirect after the useful watch URL. Walk the
                    // complete chain and keep the actual generated queue URL. Temporary TL lists
                    // are resolved by NewPipe from this watch URL, but not from a canonical
                    // /playlist URL.
                    var redirectResponse: okhttp3.Response? = response
                    var playlistUrl: String? = null
                    while (redirectResponse != null && playlistUrl == null) {
                        val candidate = redirectResponse.request.url
                        val host = candidate.host.lowercase()
                        val playlistId = candidate.queryParameter("list")
                        val isYouTubeHost = host == "youtube.com" || host.endsWith(".youtube.com")
                        if (isYouTubeHost && !playlistId.isNullOrBlank()) {
                            playlistUrl = candidate.toString()
                        }
                        redirectResponse = redirectResponse.priorResponse
                    }

                    val resolvedPlaylistUrl = playlistUrl
                    if (resolvedPlaylistUrl != null) return@withContext resolvedPlaylistUrl
                    if (!response.isSuccessful) {
                        throw NewPipePlaylistException(
                                "YouTube n'a pas pu créer la playlist temporaire (${response.code})."
                        )
                    }
                    throw NewPipePlaylistException(
                            "YouTube n'a pas renvoyé de playlist temporaire exploitable."
                    )
                }
            }

    companion object {
        const val PACKAGE_NAME = "org.schabi.newpipe"
        const val ROUTER_ACTIVITY = "$PACKAGE_NAME.RouterActivity"
        const val MAX_PLAYLIST_SIZE = 100
        private val VIDEO_ID = "[A-Za-z0-9_-]{11}".toRegex()
    }
}
