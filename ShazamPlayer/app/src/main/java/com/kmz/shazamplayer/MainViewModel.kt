package com.kmz.shazamplayer

import android.app.Application
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import com.kmz.shazamplayer.model.Track
import com.kmz.shazamplayer.network.MusicMetadataManager
import com.kmz.shazamplayer.network.SoundCloudManager
import com.kmz.shazamplayer.network.SoundCloudPlaylist
import com.kmz.shazamplayer.network.SoundCloudResult
import com.kmz.shazamplayer.network.SpotifyManager
import com.kmz.shazamplayer.network.YouTubeApiException
import com.kmz.shazamplayer.network.YouTubeManager
import com.kmz.shazamplayer.network.YouTubeResult
import com.kmz.shazamplayer.ui.components.YouTubePlayerAction
import com.kmz.shazamplayer.ui.components.YouTubePlayerCommand
import com.kmz.shazamplayer.util.CsvParser
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val prefs = context.getSharedPreferences("ShazamPrefs", Context.MODE_PRIVATE)

    // Dependencies
    private var exoPlayer: ExoPlayer? = null
    private var spotifyManager: SpotifyManager? = null
    private var soundCloudManager: SoundCloudManager? = null
    private var youtubeManager: YouTubeManager? = null
    private val metadataManager = MusicMetadataManager()
    private var onExit: (() -> Unit)? = null
    private var playbackSearchJob: Job? = null
    private var youtubeCommandId = 0L

    // Navigation State
    var currentLevel by mutableStateOf(NavLevel.HOME)

    // Data State
    var shazamTracks by mutableStateOf(emptyList<Track>())
    var filteredTracks by mutableStateOf(emptyList<Track>())

    // Discovery State
    var discoveryTracks by mutableStateOf(emptyList<Track>())
    var isDiscoveryMode by mutableStateOf(false)

    // Player State
    var currentTrackIndexInFiltered by mutableIntStateOf(-1)
    var alternateStreams by mutableStateOf(emptyList<SoundCloudResult>())
    var currentStreamIndex by mutableIntStateOf(0)
    var isActuallyPlaying by mutableStateOf(false)
    var isShuffle by mutableStateOf(false)
    var isRepeat by mutableStateOf(false)
    var isUsingSpotify by mutableStateOf(false)
    var isUsingYouTube by mutableStateOf(false)
    var youtubeVideoId by mutableStateOf<String?>(null)
    var youtubeChannel by mutableStateOf<String?>(null)
    var youtubeResults by mutableStateOf(emptyList<YouTubeResult>())
    var currentYoutubeResultIndex by mutableIntStateOf(-1)
    var youtubeCommand by mutableStateOf<YouTubePlayerCommand?>(null)
    var currentArtworkUrl by mutableStateOf<String?>(null)
        private set
    var isTrackLoading by mutableStateOf(false)
    var playbackError by mutableStateOf<String?>(null)

    // Progress State
    var currentPosition by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)

    // Sleep Timer State
    var sleepTimerMinutes by mutableIntStateOf(0)
    var sleepTimerRemainingSeconds by mutableIntStateOf(0)
    private var sleepTimerJob: Job? = null

    // Persistent Filter Inputs
    var selectedYear by mutableStateOf("Toutes")
    var selectedMonth by mutableStateOf("Tous")
    var magicArtistInput by mutableStateOf("")
    var shazamArtistInput by mutableStateOf("")
    var shazamTitleInput by mutableStateOf("")

    // Artist Radio State
    var showPlaylistSelection by mutableStateOf(false)
    var artistPlaylists by mutableStateOf<List<SoundCloudPlaylist>>(emptyList())
    var isSearchingPlaylists by mutableStateOf(false)
    var discoveryCreator by mutableStateOf<String?>(null)
    var discoveryCreatorId by mutableLongStateOf(0L)

    val currentTrack: Track?
        get() =
                if (currentTrackIndexInFiltered in filteredTracks.indices)
                        filteredTracks[currentTrackIndexInFiltered]
                else null

    fun init(
            player: ExoPlayer,
            scClientId: String,
            youtubeApiKey: String,
            spotify: SpotifyManager,
            exitCallback: () -> Unit
    ) {
        this.exoPlayer = player
        // SoundCloud remains available in the project for a possible rollback, but no request is
        // made while the YouTube trial is active.
        this.soundCloudManager =
                if (SOUNDCLOUD_FALLBACK_ENABLED) SoundCloudManager(scClientId) else null
        this.youtubeManager = YouTubeManager(context, youtubeApiKey)
        this.spotifyManager = spotify
        this.onExit = exitCallback

        // Playback Listeners
        player.addListener(
                object : androidx.media3.common.Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (!isUsingYouTube) isActuallyPlaying = isPlaying
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        if (!isUsingYouTube && state == androidx.media3.common.Player.STATE_ENDED) {
                            playNext()
                        }
                        if (!isUsingYouTube && state == androidx.media3.common.Player.STATE_READY) {
                            duration = player.duration.coerceAtLeast(0L)
                        }
                    }
                }
        )

        // Progress Update Loop
        viewModelScope.launch {
            while (true) {
                if (isActuallyPlaying) {
                    if (isUsingSpotify) {
                        // Spotify progress is handled via subscription
                    } else if (!isUsingYouTube) {
                        currentPosition = exoPlayer?.currentPosition ?: 0L
                        duration = (exoPlayer?.duration ?: 0L).coerceAtLeast(0L)
                    }
                }
                delay(1000)
            }
        }

        // Spotify Subscription
        spotify.subscribeToPlayerState { _, pos, dur, playing ->
            if (isUsingSpotify) {
                currentPosition = pos
                duration = dur
                isActuallyPlaying = playing
            }
        }

        loadSavedCsv()
    }

    private fun loadSavedCsv() {
        val savedCsv = prefs.getString("csv_data", null)
        if (savedCsv != null) {
            shazamTracks = CsvParser.parse(savedCsv.byteInputStream())
            filteredTracks = shazamTracks
        }
    }

    fun handleCsvContent(content: String) {
        prefs.edit().putString("csv_data", content).apply()
        shazamTracks = CsvParser.parse(content.byteInputStream())
        filteredTracks = shazamTracks
        isDiscoveryMode = false
        Toast.makeText(context, "${shazamTracks.size} morceaux chargés !", Toast.LENGTH_SHORT)
                .show()
    }

    fun applyFilters() {
        val monthMap =
                mapOf(
                        "Janvier" to "01",
                        "Février" to "02",
                        "Mars" to "03",
                        "Avril" to "04",
                        "Mai" to "05",
                        "Juin" to "06",
                        "Juillet" to "07",
                        "Août" to "08",
                        "Septembre" to "09",
                        "Octobre" to "10",
                        "Novembre" to "11",
                        "Décembre" to "12"
                )
        val monthSearch = monthMap[selectedMonth] ?: ""

        filteredTracks =
                shazamTracks.filter { track ->
                    val matchesYear =
                            selectedYear == "Toutes" || track.tagTime.contains(selectedYear)
                    val matchesMonth =
                            selectedMonth == "Tous" || track.tagTime.contains("-$monthSearch-")
                    val matchesArtist =
                            shazamArtistInput.isEmpty() ||
                                    track.artist.contains(shazamArtistInput, ignoreCase = true)
                    val matchesTitle =
                            shazamTitleInput.isEmpty() ||
                                    track.title.contains(shazamTitleInput, ignoreCase = true)
                    matchesYear && matchesMonth && matchesArtist && matchesTitle
                }
        isDiscoveryMode = false
        currentLevel = NavLevel.PLAYLIST
    }

    fun playTrack(index: Int, streamIdx: Int = 0) {
        if (filteredTracks.isEmpty() || index !in filteredTracks.indices) return
        if (SOUNDCLOUD_FALLBACK_ENABLED) {
            playTrackFromSoundCloud(index, streamIdx)
            return
        }

        playbackSearchJob?.cancel()
        currentTrackIndexInFiltered = index
        val track = filteredTracks[index]
        currentArtworkUrl = track.officialCoverHD ?: track.artworkUrl
        currentLevel = NavLevel.PLAYER
        exoPlayer?.pause()
        spotifyManager?.pause()
        isUsingSpotify = false
        isUsingYouTube = true
        syncYouTubeQueue(index)
        isActuallyPlaying = false
        youtubeVideoId = null
        youtubeChannel = null
        youtubeResults = emptyList()
        currentYoutubeResultIndex = -1
        youtubeCommand = null
        currentPosition = 0L
        duration = 0L
        playbackError = null
        isTrackLoading = true

        track.youtubeVideoId?.takeIf { it.isNotBlank() }?.let { videoId ->
            youtubeResults =
                    listOf(
                            YouTubeResult(
                                    videoId = videoId,
                                    title = track.title,
                                    channelTitle = track.youtubeChannel ?: track.artist,
                                    artworkUrl = currentArtworkUrl,
                                    durationMs = track.officialDurationMs ?: 0L,
                                    score = Int.MAX_VALUE
                            )
                    )
            applyYouTubeResult(0)
            isTrackLoading = false
            return
        }

        playbackSearchJob =
                viewModelScope.launch {
                    try {
                        val metadata = metadataManager.getOfficialMetadata(track.artist, track.title)
                        if (currentTrackIndexInFiltered != index) return@launch

                        metadata?.let { meta ->
                            track.officialDurationMs = meta.durationMs
                            track.officialAlbum = meta.album
                            track.officialCoverHD = meta.coverUrlHD
                            track.metadataSource = meta.source
                            track.artworkUrl = meta.coverUrlHD ?: meta.coverUrl ?: track.artworkUrl
                            currentArtworkUrl = track.artworkUrl
                            syncYouTubeQueue(index)
                        }

                        val results =
                                youtubeManager?.searchTracks(
                                        artist = track.artist,
                                        title = track.title,
                                        expectedDurationMs = track.officialDurationMs
                                ) ?: emptyList()
                        if (currentTrackIndexInFiltered != index) return@launch

                        if (results.isEmpty()) {
                            playbackError = "Aucun résultat YouTube compatible trouvé."
                        } else {
                            youtubeResults = results
                            val selectedIndex = streamIdx.coerceIn(0, results.lastIndex)
                            applyYouTubeResult(selectedIndex)
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: YouTubeApiException) {
                        playbackError = error.message
                    } catch (error: Exception) {
                        playbackError = "Recherche impossible : ${error.localizedMessage ?: "erreur réseau"}"
                    } finally {
                        if (currentTrackIndexInFiltered == index) isTrackLoading = false
                    }
                }
    }

    /**
     * Exposes a small, circular Media3 timeline while YouTube provides the actual audio.
     *
     * MBUX hides its standard previous/next controls when the MediaSession timeline is empty. The
     * placeholder items are never prepared or played by ExoPlayer; CustomForwardingPlayer routes
     * the car controls back to [playPrevious] and [playNext]. Keeping only three items also avoids
     * sending a potentially very large CSV playlist through the Bluetooth media session.
     */
    private fun syncYouTubeQueue(currentIndex: Int) {
        if (currentIndex !in filteredTracks.indices) return

        val trackCount = filteredTracks.size
        val queueIndices =
                listOf(
                        (currentIndex - 1 + trackCount) % trackCount,
                        currentIndex,
                        (currentIndex + 1) % trackCount
                )
        val queueItems =
                queueIndices.mapIndexed { slot, trackIndex ->
                    val track = filteredTracks[trackIndex]
                    val metadata =
                            MediaMetadata.Builder()
                                    .setTitle(track.title)
                                    .setArtist(track.artist)
                                    .setAlbumTitle(track.officialAlbum)
                                    .apply {
                                        (track.officialCoverHD ?: track.artworkUrl)
                                                ?.takeIf { it.isNotBlank() }
                                                ?.let { setArtworkUri(Uri.parse(it)) }
                                    }
                                    .build()

                    MediaItem.Builder()
                            .setMediaId("shazam-queue:$trackIndex:$slot")
                            // A URI lets ExoPlayer create its placeholder timeline. It is never
                            // prepared because YouTube remains the active playback engine.
                            .setUri("https://localhost.invalid/shazam-queue/$trackIndex.mp3")
                            .setMediaMetadata(metadata)
                            .build()
                }

        exoPlayer?.setMediaItems(queueItems, /* startIndex= */ 1, /* startPositionMs= */ 0L)
    }

    /** Preserved rollback path. It performs no request while SOUNDCLOUD_FALLBACK_ENABLED is false. */
    private fun playTrackFromSoundCloud(index: Int, streamIdx: Int) {
        playbackSearchJob?.cancel()
        currentTrackIndexInFiltered = index
        val track = filteredTracks[index]
        currentArtworkUrl = track.officialCoverHD ?: track.artworkUrl
        currentLevel = NavLevel.PLAYER
        youtubeVideoId = null
        youtubeChannel = null
        isUsingYouTube = false
        isUsingSpotify = false
        isActuallyPlaying = false
        playbackError = null
        isTrackLoading = true

        playbackSearchJob =
                viewModelScope.launch {
                    try {
                        val metadata = metadataManager.getOfficialMetadata(track.artist, track.title)
                        metadata?.let { meta ->
                            track.officialDurationMs = meta.durationMs
                            track.officialAlbum = meta.album
                            track.officialCoverHD = meta.coverUrlHD
                            track.metadataSource = meta.source
                        }

                        val results =
                                soundCloudManager?.searchTracks(track.artist, track.title)
                                        ?: emptyList()
                        if (currentTrackIndexInFiltered != index) return@launch

                        if (results.isEmpty()) {
                            playbackError = "Aucun flux SoundCloud trouvé."
                            return@launch
                        }

                        alternateStreams = results
                        currentStreamIndex = streamIdx % results.size
                        val selected = results[currentStreamIndex]
                        track.streamUrl = selected.streamUrl
                        track.artworkUrl =
                                track.officialCoverHD ?: selected.artworkUrl ?: track.artworkUrl
                        currentArtworkUrl = track.artworkUrl

                        val mediaMetadata =
                                MediaMetadata.Builder()
                                        .setTitle(track.title)
                                        .setArtist(track.artist)
                                        .setArtworkUri(track.artworkUrl?.let { Uri.parse(it) })
                                        .build()
                        val mediaItem =
                                MediaItem.Builder()
                                        .setUri(Uri.parse(selected.streamUrl))
                                        .setMediaMetadata(mediaMetadata)
                                        .build()
                        exoPlayer?.apply {
                            setMediaItem(mediaItem)
                            prepare()
                            play()
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        playbackError =
                                "SoundCloud indisponible : ${error.localizedMessage ?: "erreur réseau"}"
                    } finally {
                        if (currentTrackIndexInFiltered == index) isTrackLoading = false
                    }
                }
    }

    private fun applyYouTubeResult(index: Int) {
        val result = youtubeResults.getOrNull(index) ?: return
        currentYoutubeResultIndex = index
        youtubeChannel = result.channelTitle
        currentTrack?.let { track ->
            val youtubeThumbnail =
                    result.artworkUrl
                            ?: "https://i.ytimg.com/vi/${result.videoId}/hqdefault.jpg"
            track.artworkUrl = track.officialCoverHD ?: youtubeThumbnail
            currentArtworkUrl = track.artworkUrl
        }
        duration = result.durationMs
        currentPosition = 0L
        playbackError = null
        youtubeCommand = null
        youtubeVideoId = result.videoId
    }

    fun playNext() {
        if (isRepeat) {
            seekTo(0L)
        } else if (isShuffle && filteredTracks.isNotEmpty()) {
            playTrack(Random.nextInt(filteredTracks.size))
        } else {
            val next = currentTrackIndexInFiltered + 1
            if (next < filteredTracks.size) playTrack(next) else playTrack(0)
        }
    }

    fun playPrevious() {
        if (isRepeat) {
            seekTo(0L)
        } else {
            val prev = currentTrackIndexInFiltered - 1
            if (prev >= 0) playTrack(prev)
            else if (filteredTracks.isNotEmpty()) playTrack(filteredTracks.size - 1)
        }
    }

    fun openArtistRadio(artist: String) {
        val requestedArtist = artist.trim()
        if (requestedArtist.isEmpty() || isSearchingPlaylists) return

        viewModelScope.launch {
            isSearchingPlaylists = true
            try {
                val results = youtubeManager?.searchArtistTopTracks(requestedArtist).orEmpty()
                if (results.isEmpty()) {
                    Toast.makeText(
                                    context,
                                    "Aucun titre populaire trouvé pour $requestedArtist",
                                    Toast.LENGTH_SHORT
                            )
                            .show()
                    return@launch
                }

                val tracks =
                        results.mapIndexed { index, result ->
                            Track(
                                    index = (index + 1).toString(),
                                    tagTime = "",
                                    title = result.title,
                                    artist = requestedArtist,
                                    shazamUrl = "",
                                    trackKey = "youtube:${result.videoId}",
                                    artworkUrl = result.artworkUrl,
                                    officialDurationMs = result.durationMs,
                                    officialCoverHD = result.artworkUrl,
                                    metadataSource = "youtube",
                                    youtubeVideoId = result.videoId,
                                    youtubeChannel = result.channelTitle
                            )
                        }

                discoveryTracks = tracks
                filteredTracks = tracks
                isDiscoveryMode = true
                discoveryCreator = requestedArtist
                discoveryCreatorId = 0L
                showPlaylistSelection = false
                currentTrackIndexInFiltered = 0
                playTrack(0)
                currentLevel = NavLevel.PLAYER
            } catch (error: YouTubeApiException) {
                Toast.makeText(context, error.message, Toast.LENGTH_LONG).show()
            } catch (error: Exception) {
                Toast.makeText(
                                context,
                                "Radio artiste indisponible : ${error.localizedMessage ?: "erreur réseau"}",
                                Toast.LENGTH_LONG
                        )
                        .show()
            } finally {
                isSearchingPlaylists = false
            }
        }
    }

    fun loadPlaylist(playlist: SoundCloudPlaylist) {
        viewModelScope.launch {
            val tracks =
                    soundCloudManager?.getPlaylistTracks(playlist.id, playlist.secretToken)
                            ?: emptyList()
            if (tracks.isNotEmpty()) {
                discoveryTracks = tracks
                filteredTracks = tracks
                isDiscoveryMode = true
                discoveryCreator = playlist.creatorName
                discoveryCreatorId = playlist.userId

                processDiscoveryTracks(tracks, playlist.artworkUrl)

                currentTrackIndexInFiltered = 0
                playTrack(0)
                currentLevel = NavLevel.PLAYER
                showPlaylistSelection = false
            } else {
                Toast.makeText(context, "Playlist vide ou inaccessible", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun processDiscoveryTracks(tracks: List<Track>, playlistArtwork: String?) {
        tracks.forEach { t ->
            val separators = listOf(" - ", " – ", " — ", " | ", " : ")
            val foundSeparator = separators.find { t.title.contains(it) }

            if (foundSeparator != null) {
                val parts = t.title.split(foundSeparator).map { it.trim() }
                if (parts.size >= 2) {
                    viewModelScope.launch {
                        var meta = soundCloudManager?.getOfficialMetadata(parts[0], parts[1])
                        if (meta == null) {
                            meta = soundCloudManager?.getOfficialMetadata(parts[1], parts[0])
                        }
                        meta?.let {
                            t.artworkUrl = it.coverUrlHD ?: it.coverUrl ?: t.artworkUrl
                            t.artist = it.artist
                            t.title = it.title
                            t.officialAlbum = it.album
                            t.officialCoverHD = it.coverUrlHD
                        }
                    }
                }
            }
            if (t.artworkUrl.isNullOrBlank()) t.artworkUrl = playlistArtwork
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun openUserRadio(userId: Long, userName: String) {
        openArtistRadio(userName)
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimerMinutes = minutes
        sleepTimerJob?.cancel()
        if (minutes > 0) {
            sleepTimerJob =
                    viewModelScope.launch {
                        sleepTimerRemainingSeconds = minutes * 60
                        while (sleepTimerRemainingSeconds > 0) {
                            delay(1000)
                            sleepTimerRemainingSeconds--
                        }
                        onExit?.invoke()
                    }
        }
    }

    fun exitApp() {
        onExit?.invoke()
    }

    fun togglePlay() {
        if (isUsingYouTube) {
            issueYouTubeCommand(
                    if (isActuallyPlaying) YouTubePlayerAction.PAUSE else YouTubePlayerAction.PLAY
            )
        } else {
            exoPlayer?.let { if (it.isPlaying) it.pause() else it.play() }
        }
    }

    fun seekTo(position: Long) {
        if (isUsingYouTube) {
            issueYouTubeCommand(YouTubePlayerAction.SEEK, position)
        } else {
            exoPlayer?.seekTo(position)
        }
    }

    fun cycleStream() {
        if (!isUsingYouTube && alternateStreams.size > 1) {
            val nextIndex = (currentStreamIndex + 1) % alternateStreams.size
            playTrackFromSoundCloud(currentTrackIndexInFiltered, nextIndex)
        } else if (youtubeResults.size > 1) {
            val nextIndex = (currentYoutubeResultIndex + 1) % youtubeResults.size
            isActuallyPlaying = false
            applyYouTubeResult(nextIndex)
            Toast.makeText(
                            context,
                            "Résultat ${nextIndex + 1}/${youtubeResults.size}",
                            Toast.LENGTH_SHORT
                    )
                    .show()
        } else {
            Toast.makeText(context, "Aucun autre résultat en cache.", Toast.LENGTH_SHORT).show()
        }
    }

    fun pauseYouTube() {
        if (isUsingYouTube && youtubeVideoId != null) {
            issueYouTubeCommand(YouTubePlayerAction.PAUSE)
            isActuallyPlaying = false
        }
    }

    fun onYouTubeReady(playerDurationMs: Long) {
        if (playerDurationMs > 0) duration = playerDurationMs
    }

    fun onYouTubeStateChanged(state: Int) {
        when (state) {
            0 -> {
                isActuallyPlaying = false
                if (isRepeat) seekTo(0L) else playNext()
            }
            1 -> isActuallyPlaying = true
            2, 5 -> isActuallyPlaying = false
        }
    }

    fun onYouTubeProgress(positionMs: Long, playerDurationMs: Long) {
        currentPosition = positionMs.coerceAtLeast(0L)
        if (playerDurationMs > 0) duration = playerDurationMs
    }

    fun onYouTubeError(code: Int) {
        isActuallyPlaying = false
        val message =
                when (code) {
                    2 -> "Identifiant YouTube incorrect."
                    5 -> "Cette vidéo ne peut pas être lue sur cet appareil."
                    100 -> "Cette vidéo a été supprimée ou rendue privée."
                    101, 150 -> "Le propriétaire interdit la lecture intégrée."
                    153 -> "YouTube n'a pas pu identifier l'application."
                    else -> "Erreur du lecteur YouTube ($code)."
                }
        val nextIndex = currentYoutubeResultIndex + 1
        if (nextIndex in youtubeResults.indices) {
            Toast.makeText(context, "$message Essai du résultat suivant…", Toast.LENGTH_SHORT)
                    .show()
            applyYouTubeResult(nextIndex)
        } else {
            currentTrack?.let { youtubeManager?.clearCachedTrack(it.artist, it.title) }
            youtubeVideoId = null
            playbackError = message
        }
    }

    private fun issueYouTubeCommand(action: YouTubePlayerAction, positionMs: Long = 0L) {
        youtubeCommandId++
        youtubeCommand = YouTubePlayerCommand(youtubeCommandId, action, positionMs)
    }

    companion object {
        private const val SOUNDCLOUD_FALLBACK_ENABLED = false
    }
}
