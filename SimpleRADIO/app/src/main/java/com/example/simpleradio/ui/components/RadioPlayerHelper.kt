package com.example.simpleradio.ui.components

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.simpleradio.PlaybackService
import com.example.simpleradio.data.RadioRepository
import com.example.simpleradio.ui.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun rememberRadioPlayer(
        context: Context,
        viewModel: MainViewModel,
        radioRepository: RadioRepository,
        prefs: SharedPreferences
): Player? {
    val scope = rememberCoroutineScope()
    val controllerFuture = remember {
        MediaController.Builder(
                        context,
                        SessionToken(context, ComponentName(context, PlaybackService::class.java))
                )
                .buildAsync()
    }
    var exoPlayer by remember { mutableStateOf<Player?>(null) }

    LaunchedEffect(viewModel.sleepTimerTimeLeft) {
        if (viewModel.sleepTimerTimeLeft == null) {
            exoPlayer?.pause()
        }
    }

    LaunchedEffect(controllerFuture) {
        controllerFuture.addListener(
                {
                    try {
                        val player = controllerFuture.get()
                        exoPlayer = player

                        // Sync initial state
                        viewModel.playerIsPlaying = player.isPlaying
                        var lastKnownMediaIndex = player.currentMediaItemIndex
                        var lastZappingDirection = 1
                        player.currentMediaItem?.let { item ->
                            scope.launch {
                                val station = radioRepository.getStationByUuid(item.mediaId)
                                if (station != null) {
                                    viewModel.playingRadio = station
                                }
                            }
                        }

                        player.addListener(
                                object : Player.Listener {
                                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                                        viewModel.playerIsPlaying = isPlaying
                                    }

                                    override fun onMediaItemTransition(
                                            mediaItem: MediaItem?,
                                            reason: Int
                                    ) {
                                        val newIndex = player.currentMediaItemIndex
                                        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK &&
                                                        lastKnownMediaIndex >= 0 &&
                                                        newIndex >= 0 &&
                                                        newIndex != lastKnownMediaIndex
                                        ) {
                                            lastZappingDirection =
                                                    if (newIndex > lastKnownMediaIndex) 1 else -1
                                        } else if (reason ==
                                                        Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
                                        ) {
                                            // A direct station choice is not zapping; use the
                                            // natural forward fallback if its URL is invalid.
                                            lastZappingDirection = 1
                                        }
                                        lastKnownMediaIndex = newIndex

                                        val newId = mediaItem?.mediaId ?: return
                                        val currentList = viewModel.navRadioList
                                        val station = currentList.find { it.stationuuid == newId }

                                        if (station != null) {
                                            if (station.stationuuid !=
                                                            viewModel.playingRadio?.stationuuid
                                            ) {
                                                viewModel.currentArtist = null
                                                viewModel.currentTitle = null
                                                viewModel.currentArtworkUrl = null
                                                viewModel.playingRadio = station
                                                scope.launch {
                                                    radioRepository.addToRecents(
                                                            station.stationuuid
                                                    )
                                                    prefs.edit()
                                                            .putString(
                                                                    "lastPlayedStationUuid",
                                                                    station.stationuuid
                                                            )
                                                            .apply()
                                                }
                                            }
                                        } else {
                                            scope.launch {
                                                val fetched =
                                                        radioRepository.getStationByUuid(newId)
                                                if (fetched != null &&
                                                                fetched.stationuuid !=
                                                                        viewModel
                                                                                .playingRadio
                                                                                ?.stationuuid
                                                ) {
                                                    viewModel.currentArtist = null
                                                    viewModel.currentTitle = null
                                                    viewModel.currentArtworkUrl = null
                                                    viewModel.playingRadio = fetched
                                                    radioRepository.addToRecents(
                                                            fetched.stationuuid
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    override fun onPlayerError(error: PlaybackException) {
                                        val failedIndex = player.currentMediaItemIndex
                                        val fallbackIndex = failedIndex + lastZappingDirection
                                        if (failedIndex >= 0 &&
                                                        fallbackIndex in 0 until player.mediaItemCount
                                        ) {
                                            val directionLabel =
                                                    if (lastZappingDirection > 0) "suivante"
                                                    else "précédente"
                                            Toast.makeText(
                                                            context,
                                                            "URL indisponible, passage à la radio $directionLabel",
                                                            Toast.LENGTH_SHORT
                                                    )
                                                    .show()
                                            try {
                                                player.seekTo(fallbackIndex, 0L)
                                                player.prepare()
                                                player.play()
                                            } catch (_: Exception) {
                                                // Keep the application and player screen alive.
                                                // A subsequent user zap can still recover playback.
                                            }
                                        } else {
                                            Toast.makeText(
                                                            context,
                                                            "URL indisponible : aucune autre radio dans ce sens",
                                                            Toast.LENGTH_SHORT
                                                    )
                                                    .show()
                                        }
                                    }
                                }
                        )
                    } catch (_: Exception) {
                        Toast.makeText(
                                        context,
                                        "Erreur d'initialisation du service audio",
                                        Toast.LENGTH_LONG
                                )
                                .show()
                    }
                },
                ContextCompat.getMainExecutor(context)
        )
    }

    DisposableEffect(Unit) { onDispose { MediaController.releaseFuture(controllerFuture) } }

    return exoPlayer
}
