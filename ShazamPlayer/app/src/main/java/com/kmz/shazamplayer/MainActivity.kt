package com.kmz.shazamplayer

import android.graphics.BitmapFactory
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
import androidx.media3.common.MediaMetadata
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
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var forwardingPlayer: CustomForwardingPlayer? = null
    private var appViewModel: MainViewModel? = null
    private val metadataScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val artworkRequestVersion = AtomicLong(0L)
    private var artworkDownloadJob: Job? = null
    private val clientId = "vIiNGKzDDokJvMAQTU0hxe3QGK3OklKu"
    private val spotifyClientId = "YOUR_SPOTIFY_ID" // USER TODO: Replace with real ID
    private val spotifyRedirectUri = "shazamplayer://callback" // USER TODO: Replace with real URI

    private var spotifyManager: SpotifyManager? = null

    // Callback pour le MediaSession (Boutons Car/MBUX)
    var onNextAction: (() -> Unit)? = null
    var onPreviousAction: (() -> Unit)? = null
    var onPlayAction: (() -> Unit)? = null
    var onPauseAction: (() -> Unit)? = null
    var onSeekAction: ((Long) -> Unit)? = null

    @OptIn(UnstableApi::class)
    inner class CustomForwardingPlayer(player: Player) : ForwardingPlayer(player) {
        private val sessionListeners = CopyOnWriteArraySet<Player.Listener>()
        private var bluetoothTitle: String? = null
        private var bluetoothArtist: String? = null
        private var bluetoothAlbum: String? = null
        private var bluetoothArtworkData: ByteArray? = null
        private var youtubeSessionActive = false
        private var youtubePlaying = false
        private var youtubePositionMs = 0L
        private var youtubeDurationMs = 0L

        override fun addListener(listener: Player.Listener) {
            sessionListeners.add(listener)
            super.addListener(listener)
        }

        override fun removeListener(listener: Player.Listener) {
            sessionListeners.remove(listener)
            super.removeListener(listener)
        }

        fun updateBluetoothMetadata(
                title: String,
                artist: String,
                album: String?,
                artworkData: ByteArray?
        ) {
            bluetoothTitle = title
            bluetoothArtist = artist
            bluetoothAlbum = album
            bluetoothArtworkData = artworkData
            val metadata = mediaMetadata
            sessionListeners.forEach { it.onMediaMetadataChanged(metadata) }
        }

        fun updateYouTubePlayback(
                active: Boolean,
                playing: Boolean,
                positionMs: Long,
                durationMs: Long
        ) {
            val activeChanged = youtubeSessionActive != active
            val playingChanged = youtubePlaying != playing
            youtubeSessionActive = active
            youtubePlaying = playing
            youtubePositionMs = positionMs.coerceAtLeast(0L)
            youtubeDurationMs = durationMs.coerceAtLeast(0L)

            if (activeChanged) {
                val fallbackPlaybackState = super.getPlaybackState()
                sessionListeners.forEach {
                    it.onPlaybackStateChanged(
                            if (active) Player.STATE_READY else fallbackPlaybackState
                    )
                }
            }
            if (activeChanged || playingChanged) {
                sessionListeners.forEach {
                    it.onPlayWhenReadyChanged(
                            getPlayWhenReady(),
                            Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
                    )
                    it.onIsPlayingChanged(isPlaying)
                }
            }
        }

        override fun getMediaMetadata(): MediaMetadata {
            if (!youtubeSessionActive && bluetoothTitle == null) return super.getMediaMetadata()
            return super.getMediaMetadata()
                    .buildUpon()
                    .setTitle(bluetoothTitle)
                    .setArtist(bluetoothArtist)
                    .setAlbumTitle(bluetoothAlbum)
                    .setArtworkUri(null)
                    .apply {
                        bluetoothArtworkData?.let {
                            setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                        }
                    }
                    .build()
        }

        override fun isPlaying(): Boolean =
                if (youtubeSessionActive) youtubePlaying else super.isPlaying()

        override fun getPlayWhenReady(): Boolean =
                if (youtubeSessionActive) youtubePlaying else super.getPlayWhenReady()

        override fun getPlaybackState(): Int =
                if (youtubeSessionActive) Player.STATE_READY else super.getPlaybackState()

        override fun getCurrentPosition(): Long =
                if (youtubeSessionActive) youtubePositionMs else super.getCurrentPosition()

        override fun getDuration(): Long =
                if (youtubeSessionActive && youtubeDurationMs > 0L)
                        youtubeDurationMs
                else super.getDuration()

        override fun play() {
            if (youtubeSessionActive) onPlayAction?.invoke() else super.play()
        }

        override fun pause() {
            if (youtubeSessionActive) onPauseAction?.invoke() else super.pause()
        }

        override fun setPlayWhenReady(playWhenReady: Boolean) {
            if (youtubeSessionActive) {
                if (playWhenReady) onPlayAction?.invoke() else onPauseAction?.invoke()
            } else {
                super.setPlayWhenReady(playWhenReady)
            }
        }

        override fun seekTo(positionMs: Long) {
            if (youtubeSessionActive) onSeekAction?.invoke(positionMs) else super.seekTo(positionMs)
        }

        override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
            if (youtubeSessionActive) onSeekAction?.invoke(positionMs)
            else super.seekTo(mediaItemIndex, positionMs)
        }

        override fun getAvailableCommands(): Player.Commands {
            return super.getAvailableCommands()
                    .buildUpon()
                    .add(COMMAND_PLAY_PAUSE)
                    .add(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                    .add(COMMAND_SEEK_TO_NEXT)
                    .add(COMMAND_SEEK_TO_PREVIOUS)
                    .add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
        }

        override fun isCommandAvailable(command: Int): Boolean {
            return when (command) {
                COMMAND_PLAY_PAUSE,
                COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
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

        forwardingPlayer = CustomForwardingPlayer(exoPlayer!!)
        mediaSession = MediaSession.Builder(this, forwardingPlayer!!).build()

        spotifyManager = SpotifyManager(this, spotifyClientId, spotifyRedirectUri)

        val activity = this
        setContent {
            ShazamPlayerTheme {
                val viewModel: MainViewModel = viewModel()
                SideEffect { appViewModel = viewModel }

                // Initialize ViewModel with dependencies
                LaunchedEffect(Unit) {
                    viewModel.init(
                            player = exoPlayer!!,
                            scClientId = clientId,
                            youtubeApiKey = BuildConfig.YOUTUBE_API_KEY,
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
                    onPlayAction = {
                        if (!viewModel.isActuallyPlaying) viewModel.togglePlay()
                    }
                    onPauseAction = {
                        if (viewModel.isActuallyPlaying) viewModel.togglePlay()
                    }
                    onSeekAction = { viewModel.seekTo(it) }
                }

                LaunchedEffect(
                        viewModel.currentTrackIndexInFiltered,
                        viewModel.youtubeVideoId,
                        viewModel.currentArtworkUrl
                ) {
                    val track = viewModel.currentTrack
                    if (viewModel.isUsingYouTube &&
                                    viewModel.youtubeVideoId != null &&
                                    track != null
                    ) {
                        activity.publishBluetoothMetadata(
                                title = track.title,
                                artist = track.artist,
                                album = track.officialAlbum,
                                artworkUrl = viewModel.currentArtworkUrl ?: track.artworkUrl
                        )
                    }
                }

                LaunchedEffect(
                        viewModel.isUsingYouTube,
                        viewModel.isActuallyPlaying,
                        viewModel.currentPosition,
                        viewModel.duration
                ) {
                    forwardingPlayer?.updateYouTubePlayback(
                            active = viewModel.isUsingYouTube && viewModel.youtubeVideoId != null,
                            playing = viewModel.isActuallyPlaying,
                            positionMs = viewModel.currentPosition,
                            durationMs = viewModel.duration
                    )
                }

                MainScreen(viewModel = viewModel)
            }
        }
    }

    private fun publishBluetoothMetadata(
            title: String,
            artist: String,
            album: String?,
            artworkUrl: String?
    ) {
        val requestVersion = artworkRequestVersion.incrementAndGet()
        artworkDownloadJob?.cancel()

        // Send textual metadata immediately; downloading artwork must not delay MBUX.
        forwardingPlayer?.updateBluetoothMetadata(title, artist, album, null)
        artworkDownloadJob =
                metadataScope.launch {
                    val artworkData =
                            artworkUrl
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { downloadDecodableArtwork(it) }
                    if (requestVersion != artworkRequestVersion.get()) return@launch
                    withContext(Dispatchers.Main) {
                        forwardingPlayer?.updateBluetoothMetadata(
                                title,
                                artist,
                                album,
                                artworkData
                        )
                    }
                }
    }

    private suspend fun downloadDecodableArtwork(url: String): ByteArray? {
        val connection =
                try {
                    URL(url).openConnection() as HttpURLConnection
                } catch (_: Exception) {
                    return null
                }
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.setRequestProperty("Accept", "image/jpeg,image/png,image/webp")

        return try {
            if (connection.responseCode !in 200..299) return null
            val declaredSize = connection.contentLengthLong
            if (declaredSize > MAX_ARTWORK_BYTES) return null

            val output = java.io.ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(16_384)
                var total = 0
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count <= 0) break
                    total += count
                    if (total > MAX_ARTWORK_BYTES) return null
                    output.write(buffer, 0, count)
                }
            }
            output.toByteArray().takeIf { bytes ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                bounds.outWidth > 0 && bounds.outHeight > 0
            }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
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

    override fun onPause() {
        // The official YouTube player must never continue as a hidden background player.
        appViewModel?.pauseYouTube()
        super.onPause()
    }

    override fun onDestroy() {
        artworkDownloadJob?.cancel()
        metadataScope.cancel()
        super.onDestroy()
        exoPlayer?.release()
        mediaSession?.release()
        exoPlayer = null
        mediaSession = null
        forwardingPlayer = null
        appViewModel = null
    }

    private companion object {
        const val MAX_ARTWORK_BYTES = 8 * 1024 * 1024
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current

    // Navigation back handling
    BackHandler {
        when (viewModel.currentLevel) {
            NavLevel.PLAYER -> {
                viewModel.pauseYouTube()
                viewModel.currentLevel = NavLevel.PLAYLIST
            }
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
                        onExit = { viewModel.exitApp() },
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
                            artworkUrl = viewModel.currentArtworkUrl ?: track.artworkUrl,
                            isPlaying = viewModel.isActuallyPlaying,
                            isShuffle = viewModel.isShuffle,
                            isRepeat = viewModel.isRepeat,
                            currentPosition = viewModel.currentPosition,
                            duration = viewModel.duration,
                            isDiscovery = viewModel.isDiscoveryMode,
                            isUsingYouTube = viewModel.isUsingYouTube,
                            youtubeVideoId = viewModel.youtubeVideoId,
                            youtubeChannel = viewModel.youtubeChannel,
                            youtubeCommand = viewModel.youtubeCommand,
                            isTrackLoading = viewModel.isTrackLoading,
                            playbackError = viewModel.playbackError,
                            onClose = {
                                viewModel.pauseYouTube()
                                viewModel.currentLevel = NavLevel.PLAYLIST
                            },
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
                            onCreatorClick = { id, name -> viewModel.openUserRadio(id, name) },
                            onYouTubeReady = { viewModel.onYouTubeReady(it) },
                            onYouTubeStateChanged = { viewModel.onYouTubeStateChanged(it) },
                            onYouTubeProgress = { position, duration ->
                                viewModel.onYouTubeProgress(position, duration)
                            },
                            onYouTubeError = { viewModel.onYouTubeError(it) }
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
                        artworkUrl =
                                viewModel.currentArtworkUrl
                                        ?: viewModel.currentTrack!!.artworkUrl,
                        // The official YouTube player must remain visible while playing.
                        onTogglePlay = { viewModel.currentLevel = NavLevel.PLAYER },
                        onClick = { viewModel.currentLevel = NavLevel.PLAYER }
                )
            }
        }
    }
}
