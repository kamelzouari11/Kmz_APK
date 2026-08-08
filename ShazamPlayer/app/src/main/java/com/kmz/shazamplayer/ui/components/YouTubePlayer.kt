package com.kmz.shazamplayer.ui.components

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

enum class YouTubePlayerAction {
    PLAY,
    PAUSE,
    SEEK
}

data class YouTubePlayerCommand(
        val id: Long,
        val action: YouTubePlayerAction,
        val positionMs: Long = 0L
)

private class YouTubePlayerController {
    var webView: WebView? = null
    var ready = false
    var pendingCommand: YouTubePlayerCommand? = null

    fun execute(command: YouTubePlayerCommand?) {
        if (command == null) return
        if (!ready) {
            pendingCommand = command
            return
        }
        val script =
                when (command.action) {
                    YouTubePlayerAction.PLAY -> "window.shazamPlay();"
                    YouTubePlayerAction.PAUSE -> "window.shazamPause();"
                    YouTubePlayerAction.SEEK ->
                            "window.shazamSeek(${command.positionMs / 1000.0});"
                }
        webView?.evaluateJavascript(script, null)
    }

    fun markReady() {
        ready = true
        pendingCommand?.let(::execute)
        pendingCommand = null
    }

    fun reset() {
        ready = false
        pendingCommand = null
    }
}

private class YouTubeJavascriptBridge(
        private val onReady: (Long) -> Unit,
        private val onStateChanged: (Int) -> Unit,
        private val onProgress: (Long, Long) -> Unit,
        private val onError: (Int) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun ready(durationSeconds: Double) {
        mainHandler.post { onReady((durationSeconds * 1000).toLong()) }
    }

    @JavascriptInterface
    fun stateChanged(state: Int) {
        mainHandler.post { onStateChanged(state) }
    }

    @JavascriptInterface
    fun progress(positionSeconds: Double, durationSeconds: Double) {
        mainHandler.post {
            onProgress(
                    (positionSeconds * 1000).toLong(),
                    (durationSeconds * 1000).toLong()
            )
        }
    }

    @JavascriptInterface
    fun error(code: Int) {
        mainHandler.post { onError(code) }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePlayer(
        videoId: String,
        command: YouTubePlayerCommand?,
        modifier: Modifier = Modifier,
        onReady: (Long) -> Unit,
        onStateChanged: (Int) -> Unit,
        onProgress: (Long, Long) -> Unit,
        onError: (Int) -> Unit
) {
    val controller = remember { YouTubePlayerController() }

    LaunchedEffect(command?.id) { controller.execute(command) }

    AndroidView(
            modifier = modifier,
            factory = { context ->
                WebView(context).apply {
                    controller.webView = this
                    setBackgroundColor(android.graphics.Color.BLACK)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    webChromeClient = WebChromeClient()
                    webViewClient = WebViewClient()
                    addJavascriptInterface(
                            YouTubeJavascriptBridge(
                                    onReady = { duration ->
                                        controller.markReady()
                                        onReady(duration)
                                    },
                                    onStateChanged = onStateChanged,
                                    onProgress = onProgress,
                                    onError = onError
                            ),
                            "ShazamPlayer"
                    )
                    loadPlayer(videoId)
                }
            },
            update = { webView ->
                if (webView.tag != videoId) {
                    controller.reset()
                    webView.loadPlayer(videoId)
                }
            }
    )

    DisposableEffect(Unit) {
        onDispose {
            controller.webView?.evaluateJavascript("window.shazamPause();", null)
            controller.webView?.removeJavascriptInterface("ShazamPlayer")
            controller.webView?.stopLoading()
            controller.webView?.destroy()
            controller.webView = null
            controller.reset()
        }
    }
}

private fun WebView.loadPlayer(videoId: String) {
    tag = videoId
    val safeVideoId = videoId.takeIf { VIDEO_ID.matches(it) } ?: return
    val origin = "https://com.kmz.shazamplayer"
    val html =
            """
            <!doctype html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <style>
                html, body, #player { width: 100%; height: 100%; margin: 0; background: #000; overflow: hidden; }
              </style>
            </head>
            <body>
              <div id="player"></div>
              <script src="https://www.youtube.com/iframe_api"></script>
              <script>
                var player = null;
                var progressTimer = null;
                function onYouTubeIframeAPIReady() {
                  player = new YT.Player('player', {
                    width: '100%',
                    height: '100%',
                    videoId: '$safeVideoId',
                    playerVars: {
                      autoplay: 1,
                      controls: 1,
                      playsinline: 1,
                      rel: 0,
                      origin: '$origin'
                    },
                    events: {
                      onReady: function(event) {
                        ShazamPlayer.ready(event.target.getDuration() || 0);
                        event.target.playVideo();
                        progressTimer = setInterval(function() {
                          if (player && player.getCurrentTime) {
                            ShazamPlayer.progress(player.getCurrentTime() || 0, player.getDuration() || 0);
                          }
                        }, 1000);
                      },
                      onStateChange: function(event) { ShazamPlayer.stateChanged(event.data); },
                      onError: function(event) { ShazamPlayer.error(event.data); }
                    }
                  });
                }
                window.shazamPlay = function() { if (player) player.playVideo(); };
                window.shazamPause = function() { if (player) player.pauseVideo(); };
                window.shazamSeek = function(seconds) {
                  if (player) { player.seekTo(seconds, true); player.playVideo(); }
                };
                window.addEventListener('beforeunload', function() {
                  if (progressTimer) clearInterval(progressTimer);
                });
              </script>
            </body>
            </html>
            """.trimIndent()

    // A HTTPS base URL supplies the Referer required by the official embedded player.
    loadDataWithBaseURL("$origin/", html, "text/html", "UTF-8", null)
}

private val VIDEO_ID = "[A-Za-z0-9_-]{11}".toRegex()
