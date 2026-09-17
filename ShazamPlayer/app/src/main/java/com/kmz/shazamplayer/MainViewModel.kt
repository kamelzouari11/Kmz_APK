package com.kmz.shazamplayer

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.SilenceMediaSource
import com.kmz.shazamplayer.model.Track
import com.kmz.shazamplayer.network.MusicMetadataManager
import com.kmz.shazamplayer.network.NewPipeManager
import com.kmz.shazamplayer.network.NewPipePlaylistException
import com.kmz.shazamplayer.network.NewPipeSearchClient
import com.kmz.shazamplayer.network.NewPipeSearchException
import com.kmz.shazamplayer.network.SoundCloudManager
import com.kmz.shazamplayer.network.SoundCloudPlaylist
import com.kmz.shazamplayer.network.SoundCloudResult
import com.kmz.shazamplayer.network.SpotifyManager
import com.kmz.shazamplayer.network.YouTubeApiException
import com.kmz.shazamplayer.network.YouTubeManager
import com.kmz.shazamplayer.network.YouTubeMappingStore
import com.kmz.shazamplayer.network.YouTubeResult
import com.kmz.shazamplayer.ui.components.YouTubePlayerAction
import com.kmz.shazamplayer.ui.components.YouTubePlayerCommand
import com.kmz.shazamplayer.util.CsvParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val prefs = context.getSharedPreferences("ShazamPrefs", Context.MODE_PRIVATE)

    // Dependencies
    private var exoPlayer: ExoPlayer? = null
    private var spotifyManager: SpotifyManager? = null
    private var soundCloudManager: SoundCloudManager? = null
    private var youtubeManager: YouTubeManager? = null
    private val metadataManager = MusicMetadataManager()
    private val newPipeManager = NewPipeManager()
    private val newPipeSearchClient = NewPipeSearchClient(context)
    private val youtubeMappingStore = YouTubeMappingStore(context)
    private var onExit: (() -> Unit)? = null
    private var playbackSearchJob: Job? = null
    private var newPipePlaylistJob: Job? = null
    private var mappingPrefetchJob: Job? = null
    private var catalogCompletionJob: Job? = null
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
        private set
    var isRepeat by mutableStateOf(false)
    var isUsingSpotify by mutableStateOf(false)
    var isUsingYouTube by mutableStateOf(false)
    var isUsingNewPipe by mutableStateOf(false)
        private set
    var youtubeVideoId by mutableStateOf<String?>(null)
    var youtubeChannel by mutableStateOf<String?>(null)
    var youtubeResults by mutableStateOf(emptyList<YouTubeResult>())
    var currentYoutubeResultIndex by mutableIntStateOf(-1)
    var youtubeCommand by mutableStateOf<YouTubePlayerCommand?>(null)
    var currentArtworkUrl by mutableStateOf<String?>(null)
        private set
    var isTrackLoading by mutableStateOf(false)
    var playbackError by mutableStateOf<String?>(null)

    // NewPipe hand-off state
    var isPreparingNewPipePlaylist by mutableStateOf(false)
        private set
    var newPipePlaylistProgress by mutableIntStateOf(0)
        private set
    var newPipePlaylistTargetCount by mutableIntStateOf(0)
        private set
    var hasNewPipeSearchAccess by mutableStateOf(false)
        private set
    var youtubeMappingCount by mutableIntStateOf(0)
        private set
    var isCompletingYouTubeCatalog by mutableStateOf(false)
        private set
    var catalogCompletionProgress by mutableIntStateOf(0)
        private set
    var catalogCompletionTarget by mutableIntStateOf(0)
        private set

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
                    } else if (!isUsingYouTube && !isUsingNewPipe) {
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

        refreshNewPipeSearchAccess()
        loadSavedCsv()
    }

    private fun loadSavedCsv() {
        val savedCsv = prefs.getString("csv_data", null)
        if (savedCsv != null) {
            shazamTracks = runCatching { CsvParser.parse(savedCsv.byteInputStream()) }.getOrElse {
                Toast.makeText(context, "Bibliothèque illisible : réimportez SyncedSongs.csv.", Toast.LENGTH_LONG).show()
                emptyList()
            }
            restoreYouTubeMappings(shazamTracks)
            filteredTracks = shazamTracks
        }
    }

    fun handleCsvContent(content: String) {
        val importedTracks = CsvParser.parse(content.byteInputStream())
        prefs.edit().putString("csv_data", content).apply()
        shazamTracks = importedTracks
        restoreYouTubeMappings(shazamTracks)
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
        startTrack(index, streamIdx, preferNewPipe = true)
    }

    private fun startTrack(index: Int, streamIdx: Int, preferNewPipe: Boolean) {
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
        isUsingYouTube = false
        isUsingNewPipe = false
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

        if (preferNewPipe && isNewPipeInstalled()) {
            refreshNewPipeSearchAccess()
            if (!hasNewPipeSearchAccess) {
                isTrackLoading = false
                playbackError = "Autorisez d'abord la liaison avec NewPipe."
                requestNewPipeSearchAccess()
                return
            }
            playTrackInNewPipe(index, track)
            return
        }

        isUsingYouTube = true
        syncYouTubeQueue(index)

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

    /** Searches only when needed, then starts NewPipe's audio player without opening its UI. */
    private fun playTrackInNewPipe(index: Int, track: Track) {
        isUsingNewPipe = true
        playbackSearchJob =
                viewModelScope.launch {
                    val officialMetadataDeferred =
                            async { metadataManager.getOfficialMetadata(track.artist, track.title) }
                    try {
                        val knownVideoId =
                                track.youtubeVideoId?.takeIf { VIDEO_ID.matches(it) }
                                        ?: if (youtubeMappingStore.restore(track)) {
                                            track.youtubeVideoId
                                        } else {
                                            null
                                        }

                        if (knownVideoId != null) {
                            newPipeSearchClient.playVideoId(knownVideoId)
                            youtubeVideoId = knownVideoId
                            youtubeChannel = track.youtubeChannel
                            applyNewPipeArtwork(track, knownVideoId)
                        } else {
                            val result =
                                    newPipeSearchClient.searchAndPlayFirst(
                                            artist = track.artist,
                                            title = track.title
                                    )
                            if (result == null) {
                                failNewPipePlayback(index, "Aucun résultat trouvé par NewPipe.")
                                return@launch
                            }
                            youtubeMappingStore.save(
                                    track,
                                    result.videoId,
                                    result.channel,
                                    result.artworkUrl
                            )
                            refreshYouTubeMappingCount()
                            youtubeVideoId = result.videoId
                            youtubeChannel = result.channel
                            applyNewPipeArtwork(track, result.videoId, result.artworkUrl)
                        }

                        if (currentTrackIndexInFiltered != index) return@launch
                        isActuallyPlaying = true
                        isTrackLoading = false
                        playbackError = null
                        prefetchUpcomingYouTubeMappings()

                        // Replace the temporary thumbnail with the same validated cover strategy
                        // used by SimpleRADIO, without delaying NewPipe playback.
                        officialMetadataDeferred.await()?.let { metadata ->
                            if (currentTrackIndexInFiltered != index) return@let
                            track.officialDurationMs = metadata.durationMs
                            track.officialAlbum = metadata.album
                            track.officialCoverHD = metadata.coverUrlHD
                            track.metadataSource = metadata.source
                            track.artworkUrl =
                                    metadata.coverUrlHD
                                            ?: metadata.coverUrl
                                            ?: track.artworkUrl
                            currentArtworkUrl = track.artworkUrl
                            youtubeVideoId?.let { videoId ->
                                youtubeMappingStore.save(
                                        track,
                                        videoId,
                                        youtubeChannel,
                                        track.artworkUrl
                                )
                            }
                        }
                    } catch (error: CancellationException) {
                        officialMetadataDeferred.cancel()
                        throw error
                    } catch (error: NewPipeSearchException) {
                        officialMetadataDeferred.cancel()
                        failNewPipePlayback(index, error.message ?: "Liaison NewPipe impossible.")
                    } catch (error: Exception) {
                        officialMetadataDeferred.cancel()
                        failNewPipePlayback(
                                index,
                                error.localizedMessage ?: "Liaison NewPipe impossible."
                        )
                    } finally {
                        if (currentTrackIndexInFiltered == index && isUsingNewPipe) {
                            isTrackLoading = false
                        }
                    }
                }
    }

    private fun applyNewPipeArtwork(
            track: Track,
            videoId: String,
            newPipeArtworkUrl: String? = null
    ) {
        val artwork =
                track.officialCoverHD
                        ?: newPipeArtworkUrl?.takeIf { it.isNotBlank() }
                        ?: track.artworkUrl?.takeIf { it.isNotBlank() }
                        ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        track.artworkUrl = artwork
        currentArtworkUrl = artwork
    }

    private fun failNewPipePlayback(index: Int, message: String) {
        if (currentTrackIndexInFiltered != index) return
        isUsingNewPipe = false
        isActuallyPlaying = false
        isTrackLoading = false
        playbackError = message
        showToast(message, Toast.LENGTH_LONG)
    }

    /** Publishes the complete playlist to MediaSession while YouTube provides the actual audio. */
    @androidx.annotation.OptIn(UnstableApi::class)
    private fun syncYouTubeQueue(currentIndex: Int) {
        if (currentIndex !in filteredTracks.indices) return

        val queueSources =
                filteredTracks.mapIndexed { trackIndex, track ->
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
                    val mediaItem =
                            MediaItem.Builder()
                                    .setMediaId("shazam-queue:$trackIndex")
                                    .setUri(Uri.EMPTY)
                                    .setMediaMetadata(metadata)
                                    .build()
                    val durationUs =
                            (track.officialDurationMs ?: DEFAULT_QUEUE_ITEM_DURATION_MS)
                                    .coerceAtLeast(1L) * 1_000L

                    SilenceMediaSource.Factory()
                            .setDurationUs(durationUs)
                            .createMediaSource()
                            .also { it.updateMediaItem(mediaItem) }
                }

        exoPlayer?.apply {
            setMediaSources(queueSources, currentIndex, /* startPositionMs= */ 0L)
            // A prepared timeline is required for legacy Bluetooth/AVRCP clients such as MBUX.
            // The player remains paused; only the official YouTube player produces audio.
            prepare()
            pause()
        }
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
            track.youtubeVideoId = result.videoId
            track.youtubeChannel = result.channelTitle
            youtubeMappingStore.save(track, result.videoId, result.channelTitle)
            refreshYouTubeMappingCount()
            currentArtworkUrl = track.artworkUrl
        }
        duration = result.durationMs
        currentPosition = 0L
        playbackError = null
        youtubeCommand = null
        youtubeVideoId = result.videoId
        prefetchUpcomingYouTubeMappings()
    }

    /**
     * Resolves the current item and the following items, then hands a temporary YouTube playlist
     * to NewPipe, with up to 100 video IDs in the generated queue.
     */
    fun openCurrentPlaylistInNewPipe() {
        if (isPreparingNewPipePlaylist) return
        if (filteredTracks.isEmpty()) {
            showToast("La playlist est vide.")
            return
        }
        if (!isNewPipeInstalled()) {
            showToast("NewPipe n'est pas installé sur cet appareil.", Toast.LENGTH_LONG)
            return
        }
        refreshNewPipeSearchAccess()
        if (!hasNewPipeSearchAccess) {
            requestNewPipeSearchAccess()
            return
        }

        val orderedTracks = buildNewPipeQueue().take(NewPipeManager.MAX_PLAYLIST_SIZE)
        mappingPrefetchJob?.cancel()
        newPipePlaylistJob?.cancel()
        newPipePlaylistJob =
                viewModelScope.launch {
                    isPreparingNewPipePlaylist = true
                    newPipePlaylistProgress = 0
                    newPipePlaylistTargetCount = orderedTracks.size

                    try {
                        val resolvedIds = resolveTracksInParallel(orderedTracks) {
                            newPipePlaylistProgress++
                        }
                        val videoIds = resolvedIds.filterNotNull()
                        val skipped = resolvedIds.count { it == null }

                        if (videoIds.isEmpty()) {
                            showToast("Aucun titre YouTube n'a pu être préparé.", Toast.LENGTH_LONG)
                            return@launch
                        }

                        val playlistUrl = newPipeManager.createTemporaryPlaylistUrl(videoIds)
                        pauseYouTube()
                        val newPipeHomeIntent =
                                context.packageManager
                                        .getLaunchIntentForPackage(NewPipeManager.PACKAGE_NAME)
                                        ?.apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                        }
                                        ?: throw ActivityNotFoundException()
                        val playlistIntent =
                                Intent(Intent.ACTION_VIEW, Uri.parse(playlistUrl)).apply {
                                    setClassName(
                                            NewPipeManager.PACKAGE_NAME,
                                            NewPipeManager.ROUTER_ACTIVITY
                                    )
                                    addCategory(Intent.CATEGORY_BROWSABLE)
                                }
                        try {
                            // Keep NewPipe itself underneath its transparent RouterActivity. If the
                            // user's preferred URL action is background playback, the router
                            // finishes back to NewPipe instead of revealing ShazamPlayer again.
                            context.startActivities(arrayOf(newPipeHomeIntent, playlistIntent))
                            // Stop advertising ShazamPlayer's silent YouTube queue to MBUX. NewPipe
                            // now owns playback, so car controls (including Shuffle) must target
                            // NewPipe's active media session.
                            isUsingYouTube = false
                            isUsingNewPipe = true
                            youtubeCommand = null
                            exoPlayer?.clearMediaItems()
                            val omittedByLimit =
                                    (filteredTracks.size - NewPipeManager.MAX_PLAYLIST_SIZE)
                                            .coerceAtLeast(0)
                            val details =
                                    buildList {
                                                if (skipped > 0) add("$skipped introuvable(s)")
                                                if (omittedByLimit > 0) {
                                                    add("$omittedByLimit au-delà de la limite")
                                                }
                                            }
                                            .joinToString(" · ")
                            showToast(
                                    "${videoIds.size} titre(s) envoyé(s) à NewPipe" +
                                            if (details.isBlank()) "" else " · $details",
                                    Toast.LENGTH_LONG
                            )
                        } catch (_: ActivityNotFoundException) {
                            showToast(
                                    "NewPipe n'est pas disponible. Installez la version officielle puis réessayez.",
                                    Toast.LENGTH_LONG
                            )
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: NewPipePlaylistException) {
                        showToast(error.message ?: "La playlist NewPipe n'a pas pu être créée.", Toast.LENGTH_LONG)
                    } catch (error: Exception) {
                        showToast(
                                "Envoi vers NewPipe impossible : ${error.localizedMessage ?: "erreur réseau"}",
                                Toast.LENGTH_LONG
                        )
                    } finally {
                        isPreparingNewPipePlaylist = false
                    }
                }
    }

    private fun buildNewPipeQueue(): List<Track> {
        if (filteredTracks.isEmpty()) return emptyList()
        val startIndex = currentTrackIndexInFiltered.coerceIn(0, filteredTracks.lastIndex)
        val current = filteredTracks[startIndex]
        val remaining =
                if (isShuffle) {
                    filteredTracks.filterIndexed { index, _ -> index != startIndex }.shuffled()
                } else {
                    filteredTracks.drop(startIndex + 1) + filteredTracks.take(startIndex)
                }
        return listOf(current) + remaining
    }

    private suspend fun resolveVideoIdForNewPipe(track: Track): String? {
        val selectedCurrentId =
                if (track === currentTrack) youtubeVideoId?.takeIf { VIDEO_ID.matches(it) }
                else null
        val existing = selectedCurrentId ?: track.youtubeVideoId?.takeIf { VIDEO_ID.matches(it) }
        if (existing != null) {
            youtubeMappingStore.save(track, existing, track.youtubeChannel)
            return existing
        }

        if (youtubeMappingStore.restore(track)) {
            return track.youtubeVideoId
        }

        if (!hasNewPipeSearchAccess) return null
        return try {
            val newPipeResult =
                    newPipeSearchClient.searchFirst(track.artist, track.title) ?: return null
            youtubeMappingStore.save(
                    track,
                    newPipeResult.videoId,
                    newPipeResult.channel,
                    newPipeResult.artworkUrl
            )
            refreshYouTubeMappingCount()
            newPipeResult.videoId
        } catch (_: NewPipeSearchException) {
            null
        }
    }

    private suspend fun resolveTracksInParallel(
            tracks: List<Track>,
            onResolved: () -> Unit = {}
    ): List<String?> =
            coroutineScope {
                val semaphore = Semaphore(NEWPIPE_SEARCH_CONCURRENCY)
                tracks.map { track ->
                            async {
                                semaphore.withPermit {
                                    val result = resolveVideoIdForNewPipe(track)
                                    onResolved()
                                    result
                                }
                            }
                        }
                        .awaitAll()
            }

    fun requestNewPipeSearchAccess() {
        val intent =
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
        context.startActivity(intent)
        showToast(
                "Activez ShazamPlayer, puis revenez dans l'application.",
                Toast.LENGTH_LONG
        )
    }

    fun refreshNewPipeSearchAccess() {
        val wasEnabled = hasNewPipeSearchAccess
        hasNewPipeSearchAccess =
                NotificationManagerCompat.getEnabledListenerPackages(context)
                        .contains(context.packageName)
        if (!wasEnabled && hasNewPipeSearchAccess) {
            showToast("Moteur de recherche NewPipe activé.")
            prefetchUpcomingYouTubeMappings()
        }
    }

    fun completeYouTubeCatalog() {
        refreshNewPipeSearchAccess()
        if (!hasNewPipeSearchAccess) {
            requestNewPipeSearchAccess()
            return
        }
        if (isCompletingYouTubeCatalog) return

        restoreYouTubeMappings(shazamTracks)
        val unresolved = shazamTracks.filter { it.youtubeVideoId?.matches(VIDEO_ID) != true }
        if (unresolved.isEmpty()) {
            showToast("Le catalogue YouTube est déjà complet.")
            return
        }

        mappingPrefetchJob?.cancel()
        catalogCompletionJob?.cancel()
        catalogCompletionJob =
                viewModelScope.launch {
                    isCompletingYouTubeCatalog = true
                    catalogCompletionProgress = 0
                    catalogCompletionTarget = unresolved.size
                    try {
                        unresolved.chunked(CATALOG_RESOLUTION_BATCH_SIZE).forEach { batch ->
                            resolveTracksInParallel(batch) { catalogCompletionProgress++ }
                            delay(CATALOG_BATCH_PAUSE_MS)
                        }
                        showToast(
                                "Catalogue mis à jour : $youtubeMappingCount/${shazamTracks.size}",
                                Toast.LENGTH_LONG
                        )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        showToast(
                                "Catalogue interrompu : ${error.localizedMessage ?: "erreur réseau"}",
                                Toast.LENGTH_LONG
                        )
                    } finally {
                        isCompletingYouTubeCatalog = false
                    }
                }
    }

    private fun prefetchUpcomingYouTubeMappings() {
        if (!hasNewPipeSearchAccess || isCompletingYouTubeCatalog || filteredTracks.isEmpty()) return
        val candidates =
                buildNewPipeQueue()
                        .take(NewPipeManager.MAX_PLAYLIST_SIZE)
                        .filter { track ->
                            track.youtubeVideoId?.matches(VIDEO_ID) != true &&
                                    !youtubeMappingStore.restore(track)
                        }
        if (candidates.isEmpty()) return

        mappingPrefetchJob?.cancel()
        mappingPrefetchJob =
                viewModelScope.launch {
                    runCatching { resolveTracksInParallel(candidates) }
                }
    }

    private fun restoreYouTubeMappings(tracks: List<Track>) {
        tracks.forEach(youtubeMappingStore::restore)
        refreshYouTubeMappingCount()
    }

    private fun refreshYouTubeMappingCount() {
        youtubeMappingCount = shazamTracks.count { it.youtubeVideoId?.matches(VIDEO_ID) == true }
    }

    @Suppress("DEPRECATION")
    private fun isNewPipeInstalled(): Boolean =
            runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            context.packageManager.getPackageInfo(
                                    NewPipeManager.PACKAGE_NAME,
                                    PackageManager.PackageInfoFlags.of(0)
                            )
                        } else {
                            context.packageManager.getPackageInfo(NewPipeManager.PACKAGE_NAME, 0)
                        }
                    }
                    .isSuccess

    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, message, duration).show()
    }

    fun playNext() {
        if (isRepeat) {
            seekTo(0L)
        } else if (isShuffle && filteredTracks.isNotEmpty()) {
            val nextCandidates = filteredTracks.indices.filter { it != currentTrackIndexInFiltered }
            playTrack(if (nextCandidates.isEmpty()) 0 else nextCandidates.random())
        } else {
            val next = currentTrackIndexInFiltered + 1
            if (next < filteredTracks.size) playTrack(next) else playTrack(0)
        }
    }

    fun setShuffleEnabled(enabled: Boolean) {
        isShuffle = enabled
    }

    fun toggleShuffle() {
        setShuffleEnabled(!isShuffle)
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
        if (isUsingNewPipe) {
            val shouldPlay = !isActuallyPlaying
            isActuallyPlaying = shouldPlay
            viewModelScope.launch {
                runCatching { newPipeSearchClient.setPlaying(shouldPlay) }
                        .onFailure { isActuallyPlaying = !shouldPlay }
            }
        } else if (isUsingYouTube) {
            issueYouTubeCommand(
                    if (isActuallyPlaying) YouTubePlayerAction.PAUSE else YouTubePlayerAction.PLAY
            )
        } else {
            exoPlayer?.let { if (it.isPlaying) it.pause() else it.play() }
        }
    }

    fun seekTo(position: Long) {
        if (isUsingNewPipe) {
            currentPosition = position.coerceAtLeast(0L)
            viewModelScope.launch { runCatching { newPipeSearchClient.seekTo(position) } }
        } else if (isUsingYouTube) {
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
        private const val DEFAULT_QUEUE_ITEM_DURATION_MS = 3 * 60_000L
        private const val NEWPIPE_SEARCH_CONCURRENCY = 3
        private const val CATALOG_RESOLUTION_BATCH_SIZE = 12
        private const val CATALOG_BATCH_PAUSE_MS = 250L
        private val VIDEO_ID = "[A-Za-z0-9_-]{11}".toRegex()
    }
}
