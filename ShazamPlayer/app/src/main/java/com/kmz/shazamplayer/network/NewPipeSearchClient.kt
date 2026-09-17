package com.kmz.shazamplayer.network

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

data class NewPipeSearchResult(
        val mediaId: String,
        val videoId: String,
        val url: String,
        val title: String?,
        val channel: String?,
        val artworkUrl: String?
)

class NewPipeSearchException(message: String) : Exception(message)

/** Uses NewPipe's exported Android Auto interface for search and background playback. */
class NewPipeSearchClient(context: Context) {
    private val appContext = context.applicationContext
    private val serviceComponent =
            ComponentName(
                    NewPipeManager.PACKAGE_NAME,
                    "org.schabi.newpipe.player.PlayerService"
            )

    suspend fun searchFirst(artist: String, title: String): NewPipeSearchResult? =
            search(artist, title, startPlayback = false)

    suspend fun searchAndPlayFirst(artist: String, title: String): NewPipeSearchResult? =
            search(artist, title, startPlayback = true)

    suspend fun playVideoId(videoId: String) {
        if (!VIDEO_ID.matches(videoId)) {
            throw NewPipeSearchException("Identifiant YouTube incorrect.")
        }
        withBrowser { browser ->
            val mediaId =
                    Uri.Builder()
                            .authority(NewPipeManager.PACKAGE_NAME)
                            .appendPath("item")
                            .appendPath("stream")
                            .appendPath(YOUTUBE_SERVICE_ID.toString())
                            .appendQueryParameter(
                                    MEDIA_ID_URL_PARAMETER,
                                    "https://www.youtube.com/watch?v=$videoId"
                            )
                            .build()
                            .toString()
            playMediaId(browser, mediaId)
        }
    }

    suspend fun setPlaying(playing: Boolean) {
        withBrowser { browser ->
            val controls = MediaControllerCompat(appContext, browser.sessionToken).transportControls
            if (playing) controls.play() else controls.pause()
        }
    }

    suspend fun seekTo(positionMs: Long) {
        withBrowser { browser ->
            MediaControllerCompat(appContext, browser.sessionToken)
                    .transportControls
                    .seekTo(positionMs.coerceAtLeast(0L))
        }
    }

    private suspend fun search(
            artist: String,
            title: String,
            startPlayback: Boolean
    ): NewPipeSearchResult? =
            withBrowser { browser, complete, fail ->
                browser.search(
                        "$artist $title",
                        null,
                        object : MediaBrowserCompat.SearchCallback() {
                            override fun onSearchResult(
                                    query: String,
                                    extras: Bundle?,
                                    items: MutableList<MediaBrowserCompat.MediaItem>
                            ) {
                                val result =
                                        items.asSequence()
                                                .filter { it.isPlayable }
                                                .mapNotNull(::toSearchResult)
                                                .firstOrNull()
                                if (result != null && startPlayback) {
                                    runCatching { playMediaId(browser, result.mediaId) }
                                            .onFailure {
                                                fail("NewPipe n'a pas pu démarrer la lecture.")
                                                return
                                            }
                                }
                                complete(result)
                            }

                            override fun onError(query: String, extras: Bundle?) {
                                fail("La recherche NewPipe a échoué.")
                            }
                        }
                )
            }

    private fun playMediaId(browser: MediaBrowserCompat, mediaId: String) {
        MediaControllerCompat(appContext, browser.sessionToken)
                .transportControls
                .playFromMediaId(mediaId, null)
    }

    private suspend fun <T> withBrowser(onConnected: (MediaBrowserCompat) -> T): T =
            withBrowser { browser, complete, fail ->
                runCatching { onConnected(browser) }
                        .onSuccess(complete)
                        .onFailure { fail("La commande NewPipe a échoué.") }
            }

    private suspend fun <T> withBrowser(
            onConnected: (
                    browser: MediaBrowserCompat,
                    complete: (T) -> Unit,
                    fail: (String) -> Unit
            ) -> Unit
    ): T =
            try {
                withTimeout(SEARCH_TIMEOUT_MS) {
                    withContext(Dispatchers.Main.immediate) {
                        suspendCancellableCoroutine { continuation ->
                            lateinit var browser: MediaBrowserCompat
                            val finished = AtomicBoolean(false)

                            fun disconnect() {
                                runCatching { browser.disconnect() }
                            }

                            fun complete(result: T) {
                                if (finished.compareAndSet(false, true)) {
                                    disconnect()
                                    if (continuation.isActive) continuation.resume(result)
                                }
                            }

                            fun fail(message: String) {
                                if (finished.compareAndSet(false, true)) {
                                    disconnect()
                                    if (continuation.isActive) {
                                        continuation.resumeWithException(
                                                NewPipeSearchException(message)
                                        )
                                    }
                                }
                            }

                            val connectionCallback =
                                    object : MediaBrowserCompat.ConnectionCallback() {
                                        override fun onConnected() {
                                            runCatching {
                                                        onConnected(browser, ::complete, ::fail)
                                                    }
                                                    .onFailure {
                                                        fail("La commande NewPipe a échoué.")
                                                    }
                                        }

                                        override fun onConnectionSuspended() {
                                            fail("La liaison avec NewPipe a été interrompue.")
                                        }

                                        override fun onConnectionFailed() {
                                            fail(
                                                    "NewPipe refuse la liaison. Autorisez l'accès aux notifications pour ShazamPlayer."
                                            )
                                        }
                                    }

                            browser =
                                    MediaBrowserCompat(
                                            appContext,
                                            serviceComponent,
                                            connectionCallback,
                                            null
                                    )
                            continuation.invokeOnCancellation {
                                if (finished.compareAndSet(false, true)) disconnect()
                            }
                            browser.connect()
                        }
                    }
                }
            } catch (_: TimeoutCancellationException) {
                throw NewPipeSearchException("La commande NewPipe a dépassé 30 secondes.")
            }

    private fun toSearchResult(item: MediaBrowserCompat.MediaItem): NewPipeSearchResult? {
        val mediaId = item.description.mediaId ?: return null
        val url = Uri.parse(mediaId).getQueryParameter(MEDIA_ID_URL_PARAMETER) ?: return null
        val videoId = extractYouTubeVideoId(url) ?: return null
        return NewPipeSearchResult(
                mediaId = mediaId,
                videoId = videoId,
                url = url,
                title = item.description.title?.toString(),
                channel = item.description.subtitle?.toString(),
                artworkUrl =
                        item.description.iconUri?.toString()
                                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        )
    }

    private fun extractYouTubeVideoId(url: String): String? {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase().orEmpty()
        val candidate =
                when {
                    host == "youtu.be" -> uri.lastPathSegment
                    host == "youtube.com" || host.endsWith(".youtube.com") ->
                            uri.getQueryParameter("v")
                                    ?: uri.pathSegments
                                            .takeIf {
                                                it.firstOrNull() in listOf("shorts", "live")
                                            }
                                            ?.getOrNull(1)
                    else -> null
                }
        return candidate?.takeIf { VIDEO_ID.matches(it) }
    }

    private companion object {
        const val SEARCH_TIMEOUT_MS = 30_000L
        const val MEDIA_ID_URL_PARAMETER = "url"
        const val YOUTUBE_SERVICE_ID = 0
        val VIDEO_ID = "[A-Za-z0-9_-]{11}".toRegex()
    }
}
