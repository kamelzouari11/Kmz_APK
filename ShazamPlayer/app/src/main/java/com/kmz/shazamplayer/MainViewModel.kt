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
import com.kmz.shazamplayer.network.SoundCloudManager
import com.kmz.shazamplayer.network.SoundCloudPlaylist
import com.kmz.shazamplayer.network.SoundCloudResult
import com.kmz.shazamplayer.network.SpotifyManager
import com.kmz.shazamplayer.util.CsvParser
import kotlin.random.Random
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
    private var onExit: (() -> Unit)? = null

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
            spotify: SpotifyManager,
            exitCallback: () -> Unit
    ) {
        this.exoPlayer = player
        this.soundCloudManager = SoundCloudManager(scClientId)
        this.spotifyManager = spotify
        this.onExit = exitCallback

        // Playback Listeners
        player.addListener(
                object : androidx.media3.common.Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        isActuallyPlaying = isPlaying
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == androidx.media3.common.Player.STATE_ENDED) playNext()
                        if (state == androidx.media3.common.Player.STATE_READY) {
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
                    } else {
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

        currentTrackIndexInFiltered = index
        val track = filteredTracks[index]

        viewModelScope.launch {
            // 1. Tenter Spotify en premier
            spotifyManager?.let { sm ->
                if (sm.isConnected()) {
                    val spotifyResult = sm.searchTrack(track.artist, track.title)
                    if (spotifyResult != null) {
                        exoPlayer?.pause()
                        isUsingSpotify = true
                        sm.play(spotifyResult.uri)
                        spotifyResult.artworkUrl?.let { track.artworkUrl = it }
                        enrichMetadata(track)
                        return@launch
                    }
                }
            }

            // 2. Fallback SoundCloud
            val results = soundCloudManager?.searchTracks(track.artist, track.title) ?: emptyList()
            if (results.isNotEmpty()) {
                alternateStreams = results
                currentStreamIndex = streamIdx % results.size
                val selected = results[currentStreamIndex]

                enrichMetadata(track)

                track.streamUrl = selected.streamUrl
                isUsingSpotify = false
                track.artworkUrl = track.officialCoverHD ?: selected.artworkUrl

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

                exoPlayer?.let { p ->
                    p.setMediaItem(mediaItem)
                    p.prepare()
                    p.play()
                }
            } else {
                Toast.makeText(context, "Non trouvé: ${track.title}", Toast.LENGTH_SHORT).show()
                if (!isRepeat) playTrack(index + 1)
            }
        }
    }

    private suspend fun enrichMetadata(track: Track) {
        soundCloudManager?.getOfficialMetadata(track.artist, track.title)?.let { meta ->
            track.officialDurationMs = meta.durationMs
            track.officialAlbum = meta.album
            track.officialCoverHD = meta.coverUrlHD
            track.metadataSource = meta.source
            if (isUsingSpotify && track.artworkUrl == null) {
                track.artworkUrl = meta.coverUrlHD
            }
        }
    }

    fun playNext() {
        if (isRepeat) {
            exoPlayer?.let {
                it.seekTo(0)
                it.play()
            }
        } else if (isShuffle && filteredTracks.isNotEmpty()) {
            playTrack(Random.nextInt(filteredTracks.size))
        } else {
            val next = currentTrackIndexInFiltered + 1
            if (next < filteredTracks.size) playTrack(next) else playTrack(0)
        }
    }

    fun playPrevious() {
        if (isRepeat) {
            exoPlayer?.let {
                it.seekTo(0)
                it.play()
            }
        } else {
            val prev = currentTrackIndexInFiltered - 1
            if (prev >= 0) playTrack(prev)
            else if (filteredTracks.isNotEmpty()) playTrack(filteredTracks.size - 1)
        }
    }

    fun openArtistRadio(artist: String) {
        viewModelScope.launch {
            isSearchingPlaylists = true
            val playlists = soundCloudManager?.searchArtistPlaylists(artist) ?: emptyList()
            artistPlaylists = playlists
            isSearchingPlaylists = false
            if (playlists.isNotEmpty()) {
                showPlaylistSelection = true
            } else {
                Toast.makeText(context, "Aucune playlist trouvée pour $artist", Toast.LENGTH_SHORT)
                        .show()
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

    fun openUserRadio(userId: Long, userName: String) {
        viewModelScope.launch {
            isSearchingPlaylists = true
            val playlists = soundCloudManager?.searchUserPlaylists(userId) ?: emptyList()
            artistPlaylists = playlists
            isSearchingPlaylists = false
            if (playlists.isNotEmpty()) {
                showPlaylistSelection = true
            } else {
                Toast.makeText(context, "Aucune autre playlist pour $userName", Toast.LENGTH_SHORT)
                        .show()
            }
        }
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

    fun togglePlay() {
        exoPlayer?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
    }

    fun cycleStream() {
        if (alternateStreams.size > 1) {
            val nextIdx = (currentStreamIndex + 1) % alternateStreams.size
            playTrack(currentTrackIndexInFiltered, nextIdx)
        }
    }
}
