package com.example.simpleradio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.example.simpleradio.data.local.entities.RadioStationEntity
import com.google.android.gms.cast.framework.CastSession

/**
 * Reusable playback controls (Previous / Play-Pause / Next). Used identically in both Portrait and
 * Landscape player layouts.
 */
@Composable
fun PlaybackControls(
        exoPlayer: Player,
        currentStation: RadioStationEntity?,
        radioList: List<RadioStationEntity>,
        isActuallyPlaying: Boolean,
        castSession: CastSession? = null,
        buttonSize: Dp = 44.dp,
        playButtonSize: Dp = 68.dp,
        spacing: Dp = 48.dp,
        modifier: Modifier = Modifier
) {
    Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally)
    ) {
        // PREVIOUS
        var isPrevFocused by remember { mutableStateOf(false) }
        IconButton(
                onClick = {
                    val idx =
                            radioList.indexOfFirst { it.stationuuid == currentStation?.stationuuid }
                    if (idx > 0) {
                        exoPlayer.seekToPrevious()
                    }
                },
                modifier =
                        Modifier.onFocusChanged { isPrevFocused = it.isFocused }
                                .background(
                                        if (isPrevFocused) Color.White else Color.Transparent,
                                        CircleShape
                                )
        ) {
            Icon(
                    Icons.Default.SkipPrevious,
                    null,
                    tint = if (isPrevFocused) Color.Black else Color.White,
                    modifier = Modifier.size(buttonSize)
            )
        }

        // PLAY/PAUSE
        var isPlayFocused by remember { mutableStateOf(false) }
        IconButton(
                onClick = {
                    if (castSession != null && castSession.isConnected) {
                        val client = castSession.remoteMediaClient
                        if (client?.isPlaying == true) client.pause() else client?.play()
                    } else {
                        if (isActuallyPlaying) exoPlayer.pause() else exoPlayer.play()
                    }
                },
                modifier =
                        Modifier.onFocusChanged { isPlayFocused = it.isFocused }
                                .background(
                                        if (isPlayFocused) Color.White else Color.Transparent,
                                        CircleShape
                                )
        ) {
            Icon(
                    if (isActuallyPlaying) Icons.Default.PauseCircleFilled
                    else Icons.Default.PlayCircleFilled,
                    null,
                    tint = if (isPlayFocused) Color.Black else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(playButtonSize)
            )
        }

        // NEXT
        var isNextFocused by remember { mutableStateOf(false) }
        IconButton(
                onClick = {
                    val idx =
                            radioList.indexOfFirst { it.stationuuid == currentStation?.stationuuid }
                    if (idx >= 0 && idx < radioList.size - 1) {
                        exoPlayer.seekToNext()
                    }
                },
                modifier =
                        Modifier.onFocusChanged { isNextFocused = it.isFocused }
                                .background(
                                        if (isNextFocused) Color.White else Color.Transparent,
                                        CircleShape
                                )
        ) {
            Icon(
                    Icons.Default.SkipNext,
                    null,
                    tint = if (isNextFocused) Color.Black else Color.White,
                    modifier = Modifier.size(buttonSize)
            )
        }
    }
}
