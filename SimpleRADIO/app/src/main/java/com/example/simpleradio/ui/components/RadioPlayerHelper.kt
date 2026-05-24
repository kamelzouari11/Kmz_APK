package com.example.simpleradio.ui.components

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.simpleradio.PlaybackService
import com.example.simpleradio.data.RadioRepository
import com.example.simpleradio.ui.MainViewModel
import com.google.common.util.concurrent.MoreExecutors
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
                                        Toast.makeText(
                                                        context,
                                                        "Erreur de lecture : URL corrompue ou indisponible",
                                                        Toast.LENGTH_SHORT
                                                )
                                                .show()
                                        viewModel.playingRadio = null
                                        viewModel.isFullScreenPlayer = false
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
                MoreExecutors.directExecutor()
        )
    }

    DisposableEffect(Unit) { onDispose { MediaController.releaseFuture(controllerFuture) } }

    return exoPlayer
}
