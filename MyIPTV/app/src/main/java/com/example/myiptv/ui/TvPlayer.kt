package com.example.myiptv.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.myiptv.ui.theme.MyIptvPalette

@Composable
fun rememberTvPlayer(): ExoPlayer {
    val context = LocalContext.current
    val player = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    return player
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun TvPlayer(
    streamUrl: String?,
    player: ExoPlayer,
    stretchToFill: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val resizeMode = if (stretchToFill) {
        AspectRatioFrameLayout.RESIZE_MODE_FILL
    } else {
        AspectRatioFrameLayout.RESIZE_MODE_FIT
    }
    LaunchedEffect(streamUrl) {
        if (streamUrl.isNullOrBlank()) {
            player.stop()
            player.clearMediaItems()
        } else {
            val currentUrl = player.currentMediaItem?.localConfiguration?.uri?.toString()
            if (currentUrl != streamUrl) {
                player.setMediaItem(MediaItem.fromUri(streamUrl))
                player.prepare()
            } else if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
            player.play()
        }
    }

    Box(
        modifier = modifier.background(MyIptvPalette.Anthracite),
        contentAlignment = Alignment.Center,
    ) {
        if (streamUrl.isNullOrBlank()) {
            Text(
                text = "Sélectionnez une chaîne",
                color = MyIptvPalette.TextSecondary,
            )
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        useController = false
                        isFocusable = false
                        isFocusableInTouchMode = false
                        this.resizeMode = resizeMode
                        setShutterBackgroundColor(MyIptvPalette.Anthracite.toArgb())
                        this.player = player
                    }
                },
                update = { view ->
                    view.useController = false
                    view.isFocusable = false
                    view.resizeMode = resizeMode
                    view.player = player
                },
            )
        }
    }
}
