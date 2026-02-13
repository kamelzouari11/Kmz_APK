package com.example.simpleradio.ui.components

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.example.simpleradio.cast.CastHelper
import com.example.simpleradio.data.RadioRepository
import com.example.simpleradio.ui.MainViewModel
import com.example.simpleradio.utils.MetadataHelper
import com.example.simpleradio.utils.toMediaItem
import com.google.android.gms.cast.framework.CastSession
import java.io.File
import kotlinx.coroutines.delay

@Composable
fun PlaybackManagement(
        viewModel: MainViewModel,
        radioRepository: RadioRepository,
        context: Context,
        prefs: SharedPreferences,
        exoPlayer: Player?,
        castHelper: CastHelper,
        castSession: CastSession?
) {
    // 1. Initial Data & Cache Restoration
    LaunchedEffect(Unit) {
        val cached = radioRepository.loadCacheList(File(context.filesDir, "last_radio_list.json"))
        if (cached.isNotEmpty()) {
            viewModel.radioStations = cached
            viewModel.isViewingRadioResults = true
            val lastUuid = prefs.getString("lastPlayedStationUuid", null)
            val lastRadio =
                    if (lastUuid != null) cached.find { it.stationuuid == lastUuid } else null
            if (lastRadio != null) {
                viewModel.playingRadio = lastRadio
                viewModel.navRadioList = cached
            }
        }
    }

    // 2. Auto-Play on startup
    LaunchedEffect(exoPlayer, viewModel.playingRadio) {
        if (exoPlayer != null && viewModel.playingRadio != null) {
            if (exoPlayer.currentMediaItem == null) {
                val snapshot = viewModel.navRadioList
                if (snapshot.isNotEmpty()) {
                    val mediaItems = snapshot.map { it.toMediaItem() }
                    val startIndex =
                            snapshot.indexOfFirst {
                                it.stationuuid == viewModel.playingRadio!!.stationuuid
                            }
                    if (startIndex != -1) {
                        exoPlayer.setMediaItems(mediaItems, startIndex, 0L)
                        exoPlayer.prepare()
                        exoPlayer.play()
                    }
                }
            }
        }
    }

    // 3. Metadata Listener (Updates UI for Artist/Title)
    DisposableEffect(exoPlayer, viewModel.playingRadio) {
        val listener =
                MetadataHelper.createMetadataListener(
                        playingRadioName = viewModel.playingRadio?.name,
                        playingRadioCountry = viewModel.playingRadio?.country,
                        onMetadataFound = { artist, title ->
                            viewModel.currentArtist = artist
                            viewModel.currentTitle = title
                        }
                )
        exoPlayer?.addListener(listener)
        onDispose { exoPlayer?.removeListener(listener) }
    }

    // 4. Fetch Artwork (Cover image)
    LaunchedEffect(viewModel.currentArtist, viewModel.currentTitle) {
        viewModel.currentArtworkUrl =
                MetadataHelper.fetchArtwork(viewModel.currentArtist, viewModel.currentTitle)
    }

    // 5. Global Synchronization (Chromecast & System Media Session)
    LaunchedEffect(
            viewModel.playingRadio?.stationuuid,
            viewModel.currentArtist,
            viewModel.currentTitle,
            viewModel.currentArtworkUrl
    ) {
        if (castSession != null && castSession.isConnected) {
            val radio = viewModel.playingRadio ?: return@LaunchedEffect
            delay(800)
            castHelper.loadMedia(
                    castSession,
                    radio,
                    viewModel.currentArtist,
                    viewModel.currentTitle,
                    viewModel.currentArtworkUrl
            )
            try {
                exoPlayer?.volume = 0f
            } catch (_: Exception) {}
        }

        (exoPlayer as? MediaController)?.let { controller ->
            MetadataHelper.sendUpdateMetadataCommand(
                    controller,
                    viewModel.currentTitle,
                    viewModel.currentArtist,
                    viewModel.playingRadio?.name,
                    viewModel.currentArtworkUrl,
                    viewModel.playingRadio?.favicon
            )
        }
    }
}
