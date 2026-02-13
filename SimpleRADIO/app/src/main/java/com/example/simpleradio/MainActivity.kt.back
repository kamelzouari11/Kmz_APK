package com.example.simpleradio

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.simpleradio.cast.CastHelper
import com.example.simpleradio.data.CsvDataLoader
import com.example.simpleradio.data.RadioRepository
import com.example.simpleradio.data.api.LrcLibApi
import com.example.simpleradio.data.api.LyricsClient
import com.example.simpleradio.data.api.RadioClient
import com.example.simpleradio.data.local.AppDatabase
import com.example.simpleradio.data.local.entities.*
import com.example.simpleradio.data.model.RadioCountry
import com.example.simpleradio.data.model.RadioTag
import com.example.simpleradio.ui.components.dialogs.*
import com.example.simpleradio.ui.components.rememberDataManagement
import com.example.simpleradio.ui.screens.BrowseScreen
import com.example.simpleradio.ui.screens.VideoPlayerView
import com.example.simpleradio.ui.theme.SimpleRADIOTheme
import com.example.simpleradio.utils.MetadataHelper
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.common.util.concurrent.MoreExecutors
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Regex constants removed, moved to MetadataHelper

fun RadioStationEntity.toMediaItem(): MediaItem {
        // IMPORTANT: On ne met PAS le nom de la radio dans Title/Artist
        // car cela impacterait le listener de métadonnées.
        // On utilise uniquement Station et Album pour le système.
        val meta =
                MediaMetadata.Builder()
                        .setStation(name)
                        .setAlbumTitle(name)
                        .setArtworkUri(favicon?.toUri())
                        .build()
        return MediaItem.Builder()
                .setUri(url)
                .setMediaId(stationuuid)
                .setMediaMetadata(meta)
                .build()
}

class MainActivity : AppCompatActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)

                // Initialisation précoce du CastContext pour éviter le crash au clic
                try {
                        CastContext.getSharedInstance(this)
                } catch (_: Exception) {
                        // Exception during CastContext initialization
                }

                val database = AppDatabase.getDatabase(this)
                val radioApi = RadioClient.create()
                val csvDataLoader = CsvDataLoader(this)
                val radioRepository = RadioRepository(radioApi, database.radioDao(), csvDataLoader)
                val lrcLibApi = LyricsClient.createLrcLib()
                setContent {
                        SimpleRADIOTheme(darkTheme = true) {
                                MainScreen(radioRepository, lrcLibApi)
                        }
                }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(radioRepository: RadioRepository, lrcLibApi: LrcLibApi) {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        // --- Sauvegarde/Restauration via Helper ---
        val dataManagement = rememberDataManagement(radioRepository)

        val prefs = context.getSharedPreferences("SimpleRadioPrefs", Context.MODE_PRIVATE)

        var playingRadio by remember { mutableStateOf<RadioStationEntity?>(null) }
        var navRadioList by remember { mutableStateOf<List<RadioStationEntity>>(emptyList()) }
        var isFullScreenPlayer by remember { mutableStateOf(false) }
        var playerIsPlaying by remember { mutableStateOf(false) }

        val controllerFuture = remember {
                MediaController.Builder(
                                context,
                                SessionToken(
                                        context,
                                        ComponentName(context, PlaybackService::class.java)
                                )
                        )
                        .buildAsync()
        }
        var exoPlayer by remember { mutableStateOf<Player?>(null) }

        var sleepTimerTimeLeft by remember { mutableStateOf<Long?>(null) }
        LaunchedEffect(sleepTimerTimeLeft) {
                val timeLeft = sleepTimerTimeLeft
                if (timeLeft != null && timeLeft > 0) {
                        delay(1000)
                        sleepTimerTimeLeft = timeLeft - 1000
                        val newTimeLeft = sleepTimerTimeLeft
                        if (newTimeLeft != null && newTimeLeft <= 0) {
                                exoPlayer?.pause()
                                sleepTimerTimeLeft = null
                                Toast.makeText(
                                                context,
                                                "Mise en veille activée",
                                                Toast.LENGTH_SHORT
                                        )
                                        .show()
                        }
                }
        }

        // Metadata declared early for access in callbacks
        var currentArtist by remember { mutableStateOf<String?>(null) }
        var currentTitle by remember { mutableStateOf<String?>(null) }
        var currentArtworkUrl by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(controllerFuture) {
                controllerFuture.addListener(
                        {
                                try {
                                        exoPlayer = controllerFuture.get()
                                        // Sync initial state
                                        exoPlayer?.let { player ->
                                                playerIsPlaying = player.isPlaying
                                                player.currentMediaItem?.let { item ->
                                                        scope.launch {
                                                                val station =
                                                                        radioRepository
                                                                                .getStationByUuid(
                                                                                        item.mediaId
                                                                                )
                                                                if (station != null) {
                                                                        playingRadio = station
                                                                }
                                                        }
                                                }
                                        }
                                        exoPlayer?.addListener(
                                                object : Player.Listener {
                                                        override fun onIsPlayingChanged(
                                                                isPlaying: Boolean
                                                        ) {
                                                                playerIsPlaying = isPlaying
                                                        }
                                                        override fun onMediaItemTransition(
                                                                mediaItem: MediaItem?,
                                                                reason: Int
                                                        ) {
                                                                val newId =
                                                                        mediaItem?.mediaId ?: return
                                                                val currentList = navRadioList
                                                                val station =
                                                                        currentList.find {
                                                                                it.stationuuid ==
                                                                                        newId
                                                                        }
                                                                if (station != null) {
                                                                        if (station.stationuuid !=
                                                                                        playingRadio
                                                                                                ?.stationuuid
                                                                        ) {
                                                                                currentArtist = null
                                                                                currentTitle = null
                                                                                currentArtworkUrl =
                                                                                        null
                                                                                playingRadio =
                                                                                        station
                                                                                scope.launch {
                                                                                        radioRepository
                                                                                                .addToRecents(
                                                                                                        station.stationuuid
                                                                                                )
                                                                                        prefs.edit {
                                                                                                putString(
                                                                                                        "lastPlayedStationUuid",
                                                                                                        station.stationuuid
                                                                                                )
                                                                                        }
                                                                                }
                                                                        }
                                                                } else {
                                                                        // Fallback to database if
                                                                        // not in current list
                                                                        scope.launch {
                                                                                val fetched =
                                                                                        radioRepository
                                                                                                .getStationByUuid(
                                                                                                        newId
                                                                                                )
                                                                                if (fetched !=
                                                                                                null &&
                                                                                                fetched.stationuuid !=
                                                                                                        playingRadio
                                                                                                                ?.stationuuid
                                                                                ) {
                                                                                        currentArtist =
                                                                                                null
                                                                                        currentTitle =
                                                                                                null
                                                                                        currentArtworkUrl =
                                                                                                null
                                                                                        playingRadio =
                                                                                                fetched
                                                                                        radioRepository
                                                                                                .addToRecents(
                                                                                                        fetched.stationuuid
                                                                                                )
                                                                                }
                                                                        }
                                                                }
                                                        }
                                                        override fun onPlayerError(
                                                                error: PlaybackException
                                                        ) {
                                                                Toast.makeText(
                                                                                context,
                                                                                "Erreur de lecture : URL corrompue ou indisponible",
                                                                                Toast.LENGTH_SHORT
                                                                        )
                                                                        .show()
                                                                playingRadio = null
                                                                isFullScreenPlayer = false
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

        val listFocusRequester = remember { FocusRequester() }
        LaunchedEffect(isFullScreenPlayer) {
                if (!isFullScreenPlayer) {
                        delay(500)
                        try {
                                listFocusRequester.requestFocus()
                        } catch (_: Exception) {}
                }
        }

        DisposableEffect(Unit) { onDispose { MediaController.releaseFuture(controllerFuture) } }

        // --- RADIO STATE ---
        val radioFavoriteLists by
                radioRepository.allFavoriteLists.collectAsState(initial = emptyList())
        val recentRadios by radioRepository.recentRadios.collectAsState(initial = emptyList())
        var radioCountries by remember { mutableStateOf<List<RadioCountry>>(emptyList()) }

        var castSession by remember { mutableStateOf<CastSession?>(null) }
        val castHelper = remember {
                var tempHelper: CastHelper? = null
                tempHelper =
                        CastHelper(
                                context = context,
                                onSessionStatusChanged = { castSession = it },
                                onSessionStarted = { session ->
                                        playingRadio?.let { radio ->
                                                tempHelper?.loadMedia(
                                                        session,
                                                        radio,
                                                        currentArtist,
                                                        currentTitle,
                                                        currentArtworkUrl
                                                )
                                                try {
                                                        exoPlayer?.volume = 0f
                                                } catch (_: Exception) {}
                                        }
                                }
                        )
                tempHelper
        }
        LaunchedEffect(Unit) {
                if (castHelper.initCast()) {
                        try {
                                CastContext.getSharedInstance(context)
                                        .sessionManager
                                        .addSessionManagerListener(
                                                castHelper.sessionManagerListener,
                                                CastSession::class.java
                                        )
                        } catch (_: Exception) {}
                }
        }

        var radioTags by remember { mutableStateOf<List<RadioTag>>(emptyList()) }
        var radioStations by remember { mutableStateOf<List<RadioStationEntity>>(emptyList()) }

        var selectedRadioCountry by remember {
                mutableStateOf(prefs.getString("selectedRadioCountry", null))
        }
        var selectedRadioTag by remember {
                mutableStateOf(prefs.getString("selectedRadioTag", null))
        }
        var selectedRadioBitrate by remember {
                val saved = prefs.getInt("selectedRadioBitrate", -1)
                mutableStateOf(if (saved == -1) null else saved)
        }
        var selectedRadioFavoriteListId by remember {
                val saved = prefs.getInt("selectedRadioFavoriteListId", -1)
                mutableStateOf(if (saved == -1) null else saved)
        }
        var showRecentRadiosOnly by remember {
                mutableStateOf(prefs.getBoolean("showRecentRadiosOnly", false))
        }
        var radioSearchQuery by remember {
                mutableStateOf(prefs.getString("radioSearchQuery", "") ?: "")
        }

        // Persist changes
        LaunchedEffect(selectedRadioCountry) {
                prefs.edit { putString("selectedRadioCountry", selectedRadioCountry) }
        }
        LaunchedEffect(selectedRadioTag) {
                prefs.edit { putString("selectedRadioTag", selectedRadioTag) }
        }
        LaunchedEffect(selectedRadioBitrate) {
                if (selectedRadioBitrate != null)
                        prefs.edit { putInt("selectedRadioBitrate", selectedRadioBitrate!!) }
                else prefs.edit { remove("selectedRadioBitrate") }
        }
        LaunchedEffect(selectedRadioFavoriteListId) {
                if (selectedRadioFavoriteListId != null)
                        prefs.edit {
                                putInt("selectedRadioFavoriteListId", selectedRadioFavoriteListId!!)
                        }
                else prefs.edit { remove("selectedRadioFavoriteListId") }
        }
        LaunchedEffect(showRecentRadiosOnly) {
                prefs.edit { putBoolean("showRecentRadiosOnly", showRecentRadiosOnly) }
        }
        LaunchedEffect(radioSearchQuery) {
                prefs.edit { putString("radioSearchQuery", radioSearchQuery) }
        }

        var searchTrigger by rememberSaveable { mutableIntStateOf(0) }
        var radioSortOrder by remember { mutableStateOf("clickcount") }

        var isQualityExpanded by remember { mutableStateOf(false) }
        var isCountryExpanded by remember { mutableStateOf(false) }
        var isGenreExpanded by remember { mutableStateOf(false) }
        var isViewingRadioResults by rememberSaveable { mutableStateOf(false) }
        var showLyricsGlobal by remember { mutableStateOf(false) }
        var showAddCustomUrlDialog by remember { mutableStateOf(false) }
        var showSearchDialog by remember { mutableStateOf(false) }

        val resultsListState = rememberLazyListState()

        // Load cached list on startup AND Restore Last Played
        LaunchedEffect(Unit) {
                val cached =
                        radioRepository.loadCacheList(
                                File(context.filesDir, "last_radio_list.json")
                        )
                if (cached.isNotEmpty()) {
                        radioStations = cached
                        isViewingRadioResults = true // Restore list view

                        // Restore Last Played Radio
                        val lastUuid = prefs.getString("lastPlayedStationUuid", null)
                        val lastRadio =
                                if (lastUuid != null) cached.find { it.stationuuid == lastUuid }
                                else null

                        if (lastRadio != null) {
                                playingRadio = lastRadio
                                navRadioList = cached // Restore navigation context
                        }
                }
        }

        // Auto-Play on Startup if restored
        LaunchedEffect(exoPlayer, playingRadio) {
                if (exoPlayer != null && playingRadio != null) {
                        val player = exoPlayer!!
                        // Only start if player is empty (cold start)
                        if (player.currentMediaItem == null) {
                                val snapshot = navRadioList
                                if (snapshot.isNotEmpty()) {
                                        val mediaItems = snapshot.map { it.toMediaItem() }
                                        val startIndex =
                                                snapshot.indexOfFirst {
                                                        it.stationuuid == playingRadio!!.stationuuid
                                                }
                                        if (startIndex != -1) {
                                                player.setMediaItems(mediaItems, startIndex, 0L)
                                                player.prepare()
                                                player.play()
                                        }
                                }
                        }
                }
        }

        // --- NAVIGATION BACK (Global Niv3 > Niv2 > Niv1 > Niv0) ---
        BackHandler(enabled = true) {
                if (showLyricsGlobal) {
                        showLyricsGlobal = false
                } else if (isFullScreenPlayer) {
                        isFullScreenPlayer = false
                } else {
                        // Niv 0: Quitter (Background play) - La liste ne doit jamais être effacée
                        (context as? Activity)?.moveTaskToBack(true)
                }
        }

        // --- HELPER PLAYLIST ---

        val favFlow =
                remember(selectedRadioFavoriteListId) {
                        if (selectedRadioFavoriteListId != null) {
                                radioRepository.getRadiosByFavoriteList(
                                        selectedRadioFavoriteListId!!
                                )
                        } else {
                                kotlinx.coroutines.flow.flowOf(emptyList())
                        }
                }
        val radioFavStations by favFlow.collectAsState(initial = emptyList())
        val currentRadioList =
                when {
                        selectedRadioFavoriteListId != null -> radioFavStations
                        showRecentRadiosOnly -> recentRadios
                        else -> radioStations // Toujours afficher radioStations par défaut
                }

        LaunchedEffect(isViewingRadioResults, playingRadio) {
                if (isViewingRadioResults && playingRadio != null) {
                        try {
                                // If the item index is not -1, scroll to it.
                                val index =
                                        currentRadioList.indexOfFirst {
                                                it.stationuuid == playingRadio?.stationuuid
                                        }
                                if (index != -1) {
                                        resultsListState.scrollToItem(index)
                                }
                        } catch (_: Exception) {}
                }
        }

        var radioToFavorite by remember { mutableStateOf<RadioStationEntity?>(null) }
        var showAddListDialog by remember { mutableStateOf(false) }

        // --- METADATA GLOBALE (Lifted) ---
        // Variables déplacées en haut de MainScreen

        // On observe les changements du player au niveau global pour la TV et le Bluetooth
        // On observe les changements du player au niveau global pour la TV et le Bluetooth
        DisposableEffect(exoPlayer, playingRadio) {
                val listener =
                        MetadataHelper.createMetadataListener(
                                playingRadioName = playingRadio?.name,
                                playingRadioCountry = playingRadio?.country,
                                onMetadataFound = { artist, title ->
                                        currentArtist = artist
                                        currentTitle = title
                                }
                        )
                exoPlayer?.addListener(listener)
                onDispose { exoPlayer?.removeListener(listener) }
        }

        // Recherche automatique de pochette (globale)
        LaunchedEffect(currentArtist, currentTitle) {
                currentArtworkUrl = MetadataHelper.fetchArtwork(currentArtist, currentTitle)
        }

        // --- SYNC CHROMECAST GLOBALE ---
        LaunchedEffect(playingRadio?.stationuuid, currentArtist, currentTitle, currentArtworkUrl) {
                if (castSession != null && castSession!!.isConnected) {
                        val radio = playingRadio ?: return@LaunchedEffect
                        delay(800)
                        castHelper.loadMedia(
                                castSession!!,
                                radio,
                                currentArtist,
                                currentTitle,
                                currentArtworkUrl
                        )
                        try {
                                exoPlayer?.volume = 0f
                        } catch (_: Exception) {}
                }

                // --- SYNC SERVICE / BLUETOOTH / NOTIFICATION ---
                (exoPlayer as? MediaController)?.let { controller ->
                        MetadataHelper.sendUpdateMetadataCommand(
                                controller,
                                currentTitle,
                                currentArtist,
                                playingRadio?.name,
                                currentArtworkUrl,
                                playingRadio?.favicon
                        )
                }
        }
        var isLoading by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
                if (radioCountries.isEmpty()) {
                        isLoading = true
                        try {
                                radioCountries =
                                        radioRepository.getCountries().sortedByDescending {
                                                it.stationcount
                                        }
                                radioTags =
                                        radioRepository
                                                .getTags()
                                                .sortedByDescending { it.stationcount }
                                                .filter { it.stationcount > 10 }
                                                .take(100)
                        } catch (_: Exception) {}
                        isLoading = false
                        // REMOVED: isViewingRadioResults = false
                        // This was clearing the view state on every app launch, even if we
                        // wanted
                        // to restore
                        // it.
                }
        }

        LaunchedEffect(searchTrigger, selectedRadioFavoriteListId, showRecentRadiosOnly) {
                if (!showRecentRadiosOnly && selectedRadioFavoriteListId == null) {
                        isLoading = true
                        Log.d(
                                "SimpleRADIO",
                                "Loading stations: country=$selectedRadioCountry, tag=$selectedRadioTag, bitrate=$selectedRadioBitrate"
                        )
                        try {
                                val bitrateMax =
                                        when (selectedRadioBitrate) {
                                                0 -> 63
                                                64 -> 127
                                                128 -> 191
                                                else -> null
                                        }
                                radioStations =
                                        radioRepository.searchStations(
                                                selectedRadioCountry,
                                                selectedRadioTag,
                                                radioSearchQuery.takeIf { it.isNotBlank() },
                                                selectedRadioBitrate,
                                                bitrateMax,
                                                radioSortOrder
                                        )
                                Log.d("SimpleRADIO", "Loaded ${radioStations.size} stations")
                                // Save to cache
                                radioRepository.saveCacheList(
                                        File(context.filesDir, "last_radio_list.json"),
                                        radioStations
                                )
                        } catch (e: Exception) {
                                radioStations = emptyList()
                                Log.e("SimpleRADIO", "Error loading stations", e)
                        }
                        isLoading = false
                }
        }

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(modifier = Modifier.fillMaxSize()) {
                        if (!isFullScreenPlayer) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                        Card(
                                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                                colors =
                                                        CardDefaults.cardColors(
                                                                containerColor =
                                                                        MaterialTheme.colorScheme
                                                                                .background
                                                        )
                                        ) {
                                                Column(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(top = 12.dp)
                                                                        .padding(12.dp)
                                                ) {
                                                        // États de focus pour la toolbar

                                                        // LIGNE 1: Icône 48dp + Upload + Download +
                                                        // Power
                                                        Row(
                                                                modifier =
                                                                        Modifier.fillMaxWidth()
                                                                                .padding(
                                                                                        bottom =
                                                                                                8.dp
                                                                                ),
                                                                verticalAlignment =
                                                                        Alignment.CenterVertically,
                                                                horizontalArrangement =
                                                                        Arrangement.SpaceBetween
                                                        ) {
                                                                // Icône Radio 48dp
                                                                Icon(
                                                                        Icons.Default.Radio,
                                                                        "SimpleRADIO",
                                                                        tint = Color.White,
                                                                        modifier =
                                                                                Modifier.size(48.dp)
                                                                )

                                                                // Bouton Upload
                                                                var isUploadFocused by remember {
                                                                        mutableStateOf(false)
                                                                }
                                                                IconButton(
                                                                        onClick = {
                                                                                dataManagement
                                                                                        .exportFavorites()
                                                                        },
                                                                        modifier =
                                                                                Modifier.size(48.dp)
                                                                                        .onFocusChanged {
                                                                                                isUploadFocused =
                                                                                                        it.isFocused
                                                                                        }
                                                                                        .background(
                                                                                                if (isUploadFocused
                                                                                                )
                                                                                                        Color.White
                                                                                                else
                                                                                                        Color.Transparent,
                                                                                                CircleShape
                                                                                        )
                                                                ) {
                                                                        Icon(
                                                                                Icons.Default
                                                                                        .CloudUpload,
                                                                                "Upload",
                                                                                tint =
                                                                                        if (isUploadFocused
                                                                                        )
                                                                                                Color.Black
                                                                                        else
                                                                                                Color.White
                                                                        )
                                                                }

                                                                // Bouton Download
                                                                var isDownloadFocused by remember {
                                                                        mutableStateOf(false)
                                                                }
                                                                IconButton(
                                                                        onClick = {
                                                                                dataManagement
                                                                                        .importFavorites()
                                                                        },
                                                                        modifier =
                                                                                Modifier.size(48.dp)
                                                                                        .onFocusChanged {
                                                                                                isDownloadFocused =
                                                                                                        it.isFocused
                                                                                        }
                                                                                        .background(
                                                                                                if (isDownloadFocused
                                                                                                )
                                                                                                        Color.White
                                                                                                else
                                                                                                        Color.Transparent,
                                                                                                CircleShape
                                                                                        )
                                                                ) {
                                                                        Icon(
                                                                                Icons.Default
                                                                                        .CloudDownload,
                                                                                "Download",
                                                                                tint =
                                                                                        if (isDownloadFocused
                                                                                        )
                                                                                                Color.Black
                                                                                        else
                                                                                                Color.White
                                                                        )
                                                                }

                                                                // Bouton Power
                                                                var isPowerFocused by remember {
                                                                        mutableStateOf(false)
                                                                }
                                                                IconButton(
                                                                        onClick = {
                                                                                exoPlayer?.stop()
                                                                                context.stopService(
                                                                                        Intent(
                                                                                                context,
                                                                                                PlaybackService::class
                                                                                                        .java
                                                                                        )
                                                                                )
                                                                                (context as?
                                                                                                Activity)
                                                                                        ?.finish()
                                                                        },
                                                                        modifier =
                                                                                Modifier.size(48.dp)
                                                                                        .onFocusChanged {
                                                                                                isPowerFocused =
                                                                                                        it.isFocused
                                                                                        }
                                                                                        .background(
                                                                                                if (isPowerFocused
                                                                                                )
                                                                                                        Color.White
                                                                                                else
                                                                                                        Color.Transparent,
                                                                                                CircleShape
                                                                                        )
                                                                ) {
                                                                        Icon(
                                                                                Icons.Default
                                                                                        .PowerSettingsNew,
                                                                                "Quitter",
                                                                                tint =
                                                                                        if (isPowerFocused
                                                                                        )
                                                                                                Color.Red
                                                                                        else
                                                                                                Color.Red
                                                                        )
                                                                }
                                                        }

                                                        // LIGNE 2: Recent + Refresh + Player (avec
                                                        // texte pour Recent/Refresh)
                                                        Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                verticalAlignment =
                                                                        Alignment.CenterVertically,
                                                                horizontalArrangement =
                                                                        Arrangement.SpaceBetween
                                                        ) {
                                                                Row(
                                                                        horizontalArrangement =
                                                                                Arrangement
                                                                                        .spacedBy(
                                                                                                8.dp
                                                                                        ),
                                                                        verticalAlignment =
                                                                                Alignment
                                                                                        .CenterVertically
                                                                ) {
                                                                        // Bouton Recent (pillule
                                                                        // avec
                                                                        // texte)
                                                                        var isRecentFocused by remember {
                                                                                mutableStateOf(
                                                                                        false
                                                                                )
                                                                        }
                                                                        Surface(
                                                                                onClick = {
                                                                                        showRecentRadiosOnly =
                                                                                                true
                                                                                        selectedRadioCountry =
                                                                                                null
                                                                                        selectedRadioTag =
                                                                                                null
                                                                                        selectedRadioBitrate =
                                                                                                null
                                                                                        radioSearchQuery =
                                                                                                ""
                                                                                        isViewingRadioResults =
                                                                                                true
                                                                                        searchTrigger++
                                                                                },
                                                                                modifier =
                                                                                        Modifier.height(
                                                                                                        48.dp
                                                                                                )
                                                                                                .onFocusChanged {
                                                                                                        isRecentFocused =
                                                                                                                it.isFocused
                                                                                                },
                                                                                color =
                                                                                        if (isRecentFocused
                                                                                        )
                                                                                                Color.White
                                                                                        else
                                                                                                Color.Transparent,
                                                                                shape =
                                                                                        RoundedCornerShape(
                                                                                                24.dp
                                                                                        )
                                                                        ) {
                                                                                Row(
                                                                                        modifier =
                                                                                                Modifier.padding(
                                                                                                        horizontal =
                                                                                                                16.dp
                                                                                                ),
                                                                                        verticalAlignment =
                                                                                                Alignment
                                                                                                        .CenterVertically
                                                                                ) {
                                                                                        Icon(
                                                                                                Icons.Default
                                                                                                        .History,
                                                                                                "Récents",
                                                                                                tint =
                                                                                                        if (isRecentFocused
                                                                                                        )
                                                                                                                Color.Black
                                                                                                        else
                                                                                                                Color.White
                                                                                        )
                                                                                        Spacer(
                                                                                                Modifier.width(
                                                                                                        8.dp
                                                                                                )
                                                                                        )
                                                                                        Text(
                                                                                                "Récents",
                                                                                                color =
                                                                                                        if (isRecentFocused
                                                                                                        )
                                                                                                                Color.Black
                                                                                                        else
                                                                                                                Color.White,
                                                                                                fontWeight =
                                                                                                        FontWeight
                                                                                                                .Bold
                                                                                        )
                                                                                }
                                                                        }
                                                                }

                                                                // Bouton Refresh (pillule avec
                                                                // texte)
                                                                var isRefreshFocused by remember {
                                                                        mutableStateOf(false)
                                                                }
                                                                Surface(
                                                                        onClick = {
                                                                                scope.launch {
                                                                                        isLoading =
                                                                                                true
                                                                                        try {
                                                                                                // Réinitialiser tous les filtres
                                                                                                selectedRadioCountry =
                                                                                                        null
                                                                                                selectedRadioTag =
                                                                                                        null
                                                                                                selectedRadioBitrate =
                                                                                                        null
                                                                                                radioSearchQuery =
                                                                                                        ""

                                                                                                // Actualiser les pays et tags
                                                                                                radioCountries =
                                                                                                        radioRepository
                                                                                                                .getCountries()
                                                                                                                .sortedByDescending {
                                                                                                                        it.stationcount
                                                                                                                }
                                                                                                                .take(
                                                                                                                        100
                                                                                                                )
                                                                                                radioTags =
                                                                                                        radioRepository
                                                                                                                .getTags()
                                                                                                                .sortedByDescending {
                                                                                                                        it.stationcount
                                                                                                                }
                                                                                                                .take(
                                                                                                                        100
                                                                                                                )

                                                                                                // Charger toutes les stations par popularité
                                                                                                radioStations =
                                                                                                        radioRepository
                                                                                                                .searchStations(
                                                                                                                        null, // pas de filtre pays
                                                                                                                        null, // pas de filtre tag
                                                                                                                        null, // pas de recherche
                                                                                                                        null, // pas de bitrate min
                                                                                                                        null, // pas de bitrate max
                                                                                                                        radioSortOrder
                                                                                                                )
                                                                                                isViewingRadioResults =
                                                                                                        true
                                                                                        } catch (
                                                                                                _:
                                                                                                        Exception) {}
                                                                                        isLoading =
                                                                                                false
                                                                                }
                                                                        },
                                                                        modifier =
                                                                                Modifier.height(
                                                                                                48.dp
                                                                                        )
                                                                                        .onFocusChanged {
                                                                                                isRefreshFocused =
                                                                                                        it.isFocused
                                                                                        },
                                                                        color =
                                                                                if (isRefreshFocused
                                                                                )
                                                                                        Color.White
                                                                                else
                                                                                        Color.Transparent,
                                                                        shape =
                                                                                RoundedCornerShape(
                                                                                        24.dp
                                                                                )
                                                                ) {
                                                                        Row(
                                                                                modifier =
                                                                                        Modifier.padding(
                                                                                                horizontal =
                                                                                                        16.dp
                                                                                        ),
                                                                                verticalAlignment =
                                                                                        Alignment
                                                                                                .CenterVertically
                                                                        ) {
                                                                                Icon(
                                                                                        Icons.Default
                                                                                                .Refresh,
                                                                                        "Refresh",
                                                                                        tint =
                                                                                                if (isRefreshFocused
                                                                                                )
                                                                                                        Color.Black
                                                                                                else
                                                                                                        Color.White
                                                                                )
                                                                                Spacer(
                                                                                        Modifier.width(
                                                                                                8.dp
                                                                                        )
                                                                                )
                                                                                Text(
                                                                                        "Refresh",
                                                                                        color =
                                                                                                if (isRefreshFocused
                                                                                                )
                                                                                                        Color.Black
                                                                                                else
                                                                                                        Color.White,
                                                                                        fontWeight =
                                                                                                FontWeight
                                                                                                        .Bold
                                                                                )
                                                                        }
                                                                }

                                                                // Bouton Player (icône seulement)
                                                                if (playingRadio != null) {
                                                                        IconButton(
                                                                                onClick = {
                                                                                        if (!isViewingRadioResults
                                                                                        )
                                                                                                isViewingRadioResults =
                                                                                                        true
                                                                                        else if (!isFullScreenPlayer
                                                                                        )
                                                                                                isFullScreenPlayer =
                                                                                                        true
                                                                                },
                                                                                modifier =
                                                                                        Modifier.size(
                                                                                                48.dp
                                                                                        )
                                                                        ) {
                                                                                Icon(
                                                                                        if (playerIsPlaying
                                                                                        )
                                                                                                Icons.Filled
                                                                                                        .PlayCircleFilled
                                                                                        else
                                                                                                Icons.Default
                                                                                                        .PlayCircleOutline,
                                                                                        "Player",
                                                                                        tint =
                                                                                                Color.White
                                                                                )
                                                                        }
                                                                } else {
                                                                        Spacer(Modifier.size(48.dp))
                                                                }
                                                        }
                                                }
                                        }

                                        if (isLoading)
                                                LinearProgressIndicator(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(horizontal = 8.dp)
                                                )

                                        BrowseScreen(
                                                isPortrait =
                                                        LocalConfiguration.current.orientation ==
                                                                Configuration.ORIENTATION_PORTRAIT,
                                                radioCountries = radioCountries,
                                                radioTags = radioTags,
                                                currentRadioList = currentRadioList,
                                                selectedRadioCountry = selectedRadioCountry,
                                                selectedRadioTag = selectedRadioTag,
                                                selectedRadioBitrate = selectedRadioBitrate,
                                                radioSearchQuery = radioSearchQuery,
                                                isQualityExpanded = isQualityExpanded,
                                                isCountryExpanded = isCountryExpanded,
                                                isGenreExpanded = isGenreExpanded,
                                                isViewingRadioResults = isViewingRadioResults,
                                                playingRadio = playingRadio,
                                                listFocusRequester = listFocusRequester,
                                                resultsListState = resultsListState,
                                                onCountrySelected = { selectedRadioCountry = it },
                                                onTagSelected = { selectedRadioTag = it },
                                                onBitrateSelected = { selectedRadioBitrate = it },
                                                onToggleQualityExpanded = {
                                                        isQualityExpanded = !isQualityExpanded
                                                },
                                                onToggleCountryExpanded = {
                                                        isCountryExpanded = !isCountryExpanded
                                                },
                                                onToggleGenreExpanded = {
                                                        isGenreExpanded = !isGenreExpanded
                                                },
                                                onToggleViewingResults = { shouldView ->
                                                        isViewingRadioResults = shouldView
                                                },
                                                onRadioSelected = { radio ->
                                                        currentArtist = null
                                                        currentTitle = null
                                                        currentArtworkUrl = null
                                                        playingRadio = radio
                                                        val snapshot = currentRadioList.toList()
                                                        navRadioList = snapshot
                                                        exoPlayer?.let { player ->
                                                                try {
                                                                        player.volume =
                                                                                if (castSession !=
                                                                                                null &&
                                                                                                castSession!!
                                                                                                        .isConnected
                                                                                )
                                                                                        0f
                                                                                else 1f
                                                                        val mediaItems =
                                                                                snapshot.map {
                                                                                        it.toMediaItem()
                                                                                }
                                                                        val startIdx =
                                                                                snapshot
                                                                                        .indexOfFirst {
                                                                                                it.stationuuid ==
                                                                                                        radio.stationuuid
                                                                                        }
                                                                        if (startIdx != -1) {
                                                                                player.setMediaItems(
                                                                                        mediaItems,
                                                                                        startIdx,
                                                                                        0L
                                                                                )
                                                                                player.prepare()
                                                                                player.play()
                                                                        }
                                                                } catch (_: Exception) {}
                                                        }
                                                        isFullScreenPlayer = true
                                                        scope.launch {
                                                                radioRepository.addToRecents(
                                                                        radio.stationuuid
                                                                )
                                                        }
                                                },
                                                onAddFavorite = { radioToFavorite = it },
                                                onResetFilters = {
                                                        selectedRadioCountry = null
                                                        selectedRadioTag = null
                                                        selectedRadioBitrate = null
                                                        radioSearchQuery = ""
                                                        showRecentRadiosOnly = false
                                                        isViewingRadioResults = false
                                                        searchTrigger++
                                                },
                                                onSearchClick = { showSearchDialog = true },
                                                onApplyFilters = {
                                                        isViewingRadioResults = true
                                                        searchTrigger++
                                                }
                                        )
                                }
                        }

                        if (isFullScreenPlayer && playingRadio != null && exoPlayer != null) {
                                VideoPlayerView(
                                        exoPlayer = exoPlayer!!,
                                        onBack = { isFullScreenPlayer = false },
                                        radioStation = playingRadio,
                                        radioList = navRadioList,
                                        lrcLibApi = lrcLibApi,
                                        castSession = castSession,
                                        artist = currentArtist,
                                        title = currentTitle,
                                        artworkUrl = currentArtworkUrl,
                                        sleepTimerTimeLeft = sleepTimerTimeLeft,
                                        onSetSleepTimer = { mins: Int? ->
                                                sleepTimerTimeLeft =
                                                        mins?.let { it.toLong() * 60L * 1000L }
                                        },
                                        showLyrics = showLyricsGlobal,
                                        onToggleLyrics = { show: Boolean ->
                                                showLyricsGlobal = show
                                        }
                                )
                        }

                        if (showAddCustomUrlDialog) {
                                AddCustomUrlDialog(
                                        onDismiss = { showAddCustomUrlDialog = false },
                                        onConfirm = { name, url ->
                                                scope.launch {
                                                        radioRepository.addCustomRadio(name, url)
                                                        Toast.makeText(
                                                                        context,
                                                                        "Ajouté à 'mes urls'",
                                                                        Toast.LENGTH_SHORT
                                                                )
                                                                .show()
                                                }
                                                showAddCustomUrlDialog = false
                                        }
                                )
                        }

                        if (showAddListDialog) {
                                AddFavoriteListDialog(
                                        onDismiss = { showAddListDialog = false },
                                        onConfirm = { name ->
                                                scope.launch {
                                                        radioRepository.addFavoriteList(name)
                                                }
                                                showAddListDialog = false
                                        }
                                )
                        }

                        if (showSearchDialog) {
                                SearchDialog(
                                        initialQuery = radioSearchQuery,
                                        onDismiss = { showSearchDialog = false },
                                        onSearch = { query ->
                                                radioSearchQuery = query
                                                showRecentRadiosOnly = false
                                                // La recherche conserve les filtres actuels
                                                // (country, tag, bitrate)
                                                isViewingRadioResults = true
                                                searchTrigger++
                                                showSearchDialog = false
                                        }
                                )
                        }

                        if (radioToFavorite != null) {
                                GenericFavoriteDialog(
                                        title = "Favoris Radio",
                                        items = radioFavoriteLists,
                                        getName = { it.name },
                                        getId = { it.id },
                                        onDismiss = { radioToFavorite = null },
                                        onToggle = { listId ->
                                                scope.launch {
                                                        radioRepository.toggleRadioFavorite(
                                                                radioToFavorite!!.stationuuid,
                                                                listId
                                                        )
                                                }
                                        },
                                        selectedIdsProvider = {
                                                radioRepository.getListIdsForRadio(
                                                        radioToFavorite!!.stationuuid
                                                )
                                        }
                                )
                        }
                }
        }
}
