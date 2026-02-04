package com.example.simpleradio

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import androidx.mediarouter.app.MediaRouteButton
import coil.compose.AsyncImage
import com.example.simpleradio.data.RadioRepository
import com.example.simpleradio.data.api.ImageScraper
import com.example.simpleradio.data.api.LrcLibApi
import com.example.simpleradio.data.api.LyricsClient
import com.example.simpleradio.data.api.RadioClient
import com.example.simpleradio.data.api.TranslationApi
import com.example.simpleradio.data.local.AppDatabase
import com.example.simpleradio.data.local.entities.*
import com.example.simpleradio.data.model.RadioCountry
import com.example.simpleradio.data.model.RadioTag
import com.example.simpleradio.ui.theme.SimpleRADIOTheme
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata as CastMediaMetadata
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.images.WebImage
import com.google.common.util.concurrent.MoreExecutors
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                val radioRepository = RadioRepository(radioApi, database.radioDao())
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

        // --- Launchers pour Sauvegarde/Restauration ---
        val exportLauncher =
                rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("application/json")
                ) { uri ->
                        uri?.let {
                                scope.launch {
                                        try {
                                                val json = radioRepository.exportFavoritesToJson()
                                                context.contentResolver.openOutputStream(it)?.use {
                                                        out ->
                                                        out.write(json.toByteArray())
                                                }
                                                Toast.makeText(
                                                                context,
                                                                "Favoris exportés avec succès !",
                                                                Toast.LENGTH_SHORT
                                                        )
                                                        .show()
                                        } catch (e: Exception) {
                                                Toast.makeText(
                                                                context,
                                                                "Erreur export : ${e.message}",
                                                                Toast.LENGTH_LONG
                                                        )
                                                        .show()
                                        }
                                }
                        }
                }

        val importLauncher =
                rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                        uri?.let {
                                scope.launch {
                                        try {
                                                val json =
                                                        context.contentResolver
                                                                .openInputStream(it)
                                                                ?.bufferedReader()
                                                                ?.use { reader ->
                                                                        reader.readText()
                                                                }
                                                if (json != null) {
                                                        radioRepository.importFavoritesFromJson(
                                                                json
                                                        )
                                                        Toast.makeText(
                                                                        context,
                                                                        "Favoris importés ! Redémarrage conseillé.",
                                                                        Toast.LENGTH_SHORT
                                                                )
                                                                .show()
                                                }
                                        } catch (e: Exception) {
                                                Toast.makeText(
                                                                context,
                                                                "Erreur import : ${e.message}",
                                                                Toast.LENGTH_LONG
                                                        )
                                                        .show()
                                        }
                                }
                        }
                }

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

        var isCastAvailable by remember { mutableStateOf(false) }
        var castSession by remember { mutableStateOf<CastSession?>(null) }

        LaunchedEffect(Unit) {
                try {
                        if (GoogleApiAvailability.getInstance()
                                        .isGooglePlayServicesAvailable(context) ==
                                        ConnectionResult.SUCCESS
                        ) {
                                val castContext =
                                        try {
                                                CastContext.getSharedInstance(context)
                                        } catch (_: Exception) {
                                                null
                                        }
                                if (castContext != null) {
                                        isCastAvailable = true
                                        castContext.sessionManager.addSessionManagerListener(
                                                object : SessionManagerListener<CastSession> {
                                                        override fun onSessionStarted(
                                                                session: CastSession,
                                                                sessionId: String
                                                        ) {
                                                                castSession = session
                                                                playingRadio?.let { radio ->
                                                                        val mediaInfo =
                                                                                MediaInfo.Builder(
                                                                                                radio.url
                                                                                        )
                                                                                        .setStreamType(
                                                                                                MediaInfo
                                                                                                        .STREAM_TYPE_LIVE
                                                                                        )
                                                                                        .setContentType(
                                                                                                "audio/mpeg"
                                                                                        )
                                                                                        .setMetadata(
                                                                                                CastMediaMetadata(
                                                                                                                CastMediaMetadata
                                                                                                                        .MEDIA_TYPE_MUSIC_TRACK
                                                                                                        )
                                                                                                        .apply {
                                                                                                                putString(
                                                                                                                        CastMediaMetadata
                                                                                                                                .KEY_TITLE,
                                                                                                                        radio.name
                                                                                                                )
                                                                                                                putString(
                                                                                                                        CastMediaMetadata
                                                                                                                                .KEY_ARTIST,
                                                                                                                        radio.country
                                                                                                                                ?: ""
                                                                                                                )
                                                                                                                radio.favicon
                                                                                                                        ?.let {
                                                                                                                                addImage(
                                                                                                                                        WebImage(
                                                                                                                                                it.toUri()
                                                                                                                                        )
                                                                                                                                )
                                                                                                                        }
                                                                                                        }
                                                                                        )
                                                                                        .build()
                                                                        session.remoteMediaClient
                                                                                ?.load(
                                                                                        MediaLoadRequestData
                                                                                                .Builder()
                                                                                                .setMediaInfo(
                                                                                                        mediaInfo
                                                                                                )
                                                                                                .build()
                                                                                )
                                                                        try {
                                                                                exoPlayer?.volume =
                                                                                        0f
                                                                        } catch (_: Exception) {}
                                                                }
                                                        }
                                                        override fun onSessionEnded(
                                                                session: CastSession,
                                                                error: Int
                                                        ) {
                                                                castSession = null
                                                                try {
                                                                        exoPlayer?.volume = 1f
                                                                } catch (_: Exception) {}
                                                        }
                                                        override fun onSessionResumed(
                                                                session: CastSession,
                                                                wasSuspended: Boolean
                                                        ) {
                                                                castSession = session
                                                        }
                                                        override fun onSessionStarting(
                                                                session: CastSession
                                                        ) {}
                                                        override fun onSessionStartFailed(
                                                                session: CastSession,
                                                                error: Int
                                                        ) {}
                                                        override fun onSessionEnding(
                                                                session: CastSession
                                                        ) {}
                                                        override fun onSessionResumeFailed(
                                                                session: CastSession,
                                                                error: Int
                                                        ) {}
                                                        override fun onSessionResuming(
                                                                session: CastSession,
                                                                sessionId: String
                                                        ) {}
                                                        override fun onSessionSuspended(
                                                                session: CastSession,
                                                                reason: Int
                                                        ) {}
                                                },
                                                CastSession::class.java
                                        )
                                }
                        }
                } catch (_: Exception) {
                        isCastAvailable = false
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
        var sidebarCountrySearch by remember { mutableStateOf("") }
        var sidebarTagSearch by remember { mutableStateOf("") }

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

        val qualityOptions =
                listOf(
                        "Qualité" to null,
                        "Basse (< 64k)" to 0,
                        "Moyenne (64-128k)" to 64,
                        "Haute (128-192k)" to 128,
                        "Très Haute (> 192k)" to 192
                )

        // --- NAVIGATION BACK (Global Niv3 > Niv2 > Niv1 > Niv0) ---
        BackHandler(enabled = true) {
                if (showLyricsGlobal) {
                        showLyricsGlobal = false
                } else if (isFullScreenPlayer) {
                        isFullScreenPlayer = false
                } else if (isViewingRadioResults) {
                        isViewingRadioResults = false
                } else {
                        // Niv 0: Quitter (Background play)
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
                        isViewingRadioResults -> radioStations
                        else -> emptyList()
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
        LaunchedEffect(exoPlayer) {
                exoPlayer?.addListener(
                        object : Player.Listener {
                                override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                                        val rawTitle = mediaMetadata.title?.toString()
                                        val rawArtist = mediaMetadata.artist?.toString()

                                        // Si pas de métadonnées du flux, on ne touche pas aux
                                        // variables
                                        // If no stream metadata, we don't touch the variables
                                        if (rawTitle.isNullOrBlank() && rawArtist.isNullOrBlank()) {
                                                return
                                        }

                                        val stationName = playingRadio?.name ?: ""
                                        val country = playingRadio?.country ?: ""

                                        fun clean(s: String): String {
                                                var res =
                                                        s.replace(
                                                                        Regex(
                                                                                "(?i)https?://[\\w./?-]+"
                                                                        ),
                                                                        ""
                                                                )
                                                                .replace(
                                                                        Regex("(?i)RF\\s*\\d+"),
                                                                        ""
                                                                )
                                                                .trim()

                                                // Heuristic fix for common Latin-1 -> UTF-8
                                                // encoding artifacts
                                                // (replacement char ?)
                                                if (res.contains("?")) {
                                                        res =
                                                                res.replace(
                                                                                "Ann?es",
                                                                                "Années",
                                                                                ignoreCase = true
                                                                        )
                                                                        .replace(
                                                                                "Ann?e",
                                                                                "Année",
                                                                                ignoreCase = true
                                                                        )
                                                                        .replace(
                                                                                "M?t?o",
                                                                                "Météo",
                                                                                ignoreCase = true
                                                                        )
                                                                        .replace(
                                                                                "Num?ro",
                                                                                "Numéro",
                                                                                ignoreCase = true
                                                                        )
                                                                        .replace(
                                                                                "Pr?sent",
                                                                                "Présent",
                                                                                ignoreCase = true
                                                                        )
                                                                        .replace(
                                                                                "No?l",
                                                                                "Noël",
                                                                                ignoreCase = true
                                                                        )
                                                                        .replace(
                                                                                "Vari?t?",
                                                                                "Variété",
                                                                                ignoreCase = true
                                                                        )
                                                                        .replace(
                                                                                "Fran?ais",
                                                                                "Français",
                                                                                ignoreCase = true
                                                                        )
                                                                        .replace(
                                                                                "?a",
                                                                                "Ça",
                                                                                ignoreCase = true
                                                                        )
                                                }
                                                return res
                                        }

                                        val cleanTitle = rawTitle?.let { clean(it) } ?: ""
                                        val cleanArtist = rawArtist?.let { clean(it) } ?: ""

                                        // FILTRAGE : On ignore si c'est le nom de la station, le
                                        // pays, ou du bruit
                                        if (cleanTitle.equals(stationName, ignoreCase = true) ||
                                                        cleanTitle.equals(
                                                                country,
                                                                ignoreCase = true
                                                        ) ||
                                                        cleanArtist.equals(
                                                                stationName,
                                                                ignoreCase = true
                                                        ) ||
                                                        cleanArtist.equals(
                                                                country,
                                                                ignoreCase = true
                                                        )
                                        ) {
                                                return // On garde les anciennes valeurs, on ne
                                                // pollue pas
                                        }

                                        val separators =
                                                listOf(
                                                        " - ",
                                                        " – ",
                                                        " — ",
                                                        " * ",
                                                        " : ",
                                                        " | ",
                                                        " / ",
                                                        "~"
                                                )
                                        var found = false

                                        for (sep in separators) {
                                                if (cleanTitle.contains(sep)) {
                                                        val parts = cleanTitle.split(sep, limit = 2)
                                                        val p1 = parts[0].trim()
                                                        val p2 = parts[1].trim()

                                                        // Si le deuxième segment contient le nom de
                                                        // la radio, on ignore
                                                        if (!p2.equals(
                                                                        stationName,
                                                                        ignoreCase = true
                                                                ) &&
                                                                        !p2.contains(
                                                                                stationName,
                                                                                ignoreCase = true
                                                                        )
                                                        ) {
                                                                currentArtist = p1
                                                                currentTitle = p2
                                                                found = true
                                                        }
                                                        break
                                                }
                                        }

                                        if (!found && cleanTitle.isNotBlank()) {
                                                currentArtist = cleanArtist.ifBlank { null }
                                                currentTitle = cleanTitle
                                        }
                                }
                        }
                )
        }

        // Recherche automatique de pochette (globale)
        LaunchedEffect(currentArtist, currentTitle) {
                currentArtworkUrl = null
                if (!currentArtist.isNullOrBlank()) {
                        try {
                                val searchTerm =
                                        if (!currentTitle.isNullOrBlank())
                                                "$currentArtist $currentTitle"
                                        else currentArtist!!
                                val results = ImageScraper.searchGoogleLogos(searchTerm)
                                currentArtworkUrl = results.firstOrNull()
                        } catch (_: Exception) {}
                }
        }

        // --- SYNC CHROMECAST GLOBALE ---
        LaunchedEffect(playingRadio?.stationuuid, currentArtist, currentTitle, currentArtworkUrl) {
                if (castSession != null && castSession!!.isConnected) {
                        val radio = playingRadio ?: return@LaunchedEffect
                        delay(800)
                        val mediaInfo =
                                MediaInfo.Builder(radio.url)
                                        .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
                                        .setContentType("audio/mpeg")
                                        .setMetadata(
                                                CastMediaMetadata(
                                                                CastMediaMetadata
                                                                        .MEDIA_TYPE_MUSIC_TRACK
                                                        )
                                                        .apply {
                                                                // Si on a un titre de chanson, on
                                                                // l'affiche en gros
                                                                val songTitle =
                                                                        if (currentTitle
                                                                                        .isNullOrBlank()
                                                                        )
                                                                                radio.name
                                                                        else currentTitle!!
                                                                val songArtist =
                                                                        if (currentArtist
                                                                                        .isNullOrBlank()
                                                                        )
                                                                                (radio.country
                                                                                        ?: "")
                                                                        else currentArtist!!

                                                                putString(
                                                                        CastMediaMetadata.KEY_TITLE,
                                                                        songTitle
                                                                )
                                                                putString(
                                                                        CastMediaMetadata
                                                                                .KEY_ARTIST,
                                                                        songArtist
                                                                )

                                                                // On utilise le champ Album pour
                                                                // afficher les infos
                                                                // de la station (Radio | Pays |
                                                                // Bitrate)
                                                                // Ce champ apparaît souvent en
                                                                // petit sous l'artiste
                                                                // sur la Shield
                                                                val stationInfo =
                                                                        "${radio.name} | ${radio.country ?: "Monde"} | ${radio.bitrate ?: "?"} kbps"
                                                                putString(
                                                                        CastMediaMetadata
                                                                                .KEY_ALBUM_TITLE,
                                                                        stationInfo
                                                                )

                                                                val imgUrl =
                                                                        currentArtworkUrl
                                                                                ?: radio.favicon
                                                                imgUrl?.let {
                                                                        addImage(
                                                                                WebImage(it.toUri())
                                                                        )
                                                                }
                                                        }
                                        )
                                        .build()
                        castSession!!.remoteMediaClient?.load(
                                MediaLoadRequestData.Builder().setMediaInfo(mediaInfo).build()
                        )
                }

                // --- SYNC SERVICE / BLUETOOTH / NOTIFICATION ---
                if (!currentTitle.isNullOrBlank() ||
                                !currentArtist.isNullOrBlank() ||
                                playingRadio != null
                ) {
                        val args =
                                Bundle().apply {
                                        putString(
                                                "TITLE",
                                                currentTitle.takeIf { it?.isNotBlank() == true }
                                        )
                                        putString(
                                                "ARTIST",
                                                currentArtist.takeIf { it?.isNotBlank() == true }
                                        )
                                        putString("ALBUM", playingRadio?.name)
                                        putString(
                                                "ARTWORK_URL",
                                                currentArtworkUrl ?: playingRadio?.favicon
                                        )
                                }
                        (exoPlayer as? MediaController)?.let { controller ->
                                try {
                                        controller.sendCustomCommand(
                                                SessionCommand("UPDATE_METADATA", Bundle.EMPTY),
                                                args
                                        )
                                } catch (_: Exception) {
                                        // e.printStackTrace() // Removed as per instruction to
                                        // replace unused 'e' with '_'
                                }
                        }
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
                        // This was clearing the view state on every app launch, even if we wanted
                        // to restore
                        // it.
                }
        }

        LaunchedEffect(
                selectedRadioCountry,
                selectedRadioTag,
                selectedRadioBitrate,
                searchTrigger,
                selectedRadioFavoriteListId,
                showRecentRadiosOnly,
                radioSortOrder
        ) {
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
                                                                        .padding(12.dp)
                                                ) {
                                                        // États de focus pour la toolbar
                                                        var isRefreshFocused by remember {
                                                                mutableStateOf(false)
                                                        }
                                                        var isRecentFocused by remember {
                                                                mutableStateOf(false)
                                                        }
                                                        var isQuitFocused by remember {
                                                                mutableStateOf(false)
                                                        }
                                                        var isExportFocused by remember {
                                                                mutableStateOf(false)
                                                        }
                                                        var isImportFocused by remember {
                                                                mutableStateOf(false)
                                                        }
                                                        var isPlayerFocused by remember {
                                                                mutableStateOf(false)
                                                        }
                                                        var isCastFocused by remember {
                                                                mutableStateOf(false)
                                                        }

                                                        Row(
                                                                modifier =
                                                                        Modifier.fillMaxWidth(0.9f)
                                                                                .align(
                                                                                        Alignment
                                                                                                .CenterHorizontally
                                                                                ),
                                                                verticalAlignment =
                                                                        Alignment.CenterVertically,
                                                                horizontalArrangement =
                                                                        Arrangement.SpaceBetween
                                                        ) {
                                                                // 1. Icone Radio (70x70, N&B)
                                                                Icon(
                                                                        Icons.Default.Radio,
                                                                        "SimpleRADIO",
                                                                        tint = Color.White,
                                                                        modifier =
                                                                                Modifier.size(70.dp)
                                                                )

                                                                // 2. Pilule Récents
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
                                                                                if (isRecentFocused)
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
                                                                                        null,
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

                                                                // 3. Pilule Refresh
                                                                Surface(
                                                                        onClick = {
                                                                                scope.launch {
                                                                                        isLoading =
                                                                                                true
                                                                                        try {
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
                                                                                                val bitrateMax =
                                                                                                        when (selectedRadioBitrate
                                                                                                        ) {
                                                                                                                0 ->
                                                                                                                        63
                                                                                                                64 ->
                                                                                                                        127
                                                                                                                128 ->
                                                                                                                        191
                                                                                                                else ->
                                                                                                                        null
                                                                                                        }
                                                                                                radioStations =
                                                                                                        radioRepository
                                                                                                                .searchStations(
                                                                                                                        selectedRadioCountry,
                                                                                                                        selectedRadioTag,
                                                                                                                        radioSearchQuery
                                                                                                                                .takeIf {
                                                                                                                                        it.isNotBlank()
                                                                                                                                },
                                                                                                                        selectedRadioBitrate,
                                                                                                                        bitrateMax,
                                                                                                                        radioSortOrder
                                                                                                                )
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
                                                                                        null,
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

                                                                // 4. Pilule Player
                                                                if (playingRadio != null) {
                                                                        Surface(
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
                                                                                        Modifier.height(
                                                                                                        48.dp
                                                                                                )
                                                                                                .onFocusChanged {
                                                                                                        isPlayerFocused =
                                                                                                                it.isFocused
                                                                                                },
                                                                                color =
                                                                                        if (isPlayerFocused
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
                                                                                                if (playerIsPlaying
                                                                                                )
                                                                                                        Icons.Filled
                                                                                                                .PlayCircleFilled
                                                                                                else
                                                                                                        Icons.Default
                                                                                                                .PlayCircleOutline,
                                                                                                null,
                                                                                                tint =
                                                                                                        if (isPlayerFocused
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
                                                                                                "Player",
                                                                                                color =
                                                                                                        if (isPlayerFocused
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

                                                                // 5. Btn Export (CloudUpload)
                                                                IconButton(
                                                                        onClick = {
                                                                                exportLauncher
                                                                                        .launch(
                                                                                                "radio_favorites_backup.json"
                                                                                        )
                                                                        },
                                                                        modifier =
                                                                                Modifier.size(48.dp)
                                                                                        .onFocusChanged {
                                                                                                isExportFocused =
                                                                                                        it.isFocused
                                                                                        }
                                                                                        .background(
                                                                                                if (isExportFocused
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
                                                                                null,
                                                                                tint =
                                                                                        if (isExportFocused
                                                                                        )
                                                                                                Color.Black
                                                                                        else
                                                                                                Color.White
                                                                        )
                                                                }

                                                                // 6. Btn Import (CloudDownload)
                                                                IconButton(
                                                                        onClick = {
                                                                                importLauncher
                                                                                        .launch(
                                                                                                arrayOf(
                                                                                                        "application/json"
                                                                                                )
                                                                                        )
                                                                        },
                                                                        modifier =
                                                                                Modifier.size(48.dp)
                                                                                        .onFocusChanged {
                                                                                                isImportFocused =
                                                                                                        it.isFocused
                                                                                        }
                                                                                        .background(
                                                                                                if (isImportFocused
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
                                                                                null,
                                                                                tint =
                                                                                        if (isImportFocused
                                                                                        )
                                                                                                Color.Black
                                                                                        else
                                                                                                Color.White
                                                                        )
                                                                }

                                                                // 7. Btn Cast (MediaRouteButton)
                                                                Box(
                                                                        modifier =
                                                                                Modifier.size(48.dp)
                                                                                        .onFocusChanged {
                                                                                                isCastFocused =
                                                                                                        it.isFocused
                                                                                        }
                                                                                        .background(
                                                                                                if (isCastFocused
                                                                                                )
                                                                                                        Color.White
                                                                                                else
                                                                                                        Color.Transparent,
                                                                                                CircleShape
                                                                                        ),
                                                                        contentAlignment =
                                                                                Alignment.Center
                                                                ) {
                                                                        AndroidView(
                                                                                factory = { ctx ->
                                                                                        MediaRouteButton(
                                                                                                        ctx
                                                                                                )
                                                                                                .apply {
                                                                                                        CastButtonFactory
                                                                                                                .setUpMediaRouteButton(
                                                                                                                        ctx,
                                                                                                                        this
                                                                                                                )
                                                                                                }
                                                                                }
                                                                        )
                                                                }

                                                                // 8. Btn Power (Quitter)
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
                                                                                                isQuitFocused =
                                                                                                        it.isFocused
                                                                                        }
                                                                                        .background(
                                                                                                if (isQuitFocused
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
                                                                                null,
                                                                                tint =
                                                                                        if (isQuitFocused
                                                                                        )
                                                                                                Color.Black
                                                                                        else
                                                                                                Color.Red
                                                                        )
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

                                        val isPortrait =
                                                LocalConfiguration.current.orientation ==
                                                        Configuration.ORIENTATION_PORTRAIT

                                        if (isPortrait) {
                                                val countryName =
                                                        radioCountries
                                                                .find {
                                                                        it.iso_3166_1 ==
                                                                                selectedRadioCountry
                                                                }
                                                                ?.name
                                                                ?: "Tous"
                                                val tagName = selectedRadioTag ?: "Tous"

                                                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                                        if (!isViewingRadioResults &&
                                                                        radioSearchQuery.isBlank()
                                                        ) {
                                                                LazyColumn(
                                                                        modifier =
                                                                                Modifier.fillMaxSize()
                                                                                        .padding(
                                                                                                8.dp
                                                                                        ),
                                                                        verticalArrangement =
                                                                                Arrangement
                                                                                        .spacedBy(
                                                                                                8.dp
                                                                                        )
                                                                ) {
                                                                        // 1. Récents
                                                                        item {
                                                                                SidebarItem(
                                                                                        text =
                                                                                                "Récents",
                                                                                        icon =
                                                                                                Icons.Default
                                                                                                        .History,
                                                                                        isSelected =
                                                                                                showRecentRadiosOnly &&
                                                                                                        isViewingRadioResults,
                                                                                        onClick = {
                                                                                                showRecentRadiosOnly =
                                                                                                        true
                                                                                                isViewingRadioResults =
                                                                                                        true
                                                                                                selectedRadioFavoriteListId =
                                                                                                        null
                                                                                                radioSearchQuery =
                                                                                                        ""
                                                                                        }
                                                                                )
                                                                        }

                                                                        // 2. Vos listes (Header +
                                                                        // Add button)
                                                                        item {
                                                                                Row(
                                                                                        modifier =
                                                                                                Modifier.fillMaxWidth()
                                                                                                        .padding(
                                                                                                                8.dp
                                                                                                        ),
                                                                                        horizontalArrangement =
                                                                                                Arrangement
                                                                                                        .SpaceBetween,
                                                                                        verticalAlignment =
                                                                                                Alignment
                                                                                                        .CenterVertically
                                                                                ) {
                                                                                        Text(
                                                                                                "Vos listes",
                                                                                                style =
                                                                                                        MaterialTheme
                                                                                                                .typography
                                                                                                                .titleSmall,
                                                                                                color =
                                                                                                        Color.Gray
                                                                                        )
                                                                                        var isAddListFocused by remember {
                                                                                                mutableStateOf(
                                                                                                        false
                                                                                                )
                                                                                        }
                                                                                        IconButton(
                                                                                                onClick = {
                                                                                                        showAddListDialog =
                                                                                                                true
                                                                                                },
                                                                                                modifier =
                                                                                                        Modifier.size(
                                                                                                                        40.dp
                                                                                                                )
                                                                                                                .onFocusChanged {
                                                                                                                        isAddListFocused =
                                                                                                                                it.isFocused
                                                                                                                }
                                                                                                                .background(
                                                                                                                        if (isAddListFocused
                                                                                                                        )
                                                                                                                                Color.White
                                                                                                                        else
                                                                                                                                Color.Transparent,
                                                                                                                        CircleShape
                                                                                                                )
                                                                                        ) {
                                                                                                Icon(
                                                                                                        Icons.Default
                                                                                                                .Add,
                                                                                                        null,
                                                                                                        tint =
                                                                                                                if (isAddListFocused
                                                                                                                )
                                                                                                                        Color.Black
                                                                                                                else
                                                                                                                        MaterialTheme
                                                                                                                                .colorScheme
                                                                                                                                .primary
                                                                                                )
                                                                                        }
                                                                                }
                                                                        }

                                                                        // 3. Liste des favoris
                                                                        items(radioFavoriteLists) {
                                                                                list ->
                                                                                SidebarItem(
                                                                                        text =
                                                                                                list.name,
                                                                                        isSelected =
                                                                                                selectedRadioFavoriteListId ==
                                                                                                        list.id,
                                                                                        onClick = {
                                                                                                selectedRadioFavoriteListId =
                                                                                                        list.id
                                                                                                isViewingRadioResults =
                                                                                                        true
                                                                                                showRecentRadiosOnly =
                                                                                                        false
                                                                                                radioSearchQuery =
                                                                                                        ""
                                                                                        }
                                                                                )
                                                                        }

                                                                        // 4. Bouton Recherche
                                                                        item {
                                                                                SidebarItem(
                                                                                        text =
                                                                                                if (radioSearchQuery
                                                                                                                .isEmpty()
                                                                                                )
                                                                                                        "Rechercher"
                                                                                                else
                                                                                                        "Recherche: \"$radioSearchQuery\"",
                                                                                        icon =
                                                                                                Icons.Default
                                                                                                        .Search,
                                                                                        isSelected =
                                                                                                radioSearchQuery
                                                                                                        .isNotEmpty(),
                                                                                        onClick = {
                                                                                                showSearchDialog =
                                                                                                        true
                                                                                        }
                                                                                )
                                                                        }

                                                                        // 5. Ajouter WebRadio
                                                                        // (Gray)
                                                                        item {
                                                                                var isAddBtnFocused by remember {
                                                                                        mutableStateOf(
                                                                                                false
                                                                                        )
                                                                                }
                                                                                Card(
                                                                                        modifier =
                                                                                                Modifier.fillMaxWidth()
                                                                                                        .onFocusChanged {
                                                                                                                isAddBtnFocused =
                                                                                                                        it.isFocused
                                                                                                        }
                                                                                                        .clickable {
                                                                                                                Toast.makeText(
                                                                                                                                context,
                                                                                                                                "Ouverture...",
                                                                                                                                Toast.LENGTH_SHORT
                                                                                                                        )
                                                                                                                        .show()
                                                                                                                showAddCustomUrlDialog =
                                                                                                                        true
                                                                                                        },
                                                                                        colors =
                                                                                                CardDefaults
                                                                                                        .cardColors(
                                                                                                                containerColor =
                                                                                                                        if (isAddBtnFocused
                                                                                                                        )
                                                                                                                                Color.White
                                                                                                                        else
                                                                                                                                Color.Gray
                                                                                                        )
                                                                                ) {
                                                                                        Row(
                                                                                                modifier =
                                                                                                        Modifier.padding(
                                                                                                                12.dp
                                                                                                        ),
                                                                                                verticalAlignment =
                                                                                                        Alignment
                                                                                                                .CenterVertically
                                                                                        ) {
                                                                                                Icon(
                                                                                                        Icons.Default
                                                                                                                .Link,
                                                                                                        null,
                                                                                                        tint =
                                                                                                                if (isAddBtnFocused
                                                                                                                )
                                                                                                                        Color.Black
                                                                                                                else
                                                                                                                        Color.White
                                                                                                )
                                                                                                Spacer(
                                                                                                        Modifier.width(
                                                                                                                12.dp
                                                                                                        )
                                                                                                )
                                                                                                Text(
                                                                                                        "Ajouter WebRadio",
                                                                                                        color =
                                                                                                                if (isAddBtnFocused
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

                                                                        // 6. Qualité (Expandable)

                                                                        // --- QUALITÉ (Expandable)
                                                                        // ---
                                                                        val currentQualityName =
                                                                                qualityOptions
                                                                                        .find {
                                                                                                it.second ==
                                                                                                        selectedRadioBitrate
                                                                                        }
                                                                                        ?.first
                                                                                        ?: "Qualités"
                                                                        item {
                                                                                SidebarItem(
                                                                                        text =
                                                                                                "🎧 Qualité : $currentQualityName",
                                                                                        icon =
                                                                                                Icons.Default
                                                                                                        .FilterList,
                                                                                        isSelected =
                                                                                                selectedRadioBitrate !=
                                                                                                        null,
                                                                                        onClick = {
                                                                                                isQualityExpanded =
                                                                                                        !isQualityExpanded
                                                                                        }
                                                                                )
                                                                        }
                                                                        if (isQualityExpanded) {
                                                                                items(
                                                                                        qualityOptions
                                                                                ) { item ->
                                                                                        SidebarItem(
                                                                                                text =
                                                                                                        item.first,
                                                                                                isSelected =
                                                                                                        selectedRadioBitrate ==
                                                                                                                item.second,
                                                                                                onClick = {
                                                                                                        selectedRadioBitrate =
                                                                                                                item.second
                                                                                                        isQualityExpanded =
                                                                                                                false
                                                                                                        showRecentRadiosOnly =
                                                                                                                false
                                                                                                        isViewingRadioResults =
                                                                                                                true
                                                                                                        searchTrigger++
                                                                                                }
                                                                                        )
                                                                                }
                                                                        }

                                                                        // --- PAYS (Expandable) ---
                                                                        item {
                                                                                SidebarItem(
                                                                                        text =
                                                                                                "🌍 Pays : $countryName",
                                                                                        icon =
                                                                                                Icons.Default
                                                                                                        .Public,
                                                                                        isSelected =
                                                                                                selectedRadioCountry !=
                                                                                                        null,
                                                                                        onClick = {
                                                                                                isCountryExpanded =
                                                                                                        !isCountryExpanded
                                                                                        }
                                                                                )
                                                                        }
                                                                        if (isCountryExpanded) {
                                                                                item {
                                                                                        SidebarItem(
                                                                                                text =
                                                                                                        "Tous les pays",
                                                                                                isSelected =
                                                                                                        selectedRadioCountry ==
                                                                                                                null,
                                                                                                onClick = {
                                                                                                        selectedRadioCountry =
                                                                                                                null
                                                                                                        isCountryExpanded =
                                                                                                                false
                                                                                                        showRecentRadiosOnly =
                                                                                                                false
                                                                                                        isViewingRadioResults =
                                                                                                                true
                                                                                                        searchTrigger++
                                                                                                }
                                                                                        )
                                                                                }
                                                                                items(
                                                                                        radioCountries
                                                                                                .filter {
                                                                                                        it.name
                                                                                                                .contains(
                                                                                                                        sidebarCountrySearch,
                                                                                                                        ignoreCase =
                                                                                                                                true
                                                                                                                )
                                                                                                }
                                                                                                .take(
                                                                                                        50
                                                                                                )
                                                                                ) { country ->
                                                                                        SidebarItem(
                                                                                                text =
                                                                                                        country.name,
                                                                                                isSelected =
                                                                                                        selectedRadioCountry ==
                                                                                                                country.iso_3166_1,
                                                                                                onClick = {
                                                                                                        selectedRadioCountry =
                                                                                                                country.iso_3166_1
                                                                                                        isCountryExpanded =
                                                                                                                false
                                                                                                        showRecentRadiosOnly =
                                                                                                                false
                                                                                                        isViewingRadioResults =
                                                                                                                true
                                                                                                        searchTrigger++
                                                                                                }
                                                                                        )
                                                                                }
                                                                        }

                                                                        // --- GENRE (Expandable)
                                                                        // ---
                                                                        item {
                                                                                SidebarItem(
                                                                                        text =
                                                                                                "🎵 Genre : $tagName",
                                                                                        icon =
                                                                                                Icons.AutoMirrored
                                                                                                        .Filled
                                                                                                        .Label,
                                                                                        isSelected =
                                                                                                selectedRadioTag !=
                                                                                                        null,
                                                                                        onClick = {
                                                                                                isGenreExpanded =
                                                                                                        !isGenreExpanded
                                                                                        }
                                                                                )
                                                                        }
                                                                        if (isGenreExpanded) {
                                                                                item {
                                                                                        SidebarItem(
                                                                                                text =
                                                                                                        "Tous les genres",
                                                                                                isSelected =
                                                                                                        selectedRadioTag ==
                                                                                                                null,
                                                                                                onClick = {
                                                                                                        selectedRadioTag =
                                                                                                                null
                                                                                                        isGenreExpanded =
                                                                                                                false
                                                                                                        showRecentRadiosOnly =
                                                                                                                false
                                                                                                        isViewingRadioResults =
                                                                                                                true
                                                                                                        searchTrigger++
                                                                                                }
                                                                                        )
                                                                                }

                                                                                items(
                                                                                        radioTags
                                                                                                .filter {
                                                                                                        it.name
                                                                                                                .contains(
                                                                                                                        sidebarTagSearch,
                                                                                                                        ignoreCase =
                                                                                                                                true
                                                                                                                )
                                                                                                }
                                                                                                .take(
                                                                                                        50
                                                                                                )
                                                                                ) { tag ->
                                                                                        SidebarItem(
                                                                                                text =
                                                                                                        tag.name,
                                                                                                isSelected =
                                                                                                        selectedRadioTag ==
                                                                                                                tag.name,
                                                                                                onClick = {
                                                                                                        selectedRadioTag =
                                                                                                                tag.name
                                                                                                        isGenreExpanded =
                                                                                                                false
                                                                                                        showRecentRadiosOnly =
                                                                                                                false
                                                                                                        isViewingRadioResults =
                                                                                                                true
                                                                                                        searchTrigger++
                                                                                                }
                                                                                        )
                                                                                }
                                                                        }

                                                                        // 8. Réinitialiser (visible
                                                                        // si filtres actifs)
                                                                        if (radioSearchQuery
                                                                                        .isNotEmpty() ||
                                                                                        selectedRadioCountry !=
                                                                                                null ||
                                                                                        selectedRadioTag !=
                                                                                                null ||
                                                                                        selectedRadioBitrate !=
                                                                                                null
                                                                        ) {
                                                                                item {
                                                                                        var isResetFocused by remember {
                                                                                                mutableStateOf(
                                                                                                        false
                                                                                                )
                                                                                        }
                                                                                        Surface(
                                                                                                onClick = {
                                                                                                        selectedRadioCountry =
                                                                                                                null
                                                                                                        selectedRadioTag =
                                                                                                                null
                                                                                                        selectedRadioBitrate =
                                                                                                                null
                                                                                                        radioSearchQuery =
                                                                                                                ""
                                                                                                        searchTrigger++
                                                                                                },
                                                                                                modifier =
                                                                                                        Modifier.fillMaxWidth()
                                                                                                                .padding(
                                                                                                                        8.dp
                                                                                                                )
                                                                                                                .onFocusChanged {
                                                                                                                        isResetFocused =
                                                                                                                                it.isFocused
                                                                                                                },
                                                                                                color =
                                                                                                        if (isResetFocused
                                                                                                        )
                                                                                                                Color.White
                                                                                                        else
                                                                                                                Color(
                                                                                                                        0xFF8B0000
                                                                                                                ), // Dark red
                                                                                                shape =
                                                                                                        RoundedCornerShape(
                                                                                                                8.dp
                                                                                                        )
                                                                                        ) {
                                                                                                Row(
                                                                                                        modifier =
                                                                                                                Modifier.padding(
                                                                                                                        12.dp
                                                                                                                ),
                                                                                                        verticalAlignment =
                                                                                                                Alignment
                                                                                                                        .CenterVertically,
                                                                                                        horizontalArrangement =
                                                                                                                Arrangement
                                                                                                                        .Center
                                                                                                ) {
                                                                                                        Icon(
                                                                                                                Icons.Default
                                                                                                                        .Clear,
                                                                                                                null,
                                                                                                                tint =
                                                                                                                        if (isResetFocused
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
                                                                                                                "RÉINITIALISER",
                                                                                                                color =
                                                                                                                        if (isResetFocused
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
                                                                        }

                                                                        // 9. Spacer final
                                                                        item {
                                                                                Spacer(
                                                                                        modifier =
                                                                                                Modifier.height(
                                                                                                        120.dp
                                                                                                )
                                                                                )
                                                                        }
                                                                }
                                                        } else {
                                                                Column {
                                                                        Button(
                                                                                onClick = {
                                                                                        isViewingRadioResults =
                                                                                                false
                                                                                },
                                                                                modifier =
                                                                                        Modifier.padding(
                                                                                                        8.dp
                                                                                                )
                                                                                                .fillMaxWidth(),
                                                                                colors =
                                                                                        ButtonDefaults
                                                                                                .buttonColors(
                                                                                                        containerColor =
                                                                                                                Color(
                                                                                                                        0xFF2E7D32
                                                                                                                ), // Vert foncé Premium
                                                                                                        contentColor =
                                                                                                                Color.White
                                                                                                ),
                                                                                shape =
                                                                                        MaterialTheme
                                                                                                .shapes
                                                                                                .medium
                                                                        ) {
                                                                                Icon(
                                                                                        Icons.AutoMirrored
                                                                                                .Filled
                                                                                                .ArrowBack,
                                                                                        null
                                                                                )
                                                                                Spacer(
                                                                                        Modifier.width(
                                                                                                12.dp
                                                                                        )
                                                                                )
                                                                                Text(
                                                                                        "RETOUR AUX FILTRES",
                                                                                        style =
                                                                                                MaterialTheme
                                                                                                        .typography
                                                                                                        .labelLarge
                                                                                )
                                                                        }
                                                                        LazyColumn(
                                                                                state =
                                                                                        resultsListState,
                                                                                modifier =
                                                                                        Modifier.fillMaxSize()
                                                                                                .padding(
                                                                                                        8.dp
                                                                                                ),
                                                                                verticalArrangement =
                                                                                        Arrangement
                                                                                                .spacedBy(
                                                                                                        12.dp
                                                                                                )
                                                                        ) {
                                                                                // Affiche la liste
                                                                                // actuelle (peut
                                                                                // être
                                                                                // radioStations,
                                                                                // recentRadios ou
                                                                                // radioFavStations)
                                                                                items(
                                                                                        currentRadioList
                                                                                ) { radio ->
                                                                                        MainItem(
                                                                                                title =
                                                                                                        radio.name,
                                                                                                subtitle =
                                                                                                        "${radio.country ?: ""} | ${radio.bitrate ?: "?"} kbps",
                                                                                                iconUrl =
                                                                                                        radio.favicon,
                                                                                                isPlaying =
                                                                                                        playingRadio
                                                                                                                ?.stationuuid ==
                                                                                                                radio.stationuuid,
                                                                                                onClick = {
                                                                                                        currentArtist =
                                                                                                                null
                                                                                                        currentTitle =
                                                                                                                null
                                                                                                        currentArtworkUrl =
                                                                                                                null

                                                                                                        playingRadio =
                                                                                                                radio
                                                                                                        val snapshot =
                                                                                                                currentRadioList
                                                                                                                        .toList()
                                                                                                        navRadioList =
                                                                                                                snapshot

                                                                                                        Log.d(
                                                                                                                "SimpleRADIO",
                                                                                                                "Portrait Click: radio=${radio.name}, snapshot size=${snapshot.size}"
                                                                                                        )

                                                                                                        exoPlayer
                                                                                                                ?.let {
                                                                                                                        player
                                                                                                                        ->
                                                                                                                        player.volume =
                                                                                                                                if (castSession !=
                                                                                                                                                null &&
                                                                                                                                                castSession!!
                                                                                                                                                        .isConnected
                                                                                                                                )
                                                                                                                                        0f
                                                                                                                                else
                                                                                                                                        1f
                                                                                                                        val mediaItems =
                                                                                                                                snapshot
                                                                                                                                        .map {
                                                                                                                                                r
                                                                                                                                                ->
                                                                                                                                                r.toMediaItem()
                                                                                                                                        }
                                                                                                                        val startIndex =
                                                                                                                                snapshot
                                                                                                                                        .indexOfFirst {
                                                                                                                                                s
                                                                                                                                                ->
                                                                                                                                                s.stationuuid ==
                                                                                                                                                        radio.stationuuid
                                                                                                                                        }
                                                                                                                        Log.d(
                                                                                                                                "SimpleRADIO",
                                                                                                                                "startIndex=$startIndex for ${radio.name}"
                                                                                                                        )
                                                                                                                        if (startIndex !=
                                                                                                                                        -1
                                                                                                                        ) {
                                                                                                                                player.setMediaItems(
                                                                                                                                        mediaItems,
                                                                                                                                        startIndex,
                                                                                                                                        0L
                                                                                                                                )
                                                                                                                                player.prepare()
                                                                                                                                player.play()
                                                                                                                        } else {
                                                                                                                                Log.e(
                                                                                                                                        "SimpleRADIO",
                                                                                                                                        "RADIO NOT FOUND IN SNAPSHOT!"
                                                                                                                                )
                                                                                                                        }
                                                                                                                }
                                                                                                        isFullScreenPlayer =
                                                                                                                true
                                                                                                        scope
                                                                                                                .launch {
                                                                                                                        radioRepository
                                                                                                                                .addToRecents(
                                                                                                                                        radio.stationuuid
                                                                                                                                )
                                                                                                                }
                                                                                                },
                                                                                                onAddFavorite = {
                                                                                                        radioToFavorite =
                                                                                                                radio
                                                                                                }
                                                                                        )
                                                                                }
                                                                        }
                                                                }
                                                        }
                                                }
                                        } else {
                                                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                                        Box(
                                                                modifier =
                                                                        Modifier.weight(0.4f)
                                                                                .fillMaxHeight()
                                                                                .background(
                                                                                        Color(
                                                                                                0xFF121212
                                                                                        )
                                                                                )
                                                        ) { // Force Dark Background
                                                                LazyColumn(
                                                                        modifier =
                                                                                Modifier.fillMaxSize()
                                                                                        .padding(
                                                                                                8.dp
                                                                                        ),
                                                                        verticalArrangement =
                                                                                Arrangement
                                                                                        .spacedBy(
                                                                                                8.dp
                                                                                        )
                                                                ) {
                                                                        // 1. Récents
                                                                        item {
                                                                                SidebarItem(
                                                                                        text =
                                                                                                "Récents",
                                                                                        icon =
                                                                                                Icons.Default
                                                                                                        .History,
                                                                                        isSelected =
                                                                                                showRecentRadiosOnly,
                                                                                        onClick = {
                                                                                                showRecentRadiosOnly =
                                                                                                        true
                                                                                                selectedRadioFavoriteListId =
                                                                                                        null
                                                                                                selectedRadioCountry =
                                                                                                        null
                                                                                                selectedRadioTag =
                                                                                                        null
                                                                                                selectedRadioBitrate =
                                                                                                        null
                                                                                                radioSearchQuery =
                                                                                                        ""
                                                                                        }
                                                                                )
                                                                        }

                                                                        // 2. Vos listes (Header +
                                                                        // Add button)
                                                                        item {
                                                                                Row(
                                                                                        modifier =
                                                                                                Modifier.fillMaxWidth()
                                                                                                        .padding(
                                                                                                                8.dp
                                                                                                        ),
                                                                                        horizontalArrangement =
                                                                                                Arrangement
                                                                                                        .SpaceBetween,
                                                                                        verticalAlignment =
                                                                                                Alignment
                                                                                                        .CenterVertically
                                                                                ) {
                                                                                        Text(
                                                                                                "Vos listes",
                                                                                                style =
                                                                                                        MaterialTheme
                                                                                                                .typography
                                                                                                                .titleSmall,
                                                                                                color =
                                                                                                        Color.Gray
                                                                                        )
                                                                                        var isAddListFocused by remember {
                                                                                                mutableStateOf(
                                                                                                        false
                                                                                                )
                                                                                        }
                                                                                        IconButton(
                                                                                                onClick = {
                                                                                                        showAddListDialog =
                                                                                                                true
                                                                                                },
                                                                                                modifier =
                                                                                                        Modifier.size(
                                                                                                                        40.dp
                                                                                                                )
                                                                                                                .onFocusChanged {
                                                                                                                        isAddListFocused =
                                                                                                                                it.isFocused
                                                                                                                }
                                                                                                                .background(
                                                                                                                        if (isAddListFocused
                                                                                                                        )
                                                                                                                                Color.White
                                                                                                                        else
                                                                                                                                Color.Transparent,
                                                                                                                        CircleShape
                                                                                                                )
                                                                                        ) {
                                                                                                Icon(
                                                                                                        Icons.Default
                                                                                                                .Add,
                                                                                                        null,
                                                                                                        tint =
                                                                                                                if (isAddListFocused
                                                                                                                )
                                                                                                                        Color.Black
                                                                                                                else
                                                                                                                        MaterialTheme
                                                                                                                                .colorScheme
                                                                                                                                .primary
                                                                                                )
                                                                                        }
                                                                                }
                                                                        }

                                                                        // 3. Liste des favoris
                                                                        items(radioFavoriteLists) {
                                                                                list ->
                                                                                SidebarItem(
                                                                                        text =
                                                                                                list.name,
                                                                                        isSelected =
                                                                                                selectedRadioFavoriteListId ==
                                                                                                        list.id,
                                                                                        onClick = {
                                                                                                selectedRadioFavoriteListId =
                                                                                                        list.id
                                                                                                selectedRadioCountry =
                                                                                                        null
                                                                                                selectedRadioTag =
                                                                                                        null
                                                                                                selectedRadioBitrate =
                                                                                                        null
                                                                                                showRecentRadiosOnly =
                                                                                                        false
                                                                                                radioSearchQuery =
                                                                                                        ""
                                                                                        },
                                                                                        onDelete = {
                                                                                                scope
                                                                                                        .launch {
                                                                                                                radioRepository
                                                                                                                        .removeFavoriteList(
                                                                                                                                list
                                                                                                                        )
                                                                                                        }
                                                                                        }
                                                                                )
                                                                        }

                                                                        // 4. Bouton Recherche
                                                                        item {
                                                                                SidebarItem(
                                                                                        text =
                                                                                                if (radioSearchQuery
                                                                                                                .isEmpty()
                                                                                                )
                                                                                                        "Rechercher"
                                                                                                else
                                                                                                        "Recherche: \"$radioSearchQuery\"",
                                                                                        icon =
                                                                                                Icons.Default
                                                                                                        .Search,
                                                                                        isSelected =
                                                                                                radioSearchQuery
                                                                                                        .isNotEmpty(),
                                                                                        onClick = {
                                                                                                showSearchDialog =
                                                                                                        true
                                                                                        }
                                                                                )
                                                                        }

                                                                        // 5. Ajouter WebRadio
                                                                        // (Gray)
                                                                        item {
                                                                                var isAddBtnFocused by remember {
                                                                                        mutableStateOf(
                                                                                                false
                                                                                        )
                                                                                }
                                                                                Card(
                                                                                        modifier =
                                                                                                Modifier.fillMaxWidth()
                                                                                                        .onFocusChanged {
                                                                                                                isAddBtnFocused =
                                                                                                                        it.isFocused
                                                                                                        }
                                                                                                        .clickable {
                                                                                                                Toast.makeText(
                                                                                                                                context,
                                                                                                                                "Ouverture...",
                                                                                                                                Toast.LENGTH_SHORT
                                                                                                                        )
                                                                                                                        .show()
                                                                                                                showAddCustomUrlDialog =
                                                                                                                        true
                                                                                                        },
                                                                                        colors =
                                                                                                CardDefaults
                                                                                                        .cardColors(
                                                                                                                containerColor =
                                                                                                                        if (isAddBtnFocused
                                                                                                                        )
                                                                                                                                Color.White
                                                                                                                        else
                                                                                                                                Color.Gray
                                                                                                        )
                                                                                ) {
                                                                                        Row(
                                                                                                modifier =
                                                                                                        Modifier.padding(
                                                                                                                12.dp
                                                                                                        ),
                                                                                                verticalAlignment =
                                                                                                        Alignment
                                                                                                                .CenterVertically
                                                                                        ) {
                                                                                                Icon(
                                                                                                        Icons.Default
                                                                                                                .Link,
                                                                                                        null,
                                                                                                        tint =
                                                                                                                if (isAddBtnFocused
                                                                                                                )
                                                                                                                        Color.Black
                                                                                                                else
                                                                                                                        Color.White
                                                                                                )
                                                                                                Spacer(
                                                                                                        Modifier.width(
                                                                                                                12.dp
                                                                                                        )
                                                                                                )
                                                                                                Text(
                                                                                                        "Ajouter WebRadio",
                                                                                                        color =
                                                                                                                if (isAddBtnFocused
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

                                                                        // 6. Qualité (Expandable)
                                                                        item {
                                                                                SidebarItem(
                                                                                        text =
                                                                                                qualityOptions
                                                                                                        .find {
                                                                                                                it.second ==
                                                                                                                        selectedRadioBitrate
                                                                                                        }
                                                                                                        ?.first
                                                                                                        ?: "Toutes qualités",
                                                                                        icon =
                                                                                                Icons.Default
                                                                                                        .FilterList,
                                                                                        isSelected =
                                                                                                selectedRadioBitrate !=
                                                                                                        null,
                                                                                        onClick = {
                                                                                                isQualityExpanded =
                                                                                                        !isQualityExpanded
                                                                                        }
                                                                                )
                                                                        }
                                                                        if (isQualityExpanded) {
                                                                                items(
                                                                                        qualityOptions
                                                                                ) { item ->
                                                                                        SidebarItem(
                                                                                                text =
                                                                                                        item.first,
                                                                                                isSelected =
                                                                                                        selectedRadioBitrate ==
                                                                                                                item.second,
                                                                                                onClick = {
                                                                                                        selectedRadioBitrate =
                                                                                                                item.second
                                                                                                        isQualityExpanded =
                                                                                                                false
                                                                                                        showRecentRadiosOnly =
                                                                                                                false
                                                                                                        searchTrigger++
                                                                                                }
                                                                                        )
                                                                                }
                                                                        }

                                                                        // 7. Pays (Expandable)
                                                                        item {
                                                                                SidebarItem(
                                                                                        text =
                                                                                                if (selectedRadioCountry ==
                                                                                                                null
                                                                                                )
                                                                                                        "Tous les pays"
                                                                                                else
                                                                                                        radioCountries
                                                                                                                .find {
                                                                                                                        it.iso_3166_1 ==
                                                                                                                                selectedRadioCountry
                                                                                                                }
                                                                                                                ?.name
                                                                                                                ?: "Pays sélectionné",
                                                                                        icon =
                                                                                                Icons.Default
                                                                                                        .Public,
                                                                                        isSelected =
                                                                                                selectedRadioCountry !=
                                                                                                        null,
                                                                                        onClick = {
                                                                                                isCountryExpanded =
                                                                                                        !isCountryExpanded
                                                                                        }
                                                                                )
                                                                        }
                                                                        if (isCountryExpanded) {
                                                                                item {
                                                                                        SidebarItem(
                                                                                                text =
                                                                                                        "Tous les pays",
                                                                                                isSelected =
                                                                                                        selectedRadioCountry ==
                                                                                                                null,
                                                                                                onClick = {
                                                                                                        selectedRadioCountry =
                                                                                                                null
                                                                                                        isCountryExpanded =
                                                                                                                false
                                                                                                        showRecentRadiosOnly =
                                                                                                                false
                                                                                                        searchTrigger++
                                                                                                }
                                                                                        )
                                                                                }
                                                                                items(
                                                                                        radioCountries
                                                                                                .filter {
                                                                                                        it.name
                                                                                                                .contains(
                                                                                                                        sidebarCountrySearch,
                                                                                                                        ignoreCase =
                                                                                                                                true
                                                                                                                )
                                                                                                }
                                                                                                .take(
                                                                                                        50
                                                                                                )
                                                                                ) { country ->
                                                                                        SidebarItem(
                                                                                                text =
                                                                                                        country.name,
                                                                                                isSelected =
                                                                                                        selectedRadioCountry ==
                                                                                                                country.iso_3166_1,
                                                                                                onClick = {
                                                                                                        selectedRadioCountry =
                                                                                                                country.iso_3166_1
                                                                                                        isCountryExpanded =
                                                                                                                false
                                                                                                        showRecentRadiosOnly =
                                                                                                                false
                                                                                                        searchTrigger++
                                                                                                }
                                                                                        )
                                                                                }
                                                                        }

                                                                        // 8. Genre (Expandable)
                                                                        item {
                                                                                SidebarItem(
                                                                                        text =
                                                                                                selectedRadioTag
                                                                                                        ?: "Tous les genres",
                                                                                        icon =
                                                                                                Icons.AutoMirrored
                                                                                                        .Filled
                                                                                                        .Label,
                                                                                        isSelected =
                                                                                                selectedRadioTag !=
                                                                                                        null,
                                                                                        onClick = {
                                                                                                isGenreExpanded =
                                                                                                        !isGenreExpanded
                                                                                        }
                                                                                )
                                                                        }
                                                                        if (isGenreExpanded) {
                                                                                item {
                                                                                        SidebarItem(
                                                                                                text =
                                                                                                        "Tous les genres",
                                                                                                isSelected =
                                                                                                        selectedRadioTag ==
                                                                                                                null,
                                                                                                onClick = {
                                                                                                        selectedRadioTag =
                                                                                                                null
                                                                                                        isGenreExpanded =
                                                                                                                false
                                                                                                        showRecentRadiosOnly =
                                                                                                                false
                                                                                                        searchTrigger++
                                                                                                }
                                                                                        )
                                                                                }
                                                                                items(
                                                                                        radioTags
                                                                                                .filter {
                                                                                                        it.name
                                                                                                                .contains(
                                                                                                                        sidebarTagSearch,
                                                                                                                        ignoreCase =
                                                                                                                                true
                                                                                                                )
                                                                                                }
                                                                                                .take(
                                                                                                        50
                                                                                                )
                                                                                ) { tag ->
                                                                                        SidebarItem(
                                                                                                text =
                                                                                                        tag.name,
                                                                                                isSelected =
                                                                                                        selectedRadioTag ==
                                                                                                                tag.name,
                                                                                                onClick = {
                                                                                                        selectedRadioTag =
                                                                                                                tag.name
                                                                                                        isGenreExpanded =
                                                                                                                false
                                                                                                        showRecentRadiosOnly =
                                                                                                                false
                                                                                                        searchTrigger++
                                                                                                }
                                                                                        )
                                                                                }
                                                                        }

                                                                        // 9. Réinitialiser
                                                                        // (toujours visible)
                                                                        item {
                                                                                var isResetFocused by remember {
                                                                                        mutableStateOf(
                                                                                                false
                                                                                        )
                                                                                }
                                                                                Surface(
                                                                                        onClick = {
                                                                                                selectedRadioCountry =
                                                                                                        null
                                                                                                selectedRadioTag =
                                                                                                        null
                                                                                                selectedRadioBitrate =
                                                                                                        null
                                                                                                radioSearchQuery =
                                                                                                        ""
                                                                                                searchTrigger++
                                                                                        },
                                                                                        modifier =
                                                                                                Modifier.fillMaxWidth()
                                                                                                        .padding(
                                                                                                                8.dp
                                                                                                        )
                                                                                                        .onFocusChanged {
                                                                                                                isResetFocused =
                                                                                                                        it.isFocused
                                                                                                        },
                                                                                        color =
                                                                                                if (isResetFocused
                                                                                                )
                                                                                                        Color.White
                                                                                                else
                                                                                                        Color(
                                                                                                                0xFF8B0000
                                                                                                        ),
                                                                                        shape =
                                                                                                RoundedCornerShape(
                                                                                                        8.dp
                                                                                                )
                                                                                ) {
                                                                                        Row(
                                                                                                modifier =
                                                                                                        Modifier.padding(
                                                                                                                12.dp
                                                                                                        ),
                                                                                                verticalAlignment =
                                                                                                        Alignment
                                                                                                                .CenterVertically,
                                                                                                horizontalArrangement =
                                                                                                        Arrangement
                                                                                                                .Center
                                                                                        ) {
                                                                                                Icon(
                                                                                                        Icons.Default
                                                                                                                .Clear,
                                                                                                        null,
                                                                                                        tint =
                                                                                                                if (isResetFocused
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
                                                                                                        "RÉINITIALISER",
                                                                                                        color =
                                                                                                                if (isResetFocused
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

                                                                        // Spacer final
                                                                        item {
                                                                                Spacer(
                                                                                        modifier =
                                                                                                Modifier.height(
                                                                                                        120.dp
                                                                                                )
                                                                                )
                                                                        }
                                                                }
                                                        }
                                                        Box(
                                                                modifier =
                                                                        Modifier.weight(0.6f)
                                                                                .fillMaxHeight()
                                                        ) {
                                                                LazyColumn(
                                                                        state = resultsListState,
                                                                        modifier =
                                                                                Modifier.fillMaxSize()
                                                                                        .padding(
                                                                                                8.dp
                                                                                        ),
                                                                        verticalArrangement =
                                                                                Arrangement
                                                                                        .spacedBy(
                                                                                                12.dp
                                                                                        )
                                                                ) {
                                                                        items(currentRadioList) {
                                                                                radio ->
                                                                                val isCurrent =
                                                                                        playingRadio
                                                                                                ?.stationuuid ==
                                                                                                radio.stationuuid
                                                                                MainItem(
                                                                                        title =
                                                                                                radio.name,
                                                                                        subtitle =
                                                                                                "${radio.country ?: ""} | ${radio.bitrate ?: "?"} kbps",
                                                                                        iconUrl =
                                                                                                radio.favicon,
                                                                                        isPlaying =
                                                                                                isCurrent,
                                                                                        modifier =
                                                                                                if (isCurrent
                                                                                                )
                                                                                                        Modifier.focusRequester(
                                                                                                                listFocusRequester
                                                                                                        )
                                                                                                else
                                                                                                        Modifier,
                                                                                        onClick = {
                                                                                                if (exoPlayer !=
                                                                                                                null
                                                                                                ) {
                                                                                                        currentArtist =
                                                                                                                null
                                                                                                        currentTitle =
                                                                                                                null
                                                                                                        currentArtworkUrl =
                                                                                                                null

                                                                                                        playingRadio =
                                                                                                                radio
                                                                                                        val snapshot =
                                                                                                                currentRadioList
                                                                                                                        .toList()
                                                                                                        navRadioList =
                                                                                                                snapshot

                                                                                                        exoPlayer
                                                                                                                ?.let {
                                                                                                                        player
                                                                                                                        ->
                                                                                                                        try {
                                                                                                                                player.volume =
                                                                                                                                        if (castSession !=
                                                                                                                                                        null &&
                                                                                                                                                        castSession!!
                                                                                                                                                                .isConnected
                                                                                                                                        )
                                                                                                                                                0f
                                                                                                                                        else
                                                                                                                                                1f
                                                                                                                                val mediaItems =
                                                                                                                                        snapshot
                                                                                                                                                .map {
                                                                                                                                                        r
                                                                                                                                                        ->
                                                                                                                                                        r.toMediaItem()
                                                                                                                                                }
                                                                                                                                val startIndex =
                                                                                                                                        snapshot
                                                                                                                                                .indexOfFirst {
                                                                                                                                                        s
                                                                                                                                                        ->
                                                                                                                                                        s.stationuuid ==
                                                                                                                                                                radio.stationuuid
                                                                                                                                                }
                                                                                                                                if (startIndex !=
                                                                                                                                                -1
                                                                                                                                ) {
                                                                                                                                        player.setMediaItems(
                                                                                                                                                mediaItems,
                                                                                                                                                startIndex,
                                                                                                                                                0L
                                                                                                                                        )
                                                                                                                                        player.prepare()
                                                                                                                                        player.play()
                                                                                                                                }
                                                                                                                        } catch (
                                                                                                                                _:
                                                                                                                                        Exception) {}
                                                                                                                }
                                                                                                        isFullScreenPlayer =
                                                                                                                true
                                                                                                        scope
                                                                                                                .launch {
                                                                                                                        radioRepository
                                                                                                                                .addToRecents(
                                                                                                                                        radio.stationuuid
                                                                                                                                )
                                                                                                                }
                                                                                                }
                                                                                        },
                                                                                        onAddFavorite = {
                                                                                                radioToFavorite =
                                                                                                        radio
                                                                                        }
                                                                                )
                                                                        }
                                                                }
                                                        }
                                                }
                                        }
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
                                        onSetSleepTimer = { mins ->
                                                sleepTimerTimeLeft = mins?.let { it * 60 * 1000L }
                                        },
                                        showLyrics = showLyricsGlobal,
                                        onToggleLyrics = { showLyricsGlobal = it }
                                )
                        }

                        if (showAddCustomUrlDialog) {
                                var name by remember { mutableStateOf("") }
                                var url by remember { mutableStateOf("") }
                                AlertDialog(
                                        onDismissRequest = { showAddCustomUrlDialog = false },
                                        title = { Text("Ajouter WebRadio") },
                                        text = {
                                                Column {
                                                        TextField(
                                                                value = name,
                                                                onValueChange = { name = it },
                                                                placeholder = {
                                                                        Text("Nom (ex: Impact FM)")
                                                                },
                                                                modifier =
                                                                        Modifier.fillMaxWidth()
                                                                                .padding(
                                                                                        bottom =
                                                                                                8.dp
                                                                                )
                                                        )
                                                        TextField(
                                                                value = url,
                                                                onValueChange = { url = it },
                                                                placeholder = {
                                                                        Text("URL (http...)")
                                                                },
                                                                modifier = Modifier.fillMaxWidth()
                                                        )
                                                }
                                        },
                                        confirmButton = {
                                                TextButton(
                                                        onClick = {
                                                                if (name.isNotBlank() &&
                                                                                url.isNotBlank()
                                                                ) {
                                                                        scope.launch {
                                                                                radioRepository
                                                                                        .addCustomRadio(
                                                                                                name,
                                                                                                url
                                                                                        )
                                                                                Toast.makeText(
                                                                                                context,
                                                                                                "Ajouté à 'mes urls'",
                                                                                                Toast.LENGTH_SHORT
                                                                                        )
                                                                                        .show()
                                                                        }
                                                                }
                                                                showAddCustomUrlDialog = false
                                                        }
                                                ) { Text("Ajouter") }
                                        },
                                        dismissButton = {
                                                TextButton(
                                                        onClick = { showAddCustomUrlDialog = false }
                                                ) { Text("Annuler") }
                                        }
                                )
                        }
                        if (showAddListDialog) {
                                var name by remember { mutableStateOf("") }
                                AlertDialog(
                                        onDismissRequest = { showAddListDialog = false },
                                        title = { Text("Nouvelle Liste") },
                                        text = {
                                                TextField(
                                                        value = name,
                                                        onValueChange = { name = it },
                                                        placeholder = { Text("Nom de la liste") }
                                                )
                                        },
                                        confirmButton = {
                                                TextButton(
                                                        onClick = {
                                                                if (name.isNotBlank())
                                                                        scope.launch {
                                                                                radioRepository
                                                                                        .addFavoriteList(
                                                                                                name
                                                                                        )
                                                                        }
                                                                showAddListDialog = false
                                                        }
                                                ) { Text("Ajouter") }
                                        },
                                        dismissButton = {
                                                TextButton(
                                                        onClick = { showAddListDialog = false }
                                                ) { Text("Annuler") }
                                        }
                                )
                        }

                        if (showSearchDialog) {
                                var tempQuery by remember { mutableStateOf(radioSearchQuery) }
                                AlertDialog(
                                        onDismissRequest = { showSearchDialog = false },
                                        title = { Text("Rechercher une Radio") },
                                        text = {
                                                OutlinedTextField(
                                                        value = tempQuery,
                                                        onValueChange = { tempQuery = it },
                                                        placeholder = {
                                                                Text("Ex: Jazz, Pop, France...")
                                                        },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        singleLine = true,
                                                        keyboardOptions =
                                                                KeyboardOptions(
                                                                        imeAction = ImeAction.Search
                                                                ),
                                                        keyboardActions =
                                                                KeyboardActions(
                                                                        onSearch = {
                                                                                radioSearchQuery =
                                                                                        tempQuery
                                                                                showRecentRadiosOnly =
                                                                                        false
                                                                                searchTrigger++
                                                                                showSearchDialog =
                                                                                        false
                                                                        }
                                                                )
                                                )
                                        },
                                        confirmButton = {
                                                TextButton(
                                                        onClick = {
                                                                radioSearchQuery = tempQuery
                                                                showRecentRadiosOnly = false
                                                                searchTrigger++
                                                                showSearchDialog = false
                                                        }
                                                ) { Text("Rechercher") }
                                        },
                                        dismissButton = {
                                                TextButton(onClick = { showSearchDialog = false }) {
                                                        Text("Annuler")
                                                }
                                        }
                                )
                        }

                        if (radioToFavorite != null)
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

@Composable
fun SidebarItem(
        text: String,
        isSelected: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        icon: ImageVector? = null,
        onDelete: (() -> Unit)? = null
) {
        var isFocused by remember { mutableStateOf(false) }

        Card(
                modifier =
                        modifier.fillMaxWidth()
                                .onFocusChanged { isFocused = it.isFocused }
                                .clickable { onClick() },
                border =
                        if (isSelected && !isFocused)
                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        else null,
                colors =
                        CardDefaults.cardColors(
                                containerColor =
                                        if (isFocused) Color.White
                                        else if (isSelected)
                                                Color(0xFF333333) // Dark Gray for selected
                                        else Color.Transparent // Transparent for unselected (shows
                                // sidebar bg)
                                )
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                        ) {
                                if (icon != null) {
                                        Icon(
                                                icon,
                                                null,
                                                tint =
                                                        if (isFocused) Color.Black
                                                        else Color.White // Force White for
                                                // unselected icons
                                                )
                                        Spacer(Modifier.width(12.dp))
                                }
                                Text(
                                        text,
                                        color =
                                                if (isFocused) Color.Black
                                                else if (isSelected) Color.White
                                                else Color.White, // Force visible white for
                                        // unselected
                                        maxLines = 1
                                )
                        }
                        if (onDelete != null)
                                IconButton(onClick = onDelete, modifier = Modifier.size(20.dp)) {
                                        Icon(
                                                Icons.Default.Delete,
                                                null,
                                                tint =
                                                        if (isFocused) Color.Red
                                                        else Color.Red.copy(alpha = 0.5f)
                                        )
                                }
                }
        }
}

@Composable
fun MainItem(
        title: String,
        iconUrl: String?,
        isPlaying: Boolean,
        onClick: () -> Unit,
        onAddFavorite: () -> Unit,
        modifier: Modifier = Modifier,
        subtitle: String? = null
) {
        var isFocused by remember { mutableStateOf(false) }
        var isFavFocused by remember { mutableStateOf(false) }

        Row(
                modifier = modifier.fillMaxWidth().height(65.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
                Card(
                        modifier =
                                Modifier.weight(0.833f)
                                        .fillMaxHeight()
                                        .onFocusChanged { isFocused = it.isFocused }
                                        .clickable { onClick() },
                        border =
                                if (isPlaying && !isFocused)
                                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                else null,
                        colors =
                                CardDefaults.cardColors(
                                        containerColor =
                                                if (isFocused) Color.White
                                                else if (isPlaying)
                                                        MaterialTheme.colorScheme.secondaryContainer
                                                else MaterialTheme.colorScheme.surfaceVariant
                                )
                ) {
                        Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                AsyncImage(
                                        model = iconUrl,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        contentScale = ContentScale.Fit
                                )
                                Spacer(Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                                title,
                                                style = MaterialTheme.typography.bodyLarge,
                                                maxLines = 1,
                                                color =
                                                        if (isFocused) Color.Black
                                                        else
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                        )
                                        if (subtitle != null)
                                                Text(
                                                        subtitle,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color =
                                                                if (isFocused) Color.DarkGray
                                                                else Color.Gray
                                                )
                                }
                        }
                }
                Spacer(Modifier.width(12.dp))
                IconButton(
                        onClick = onAddFavorite,
                        modifier =
                                Modifier.weight(0.083f)
                                        .fillMaxHeight()
                                        .onFocusChanged { isFavFocused = it.isFocused }
                                        .background(
                                                if (isFavFocused) Color.White
                                                else
                                                        MaterialTheme.colorScheme.surfaceVariant
                                                                .copy(alpha = 0.5f),
                                                MaterialTheme.shapes.medium
                                        )
                ) {
                        Icon(
                                Icons.Default.Add,
                                null,
                                tint = if (isFavFocused) Color.Black else Color.White
                        )
                }
                Spacer(Modifier.weight(0.084f))
        }
}

@Composable
fun <T> GenericFavoriteDialog(
        title: String,
        items: List<T>,
        getName: (T) -> String,
        getId: (T) -> Int,
        onDismiss: () -> Unit,
        onToggle: (Int) -> Unit,
        selectedIdsProvider: suspend () -> List<Int>
) {
        val selectedIds = remember { mutableStateListOf<Int>() }
        LaunchedEffect(Unit) {
                selectedIds.clear()
                selectedIds.addAll(selectedIdsProvider())
        }
        AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(title) },
                text = {
                        if (items.isEmpty()) Text("Aucune liste.")
                        else
                                LazyColumn {
                                        items(items) { list ->
                                                Row(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .clickable {
                                                                                onToggle(
                                                                                        getId(list)
                                                                                )
                                                                                if (selectedIds
                                                                                                .contains(
                                                                                                        getId(
                                                                                                                list
                                                                                                        )
                                                                                                )
                                                                                )
                                                                                        selectedIds
                                                                                                .remove(
                                                                                                        getId(
                                                                                                                list
                                                                                                        )
                                                                                                )
                                                                                else
                                                                                        selectedIds
                                                                                                .add(
                                                                                                        getId(
                                                                                                                list
                                                                                                        )
                                                                                                )
                                                                        }
                                                                        .padding(8.dp),
                                                        verticalAlignment =
                                                                Alignment.CenterVertically
                                                ) {
                                                        Checkbox(
                                                                checked =
                                                                        selectedIds.contains(
                                                                                getId(list)
                                                                        ),
                                                                onCheckedChange = null
                                                        )
                                                        Spacer(Modifier.width(8.dp))
                                                        Text(getName(list))
                                                }
                                        }
                                }
                },
                confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } }
        )
}

@Composable
fun VideoPlayerView(
        exoPlayer: Player,
        onBack: () -> Unit,
        radioStation: RadioStationEntity? = null,
        lrcLibApi: LrcLibApi? = null,
        radioList: List<RadioStationEntity> = emptyList(),
        castSession: CastSession? = null,
        artist: String? = null,
        title: String? = null,
        artworkUrl: String? = null,
        sleepTimerTimeLeft: Long? = null,
        onSetSleepTimer: (Int?) -> Unit = {},
        showLyrics: Boolean = false,
        onToggleLyrics: (Boolean) -> Unit = {}
) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        var isActuallyPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }
        var lyricsText by remember { mutableStateOf<String?>(null) }
        var isFetchingLyrics by remember { mutableStateOf(false) }

        // --- LOGIC: Translation ---
        var isTranslating by remember { mutableStateOf(false) }
        var translatedLyrics by remember { mutableStateOf<String?>(null) }

        fun toggleTranslation() {
                if (!isTranslating) {
                        if (translatedLyrics == null && lyricsText != null) {
                                scope.launch {
                                        val translated = TranslationApi.translate(lyricsText!!)
                                        translatedLyrics = translated
                                        isTranslating = true
                                }
                        } else {
                                isTranslating = true
                        }
                } else {
                        isTranslating = false
                }
        }

        // --- LOGIC: Artwork/Logo Cycling ---
        var artworkCycleIndex by remember { mutableIntStateOf(0) }
        var alternativeArtworks by remember { mutableStateOf<List<String>>(emptyList()) }
        var dynamicLogos by remember { mutableStateOf<List<String>>(emptyList()) }

        // On reset l'index quand la station ou le titre change
        LaunchedEffect(radioStation?.stationuuid, artist, title) { artworkCycleIndex = 0 }

        LaunchedEffect(radioStation, artworkUrl) {
                if (!artworkUrl.isNullOrBlank()) {
                        dynamicLogos = emptyList()
                        return@LaunchedEffect
                }
                if (radioStation != null) {
                        withContext(Dispatchers.IO) {
                                try {
                                        val logos =
                                                ImageScraper.findLogos(
                                                        radioName = radioStation.name,
                                                        country = radioStation.country,
                                                        streamUrl = radioStation.url
                                                )
                                        dynamicLogos = logos
                                } catch (_: Exception) {}
                        }
                }
        }

        LaunchedEffect(artist, title) {
                alternativeArtworks = emptyList()
                if (!artist.isNullOrBlank() && !title.isNullOrBlank()) {
                        try {
                                val searchTerm = "$artist $title"
                                val results = ImageScraper.searchGoogleLogos(searchTerm)
                                alternativeArtworks = results
                        } catch (_: Exception) {}
                }
        }

        val cycleList =
                remember(artworkUrl, alternativeArtworks, dynamicLogos, radioStation) {
                        val list = mutableListOf<String>()
                        if (!artworkUrl.isNullOrBlank() || alternativeArtworks.isNotEmpty()) {
                                if (!artworkUrl.isNullOrBlank()) list.add(artworkUrl)
                                list.addAll(alternativeArtworks.filter { it != artworkUrl })
                        } else {
                                list.addAll(dynamicLogos)
                                radioStation?.favicon?.takeIf { it.isNotBlank() }?.let {
                                        if (!list.contains(it)) list.add(it)
                                }
                        }
                        list.distinct().take(5)
                }

        val effectiveArtworkUrl =
                if (cycleList.isNotEmpty()) cycleList[artworkCycleIndex % cycleList.size] else null

        // Interception des commandes Bluetooth (Voiture) via Broadcast
        DisposableEffect(Unit) {
                val receiver =
                        object : BroadcastReceiver() {
                                override fun onReceive(context: Context?, intent: Intent?) {
                                        if (radioList.isEmpty()) return
                                        val currentIndex =
                                                radioList.indexOfFirst {
                                                        it.stationuuid == radioStation?.stationuuid
                                                }
                                        if (currentIndex == -1) return

                                        var nextStation: RadioStationEntity? = null

                                        if (intent?.action == "com.example.simpleradio.NEXT") {
                                                if (currentIndex < radioList.size - 1)
                                                        nextStation = radioList[currentIndex + 1]
                                        } else if (intent?.action == "com.example.simpleradio.PREV"
                                        ) {
                                                if (currentIndex > 0)
                                                        nextStation = radioList[currentIndex - 1]
                                        }

                                        if (nextStation != null) {
                                                // On ne fait plus setMediaItem ici pour laisser
                                                // ExoPlayer gérer sa
                                                // playlist interne si possible,
                                                // mais si on vient du Broadcast (Bluetooth hérité),
                                                // on déclenche le
                                                // changement de station indexé.
                                                val index =
                                                        radioList.indexOfFirst {
                                                                it.stationuuid ==
                                                                        nextStation.stationuuid
                                                        }
                                                if (index != -1) {
                                                        exoPlayer.seekTo(index, 0L)
                                                        exoPlayer.prepare()
                                                        exoPlayer.play()
                                                }
                                        }
                                }
                        }
                val filter =
                        IntentFilter().apply {
                                addAction("com.example.simpleradio.NEXT")
                                addAction("com.example.simpleradio.PREV")
                        }
                ContextCompat.registerReceiver(
                        context,
                        receiver,
                        filter,
                        ContextCompat.RECEIVER_NOT_EXPORTED
                )

                onDispose {
                        try {
                                context.unregisterReceiver(receiver)
                        } catch (_: Exception) {}
                }
        }

        // Synchronisation avec les métadonnées et la playlist
        DisposableEffect(exoPlayer) {
                val listener =
                        object : Player.Listener {
                                override fun onIsPlayingChanged(isPlaying: Boolean) {
                                        isActuallyPlaying = isPlaying
                                }
                        }
                exoPlayer.addListener(listener)
                onDispose { exoPlayer.removeListener(listener) }
        }

        // Cache pour les paroles : "Artist - Title" -> "Lyrics text"
        val lyricsCache = remember { mutableStateMapOf<String, String>() }

        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        // Logic to fetch lyrics with caching
        fun fetchLyrics() {
                if (lrcLibApi != null && !artist.isNullOrBlank() && !title.isNullOrBlank()) {
                        val a = artist
                        val t = title
                        val key = "$a - $t"
                        if (lyricsCache.containsKey(key)) {
                                lyricsText = lyricsCache[key]
                        } else {
                                isFetchingLyrics = true
                                scope.launch {
                                        try {
                                                val resp = lrcLibApi.getLyrics(a, t)
                                                val text =
                                                        resp.plainLyrics
                                                                ?: "Paroles non disponibles."
                                                lyricsText = text
                                                lyricsCache[key] = text
                                        } catch (_: Exception) {
                                                lyricsText = "Paroles introuvables."
                                        }
                                        isFetchingLyrics = false
                                }
                        }
                }
        }

        Box(
                modifier =
                        Modifier.fillMaxSize().background(Color.Black).clickable(enabled = false) {}
        ) {
                if (isLandscape) {
                        LandscapePlayerLayout(
                                currentStation = radioStation,
                                artist = artist,
                                title = title,
                                artworkUrl = effectiveArtworkUrl,
                                exoPlayer = exoPlayer,
                                isActuallyPlaying = isActuallyPlaying,
                                onBack = onBack,
                                onLyrics = {
                                        onToggleLyrics(!showLyrics)
                                        if (!showLyrics) fetchLyrics()
                                },
                                radioList = radioList,
                                showLyricsButton =
                                        lrcLibApi != null &&
                                                !artist.isNullOrBlank() &&
                                                !title.isNullOrBlank(),
                                showLyrics = showLyrics,
                                lyricsText = lyricsText,
                                isFetchingLyrics = isFetchingLyrics,
                                onCloseLyrics = { onToggleLyrics(false) },
                                castSession = castSession,
                                sleepTimerTimeLeft = sleepTimerTimeLeft,
                                onSetSleepTimer = onSetSleepTimer,
                                onCycleArtwork = {
                                        artworkCycleIndex = (artworkCycleIndex + 1) % cycleList.size
                                },
                                onTranslate = { toggleTranslation() },
                                isTranslating = isTranslating,
                                translatedLyrics = translatedLyrics
                        )
                } else {
                        PortraitPlayerLayout(
                                currentStation = radioStation,
                                artist = artist,
                                title = title,
                                artworkUrl = effectiveArtworkUrl,
                                exoPlayer = exoPlayer,
                                isActuallyPlaying = isActuallyPlaying,
                                onBack = onBack,
                                onLyrics = {
                                        onToggleLyrics(true)
                                        fetchLyrics()
                                },
                                radioList = radioList,
                                showLyricsButton =
                                        lrcLibApi != null &&
                                                !artist.isNullOrBlank() &&
                                                !title.isNullOrBlank(),
                                castSession = castSession,
                                sleepTimerTimeLeft = sleepTimerTimeLeft,
                                onSetSleepTimer = onSetSleepTimer,
                                onCycleArtwork = {
                                        artworkCycleIndex = (artworkCycleIndex + 1) % cycleList.size
                                }
                        )

                        // Portrait Full Screen Lyrics Overlay
                        if (showLyrics) {
                                Box(
                                        modifier =
                                                Modifier.fillMaxSize()
                                                        .background(Color.Black)
                                                        .zIndex(10f) // Au dessus de tout
                                                        .padding(16.dp)
                                ) {
                                        Column(modifier = Modifier.fillMaxSize()) {
                                                Row(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(bottom = 16.dp),
                                                        horizontalArrangement =
                                                                Arrangement.SpaceBetween,
                                                        verticalAlignment =
                                                                Alignment.CenterVertically
                                                ) {
                                                        TextButton(
                                                                onClick = { toggleTranslation() }
                                                        ) {
                                                                Text(
                                                                        if (isTranslating)
                                                                                "Original"
                                                                        else "Traduire (FR)",
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primary
                                                                )
                                                        }
                                                        Button(
                                                                onClick = { onToggleLyrics(false) },
                                                                colors =
                                                                        ButtonDefaults.buttonColors(
                                                                                containerColor =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .primary
                                                                        )
                                                        ) { Text("Fermer") }
                                                }

                                                if (isFetchingLyrics) {
                                                        Box(
                                                                modifier = Modifier.fillMaxSize(),
                                                                contentAlignment = Alignment.Center
                                                        ) { CircularProgressIndicator() }
                                                } else {
                                                        BilingualLyrics(
                                                                original = lyricsText ?: "",
                                                                translated = translatedLyrics,
                                                                isTranslating = isTranslating,
                                                                modifier = Modifier.fillMaxSize()
                                                        )
                                                }
                                        }
                                }
                        }
                }
        }
}

@Composable
fun PortraitPlayerLayout(
        currentStation: RadioStationEntity?,
        artist: String?,
        title: String?,
        artworkUrl: String?,
        exoPlayer: Player,
        isActuallyPlaying: Boolean,
        onBack: () -> Unit,
        onLyrics: () -> Unit,
        radioList: List<RadioStationEntity>,
        showLyricsButton: Boolean,
        castSession: CastSession? = null,
        sleepTimerTimeLeft: Long? = null,
        onSetSleepTimer: (Int?) -> Unit = {},
        onCycleArtwork: () -> Unit = {}
) {
        Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        // Back Button
                        var isBackFocused by remember { mutableStateOf(false) }
                        IconButton(
                                onClick = onBack,
                                modifier =
                                        Modifier.onFocusChanged { isBackFocused = it.isFocused }
                                                .background(
                                                        if (isBackFocused) Color.White
                                                        else Color.White.copy(alpha = 0.2f),
                                                        CircleShape
                                                )
                        ) {
                                Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        null,
                                        tint = if (isBackFocused) Color.Black else Color.White,
                                        modifier = Modifier.size(32.dp)
                                )
                        }

                        // --- BOUTON TIMER ---
                        Box {
                                var showTimerMenu by remember { mutableStateOf(false) }
                                var isTimerFocused by remember { mutableStateOf(false) }
                                IconButton(
                                        onClick = { showTimerMenu = true },
                                        modifier =
                                                Modifier.onFocusChanged {
                                                                isTimerFocused = it.isFocused
                                                        }
                                                        .background(
                                                                if (isTimerFocused) Color.White
                                                                else if (sleepTimerTimeLeft != null)
                                                                        MaterialTheme.colorScheme
                                                                                .primary.copy(
                                                                                alpha = 0.3f
                                                                        )
                                                                else Color.White.copy(alpha = 0.1f),
                                                                CircleShape
                                                        )
                                ) {
                                        Icon(
                                                Icons.Default.Timer,
                                                null,
                                                tint =
                                                        if (isTimerFocused) Color.Black
                                                        else if (sleepTimerTimeLeft != null)
                                                                MaterialTheme.colorScheme.primary
                                                        else Color.White,
                                                modifier = Modifier.size(28.dp)
                                        )
                                        if (sleepTimerTimeLeft != null && !isTimerFocused) {
                                                Text(
                                                        text = "${sleepTimerTimeLeft / 60000}m",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color.White,
                                                        modifier =
                                                                Modifier.align(
                                                                                Alignment
                                                                                        .BottomCenter
                                                                        )
                                                                        .padding(bottom = 2.dp)
                                                )
                                        }
                                }
                                DropdownMenu(
                                        expanded = showTimerMenu,
                                        onDismissRequest = { showTimerMenu = false }
                                ) {
                                        listOf(10, 20, 30, 40).forEach { mins ->
                                                DropdownMenuItem(
                                                        text = { Text("$mins min") },
                                                        onClick = {
                                                                onSetSleepTimer(mins)
                                                                showTimerMenu = false
                                                        }
                                                )
                                        }
                                        if (sleepTimerTimeLeft != null) {
                                                DropdownMenuItem(
                                                        text = {
                                                                Text(
                                                                        "Annuler le timer",
                                                                        color = Color.Red
                                                                )
                                                        },
                                                        onClick = {
                                                                onSetSleepTimer(null)
                                                                showTimerMenu = false
                                                        }
                                                )
                                        }
                                }
                        }

                        // --- CAST BUTTON (Always Visible) ---
                        // Note: MediaRouteButton handles its own colors usually, hard to style
                        // exactly like our
                        // custom buttons
                        // without custom drawable, but we'll try to keep it standard or wrap it.
                        // For now, standard AndroidView integration.
                        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                AndroidView(
                                        factory = { ctx ->
                                                MediaRouteButton(ctx).apply {
                                                        CastButtonFactory.setUpMediaRouteButton(
                                                                ctx,
                                                                this
                                                        )
                                                        // Try to force visibility (API
                                                        // might vary, usually assumes
                                                        // always
                                                        // visible if set so)
                                                        // setAlwaysVisible(true) is a
                                                        // method on MediaRouteButton? Yes.
                                                        try {
                                                                @Suppress("DEPRECATION")
                                                                this.setAlwaysVisible(true)
                                                        } catch (_: Exception) {}
                                                }
                                        }
                                )
                        }

                        // --- LYRICS BUTTON ---
                        if (showLyricsButton) {
                                var isLyricsFocused by remember { mutableStateOf(false) }
                                IconButton(
                                        onClick = onLyrics,
                                        modifier =
                                                Modifier.onFocusChanged {
                                                                isLyricsFocused = it.isFocused
                                                        }
                                                        .background(
                                                                if (isLyricsFocused) Color.White
                                                                else Color.Transparent,
                                                                CircleShape
                                                        )
                                ) {
                                        Icon(
                                                Icons.AutoMirrored.Filled
                                                        .Subject, // Or similar for Lyrics
                                                "Lyrics",
                                                tint =
                                                        if (isLyricsFocused) Color.Black
                                                        else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(32.dp)
                                        )
                                }
                        } else {
                                Spacer(Modifier.size(48.dp))
                        }
                }

                // ZONE 1 : LOGO/POCHETTE
                Box(modifier = Modifier.fillMaxWidth().weight(0.5f).background(Color.Black)) {
                        // Fond par défaut
                        Icon(
                                Icons.Default.Radio,
                                contentDescription = null,
                                modifier = Modifier.align(Alignment.Center).size(120.dp),
                                tint = Color.White.copy(alpha = 0.2f)
                        )

                        if (artworkUrl != null) {
                                AsyncImage(
                                        model = artworkUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                )
                        }

                        // --- BOUTON SYNC (Cycling) ---
                        var isSyncFocused by remember { mutableStateOf(false) }
                        IconButton(
                                onClick = onCycleArtwork,
                                modifier =
                                        Modifier.align(Alignment.BottomEnd)
                                                .padding(8.dp)
                                                .onFocusChanged { isSyncFocused = it.isFocused }
                                                .background(
                                                        if (isSyncFocused) Color.Black
                                                        else Color.Black.copy(alpha = 0.3f),
                                                        CircleShape
                                                )
                                                .size(40.dp)
                        ) {
                                Icon(
                                        Icons.Default.Sync,
                                        null,
                                        tint =
                                                if (isSyncFocused) Color.White
                                                else Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.size(20.dp)
                                )
                        }
                }

                // ZONE 2 : INFOS STATION (20% Hauteur)
                Column(
                        modifier = Modifier.fillMaxWidth().weight(0.2f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                ) {
                        Text(
                                text = currentStation?.name ?: "Station inconnue",
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                maxLines = 1
                        )
                        Text(
                                text =
                                        "${currentStation?.country ?: "Monde"} | ${currentStation?.bitrate ?: "?"} kbps",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray,
                                maxLines = 1
                        )
                }

                Row(
                        modifier = Modifier.fillMaxWidth().weight(0.1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement =
                                Arrangement.spacedBy(48.dp, Alignment.CenterHorizontally)
                ) {
                        // PREVIOUS
                        var isPrevFocused by remember { mutableStateOf(false) }
                        IconButton(
                                onClick = {
                                        val idx =
                                                radioList.indexOfFirst {
                                                        it.stationuuid ==
                                                                currentStation?.stationuuid
                                                }
                                        if (idx > 0) {
                                                exoPlayer.seekToPrevious()
                                        }
                                },
                                modifier =
                                        Modifier.onFocusChanged { isPrevFocused = it.isFocused }
                                                .background(
                                                        if (isPrevFocused) Color.White
                                                        else Color.Transparent,
                                                        CircleShape
                                                )
                        ) {
                                Icon(
                                        Icons.Default.SkipPrevious,
                                        null,
                                        tint = if (isPrevFocused) Color.Black else Color.White,
                                        modifier = Modifier.size(44.dp)
                                )
                        }

                        // PLAY/PAUSE
                        var isPlayFocused by remember { mutableStateOf(false) }
                        IconButton(
                                onClick = {
                                        if (castSession != null && castSession.isConnected) {
                                                val client = castSession.remoteMediaClient
                                                if (client?.isPlaying == true) client.pause()
                                                else client?.play()
                                        } else {
                                                if (isActuallyPlaying) exoPlayer.pause()
                                                else exoPlayer.play()
                                        }
                                },
                                modifier =
                                        Modifier.onFocusChanged { isPlayFocused = it.isFocused }
                                                .background(
                                                        if (isPlayFocused) Color.White
                                                        else Color.Transparent,
                                                        CircleShape
                                                )
                        ) {
                                Icon(
                                        if (isActuallyPlaying) Icons.Default.PauseCircleFilled
                                        else Icons.Default.PlayCircleFilled,
                                        null,
                                        tint =
                                                if (isPlayFocused) Color.Black
                                                else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(68.dp)
                                )
                        }

                        // NEXT
                        var isNextFocused by remember { mutableStateOf(false) }
                        IconButton(
                                onClick = {
                                        val idx =
                                                radioList.indexOfFirst {
                                                        it.stationuuid ==
                                                                currentStation?.stationuuid
                                                }
                                        if (idx >= 0 && idx < radioList.size - 1) {
                                                exoPlayer.seekToNext()
                                        }
                                },
                                modifier =
                                        Modifier.onFocusChanged { isNextFocused = it.isFocused }
                                                .background(
                                                        if (isNextFocused) Color.White
                                                        else Color.Transparent,
                                                        CircleShape
                                                )
                        ) {
                                Icon(
                                        Icons.Default.SkipNext,
                                        null,
                                        tint = if (isNextFocused) Color.Black else Color.White,
                                        modifier = Modifier.size(44.dp)
                                )
                        }
                }

                // ZONE 4 : INFOS ARTISTE / TITRE (20% Hauteur)
                Column(
                        modifier = Modifier.fillMaxWidth().weight(0.2f).padding(bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                ) {
                        // Uniquement si on a de VRAIES métadonnées de chanson
                        if (!artist.isNullOrBlank() || !title.isNullOrBlank()) {
                                Text(
                                        text = artist ?: "",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1
                                )
                                Text(
                                        text = title ?: "",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color.White,
                                        maxLines = 1
                                )
                        } else {
                                // Sinon on affiche juste un rappel discret de la station si besoin,
                                // mais on évite de polluer les champs Artiste/Titre
                                Text(
                                        text = currentStation?.name ?: "",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.Gray
                                )
                        }
                }
        }
}

@Composable
fun LandscapePlayerLayout(
        currentStation: RadioStationEntity?,
        artist: String?,
        title: String?,
        artworkUrl: String?,
        exoPlayer: Player,
        isActuallyPlaying: Boolean,
        onBack: () -> Unit,
        onLyrics: () -> Unit,
        radioList: List<RadioStationEntity>,
        showLyricsButton: Boolean,
        showLyrics: Boolean = false,
        lyricsText: String? = null,
        isFetchingLyrics: Boolean = false,
        onCloseLyrics: () -> Unit = {},
        castSession: CastSession? = null,
        sleepTimerTimeLeft: Long? = null,
        onSetSleepTimer: (Int?) -> Unit = {},
        onCycleArtwork: () -> Unit = {},
        onTranslate: () -> Unit = {},
        isTranslating: Boolean = false,
        translatedLyrics: String? = null
) {

        // Gestion du Back Press (Niv 3 > Niv 2 > Niv 1)
        BackHandler(enabled = true) {
                if (showLyrics) {
                        onCloseLyrics()
                } else {
                        onBack()
                }
        }

        Box(modifier = Modifier.fillMaxSize()) {
                // --- NIV 2 : PLAYER (Fond) ---
                if (!showLyrics) {
                        Row(modifier = Modifier.fillMaxSize()) {
                                // GAUCHE (7/16) - Controles et Infos
                                Column(
                                        modifier =
                                                Modifier.weight(7f).fillMaxHeight().padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                        // ZONE 1 (15%) : Boutons Back, Timer et Lyrics
                                        Row(
                                                modifier = Modifier.fillMaxWidth().weight(0.15f),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                var isBackFocused by remember {
                                                        mutableStateOf(false)
                                                }
                                                IconButton(
                                                        onClick = onBack,
                                                        modifier =
                                                                Modifier.onFocusChanged {
                                                                                isBackFocused =
                                                                                        it.isFocused
                                                                        }
                                                                        .background(
                                                                                if (isBackFocused)
                                                                                        Color.White
                                                                                else
                                                                                        Color.White
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.15f
                                                                                                ),
                                                                                MaterialTheme.shapes
                                                                                        .small
                                                                        )
                                                ) {
                                                        Icon(
                                                                Icons.AutoMirrored.Filled.ArrowBack,
                                                                null,
                                                                tint =
                                                                        if (isBackFocused)
                                                                                Color.Black
                                                                        else Color.White,
                                                                modifier = Modifier.size(32.dp)
                                                        )
                                                }

                                                // --- BOUTON TIMER (Landscape) ---
                                                Box {
                                                        var showTimerMenu by remember {
                                                                mutableStateOf(false)
                                                        }
                                                        var isTimerFocused by remember {
                                                                mutableStateOf(false)
                                                        }
                                                        IconButton(
                                                                onClick = { showTimerMenu = true },
                                                                modifier =
                                                                        Modifier.onFocusChanged {
                                                                                        isTimerFocused =
                                                                                                it.isFocused
                                                                                }
                                                                                .background(
                                                                                        if (isTimerFocused
                                                                                        )
                                                                                                Color.White
                                                                                        else if (sleepTimerTimeLeft !=
                                                                                                        null
                                                                                        )
                                                                                                MaterialTheme
                                                                                                        .colorScheme
                                                                                                        .primary
                                                                                                        .copy(
                                                                                                                alpha =
                                                                                                                        0.3f
                                                                                                        )
                                                                                        else
                                                                                                Color.White
                                                                                                        .copy(
                                                                                                                alpha =
                                                                                                                        0.15f
                                                                                                        ),
                                                                                        MaterialTheme
                                                                                                .shapes
                                                                                                .small
                                                                                )
                                                        ) {
                                                                Icon(
                                                                        Icons.Default.Timer,
                                                                        null,
                                                                        tint =
                                                                                if (isTimerFocused)
                                                                                        Color.Black
                                                                                else if (sleepTimerTimeLeft !=
                                                                                                null
                                                                                )
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .primary
                                                                                else Color.White,
                                                                        modifier =
                                                                                Modifier.size(28.dp)
                                                                )
                                                        }
                                                        DropdownMenu(
                                                                expanded = showTimerMenu,
                                                                onDismissRequest = {
                                                                        showTimerMenu = false
                                                                }
                                                        ) {
                                                                listOf(10, 20, 30, 40).forEach {
                                                                        mins ->
                                                                        DropdownMenuItem(
                                                                                text = {
                                                                                        Text(
                                                                                                "$mins min"
                                                                                        )
                                                                                },
                                                                                onClick = {
                                                                                        onSetSleepTimer(
                                                                                                mins
                                                                                        )
                                                                                        showTimerMenu =
                                                                                                false
                                                                                }
                                                                        )
                                                                }
                                                                if (sleepTimerTimeLeft != null) {
                                                                        DropdownMenuItem(
                                                                                text = {
                                                                                        Text(
                                                                                                "Annuler",
                                                                                                color =
                                                                                                        Color.Red
                                                                                        )
                                                                                },
                                                                                onClick = {
                                                                                        onSetSleepTimer(
                                                                                                null
                                                                                        )
                                                                                        showTimerMenu =
                                                                                                false
                                                                                }
                                                                        )
                                                                }
                                                        }
                                                }

                                                // --- CAST BUTTON (Always Visible) ---
                                                Box(
                                                        modifier = Modifier.size(48.dp),
                                                        contentAlignment = Alignment.Center
                                                ) {
                                                        AndroidView(
                                                                factory = { ctx ->
                                                                        MediaRouteButton(ctx)
                                                                                .apply {
                                                                                        CastButtonFactory
                                                                                                .setUpMediaRouteButton(
                                                                                                        ctx,
                                                                                                        this
                                                                                                )
                                                                                        try {
                                                                                                @Suppress(
                                                                                                        "DEPRECATION"
                                                                                                )
                                                                                                this.setAlwaysVisible(
                                                                                                        true
                                                                                                )
                                                                                        } catch (
                                                                                                _:
                                                                                                        Exception) {}
                                                                                }
                                                                }
                                                        )
                                                }

                                                // --- LYRICS BUTTON (Always Reachable - Box Style)
                                                // ---
                                                val context = LocalContext.current
                                                var isLyricsFocused by remember {
                                                        mutableStateOf(false)
                                                }

                                                Box(
                                                        modifier =
                                                                Modifier.height(40.dp)
                                                                        .onFocusChanged {
                                                                                isLyricsFocused =
                                                                                        it.isFocused
                                                                        }
                                                                        .background(
                                                                                color =
                                                                                        if (isLyricsFocused
                                                                                        )
                                                                                                Color.White
                                                                                        else if (showLyricsButton
                                                                                        )
                                                                                                MaterialTheme
                                                                                                        .colorScheme
                                                                                                        .surfaceVariant
                                                                                                        .copy(
                                                                                                                alpha =
                                                                                                                        0.3f
                                                                                                        )
                                                                                        else
                                                                                                Color.Transparent,
                                                                                shape =
                                                                                        MaterialTheme
                                                                                                .shapes
                                                                                                .small
                                                                        )
                                                                        .border(
                                                                                width = 1.dp,
                                                                                color =
                                                                                        if (isLyricsFocused
                                                                                        )
                                                                                                Color.Transparent
                                                                                        else if (showLyricsButton
                                                                                        )
                                                                                                MaterialTheme
                                                                                                        .colorScheme
                                                                                                        .primary
                                                                                                        .copy(
                                                                                                                alpha =
                                                                                                                        0.5f
                                                                                                        )
                                                                                        else
                                                                                                Color.Gray
                                                                                                        .copy(
                                                                                                                alpha =
                                                                                                                        0.3f
                                                                                                        ),
                                                                                shape =
                                                                                        MaterialTheme
                                                                                                .shapes
                                                                                                .small
                                                                        )
                                                                        .clickable {
                                                                                if (showLyricsButton
                                                                                ) {
                                                                                        onLyrics()
                                                                                } else {
                                                                                        Toast.makeText(
                                                                                                        context,
                                                                                                        "Pas de paroles disponibles",
                                                                                                        Toast.LENGTH_SHORT
                                                                                                )
                                                                                                .show()
                                                                                }
                                                                        }
                                                                        .padding(
                                                                                horizontal = 12.dp
                                                                        ),
                                                        contentAlignment = Alignment.Center
                                                ) {
                                                        Row(
                                                                verticalAlignment =
                                                                        Alignment.CenterVertically
                                                        ) {
                                                                Icon(
                                                                        imageVector =
                                                                                Icons.AutoMirrored
                                                                                        .Filled
                                                                                        .Subject,
                                                                        contentDescription =
                                                                                "Lyrics",
                                                                        tint =
                                                                                if (isLyricsFocused)
                                                                                        Color.Black
                                                                                else if (showLyricsButton
                                                                                )
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .primary
                                                                                else Color.Gray,
                                                                        modifier =
                                                                                Modifier.size(20.dp)
                                                                )
                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.width(8.dp)
                                                                )
                                                                Text(
                                                                        text = "PAROLES",
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .labelMedium,
                                                                        color =
                                                                                if (isLyricsFocused)
                                                                                        Color.Black
                                                                                else if (showLyricsButton
                                                                                )
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .primary
                                                                                else Color.Gray
                                                                )
                                                        }
                                                }
                                        }

                                        // ZONE 2 (35%) : Nom Radio et Pays
                                        Column(
                                                modifier = Modifier.weight(0.35f).fillMaxWidth(),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                        ) {
                                                Text(
                                                        text = currentStation?.name
                                                                        ?: "Station inconnue",
                                                        style =
                                                                MaterialTheme.typography
                                                                        .headlineMedium,
                                                        color = Color.White,
                                                        maxLines = 2,
                                                        textAlign = TextAlign.Center
                                                )
                                                Spacer(Modifier.height(8.dp))
                                                Text(
                                                        text =
                                                                "${currentStation?.country ?: "Monde"} | ${currentStation?.bitrate ?: "?"} kbps",
                                                        style =
                                                                MaterialTheme.typography
                                                                        .titleMedium,
                                                        color = Color.Gray,
                                                        maxLines = 1
                                                )
                                        }

                                        // ZONE 3 (15%) : Boutons Lecture
                                        Row(
                                                modifier = Modifier.fillMaxWidth().weight(0.15f),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                        ) {
                                                var isPrevFocused by remember {
                                                        mutableStateOf(false)
                                                }
                                                IconButton(
                                                        onClick = {
                                                                val idx =
                                                                        radioList.indexOfFirst {
                                                                                it.stationuuid ==
                                                                                        currentStation
                                                                                                ?.stationuuid
                                                                        }
                                                                if (idx > 0) {
                                                                        exoPlayer.seekToPrevious()
                                                                }
                                                        },
                                                        modifier =
                                                                Modifier.onFocusChanged {
                                                                                isPrevFocused =
                                                                                        it.isFocused
                                                                        }
                                                                        .background(
                                                                                if (isPrevFocused)
                                                                                        Color.White
                                                                                else
                                                                                        Color.Transparent,
                                                                                CircleShape
                                                                        )
                                                ) {
                                                        Icon(
                                                                Icons.Default.SkipPrevious,
                                                                null,
                                                                tint =
                                                                        if (isPrevFocused)
                                                                                Color.Black
                                                                        else Color.White,
                                                                modifier = Modifier.size(48.dp)
                                                        )
                                                }

                                                Spacer(Modifier.width(32.dp))

                                                var isPlayFocused by remember {
                                                        mutableStateOf(false)
                                                }
                                                IconButton(
                                                        onClick = {
                                                                if (castSession != null &&
                                                                                castSession
                                                                                        .isConnected
                                                                ) {
                                                                        val client =
                                                                                castSession
                                                                                        .remoteMediaClient
                                                                        if (client?.isPlaying ==
                                                                                        true
                                                                        )
                                                                                client.pause()
                                                                        else client?.play()
                                                                } else {
                                                                        if (isActuallyPlaying)
                                                                                exoPlayer.pause()
                                                                        else exoPlayer.play()
                                                                }
                                                        },
                                                        modifier =
                                                                Modifier.onFocusChanged {
                                                                                isPlayFocused =
                                                                                        it.isFocused
                                                                        }
                                                                        .background(
                                                                                if (isPlayFocused)
                                                                                        Color.White
                                                                                else
                                                                                        Color.Transparent,
                                                                                CircleShape
                                                                        )
                                                ) {
                                                        Icon(
                                                                if (isActuallyPlaying)
                                                                        Icons.Default
                                                                                .PauseCircleFilled
                                                                else Icons.Default.PlayCircleFilled,
                                                                null,
                                                                tint =
                                                                        if (isPlayFocused)
                                                                                Color.Black
                                                                        else
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primary,
                                                                modifier = Modifier.size(80.dp)
                                                        )
                                                }

                                                Spacer(Modifier.width(32.dp))

                                                var isNextFocused by remember {
                                                        mutableStateOf(false)
                                                }
                                                IconButton(
                                                        onClick = {
                                                                val idx =
                                                                        radioList.indexOfFirst {
                                                                                it.stationuuid ==
                                                                                        currentStation
                                                                                                ?.stationuuid
                                                                        }
                                                                if (idx >= 0 &&
                                                                                idx <
                                                                                        radioList
                                                                                                .size -
                                                                                                1
                                                                ) {
                                                                        exoPlayer.seekToNext()
                                                                }
                                                        },
                                                        modifier =
                                                                Modifier.onFocusChanged {
                                                                                isNextFocused =
                                                                                        it.isFocused
                                                                        }
                                                                        .background(
                                                                                if (isNextFocused)
                                                                                        Color.White
                                                                                else
                                                                                        Color.Transparent,
                                                                                CircleShape
                                                                        )
                                                ) {
                                                        Icon(
                                                                Icons.Default.SkipNext,
                                                                null,
                                                                tint =
                                                                        if (isNextFocused)
                                                                                Color.Black
                                                                        else Color.White,
                                                                modifier = Modifier.size(48.dp)
                                                        )
                                                }
                                        }

                                        // ZONE 4 (35%) : Artist / Title
                                        Column(
                                                modifier = Modifier.weight(0.35f).fillMaxWidth(),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                        ) {
                                                Text(
                                                        text = artist ?: "",
                                                        style =
                                                                MaterialTheme.typography
                                                                        .headlineMedium,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        maxLines = 2,
                                                        textAlign = TextAlign.Center
                                                )
                                                Spacer(Modifier.height(8.dp))
                                                Text(
                                                        text = title ?: "",
                                                        style = MaterialTheme.typography.titleLarge,
                                                        color = Color.White,
                                                        maxLines = 3,
                                                        textAlign = TextAlign.Center
                                                )
                                        }
                                }

                                // DROITE (9/16) - Logo/Artwork
                                Box(
                                        modifier =
                                                Modifier.weight(9f)
                                                        .fillMaxHeight()
                                                        .background(Color.Black)
                                ) {
                                        // Fond par défaut
                                        Icon(
                                                Icons.Default.Radio,
                                                contentDescription = null,
                                                modifier =
                                                        Modifier.align(Alignment.Center)
                                                                .size(160.dp),
                                                tint = Color.White.copy(alpha = 0.2f)
                                        )

                                        if (artworkUrl != null) {
                                                AsyncImage(
                                                        model = artworkUrl,
                                                        contentDescription = null,
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Fit
                                                )
                                        }

                                        // --- BOUTON SYNC (Cycling - Landscape) ---
                                        // --- BOUTON SYNC (Cycling - Landscape) ---
                                        var isSyncFocused by remember { mutableStateOf(false) }
                                        IconButton(
                                                onClick = onCycleArtwork,
                                                modifier =
                                                        Modifier.align(Alignment.BottomEnd)
                                                                .padding(16.dp)
                                                                .onFocusChanged {
                                                                        isSyncFocused = it.isFocused
                                                                }
                                                                .background(
                                                                        if (isSyncFocused)
                                                                                Color.Black
                                                                        else
                                                                                Color.Black.copy(
                                                                                        alpha = 0.3f
                                                                                ),
                                                                        CircleShape
                                                                )
                                                                .size(48.dp)
                                        ) {
                                                Icon(
                                                        Icons.Default.Sync,
                                                        null,
                                                        tint =
                                                                if (isSyncFocused) Color.White
                                                                else Color.White.copy(alpha = 0.5f),
                                                        modifier = Modifier.size(24.dp)
                                                )
                                        }
                                }
                        }
                }

                // --- NIV 3 : LYRICS OVERLAY (Flottant) ---
                // Occupe 75% de la largeur totale (soit 12/16), aligné à gauche.
                if (showLyrics) {
                        val closeButtonFocusRequester = remember { FocusRequester() }

                        // Force le focus sur le bouton Fermer dès l'ouverture
                        LaunchedEffect(Unit) {
                                try {
                                        closeButtonFocusRequester.requestFocus()
                                } catch (_: Exception) {}
                        }

                        Box(
                                modifier =
                                        Modifier.fillMaxHeight()
                                                .fillMaxWidth(0.75f) // 12/16 de l'écran (= 75%)
                                                .align(Alignment.CenterStart)
                                                .background(Color.Black) // Totalement opaque
                                                .zIndex(10f) // S'assure d'être au-dessus
                                                .padding(24.dp)
                        ) {
                                if (isFetchingLyrics) {
                                        CircularProgressIndicator(
                                                modifier = Modifier.align(Alignment.Center)
                                        )
                                } else {
                                        Column(modifier = Modifier.fillMaxSize()) {
                                                Row(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(bottom = 16.dp),
                                                        horizontalArrangement =
                                                                Arrangement.SpaceBetween,
                                                        verticalAlignment =
                                                                Alignment.CenterVertically
                                                ) {
                                                        var isTranslateFocused by remember {
                                                                mutableStateOf(false)
                                                        }
                                                        TextButton(
                                                                onClick = onTranslate,
                                                                modifier =
                                                                        Modifier.onFocusChanged {
                                                                                        isTranslateFocused =
                                                                                                it.isFocused
                                                                                }
                                                                                .background(
                                                                                        if (isTranslateFocused
                                                                                        )
                                                                                                Color.White
                                                                                        else
                                                                                                Color.Transparent,
                                                                                        MaterialTheme
                                                                                                .shapes
                                                                                                .small
                                                                                )
                                                        ) {
                                                                Text(
                                                                        if (isTranslating)
                                                                                "Original"
                                                                        else "Translate (FR)",
                                                                        color =
                                                                                if (isTranslateFocused
                                                                                )
                                                                                        Color.Black
                                                                                else
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .primary
                                                                )
                                                        }
                                                        var isCloseFocused by remember {
                                                                mutableStateOf(false)
                                                        }
                                                        IconButton(
                                                                onClick = onCloseLyrics,
                                                                modifier =
                                                                        Modifier.focusRequester(
                                                                                        closeButtonFocusRequester
                                                                                )
                                                                                .onFocusChanged {
                                                                                        isCloseFocused =
                                                                                                it.isFocused
                                                                                }
                                                                                .background(
                                                                                        if (isCloseFocused
                                                                                        )
                                                                                                Color.White
                                                                                        else
                                                                                                Color.Transparent,
                                                                                        CircleShape
                                                                                )
                                                        ) {
                                                                Icon(
                                                                        Icons.Default.Close,
                                                                        null,
                                                                        tint =
                                                                                if (isCloseFocused)
                                                                                        Color.Black
                                                                                else Color.White
                                                                )
                                                        }
                                                }

                                                BilingualLyrics(
                                                        original = lyricsText ?: "",
                                                        translated = translatedLyrics,
                                                        isTranslating = isTranslating,
                                                        modifier = Modifier.fillMaxSize()
                                                )
                                        }
                                }

                                // Petit bouton fermer discret en haut à droite de l'overlay pour la
                                // souris/touch
                                IconButton(
                                        onClick = onCloseLyrics,
                                        modifier = Modifier.align(Alignment.TopEnd)
                                ) { Icon(Icons.Default.Close, "Fermer", tint = Color.Gray) }
                        }
                }
        }
}

@Composable
fun BilingualLyrics(
        original: String,
        translated: String?,
        isTranslating: Boolean,
        modifier: Modifier = Modifier
) {
        val originalLines = remember(original) { original.split("\n") }
        val translatedLines = remember(translated) { translated?.split("\n") ?: emptyList() }
        val listState = rememberLazyListState()

        LazyColumn(state = listState, modifier = modifier.padding(top = 8.dp)) {
                items(originalLines.size) { i ->
                        val orig = originalLines[i]
                        if (orig.isBlank()) {
                                Spacer(Modifier.height(16.dp))
                        } else {
                                var isFocused by remember { mutableStateOf(false) }
                                Column(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .padding(vertical = 4.dp)
                                                        .background(
                                                                if (isFocused)
                                                                        Color(
                                                                                0xFF252525
                                                                        ) // Gris foncé proche dark
                                                                // theme
                                                                else Color.Transparent,
                                                                MaterialTheme.shapes.small
                                                        )
                                                        .onFocusChanged { isFocused = it.isFocused }
                                                        .focusable() // MAGIC: Allows D-Pad
                                                        // navigation line by line
                                                        .padding(8.dp)
                                ) {
                                        Text(
                                                text = orig,
                                                style = MaterialTheme.typography.headlineSmall,
                                                color = Color.White
                                        )
                                        if (isTranslating &&
                                                        translated != null &&
                                                        i < translatedLines.size &&
                                                        translatedLines[i].isNotBlank()
                                        ) {
                                                Text(
                                                        text = translatedLines[i],
                                                        style =
                                                                MaterialTheme.typography
                                                                        .titleMedium,
                                                        color = Color.Gray
                                                )
                                        }
                                }
                        }
                }
        }
}
