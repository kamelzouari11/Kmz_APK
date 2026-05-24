package com.kmz.shazamplayer

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.kmz.shazamplayer.network.SpotifyManager
import com.kmz.shazamplayer.ui.components.PlayerBottomBar
import com.kmz.shazamplayer.ui.components.PlaylistSelectionDialog
import com.kmz.shazamplayer.ui.screens.FullScreenPlayer
import com.kmz.shazamplayer.ui.screens.HomeScreen
import com.kmz.shazamplayer.ui.screens.PlaylistScreen
import com.kmz.shazamplayer.ui.theme.ShazamPlayerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private val clientId = "vIiNGKzDDokJvMAQTU0hxe3QGK3OklKu"
    private val spotifyClientId = "YOUR_SPOTIFY_ID" // USER TODO: Replace with real ID
    private val spotifyRedirectUri = "shazamplayer://callback" // USER TODO: Replace with real URI

    private var spotifyManager: SpotifyManager? = null

    // Callback pour le MediaSession (Boutons Car/MBUX)
    var onNextAction: (() -> Unit)? = null
    var onPreviousAction: (() -> Unit)? = null

    @OptIn(UnstableApi::class)
    inner class CustomForwardingPlayer(player: Player) : ForwardingPlayer(player) {
        override fun getAvailableCommands(): Player.Commands {
            return super.getAvailableCommands()
                    .buildUpon()
                    .add(COMMAND_SEEK_TO_NEXT)
                    .add(COMMAND_SEEK_TO_PREVIOUS)
                    .add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
        }

        override fun isCommandAvailable(command: Int): Boolean {
            return when (command) {
                COMMAND_SEEK_TO_NEXT,
                COMMAND_SEEK_TO_PREVIOUS,
                COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
                else -> super.isCommandAvailable(command)
            }
        }

        override fun seekToNext() {
            onNextAction?.invoke() ?: super.seekToNext()
        }

        override fun seekToPrevious() {
            onPreviousAction?.invoke() ?: super.seekToPrevious()
        }

        override fun seekToNextMediaItem() {
            onNextAction?.invoke() ?: super.seekToNextMediaItem()
        }

        override fun seekToPreviousMediaItem() {
            onPreviousAction?.invoke() ?: super.seekToPreviousMediaItem()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exoPlayer = ExoPlayer.Builder(this).setHandleAudioBecomingNoisy(true).build()

        val forwardingPlayer = CustomForwardingPlayer(exoPlayer!!)
        mediaSession = MediaSession.Builder(this, forwardingPlayer).build()

        spotifyManager = SpotifyManager(this, spotifyClientId, spotifyRedirectUri)

        val activity = this
        setContent {
            ShazamPlayerTheme {
                val viewModel: MainViewModel = viewModel()

                // Initialize ViewModel with dependencies
                LaunchedEffect(Unit) {
                    viewModel.init(
                            player = exoPlayer!!,
                            scClientId = clientId,
                            spotify = spotifyManager!!,
                            exitCallback = { activity.shutdown() }
                    )
                }

                // Bind MBUX actions
                LaunchedEffect(
                        viewModel.filteredTracks,
                        viewModel.currentTrackIndexInFiltered,
                        viewModel.isShuffle,
                        viewModel.isRepeat
                ) {
                    onNextAction = { viewModel.playNext() }
                    onPreviousAction = { viewModel.playPrevious() }
                }

                MainScreen(viewModel = viewModel)
            }
        }
    }

    private fun shutdown() {
        exoPlayer?.stop()
        exoPlayer?.release()
        exoPlayer = null
        mediaSession?.release()
        mediaSession = null
        spotifyManager?.disconnect()
        spotifyManager = null
        finishAffinity()
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        mediaSession?.release()
        exoPlayer = null
        mediaSession = null
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current

    // Navigation back handling
    BackHandler {
        when (viewModel.currentLevel) {
            NavLevel.PLAYER -> viewModel.currentLevel = NavLevel.PLAYLIST
            NavLevel.PLAYLIST -> viewModel.currentLevel = NavLevel.HOME
            NavLevel.HOME -> {
                /* system handles */
            }
        }
    }

    val filePickerLauncher =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) {
                    uri: Uri? ->
                uri?.let {
                    try {
                        context.contentResolver.openInputStream(it)?.use { inputStream ->
                            val content = inputStream.bufferedReader().use { it.readText() }
                            viewModel.handleCsvContent(content)
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Erreur : ${e.localizedMessage}", Toast.LENGTH_LONG)
                                .show()
                    }
                }
            }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when (viewModel.currentLevel) {
            NavLevel.HOME -> {
                HomeScreen(
                        onLoadCsv = { filePickerLauncher.launch("*/*") },
                        onExit = { /* handled via shutdown in Activity */},
                        years = listOf("Toutes") + (2021..2026).map { it.toString() },
                        months =
                                listOf(
                                        "Tous",
                                        "Janvier",
                                        "Février",
                                        "Mars",
                                        "Avril",
                                        "Mai",
                                        "Juin",
                                        "Juillet",
                                        "Août",
                                        "Septembre",
                                        "Octobre",
                                        "Novembre",
                                        "Décembre"
                                ),
                        selectedYear = viewModel.selectedYear,
                        selectedMonth = viewModel.selectedMonth,
                        magicArtistValue = viewModel.magicArtistInput,
                        shazamArtistValue = viewModel.shazamArtistInput,
                        shazamTitleValue = viewModel.shazamTitleInput,
                        isActuallyPlaying = viewModel.isActuallyPlaying,
                        sleepTimerMinutes = viewModel.sleepTimerMinutes,
                        onYearChange = { viewModel.selectedYear = it },
                        onMonthChange = { viewModel.selectedMonth = it },
                        onMagicArtistInputChange = { viewModel.magicArtistInput = it },
                        onShazamArtistInputChange = { viewModel.shazamArtistInput = it },
                        onShazamTitleInputChange = { viewModel.shazamTitleInput = it },
                        onApply = { viewModel.applyFilters() },
                        onMagicSearch = { viewModel.openArtistRadio(it) },
                        onSetSleepTimer = { viewModel.startSleepTimer(it) },
                        onBackToPlaylist = { viewModel.currentLevel = NavLevel.PLAYLIST }
                )
            }
            NavLevel.PLAYLIST -> {
                PlaylistScreen(
                        tracks = viewModel.filteredTracks,
                        selectedIndex = viewModel.currentTrackIndexInFiltered,
                        isDiscovery = viewModel.isDiscoveryMode,
                        onTrackClick = { idx -> viewModel.playTrack(idx) },
                        onBack = { viewModel.currentLevel = NavLevel.HOME }
                )
            }
            NavLevel.PLAYER -> {
                viewModel.currentTrack?.let { track ->
                    FullScreenPlayer(
                            track = track,
                            isPlaying = viewModel.isActuallyPlaying,
                            isShuffle = viewModel.isShuffle,
                            isRepeat = viewModel.isRepeat,
                            currentPosition = viewModel.currentPosition,
                            duration = viewModel.duration,
                            isDiscovery = viewModel.isDiscoveryMode,
                            onClose = { viewModel.currentLevel = NavLevel.PLAYLIST },
                            onTogglePlay = { viewModel.togglePlay() },
                            onPrevious = { viewModel.playPrevious() },
                            onNext = { viewModel.playNext() },
                            onShuffleToggle = { viewModel.isShuffle = !viewModel.isShuffle },
                            onRepeatToggle = { viewModel.isRepeat = !viewModel.isRepeat },
                            onCycleStream = { viewModel.cycleStream() },
                            onSeek = { viewModel.seekTo(it) },
                            onArtistRadio = { viewModel.openArtistRadio(track.artist) },
                            isSearchingPlaylists = viewModel.isSearchingPlaylists,
                            discoveryCreator = viewModel.discoveryCreator,
                            discoveryCreatorId = viewModel.discoveryCreatorId,
                            onCreatorClick = { id, name -> viewModel.openUserRadio(id, name) }
                    )
                }
            }
        }

        if (viewModel.showPlaylistSelection) {
            PlaylistSelectionDialog(
                    playlists = viewModel.artistPlaylists,
                    onDismiss = { viewModel.showPlaylistSelection = false },
                    onSelect = { viewModel.loadPlaylist(it) }
            )
        }

        if (viewModel.currentTrack != null &&
                        (viewModel.currentLevel == NavLevel.PLAYLIST ||
                                viewModel.currentLevel == NavLevel.HOME)
        ) {
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                PlayerBottomBar(
                        viewModel.currentTrack!!,
                        viewModel.isActuallyPlaying,
                        onTogglePlay = { viewModel.togglePlay() },
                        onClick = { viewModel.currentLevel = NavLevel.PLAYER }
                )
            }
        }
    }
}
