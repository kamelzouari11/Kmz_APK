package com.example.simpleradio

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.mediarouter.app.MediaRouteButton
import coil.compose.AsyncImage
import com.example.simpleradio.data.RadioRepository
import com.example.simpleradio.data.api.ITunesApi
import com.example.simpleradio.data.api.ITunesClient
import com.example.simpleradio.data.api.LyricsClient
import com.example.simpleradio.data.api.RadioClient
import com.example.simpleradio.data.local.AppDatabase
import com.example.simpleradio.data.local.entities.*
import com.example.simpleradio.data.model.RadioCountry
import com.example.simpleradio.data.model.RadioTag
import com.example.simpleradio.ui.theme.SimpleRADIOTheme
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialisation précoce du CastContext pour éviter le crash au clic
        try {
            CastContext.getSharedInstance(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val database = AppDatabase.getDatabase(this)
        val radioApi = RadioClient.create()
        val radioRepository = RadioRepository(radioApi, database.radioDao())
        val iTunesApi = ITunesClient.create()
        val lrcLibApi = LyricsClient.createLrcLib()
        setContent {
            SimpleRADIOTheme(darkTheme = true) { MainScreen(radioRepository, iTunesApi, lrcLibApi) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
        radioRepository: RadioRepository,
        iTunesApi: ITunesApi,
        lrcLibApi: com.example.simpleradio.data.api.LrcLibApi
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // --- Launchers pour Sauvegarde/Restauration ---
    val exportLauncher =
            androidx.activity.compose.rememberLauncherForActivityResult(
                    contract =
                            androidx.activity.result.contract.ActivityResultContracts
                                    .CreateDocument("application/json")
            ) { uri ->
                uri?.let {
                    scope.launch {
                        try {
                            val json = radioRepository.exportFavoritesToJson()
                            context.contentResolver.openOutputStream(it)?.use { out ->
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
            androidx.activity.compose.rememberLauncherForActivityResult(
                    contract =
                            androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
            ) { uri ->
                uri?.let {
                    scope.launch {
                        try {
                            val json =
                                    context.contentResolver
                                            .openInputStream(it)
                                            ?.bufferedReader()
                                            ?.use { reader -> reader.readText() }
                            if (json != null) {
                                radioRepository.importFavoritesFromJson(json)
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

    var playingRadio by remember { mutableStateOf<RadioStationEntity?>(null) }
    var navRadioList by remember { mutableStateOf<List<RadioStationEntity>>(emptyList()) }
    var isFullScreenPlayer by remember { mutableStateOf(false) }
    var playerIsPlaying by remember { mutableStateOf(false) }

    val controllerFuture = remember {
        MediaController.Builder(
                        context,
                        SessionToken(context, ComponentName(context, PlaybackService::class.java))
                )
                .buildAsync()
    }
    var exoPlayer by remember { mutableStateOf<androidx.media3.common.Player?>(null) }

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
                Toast.makeText(context, "Mise en veille activée", Toast.LENGTH_SHORT).show()
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
                                    val station = radioRepository.getStationByUuid(item.mediaId)
                                    if (station != null) {
                                        playingRadio = station
                                    }
                                }
                            }
                        }
                        exoPlayer?.addListener(
                                object : androidx.media3.common.Player.Listener {
                                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                                        playerIsPlaying = isPlaying
                                    }
                                    override fun onMediaItemTransition(
                                            mediaItem: MediaItem?,
                                            reason: Int
                                    ) {
                                        val newId = mediaItem?.mediaId ?: return
                                        val currentList = navRadioList
                                        val station = currentList.find { it.stationuuid == newId }
                                        if (station != null) {
                                            if (station.stationuuid != playingRadio?.stationuuid) {
                                                currentArtist = null
                                                currentTitle = null
                                                currentArtworkUrl = null
                                                playingRadio = station
                                                scope.launch {
                                                    radioRepository.addToRecents(
                                                            station.stationuuid
                                                    )
                                                }
                                            }
                                        } else {
                                            // Fallback to database if not in current list
                                            scope.launch {
                                                val fetched =
                                                        radioRepository.getStationByUuid(newId)
                                                if (fetched != null &&
                                                                fetched.stationuuid !=
                                                                        playingRadio?.stationuuid
                                                ) {
                                                    currentArtist = null
                                                    currentTitle = null
                                                    currentArtworkUrl = null
                                                    playingRadio = fetched
                                                    radioRepository.addToRecents(
                                                            fetched.stationuuid
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    override fun onPlayerError(
                                            error: androidx.media3.common.PlaybackException
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
                    } catch (e: Exception) {
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
            } catch (e: Exception) {}
        }
    }

    DisposableEffect(Unit) { onDispose { MediaController.releaseFuture(controllerFuture) } }

    // --- RADIO STATE ---
    val radioFavoriteLists by radioRepository.allFavoriteLists.collectAsState(initial = emptyList())
    val recentRadios by radioRepository.recentRadios.collectAsState(initial = emptyList())
    var radioCountries by remember { mutableStateOf<List<RadioCountry>>(emptyList()) }

    var isCastAvailable by remember { mutableStateOf(false) }
    var castSession by remember {
        mutableStateOf<com.google.android.gms.cast.framework.CastSession?>(null)
    }

    LaunchedEffect(Unit) {
        try {
            if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) ==
                            ConnectionResult.SUCCESS
            ) {
                val castContext =
                        try {
                            CastContext.getSharedInstance(context)
                        } catch (e: Exception) {
                            null
                        }
                if (castContext != null) {
                    isCastAvailable = true
                    castContext.sessionManager.addSessionManagerListener(
                            object :
                                    com.google.android.gms.cast.framework.SessionManagerListener<
                                            com.google.android.gms.cast.framework.CastSession> {
                                override fun onSessionStarted(
                                        session: com.google.android.gms.cast.framework.CastSession,
                                        sessionId: String
                                ) {
                                    castSession = session
                                    playingRadio?.let { radio ->
                                        val mediaInfo =
                                                com.google.android.gms.cast.MediaInfo.Builder(
                                                                radio.url
                                                        )
                                                        .setStreamType(
                                                                com.google.android.gms.cast
                                                                        .MediaInfo.STREAM_TYPE_LIVE
                                                        )
                                                        .setContentType("audio/mpeg")
                                                        .setMetadata(
                                                                com.google.android.gms.cast
                                                                        .MediaMetadata(
                                                                                com.google.android
                                                                                        .gms.cast
                                                                                        .MediaMetadata
                                                                                        .MEDIA_TYPE_MUSIC_TRACK
                                                                        )
                                                                        .apply {
                                                                            putString(
                                                                                    com.google
                                                                                            .android
                                                                                            .gms
                                                                                            .cast
                                                                                            .MediaMetadata
                                                                                            .KEY_TITLE,
                                                                                    radio.name
                                                                            )
                                                                            putString(
                                                                                    com.google
                                                                                            .android
                                                                                            .gms
                                                                                            .cast
                                                                                            .MediaMetadata
                                                                                            .KEY_ARTIST,
                                                                                    radio.country
                                                                                            ?: ""
                                                                            )
                                                                            radio.favicon?.let {
                                                                                addImage(
                                                                                        com.google
                                                                                                .android
                                                                                                .gms
                                                                                                .common
                                                                                                .images
                                                                                                .WebImage(
                                                                                                        Uri.parse(
                                                                                                                it
                                                                                                        )
                                                                                                )
                                                                                )
                                                                            }
                                                                        }
                                                        )
                                                        .build()
                                        session.remoteMediaClient?.load(
                                                com.google.android.gms.cast.MediaLoadRequestData
                                                        .Builder()
                                                        .setMediaInfo(mediaInfo)
                                                        .build()
                                        )
                                        try {
                                            exoPlayer?.volume = 0f
                                        } catch (e: Exception) {}
                                    }
                                }
                                override fun onSessionEnded(
                                        session: com.google.android.gms.cast.framework.CastSession,
                                        error: Int
                                ) {
                                    castSession = null
                                    try {
                                        exoPlayer?.volume = 1f
                                    } catch (e: Exception) {}
                                }
                                override fun onSessionResumed(
                                        session: com.google.android.gms.cast.framework.CastSession,
                                        wasSuspended: Boolean
                                ) {
                                    castSession = session
                                }
                                override fun onSessionStarting(
                                        session: com.google.android.gms.cast.framework.CastSession
                                ) {}
                                override fun onSessionStartFailed(
                                        session: com.google.android.gms.cast.framework.CastSession,
                                        error: Int
                                ) {}
                                override fun onSessionEnding(
                                        session: com.google.android.gms.cast.framework.CastSession
                                ) {}
                                override fun onSessionResumeFailed(
                                        session: com.google.android.gms.cast.framework.CastSession,
                                        error: Int
                                ) {}
                                override fun onSessionResuming(
                                        session: com.google.android.gms.cast.framework.CastSession,
                                        sessionId: String
                                ) {}
                                override fun onSessionSuspended(
                                        session: com.google.android.gms.cast.framework.CastSession,
                                        reason: Int
                                ) {}
                            },
                            com.google.android.gms.cast.framework.CastSession::class.java
                    )
                }
            }
        } catch (e: Exception) {
            isCastAvailable = false
        }
    }
    var radioTags by remember { mutableStateOf<List<RadioTag>>(emptyList()) }
    var radioStations by remember { mutableStateOf<List<RadioStationEntity>>(emptyList()) }

    // Load cached list on startup
    LaunchedEffect(Unit) {
        val cached =
                radioRepository.loadCacheList(
                        java.io.File(context.filesDir, "last_radio_list.json")
                )
        if (cached.isNotEmpty()) {
            radioStations = cached
        }
    }
    // --- PREFERENCES (Persistence) ---
    val prefs = context.getSharedPreferences("SimpleRadioPrefs", Context.MODE_PRIVATE)

    var selectedRadioCountry by remember {
        mutableStateOf(prefs.getString("selectedRadioCountry", null))
    }
    var selectedRadioTag by remember { mutableStateOf(prefs.getString("selectedRadioTag", null)) }
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
        prefs.edit().putString("selectedRadioCountry", selectedRadioCountry).apply()
    }
    LaunchedEffect(selectedRadioTag) {
        prefs.edit().putString("selectedRadioTag", selectedRadioTag).apply()
    }
    LaunchedEffect(selectedRadioBitrate) {
        if (selectedRadioBitrate != null)
                prefs.edit().putInt("selectedRadioBitrate", selectedRadioBitrate!!).apply()
        else prefs.edit().remove("selectedRadioBitrate").apply()
    }
    LaunchedEffect(selectedRadioFavoriteListId) {
        if (selectedRadioFavoriteListId != null)
                prefs.edit()
                        .putInt("selectedRadioFavoriteListId", selectedRadioFavoriteListId!!)
                        .apply()
        else prefs.edit().remove("selectedRadioFavoriteListId").apply()
    }
    LaunchedEffect(showRecentRadiosOnly) {
        prefs.edit().putBoolean("showRecentRadiosOnly", showRecentRadiosOnly).apply()
    }
    LaunchedEffect(radioSearchQuery) {
        prefs.edit().putString("radioSearchQuery", radioSearchQuery).apply()
    }

    var searchTrigger by rememberSaveable { mutableStateOf(0) }
    var radioSortOrder by remember { mutableStateOf("clickcount") }
    var sidebarCountrySearch by remember { mutableStateOf("") }
    var sidebarTagSearch by remember { mutableStateOf("") }

    var isSearchActive by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val countryFocusRequester = remember { FocusRequester() }
    val genreFocusRequester = remember { FocusRequester() }
    val qualityFocusRequester = remember { FocusRequester() }

    var isQualityExpanded by remember { mutableStateOf(false) }
    var isCountryExpanded by remember { mutableStateOf(false) }
    var isGenreExpanded by remember { mutableStateOf(false) }
    var isViewingRadioResults by rememberSaveable { mutableStateOf(false) }
    var showLyricsGlobal by remember { mutableStateOf(false) }

    val resultsListState = rememberLazyListState()

    val qualityOptions =
            listOf(
                    "Toutes" to null,
                    "Basse (< 64k)" to 0,
                    "Moyenne (64-128k)" to 64,
                    "Haute (128-192k)" to 128,
                    "Très Haute (> 192k)" to 192
            )

    LaunchedEffect(isCountryExpanded) {
        if (isCountryExpanded) {
            delay(100)
            try {
                countryFocusRequester.requestFocus()
            } catch (e: Exception) {}
        }
    }
    LaunchedEffect(isGenreExpanded) {
        if (isGenreExpanded) {
            delay(100)
            try {
                genreFocusRequester.requestFocus()
            } catch (e: Exception) {}
        }
    }

    // --- NAVIGATION BACK (Global Niv3 > Niv2 > Niv1 > Niv0) ---
    androidx.activity.compose.BackHandler(enabled = true) {
        if (showLyricsGlobal) {
            showLyricsGlobal = false
        } else if (isFullScreenPlayer) {
            isFullScreenPlayer = false
        } else if (isViewingRadioResults) {
            isViewingRadioResults = false
        } else {
            // Niv 0: Quitter (Background play)
            (context as? android.app.Activity)?.moveTaskToBack(true)
        }
    }

    // --- HELPER PLAYLIST ---
    fun RadioStationEntity.toMediaItem(): MediaItem {
        // IMPORTANT: On ne met PAS le nom de la radio dans Title/Artist
        // car cela polluerait le listener de métadonnées.
        // On utilise uniquement Station et Album pour le système.
        val meta =
                androidx.media3.common.MediaMetadata.Builder()
                        .setStation(name)
                        .setAlbumTitle(name)
                        .setArtworkUri(favicon?.let { Uri.parse(it) })
                        .build()
        return MediaItem.Builder()
                .setUri(url)
                .setMediaId(stationuuid)
                .setMediaMetadata(meta)
                .build()
    }

    val radioFavStations by
            (if (selectedRadioFavoriteListId != null)
                    radioRepository
                            .getRadiosByFavoriteList(selectedRadioFavoriteListId!!)
                            .collectAsState(initial = emptyList())
            else mutableStateOf(emptyList()))
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
            } catch (e: Exception) {}
        }
    }

    var radioToFavorite by remember { mutableStateOf<RadioStationEntity?>(null) }
    var showAddListDialog by remember { mutableStateOf(false) }

    // --- METADATA GLOBALE (Lifted) ---
    // Variables déplacées en haut de MainScreen

    // On observe les changements du player au niveau global pour la TV et le Bluetooth
    LaunchedEffect(exoPlayer) {
        exoPlayer?.addListener(
                object : androidx.media3.common.Player.Listener {
                    override fun onMediaMetadataChanged(
                            mediaMetadata: androidx.media3.common.MediaMetadata
                    ) {
                        val rawTitle = mediaMetadata.title?.toString()
                        val rawArtist = mediaMetadata.artist?.toString()

                        // Si pas de métadonnées du flux, on ne touche pas aux variables
                        if (rawTitle.isNullOrBlank() && rawArtist.isNullOrBlank()) {
                            return
                        }

                        val stationName = playingRadio?.name ?: ""
                        val country = playingRadio?.country ?: ""

                        fun clean(s: String) =
                                s.replace(Regex("(?i)https?://[\\w./?-]+"), "")
                                        .replace(Regex("(?i)RF\\s*\\d+"), "")
                                        .trim()

                        val cleanTitle = rawTitle?.let { clean(it) } ?: ""
                        val cleanArtist = rawArtist?.let { clean(it) } ?: ""

                        // FILTRAGE : On ignore si c'est le nom de la station, le pays, ou du bruit
                        if (cleanTitle.equals(stationName, ignoreCase = true) ||
                                        cleanTitle.equals(country, ignoreCase = true) ||
                                        cleanArtist.equals(stationName, ignoreCase = true) ||
                                        cleanArtist.equals(country, ignoreCase = true)
                        ) {
                            return // On garde les anciennes valeurs, on ne pollue pas
                        }

                        val separators =
                                listOf(" - ", " – ", " — ", " * ", " : ", " | ", " / ", "~")
                        var found = false

                        for (sep in separators) {
                            if (cleanTitle.contains(sep)) {
                                val parts = cleanTitle.split(sep, limit = 2)
                                val p1 = parts[0].trim()
                                val p2 = parts[1].trim()

                                // Si le deuxième segment contient le nom de la radio, on ignore
                                if (!p2.equals(stationName, ignoreCase = true) &&
                                                !p2.contains(stationName, ignoreCase = true)
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
                        if (!currentTitle.isNullOrBlank()) "$currentArtist $currentTitle"
                        else currentArtist!!
                val response = iTunesApi.search(term = searchTerm, limit = 1)
                val newUrl =
                        response.results
                                ?.firstOrNull()
                                ?.artworkUrl100
                                ?.replace("100x100", "600x600")
                if (newUrl != null) currentArtworkUrl = newUrl
            } catch (e: Exception) {}
        }
    }

    // --- SYNC CHROMECAST GLOBALE ---
    LaunchedEffect(playingRadio?.stationuuid, currentArtist, currentTitle, currentArtworkUrl) {
        if (castSession != null && castSession!!.isConnected) {
            val radio = playingRadio ?: return@LaunchedEffect
            delay(800)
            val mediaInfo =
                    com.google.android.gms.cast.MediaInfo.Builder(radio.url)
                            .setStreamType(com.google.android.gms.cast.MediaInfo.STREAM_TYPE_LIVE)
                            .setContentType("audio/mpeg")
                            .setMetadata(
                                    com.google.android.gms.cast.MediaMetadata(
                                                    com.google.android.gms.cast.MediaMetadata
                                                            .MEDIA_TYPE_MUSIC_TRACK
                                            )
                                            .apply {
                                                // Si on a un titre de chanson, on l'affiche en gros
                                                val songTitle =
                                                        if (currentTitle.isNullOrBlank()) radio.name
                                                        else currentTitle!!
                                                val songArtist =
                                                        if (currentArtist.isNullOrBlank())
                                                                (radio.country ?: "")
                                                        else currentArtist!!

                                                putString(
                                                        com.google.android.gms.cast.MediaMetadata
                                                                .KEY_TITLE,
                                                        songTitle
                                                )
                                                putString(
                                                        com.google.android.gms.cast.MediaMetadata
                                                                .KEY_ARTIST,
                                                        songArtist
                                                )

                                                // On utilise le champ Album pour afficher les infos
                                                // de la station (Radio | Pays | Bitrate)
                                                // Ce champ apparaît souvent en petit sous l'artiste
                                                // sur la Shield
                                                val stationInfo =
                                                        "${radio.name} | ${radio.country ?: "Monde"} | ${radio.bitrate ?: "?"} kbps"
                                                putString(
                                                        com.google.android.gms.cast.MediaMetadata
                                                                .KEY_ALBUM_TITLE,
                                                        stationInfo
                                                )

                                                val imgUrl = currentArtworkUrl ?: radio.favicon
                                                imgUrl?.let {
                                                    addImage(
                                                            com.google.android.gms.common.images
                                                                    .WebImage(Uri.parse(it))
                                                    )
                                                }
                                            }
                            )
                            .build()
            castSession!!.remoteMediaClient?.load(
                    com.google.android.gms.cast.MediaLoadRequestData.Builder()
                            .setMediaInfo(mediaInfo)
                            .build()
            )
        }

        // --- SYNC SERVICE / BLUETOOTH / NOTIFICATION ---
        if (!currentTitle.isNullOrBlank() || !currentArtist.isNullOrBlank() || playingRadio != null
        ) {
            val args =
                    android.os.Bundle().apply {
                        putString("TITLE", currentTitle.takeIf { it?.isNotBlank() == true })
                        putString("ARTIST", currentArtist.takeIf { it?.isNotBlank() == true })
                        putString("ALBUM", playingRadio?.name)
                        putString("ARTWORK_URL", currentArtworkUrl ?: playingRadio?.favicon)
                    }
            (exoPlayer as? androidx.media3.session.MediaController)?.let { controller ->
                try {
                    controller.sendCustomCommand(
                            androidx.media3.session.SessionCommand(
                                    "UPDATE_METADATA",
                                    android.os.Bundle.EMPTY
                            ),
                            args
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
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
                        radioRepository.getCountries().sortedByDescending { it.stationcount }
                radioTags =
                        radioRepository
                                .getTags()
                                .sortedByDescending { it.stationcount }
                                .filter { it.stationcount > 10 }
                                .take(100)
            } catch (e: Exception) {}
            isLoading = false
            // REMOVED: isViewingRadioResults = false
            // This was clearing the view state on every app launch, even if we wanted to restore
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
            android.util.Log.d(
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
                android.util.Log.d("SimpleRADIO", "Loaded ${radioStations.size} stations")
                // Save to cache
                radioRepository.saveCacheList(
                        java.io.File(context.filesDir, "last_radio_list.json"),
                        radioStations
                )
            } catch (e: Exception) {
                radioStations = emptyList()
                android.util.Log.e("SimpleRADIO", "Error loading stations", e)
            }
            isLoading = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Card(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor =
                                                MaterialTheme.colorScheme.surfaceVariant.copy(
                                                        alpha = 0.5f
                                                )
                                )
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                    "SimpleRADIO",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(0.55f)
                            )

                            if (isCastAvailable) {
                                AndroidView(
                                        factory = { ctx ->
                                            MediaRouteButton(ctx).apply {
                                                CastButtonFactory.setUpMediaRouteButton(ctx, this)
                                            }
                                        },
                                        modifier = Modifier.weight(0.15f).size(48.dp)
                                )
                            } else {
                                Spacer(Modifier.weight(0.15f).size(48.dp))
                            }

                            IconButton(
                                    onClick = {
                                        scope.launch {
                                            isLoading = true
                                            try {
                                                radioCountries =
                                                        radioRepository
                                                                .getCountries()
                                                                .sortedByDescending {
                                                                    it.stationcount
                                                                }
                                                                .take(100)
                                                radioTags =
                                                        radioRepository
                                                                .getTags()
                                                                .sortedByDescending {
                                                                    it.stationcount
                                                                }
                                                                .take(100)
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
                                                                radioSearchQuery.takeIf {
                                                                    it.isNotBlank()
                                                                },
                                                                selectedRadioBitrate,
                                                                bitrateMax
                                                        )
                                            } catch (e: Exception) {}
                                            isLoading = false
                                        }
                                    },
                                    modifier = Modifier.weight(0.15f).size(48.dp)
                            ) {
                                Icon(
                                        Icons.Default.Refresh,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                )
                            }
                            IconButton(
                                    onClick = {
                                        exoPlayer?.stop()
                                        context.stopService(
                                                Intent(context, PlaybackService::class.java)
                                        )
                                        (context as? Activity)?.finish()
                                    },
                                    modifier = Modifier.weight(0.15f).size(48.dp)
                            ) {
                                Icon(
                                        Icons.Default.PowerSettingsNew,
                                        "Quitter",
                                        tint = Color.Red,
                                        modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                    value = radioSearchQuery,
                                    onValueChange = { radioSearchQuery = it },
                                    modifier =
                                            Modifier.weight(0.6f)
                                                    .focusRequester(searchFocusRequester)
                                                    .onFocusChanged {
                                                        if (!it.isFocused) isSearchActive = false
                                                    }
                                                    .onKeyEvent {
                                                        if (it.key == Key.DirectionCenter ||
                                                                        it.key == Key.Enter ||
                                                                        it.nativeKeyEvent.keyCode ==
                                                                                66
                                                        ) {
                                                            if (isSearchActive &&
                                                                            radioSearchQuery
                                                                                    .isNotBlank()
                                                            ) {
                                                                // Trigger actual search
                                                                showRecentRadiosOnly = false
                                                                isViewingRadioResults = true
                                                                searchTrigger++
                                                            }
                                                            isSearchActive = !isSearchActive
                                                            true
                                                        } else false
                                                    },
                                    placeholder = { Text("Rech Radios") },
                                    leadingIcon = {
                                        Icon(
                                                Icons.Default.Search,
                                                null,
                                                tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    singleLine = true,
                                    shape = MaterialTheme.shapes.medium,
                                    readOnly = !isSearchActive,
                                    interactionSource =
                                            remember {
                                                androidx.compose.foundation.interaction
                                                        .MutableInteractionSource()
                                            }
                                                    .also { interactionSource ->
                                                        LaunchedEffect(interactionSource) {
                                                            interactionSource.interactions
                                                                    .collect { interaction ->
                                                                        if (interaction is
                                                                                        androidx.compose.foundation.interaction.PressInteraction.Release
                                                                        )
                                                                                isSearchActive =
                                                                                        true
                                                                    }
                                                        }
                                                    }
                            )

                            // Bouton SYNC (Rechercher)
                            var isSyncFocused by remember { mutableStateOf(false) }
                            IconButton(
                                    onClick = {
                                        if (radioSearchQuery.isNotBlank()) {
                                            showRecentRadiosOnly = false
                                            isViewingRadioResults = true
                                            searchTrigger++
                                        }
                                    },
                                    modifier =
                                            Modifier.weight(0.13f)
                                                    .onFocusChanged { isSyncFocused = it.isFocused }
                                                    .background(
                                                            if (isSyncFocused)
                                                                    MaterialTheme.colorScheme
                                                                            .primary.copy(
                                                                            alpha = 0.2f
                                                                    )
                                                            else Color.Transparent,
                                                            CircleShape
                                                    )
                                                    .border(
                                                            if (isSyncFocused) 2.dp else 0.dp,
                                                            if (isSyncFocused) Color.Yellow
                                                            else Color.Transparent,
                                                            CircleShape
                                                    )
                            ) {
                                Icon(
                                        Icons.Default.Check,
                                        "Valider",
                                        tint =
                                                if (isSyncFocused) Color.Yellow
                                                else MaterialTheme.colorScheme.primary
                                )
                            }

                            if (radioSearchQuery.isNotEmpty()) {
                                var isClearFocused by remember { mutableStateOf(false) }
                                IconButton(
                                        onClick = { radioSearchQuery = "" },
                                        modifier =
                                                Modifier.weight(0.13f)
                                                        .onFocusChanged {
                                                            isClearFocused = it.isFocused
                                                        }
                                                        .background(
                                                                if (isClearFocused)
                                                                        MaterialTheme.colorScheme
                                                                                .primary.copy(
                                                                                alpha = 0.2f
                                                                        )
                                                                else Color.Transparent,
                                                                CircleShape
                                                        )
                                                        .border(
                                                                if (isClearFocused) 2.dp else 0.dp,
                                                                if (isClearFocused) Color.Yellow
                                                                else Color.Transparent,
                                                                CircleShape
                                                        )
                                ) {
                                    Icon(
                                            Icons.Default.Close,
                                            "Effacer",
                                            tint =
                                                    if (isClearFocused) Color.Yellow
                                                    else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            } else {
                                Spacer(Modifier.weight(0.13f))
                            }
                            if (playingRadio != null) {
                                IconButton(
                                        onClick = {
                                            if (!isViewingRadioResults) {
                                                isViewingRadioResults = true
                                                // We preserve the previous filter state (Recents,
                                                // Favorites, or Search)
                                                // instead of resetting to default search.
                                            } else if (!isFullScreenPlayer) {
                                                isFullScreenPlayer = true
                                            }
                                        },
                                        modifier = Modifier.weight(0.15f)
                                ) {
                                    Icon(
                                            if (playerIsPlaying) Icons.Filled.PlayCircleFilled
                                            else Icons.Default.PlayCircleOutline,
                                            "Player",
                                            tint =
                                                    if (playerIsPlaying) Color.Yellow
                                                    else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp)
                                    )
                                }
                            } else {
                                Spacer(Modifier.weight(0.15f))
                            }
                        }
                    }
                }

                if (isLoading)
                        LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        )

                val isPortrait =
                        LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT

                if (isPortrait) {
                    val countryName =
                            radioCountries.find { it.iso_3166_1 == selectedRadioCountry }?.name
                                    ?: "Tous"
                    val tagName = selectedRadioTag ?: "Tous"

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (!isViewingRadioResults && radioSearchQuery.isBlank()) {
                            LazyColumn(
                                    modifier = Modifier.fillMaxSize().padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    Row(
                                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                                            horizontalArrangement = Arrangement.SpaceAround
                                    ) {
                                        TextButton(
                                                onClick = {
                                                    exportLauncher.launch(
                                                            "radio_favorites_backup.json"
                                                    )
                                                }
                                        ) {
                                            Icon(Icons.Default.CloudUpload, null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Sauvegarder")
                                        }
                                        TextButton(
                                                onClick = {
                                                    importLauncher.launch(
                                                            arrayOf("application/json")
                                                    )
                                                }
                                        ) {
                                            Icon(Icons.Default.CloudDownload, null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Restaurer")
                                        }
                                    }
                                }
                                item {
                                    SidebarItem(
                                            text = "Récents",
                                            icon = Icons.Default.History,
                                            isSelected =
                                                    showRecentRadiosOnly && isViewingRadioResults,
                                            onClick = {
                                                showRecentRadiosOnly = true
                                                isViewingRadioResults = true
                                                selectedRadioFavoriteListId = null
                                                radioSearchQuery = ""
                                            }
                                    )
                                }
                                item {
                                    Text(
                                            "Tri",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = Color.Gray
                                    )
                                    Row {
                                        FilterChip(
                                                selected = radioSortOrder == "clickcount",
                                                onClick = { radioSortOrder = "clickcount" },
                                                label = { Text("Popularité") },
                                                modifier = Modifier.weight(1f).padding(4.dp)
                                        )
                                        FilterChip(
                                                selected = radioSortOrder == "votes",
                                                onClick = { radioSortOrder = "votes" },
                                                label = { Text("Votes") },
                                                modifier = Modifier.weight(1f).padding(4.dp)
                                        )
                                    }
                                }

                                // --- QUALITÉ (Expandable) ---
                                val currentQualityName =
                                        qualityOptions
                                                .find { it.second == selectedRadioBitrate }
                                                ?.first
                                                ?: "Toutes"
                                item {
                                    SidebarItem(
                                            text = "🎧 Qualité : $currentQualityName",
                                            icon = Icons.Default.FilterList,
                                            isSelected = selectedRadioBitrate != null,
                                            onClick = { isQualityExpanded = !isQualityExpanded }
                                    )
                                }
                                if (isQualityExpanded) {
                                    items(qualityOptions) { item ->
                                        SidebarItem(
                                                text = item.first,
                                                isSelected = selectedRadioBitrate == item.second,
                                                onClick = {
                                                    selectedRadioBitrate = item.second
                                                    isQualityExpanded = false
                                                    showRecentRadiosOnly = false
                                                }
                                        )
                                    }
                                }

                                // --- PAYS (Expandable) ---
                                item {
                                    SidebarItem(
                                            text = "🌍 Pays : $countryName",
                                            icon = Icons.Default.Public,
                                            isSelected = selectedRadioCountry != null,
                                            onClick = { isCountryExpanded = !isCountryExpanded }
                                    )
                                }
                                if (isCountryExpanded) {
                                    item {
                                        OutlinedTextField(
                                                value = sidebarCountrySearch,
                                                onValueChange = { sidebarCountrySearch = it },
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(horizontal = 8.dp),
                                                placeholder = { Text("Rech pays...") },
                                                singleLine = true
                                        )
                                    }
                                    item {
                                        SidebarItem(
                                                text = "Tous les pays",
                                                isSelected = selectedRadioCountry == null,
                                                onClick = {
                                                    selectedRadioCountry = null
                                                    isCountryExpanded = false
                                                }
                                        )
                                    }
                                    items(
                                            radioCountries
                                                    .filter {
                                                        it.name.contains(
                                                                sidebarCountrySearch,
                                                                ignoreCase = true
                                                        )
                                                    }
                                                    .take(50)
                                    ) { country ->
                                        SidebarItem(
                                                text = country.name,
                                                isSelected =
                                                        selectedRadioCountry == country.iso_3166_1,
                                                onClick = {
                                                    selectedRadioCountry = country.iso_3166_1
                                                    isCountryExpanded = false
                                                }
                                        )
                                    }
                                }

                                // --- GENRE (Expandable) ---
                                item {
                                    SidebarItem(
                                            text = "🎵 Genre : $tagName",
                                            icon = Icons.AutoMirrored.Filled.Label,
                                            isSelected = selectedRadioTag != null,
                                            onClick = { isGenreExpanded = !isGenreExpanded }
                                    )
                                }
                                if (isGenreExpanded) {
                                    item {
                                        OutlinedTextField(
                                                value = sidebarTagSearch,
                                                onValueChange = { sidebarTagSearch = it },
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(horizontal = 8.dp),
                                                placeholder = { Text("Rech genre...") },
                                                singleLine = true
                                        )
                                    }
                                    item {
                                        SidebarItem(
                                                text = "Tous les genres",
                                                isSelected = selectedRadioTag == null,
                                                onClick = {
                                                    selectedRadioTag = null
                                                    isGenreExpanded = false
                                                }
                                        )
                                    }
                                    items(
                                            radioTags
                                                    .filter {
                                                        it.name.contains(
                                                                sidebarTagSearch,
                                                                ignoreCase = true
                                                        )
                                                    }
                                                    .take(50)
                                    ) { tag ->
                                        SidebarItem(
                                                text = tag.name,
                                                isSelected = selectedRadioTag == tag.name,
                                                onClick = {
                                                    selectedRadioTag = tag.name
                                                    isGenreExpanded = false
                                                }
                                        )
                                    }
                                }

                                item {
                                    Row(
                                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                                onClick = {
                                                    android.util.Log.d(
                                                            "SimpleRADIO",
                                                            "Button click: radioStations.size=${radioStations.size}"
                                                    )
                                                    isViewingRadioResults = true
                                                    showRecentRadiosOnly = false
                                                    selectedRadioFavoriteListId = null
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = MaterialTheme.shapes.medium
                                        ) {
                                            Icon(Icons.Default.PlayArrow, null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Afficher les radios")
                                        }

                                        if (selectedRadioCountry != null ||
                                                        selectedRadioTag != null ||
                                                        selectedRadioBitrate != null
                                        ) {
                                            FilledTonalIconButton(
                                                    onClick = {
                                                        selectedRadioCountry = null
                                                        selectedRadioTag = null
                                                        selectedRadioBitrate = null
                                                        isCountryExpanded = false
                                                        isGenreExpanded = false
                                                        isQualityExpanded = false
                                                    }
                                            ) { Icon(Icons.Default.DeleteSweep, "Réinitialiser") }
                                        }
                                    }
                                }
                                item {
                                    Row(
                                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                                "Vos listes",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = Color.Gray
                                        )
                                        IconButton(
                                                onClick = { showAddListDialog = true },
                                                modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                    Icons.Default.Add,
                                                    null,
                                                    tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                                items(radioFavoriteLists) { list ->
                                    SidebarItem(
                                            text = list.name,
                                            isSelected = selectedRadioFavoriteListId == list.id,
                                            onClick = {
                                                selectedRadioFavoriteListId = list.id
                                                isViewingRadioResults = true
                                                showRecentRadiosOnly = false
                                                radioSearchQuery = ""
                                            }
                                    )
                                }
                            }
                        } else {
                            Column {
                                Button(
                                        onClick = { isViewingRadioResults = false },
                                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        containerColor =
                                                                Color(
                                                                        0xFF2E7D32
                                                                ), // Vert foncé Premium
                                                        contentColor = Color.White
                                                ),
                                        shape = MaterialTheme.shapes.medium
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                            "RETOUR AUX FILTRES",
                                            style = MaterialTheme.typography.labelLarge
                                    )
                                }
                                LazyColumn(
                                        state = resultsListState,
                                        modifier = Modifier.fillMaxSize().padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Affiche la liste actuelle (peut être radioStations,
                                    // recentRadios ou radioFavStations)
                                    items(currentRadioList) { radio ->
                                        MainItem(
                                                title = radio.name,
                                                subtitle =
                                                        "${radio.country ?: ""} | ${radio.bitrate ?: "?"} kbps",
                                                iconUrl = radio.favicon,
                                                isPlaying =
                                                        playingRadio?.stationuuid ==
                                                                radio.stationuuid,
                                                onClick = {
                                                    currentArtist = null
                                                    currentTitle = null
                                                    currentArtworkUrl = null

                                                    playingRadio = radio
                                                    val snapshot = currentRadioList.toList()
                                                    navRadioList = snapshot

                                                    android.util.Log.d(
                                                            "SimpleRADIO",
                                                            "Portrait Click: radio=${radio.name}, snapshot size=${snapshot.size}"
                                                    )

                                                    exoPlayer?.let { player ->
                                                        player.volume =
                                                                if (castSession != null &&
                                                                                castSession!!
                                                                                        .isConnected
                                                                )
                                                                        0f
                                                                else 1f
                                                        val mediaItems =
                                                                snapshot.map { r ->
                                                                    r.toMediaItem()
                                                                }
                                                        val startIndex =
                                                                snapshot.indexOfFirst { s ->
                                                                    s.stationuuid ==
                                                                            radio.stationuuid
                                                                }
                                                        android.util.Log.d(
                                                                "SimpleRADIO",
                                                                "startIndex=$startIndex for ${radio.name}"
                                                        )
                                                        if (startIndex != -1) {
                                                            player.setMediaItems(
                                                                    mediaItems,
                                                                    startIndex,
                                                                    0L
                                                            )
                                                            player.prepare()
                                                            player.play()
                                                        } else {
                                                            android.util.Log.e(
                                                                    "SimpleRADIO",
                                                                    "RADIO NOT FOUND IN SNAPSHOT!"
                                                            )
                                                        }
                                                    }
                                                    isFullScreenPlayer = true
                                                    scope.launch {
                                                        radioRepository.addToRecents(
                                                                radio.stationuuid
                                                        )
                                                    }
                                                },
                                                onAddFavorite = { radioToFavorite = radio }
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        Box(modifier = Modifier.weight(0.4f).fillMaxHeight()) {
                            LazyColumn(
                                    modifier = Modifier.fillMaxSize().padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    SidebarItem(
                                            text = "Récents",
                                            icon = Icons.Default.History,
                                            isSelected = showRecentRadiosOnly,
                                            onClick = {
                                                showRecentRadiosOnly = true
                                                selectedRadioCountry = null
                                                selectedRadioTag = null
                                                selectedRadioBitrate = null
                                                selectedRadioFavoriteListId = null
                                                radioSearchQuery = ""
                                            }
                                    )
                                }
                                item {
                                    Row(
                                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                                "Favoris",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = Color.Gray
                                        )
                                        IconButton(
                                                onClick = { showAddListDialog = true },
                                                modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                    Icons.Default.Add,
                                                    null,
                                                    tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                                items(radioFavoriteLists) { list ->
                                    SidebarItem(
                                            text = list.name,
                                            isSelected = selectedRadioFavoriteListId == list.id,
                                            onClick = {
                                                selectedRadioFavoriteListId = list.id
                                                selectedRadioCountry = null
                                                selectedRadioTag = null
                                                selectedRadioBitrate = null
                                                showRecentRadiosOnly = false
                                                radioSearchQuery = ""
                                            },
                                            onDelete = {
                                                scope.launch {
                                                    radioRepository.removeFavoriteList(list)
                                                }
                                            }
                                    )
                                }
                                item {
                                    Text(
                                            "Filtres",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = Color.Gray,
                                            modifier = Modifier.padding(8.dp)
                                    )
                                }
                                item {
                                    SidebarItem(
                                            text =
                                                    if (radioSortOrder == "clickcount") "Populaires"
                                                    else "Mieux notées",
                                            icon =
                                                    if (radioSortOrder == "clickcount")
                                                            Icons.AutoMirrored.Filled.TrendingUp
                                                    else Icons.Default.Star,
                                            isSelected = true,
                                            onClick = {
                                                radioSortOrder =
                                                        if (radioSortOrder == "clickcount") "votes"
                                                        else "clickcount"
                                            }
                                    )
                                }
                                item {
                                    SidebarItem(
                                            text =
                                                    qualityOptions
                                                            .find {
                                                                it.second == selectedRadioBitrate
                                                            }
                                                            ?.first
                                                            ?: "Toutes qualités",
                                            icon = Icons.Default.FilterList,
                                            isSelected = true,
                                            modifier =
                                                    Modifier.focusRequester(qualityFocusRequester),
                                            onClick = { isQualityExpanded = true }
                                    )
                                }
                                if (isQualityExpanded)
                                        items(qualityOptions) { item ->
                                            SidebarItem(
                                                    text = item.first,
                                                    isSelected =
                                                            selectedRadioBitrate == item.second,
                                                    onClick = {
                                                        selectedRadioBitrate = item.second
                                                        isQualityExpanded = false
                                                        scope.launch {
                                                            delay(100)
                                                            try {
                                                                qualityFocusRequester.requestFocus()
                                                            } catch (e: Exception) {}
                                                        }
                                                    }
                                            )
                                        }
                                item {
                                    SidebarItem(
                                            text =
                                                    if (selectedRadioCountry == null)
                                                            "Tous les pays"
                                                    else
                                                            radioCountries
                                                                    .find {
                                                                        it.iso_3166_1 ==
                                                                                selectedRadioCountry
                                                                    }
                                                                    ?.name
                                                                    ?: "Pays sélectionné",
                                            icon = Icons.Default.Public,
                                            isSelected = true,
                                            onClick = { isCountryExpanded = !isCountryExpanded }
                                    )
                                }
                                if (isCountryExpanded) {
                                    item {
                                        var isSearchActiveByInput by remember {
                                            mutableStateOf(false)
                                        }
                                        OutlinedTextField(
                                                value = sidebarCountrySearch,
                                                onValueChange = { sidebarCountrySearch = it },
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(horizontal = 8.dp)
                                                                .focusRequester(
                                                                        countryFocusRequester
                                                                )
                                                                .onFocusChanged {
                                                                    if (!it.isFocused)
                                                                            isSearchActiveByInput =
                                                                                    false
                                                                }
                                                                .onKeyEvent {
                                                                    if (it.key ==
                                                                                    Key.DirectionCenter ||
                                                                                    it.key ==
                                                                                            Key.Enter
                                                                    ) {
                                                                        isSearchActiveByInput = true
                                                                        false
                                                                    } else false
                                                                },
                                                placeholder = { Text("Filtrer pays...") },
                                                singleLine = true,
                                                readOnly = !isSearchActiveByInput,
                                                interactionSource =
                                                        remember {
                                                            androidx.compose.foundation.interaction
                                                                    .MutableInteractionSource()
                                                        }
                                                                .also { interactionSource ->
                                                                    LaunchedEffect(
                                                                            interactionSource
                                                                    ) {
                                                                        interactionSource
                                                                                .interactions
                                                                                .collect {
                                                                                    if (it is
                                                                                                    androidx.compose.foundation.interaction.PressInteraction.Release
                                                                                    )
                                                                                            isSearchActiveByInput =
                                                                                                    true
                                                                                }
                                                                    }
                                                                }
                                        )
                                    }
                                    item {
                                        SidebarItem(
                                                text = "Tous les pays",
                                                isSelected = selectedRadioCountry == null,
                                                onClick = {
                                                    selectedRadioCountry = null
                                                    isCountryExpanded = false
                                                }
                                        )
                                    }
                                    items(
                                            radioCountries
                                                    .filter {
                                                        it.name.contains(
                                                                sidebarCountrySearch,
                                                                ignoreCase = true
                                                        )
                                                    }
                                                    .take(50)
                                    ) { country ->
                                        SidebarItem(
                                                text = country.name,
                                                isSelected =
                                                        selectedRadioCountry == country.iso_3166_1,
                                                onClick = {
                                                    selectedRadioCountry = country.iso_3166_1
                                                    isCountryExpanded = false
                                                }
                                        )
                                    }
                                }
                                item {
                                    SidebarItem(
                                            text = selectedRadioTag ?: "Tous les genres",
                                            icon = Icons.AutoMirrored.Filled.Label,
                                            isSelected = true,
                                            onClick = { isGenreExpanded = !isGenreExpanded }
                                    )
                                }
                                if (isGenreExpanded) {
                                    item {
                                        var isSearchActiveByInput by remember {
                                            mutableStateOf(false)
                                        }
                                        OutlinedTextField(
                                                value = sidebarTagSearch,
                                                onValueChange = { sidebarTagSearch = it },
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(horizontal = 8.dp)
                                                                .focusRequester(genreFocusRequester)
                                                                .onFocusChanged {
                                                                    if (!it.isFocused)
                                                                            isSearchActiveByInput =
                                                                                    false
                                                                }
                                                                .onKeyEvent {
                                                                    if (it.key ==
                                                                                    Key.DirectionCenter ||
                                                                                    it.key ==
                                                                                            Key.Enter
                                                                    ) {
                                                                        isSearchActiveByInput = true
                                                                        false
                                                                    } else false
                                                                },
                                                placeholder = { Text("Filtrer genre...") },
                                                singleLine = true,
                                                readOnly = !isSearchActiveByInput,
                                                interactionSource =
                                                        remember {
                                                            androidx.compose.foundation.interaction
                                                                    .MutableInteractionSource()
                                                        }
                                                                .also { interactionSource ->
                                                                    LaunchedEffect(
                                                                            interactionSource
                                                                    ) {
                                                                        interactionSource
                                                                                .interactions
                                                                                .collect {
                                                                                    if (it is
                                                                                                    androidx.compose.foundation.interaction.PressInteraction.Release
                                                                                    )
                                                                                            isSearchActiveByInput =
                                                                                                    true
                                                                                }
                                                                    }
                                                                }
                                        )
                                    }
                                    item {
                                        SidebarItem(
                                                text = "Tous les genres",
                                                isSelected = selectedRadioTag == null,
                                                onClick = {
                                                    selectedRadioTag = null
                                                    isGenreExpanded = false
                                                }
                                        )
                                    }
                                    items(
                                            radioTags
                                                    .filter {
                                                        it.name.contains(
                                                                sidebarTagSearch,
                                                                ignoreCase = true
                                                        )
                                                    }
                                                    .take(50)
                                    ) { tag ->
                                        SidebarItem(
                                                text = tag.name,
                                                isSelected = selectedRadioTag == tag.name,
                                                onClick = {
                                                    selectedRadioTag = tag.name
                                                    isGenreExpanded = false
                                                }
                                        )
                                    }
                                }

                                // Bouton pour forcer l'affichage de la liste même avec filtres par
                                // défaut
                                item {
                                    Row(
                                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                                onClick = {
                                                    android.util.Log.d(
                                                            "SimpleRADIO",
                                                            "Landscape Button click: radioStations.size=${radioStations.size}"
                                                    )
                                                    isViewingRadioResults = true
                                                    showRecentRadiosOnly = false
                                                    selectedRadioFavoriteListId = null
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = MaterialTheme.shapes.medium
                                        ) {
                                            Icon(Icons.Default.PlayArrow, null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Afficher les radios")
                                        }
                                    }
                                }

                                // --- SAUVEGARDE ---
                                item {
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                            "Sauvegarde",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = Color.Gray,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                    Row(
                                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        var isExportFocused by remember { mutableStateOf(false) }
                                        var isImportFocused by remember { mutableStateOf(false) }

                                        IconButton(
                                                onClick = {
                                                    exportLauncher.launch(
                                                            "radio_favorites_backup.json"
                                                    )
                                                },
                                                modifier =
                                                        Modifier.onFocusChanged {
                                                                    isExportFocused = it.isFocused
                                                                }
                                                                .background(
                                                                        if (isExportFocused)
                                                                                Color.Yellow.copy(
                                                                                        alpha = 0.2f
                                                                                )
                                                                        else Color.Transparent,
                                                                        CircleShape
                                                                )
                                                                .border(
                                                                        if (isExportFocused) 2.dp
                                                                        else 0.dp,
                                                                        if (isExportFocused)
                                                                                Color.Yellow
                                                                        else Color.Transparent,
                                                                        CircleShape
                                                                )
                                        ) {
                                            Icon(
                                                    Icons.Default.CloudUpload,
                                                    "Sauvegarder",
                                                    tint =
                                                            if (isExportFocused) Color.Yellow
                                                            else MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        IconButton(
                                                onClick = {
                                                    importLauncher.launch(
                                                            arrayOf("application/json")
                                                    )
                                                },
                                                modifier =
                                                        Modifier.onFocusChanged {
                                                                    isImportFocused = it.isFocused
                                                                }
                                                                .background(
                                                                        if (isImportFocused)
                                                                                Color.Yellow.copy(
                                                                                        alpha = 0.2f
                                                                                )
                                                                        else Color.Transparent,
                                                                        CircleShape
                                                                )
                                                                .border(
                                                                        if (isImportFocused) 2.dp
                                                                        else 0.dp,
                                                                        if (isImportFocused)
                                                                                Color.Yellow
                                                                        else Color.Transparent,
                                                                        CircleShape
                                                                )
                                        ) {
                                            Icon(
                                                    Icons.Default.CloudDownload,
                                                    "Restaurer",
                                                    tint =
                                                            if (isImportFocused) Color.Yellow
                                                            else MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Box(modifier = Modifier.weight(0.6f).fillMaxHeight()) {
                            LazyColumn(
                                    state = resultsListState,
                                    modifier = Modifier.fillMaxSize().padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item {
                                    Text(
                                            "Radios Mondiales",
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.padding(8.dp)
                                    )
                                }
                                items(currentRadioList) { radio ->
                                    val isCurrent = playingRadio?.stationuuid == radio.stationuuid
                                    MainItem(
                                            title = radio.name,
                                            subtitle =
                                                    "${radio.country ?: ""} | ${radio.bitrate ?: "?"} kbps",
                                            iconUrl = radio.favicon,
                                            isPlaying = isCurrent,
                                            modifier =
                                                    if (isCurrent)
                                                            Modifier.focusRequester(
                                                                    listFocusRequester
                                                            )
                                                    else Modifier,
                                            onClick = {
                                                if (exoPlayer != null) {
                                                    currentArtist = null
                                                    currentTitle = null
                                                    currentArtworkUrl = null

                                                    playingRadio = radio
                                                    val snapshot = currentRadioList.toList()
                                                    navRadioList = snapshot

                                                    exoPlayer?.let { player ->
                                                        try {
                                                            player.volume =
                                                                    if (castSession != null &&
                                                                                    castSession!!
                                                                                            .isConnected
                                                                    )
                                                                            0f
                                                                    else 1f
                                                            val mediaItems =
                                                                    snapshot.map { r ->
                                                                        r.toMediaItem()
                                                                    }
                                                            val startIndex =
                                                                    snapshot.indexOfFirst { s ->
                                                                        s.stationuuid ==
                                                                                radio.stationuuid
                                                                    }
                                                            if (startIndex != -1) {
                                                                player.setMediaItems(
                                                                        mediaItems,
                                                                        startIndex,
                                                                        0L
                                                                )
                                                                player.prepare()
                                                                player.play()
                                                            }
                                                        } catch (e: Exception) {
                                                            e.printStackTrace()
                                                        }
                                                    }
                                                    isFullScreenPlayer = true
                                                    scope.launch {
                                                        radioRepository.addToRecents(
                                                                radio.stationuuid
                                                        )
                                                    }
                                                }
                                            },
                                            onAddFavorite = { radioToFavorite = radio }
                                    )
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
                        iTunesApi = iTunesApi,
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
                                                    radioRepository.addFavoriteList(name)
                                                }
                                        showAddListDialog = false
                                    }
                            ) { Text("Ajouter") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showAddListDialog = false }) { Text("Annuler") }
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
                                radioRepository.getListIdsForRadio(radioToFavorite!!.stationuuid)
                            }
                    )
        }
    }
}

@Composable
fun SidebarItem(
        text: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
        isSelected: Boolean,
        modifier: Modifier = Modifier,
        onClick: () -> Unit,
        onDelete: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    Card(
            modifier =
                    modifier.fillMaxWidth().onFocusChanged { isFocused = it.isFocused }.clickable {
                        onClick()
                    },
            border =
                    if (isFocused) BorderStroke(3.dp, Color.Yellow)
                    else if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    else null,
            colors =
                    CardDefaults.cardColors(
                            containerColor =
                                    if (isFocused)
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    else if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                    )
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (icon != null) {
                    Icon(
                            icon,
                            null,
                            tint =
                                    if (isSelected || isFocused) MaterialTheme.colorScheme.primary
                                    else Color.Gray
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                        text,
                        color =
                                if (isSelected || isFocused) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                )
            }
            if (onDelete != null)
                    IconButton(onClick = onDelete, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.Delete, null, tint = Color.Red.copy(alpha = 0.5f))
                    }
        }
    }
}

@Composable
fun MainItem(
        title: String,
        subtitle: String? = null,
        iconUrl: String?,
        isPlaying: Boolean,
        modifier: Modifier = Modifier,
        onClick: () -> Unit,
        onAddFavorite: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    var isFavFocused by remember { mutableStateOf(false) }
    Row(
            modifier = modifier.fillMaxWidth().height(80.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        Card(
                modifier =
                        Modifier.weight(0.833f)
                                .fillMaxHeight()
                                .onFocusChanged { isFocused = it.isFocused }
                                .clickable { onClick() },
                border =
                        if (isFocused) BorderStroke(4.dp, Color.Yellow)
                        else if (isPlaying) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        else null,
                colors =
                        CardDefaults.cardColors(
                                containerColor =
                                        if (isFocused)
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
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
                                    if (isFocused) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (subtitle != null)
                            Text(
                                    subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
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
                                        if (isFavFocused) MaterialTheme.colorScheme.primary
                                        else
                                                MaterialTheme.colorScheme.surfaceVariant.copy(
                                                        alpha = 0.5f
                                                ),
                                        MaterialTheme.shapes.medium
                                )
        ) { Icon(Icons.Default.Add, null, tint = if (isFavFocused) Color.Black else Color.White) }
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
                                                            onToggle(getId(list))
                                                            if (selectedIds.contains(getId(list)))
                                                                    selectedIds.remove(getId(list))
                                                            else selectedIds.add(getId(list))
                                                        }
                                                        .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                            checked = selectedIds.contains(getId(list)),
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
        exoPlayer: androidx.media3.common.Player,
        onBack: () -> Unit,
        radioStation: RadioStationEntity? = null,
        lrcLibApi: com.example.simpleradio.data.api.LrcLibApi? = null,
        iTunesApi: com.example.simpleradio.data.api.ITunesApi? = null,
        radioList: List<RadioStationEntity> = emptyList(),
        castSession: com.google.android.gms.cast.framework.CastSession? = null,
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
    val backFocusRequester = remember { FocusRequester() }

    // --- LOGIC: Translation ---
    var isTranslating by remember { mutableStateOf(false) }
    var translatedLyrics by remember { mutableStateOf<String?>(null) }

    fun toggleTranslation() {
        if (!isTranslating) {
            if (translatedLyrics == null && lyricsText != null) {
                scope.launch {
                    val translated =
                            com.example.simpleradio.data.api.TranslationApi.translate(lyricsText!!)
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
    var artworkCycleIndex by remember { mutableStateOf(0) }
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
                            com.example.simpleradio.data.api.ImageScraper.findLogos(
                                    radioName = radioStation.name,
                                    country = radioStation.country,
                                    streamUrl = radioStation.url
                            )
                    dynamicLogos = logos
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    LaunchedEffect(artist, title) {
        alternativeArtworks = emptyList()
        if (iTunesApi != null && !artist.isNullOrBlank() && !title.isNullOrBlank()) {
            try {
                val searchTerm = "$artist $title"
                val response = iTunesApi.search(term = searchTerm, limit = 3)
                alternativeArtworks =
                        response.results?.mapNotNull {
                            it.artworkUrl100?.replace("100x100", "600x600")
                        }
                                ?: emptyList()
            } catch (e: Exception) {}
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
                list.distinct().take(3)
            }

    val effectiveArtworkUrl =
            if (cycleList.isNotEmpty()) cycleList[artworkCycleIndex % cycleList.size] else null

    LaunchedEffect(Unit) {
        delay(800)
        try {
            backFocusRequester.requestFocus()
        } catch (e: Exception) {}
    }

    // Interception des commandes Bluetooth (Voiture) via Broadcast
    DisposableEffect(Unit) {
        val receiver =
                object : android.content.BroadcastReceiver() {
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
                        } else if (intent?.action == "com.example.simpleradio.PREV") {
                            if (currentIndex > 0) nextStation = radioList[currentIndex - 1]
                        }

                        if (nextStation != null) {
                            // On ne fait plus setMediaItem ici pour laisser ExoPlayer gérer sa
                            // playlist interne si possible,
                            // mais si on vient du Broadcast (Bluetooth hérité), on déclenche le
                            // changement de station indexé.
                            val index =
                                    radioList.indexOfFirst {
                                        it.stationuuid == nextStation.stationuuid
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
                android.content.IntentFilter().apply {
                    addAction("com.example.simpleradio.NEXT")
                    addAction("com.example.simpleradio.PREV")
                }
        val flags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        Context.RECEIVER_NOT_EXPORTED
                else 0
        try {
            context.registerReceiver(receiver, filter, flags)
        } catch (e: Exception) {
            // Fallback pour les versions/devices capricieux
            context.registerReceiver(receiver, filter)
        }

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {}
        }
    }

    // Synchronisation avec les métadonnées et la playlist
    DisposableEffect(exoPlayer) {
        val listener =
                object : androidx.media3.common.Player.Listener {
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
                        val text = resp.plainLyrics ?: "Paroles non disponibles."
                        lyricsText = text
                        lyricsCache[key] = text
                    } catch (e: Exception) {
                        lyricsText = "Paroles introuvables."
                    }
                    isFetchingLyrics = false
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black).clickable(enabled = false) {}) {
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
                            lrcLibApi != null && !artist.isNullOrBlank() && !title.isNullOrBlank(),
                    backFocusRequester = backFocusRequester,
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
                            lrcLibApi != null && !artist.isNullOrBlank() && !title.isNullOrBlank(),
                    backFocusRequester = backFocusRequester,
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
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { toggleTranslation() }) {
                                Text(
                                        if (isTranslating) "Original" else "Traduire (FR)",
                                        color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Button(
                                    onClick = { onToggleLyrics(false) },
                                    colors =
                                            ButtonDefaults.buttonColors(
                                                    containerColor =
                                                            MaterialTheme.colorScheme.primary
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
        exoPlayer: androidx.media3.common.Player,
        isActuallyPlaying: Boolean,
        onBack: () -> Unit,
        onLyrics: () -> Unit,
        radioList: List<RadioStationEntity>,
        showLyricsButton: Boolean,
        backFocusRequester: FocusRequester,
        castSession: com.google.android.gms.cast.framework.CastSession? = null,
        sleepTimerTimeLeft: Long? = null,
        onSetSleepTimer: (Int?) -> Unit = {},
        onCycleArtwork: () -> Unit = {},
        onTranslate: () -> Unit = {},
        isTranslating: Boolean = false,
        translatedLyrics: String? = null
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header Bar
        Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                    onClick = onBack,
                    modifier =
                            Modifier.focusRequester(backFocusRequester)
                                    .background(
                                            Color.White.copy(alpha = 0.2f),
                                            androidx.compose.foundation.shape.CircleShape
                                    )
            ) {
                Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        null,
                        tint = Color.Green, // Harmonisé en vert
                        modifier = Modifier.size(32.dp)
                )
            }

            // --- BOUTON TIMER (Top Center) ---
            Box {
                var showTimerMenu by remember { mutableStateOf(false) }
                IconButton(
                        onClick = { showTimerMenu = true },
                        modifier =
                                Modifier.background(
                                        if (sleepTimerTimeLeft != null)
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                        else Color.White.copy(alpha = 0.1f),
                                        androidx.compose.foundation.shape.CircleShape
                                )
                ) {
                    Icon(
                            Icons.Default.Timer,
                            null,
                            tint =
                                    if (sleepTimerTimeLeft != null)
                                            MaterialTheme.colorScheme.primary
                                    else Color.White,
                            modifier = Modifier.size(28.dp)
                    )
                    if (sleepTimerTimeLeft != null) {
                        Text(
                                text = "${sleepTimerTimeLeft / 60000}m",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier =
                                        Modifier.align(Alignment.BottomCenter)
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
                                text = { Text("Annuler le timer", color = Color.Red) },
                                onClick = {
                                    onSetSleepTimer(null)
                                    showTimerMenu = false
                                }
                        )
                    }
                }
            }

            if (showLyricsButton) {
                TextButton(onClick = onLyrics) {
                    Text("Lyrics", color = MaterialTheme.colorScheme.primary)
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
            IconButton(
                    onClick = onCycleArtwork,
                    modifier =
                            Modifier.align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .background(
                                            Color.Black.copy(alpha = 0.3f),
                                            androidx.compose.foundation.shape.CircleShape
                                    )
                                    .size(40.dp)
            ) {
                Icon(
                        Icons.Default.Sync,
                        null,
                        tint = Color.White.copy(alpha = 0.5f),
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
                horizontalArrangement = Arrangement.spacedBy(48.dp, Alignment.CenterHorizontally)
        ) {
            IconButton(
                    onClick = {
                        val idx =
                                radioList.indexOfFirst {
                                    it.stationuuid == currentStation?.stationuuid
                                }
                        if (idx > 0) {
                            exoPlayer.seekToPrevious()
                        }
                    }
            ) {
                Icon(
                        Icons.Default.SkipPrevious,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                )
            }
            IconButton(
                    onClick = {
                        if (castSession != null && castSession.isConnected) {
                            val client = castSession.remoteMediaClient
                            if (client?.isPlaying == true) client.pause() else client?.play()
                        } else {
                            if (isActuallyPlaying) exoPlayer.pause() else exoPlayer.play()
                        }
                    }
            ) {
                Icon(
                        if (isActuallyPlaying) Icons.Default.PauseCircleFilled
                        else Icons.Default.PlayCircleFilled,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(68.dp)
                )
            }
            IconButton(
                    onClick = {
                        val idx =
                                radioList.indexOfFirst {
                                    it.stationuuid == currentStation?.stationuuid
                                }
                        if (idx >= 0 && idx < radioList.size - 1) {
                            exoPlayer.seekToNext()
                        }
                    }
            ) {
                Icon(
                        Icons.Default.SkipNext,
                        null,
                        tint = Color.White,
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
        exoPlayer: androidx.media3.common.Player,
        isActuallyPlaying: Boolean,
        onBack: () -> Unit,
        onLyrics: () -> Unit,
        radioList: List<RadioStationEntity>,
        showLyricsButton: Boolean,
        backFocusRequester: FocusRequester,
        showLyrics: Boolean = false,
        lyricsText: String? = null,
        isFetchingLyrics: Boolean = false,
        onCloseLyrics: () -> Unit = {},
        castSession: com.google.android.gms.cast.framework.CastSession? = null,
        sleepTimerTimeLeft: Long? = null,
        onSetSleepTimer: (Int?) -> Unit = {},
        onCycleArtwork: () -> Unit = {},
        onTranslate: () -> Unit = {},
        isTranslating: Boolean = false,
        translatedLyrics: String? = null
) {
    // Focus Requesters pour la navigation explicite TV
    val playButtonFocusRequester = remember { FocusRequester() }
    val lyricsButtonFocusRequester = remember { FocusRequester() }
    val timerButtonFocusRequester = remember { FocusRequester() }

    // Gestion du Back Press (Niv 3 > Niv 2 > Niv 1)
    BackHandler(enabled = true) {
        if (showLyrics) {
            onCloseLyrics()
            // Retour focus au player
            try {
                backFocusRequester.requestFocus()
            } catch (e: Exception) {}
        } else {
            onBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // --- NIV 2 : PLAYER (Fond) ---
        Row(modifier = Modifier.fillMaxSize()) {
            // GAUCHE (7/16) - Controles et Infos
            Column(
                    modifier = Modifier.weight(7f).fillMaxHeight().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ZONE 1 (15%) : Boutons Back, Timer et Lyrics
                Row(
                        modifier =
                                Modifier.fillMaxWidth().weight(0.15f).focusProperties {
                                    down = playButtonFocusRequester
                                },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    var isFocused by remember { mutableStateOf(false) }
                    IconButton(
                            onClick = onBack,
                            modifier =
                                    Modifier.focusRequester(backFocusRequester)
                                            .onFocusChanged { isFocused = it.isFocused }
                                            .background(
                                                    if (isFocused) MaterialTheme.colorScheme.primary
                                                    else Color.White.copy(alpha = 0.15f),
                                                    MaterialTheme.shapes.small
                                            )
                                            .border(
                                                    if (isFocused) 3.dp else 0.dp,
                                                    if (isFocused) MaterialTheme.colorScheme.primary
                                                    else Color.Transparent,
                                                    MaterialTheme.shapes.small
                                            )
                    ) {
                        Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                null,
                                tint = if (isFocused) Color.Black else Color.Green,
                                modifier = Modifier.size(32.dp)
                        )
                    }

                    // --- BOUTON TIMER (Landscape) ---
                    Box {
                        var showTimerMenu by remember { mutableStateOf(false) }
                        var isTimerFocused by remember { mutableStateOf(false) }
                        IconButton(
                                onClick = { showTimerMenu = true },
                                modifier =
                                        Modifier.focusRequester(timerButtonFocusRequester)
                                                .onFocusChanged { isTimerFocused = it.isFocused }
                                                .background(
                                                        if (isTimerFocused)
                                                                MaterialTheme.colorScheme.primary
                                                        else if (sleepTimerTimeLeft != null)
                                                                MaterialTheme.colorScheme.primary
                                                                        .copy(alpha = 0.3f)
                                                        else Color.White.copy(alpha = 0.15f),
                                                        MaterialTheme.shapes.small
                                                )
                                                .border(
                                                        if (isTimerFocused) 3.dp else 0.dp,
                                                        if (isTimerFocused)
                                                                MaterialTheme.colorScheme.primary
                                                        else Color.Transparent,
                                                        MaterialTheme.shapes.small
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
                                        text = { Text("Annuler", color = Color.Red) },
                                        onClick = {
                                            onSetSleepTimer(null)
                                            showTimerMenu = false
                                        }
                                )
                            }
                        }
                    }

                    if (showLyricsButton) {
                        var isLyricsFocused by remember { mutableStateOf(false) }
                        TextButton(
                                onClick = onLyrics,
                                modifier =
                                        Modifier.focusRequester(lyricsButtonFocusRequester)
                                                .onFocusChanged { isLyricsFocused = it.isFocused }
                                                .border(
                                                        if (isLyricsFocused) 3.dp else 0.dp,
                                                        if (isLyricsFocused)
                                                                MaterialTheme.colorScheme.primary
                                                        else Color.Transparent,
                                                        MaterialTheme.shapes.small
                                                )
                        ) { Text("Lyrics", color = MaterialTheme.colorScheme.primary) }
                    } else {
                        Spacer(Modifier.size(1.dp))
                    }
                }

                // ZONE 2 (35%) : Nom Radio et Pays
                Column(
                        modifier = Modifier.weight(0.35f).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                ) {
                    Text(
                            text = currentStation?.name ?: "Station inconnue",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            maxLines = 2,
                            textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                            text =
                                    "${currentStation?.country ?: "Monde"} | ${currentStation?.bitrate ?: "?"} kbps",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Gray,
                            maxLines = 1
                    )
                }

                // ZONE 3 (15%) : Boutons Lecture
                Row(
                        modifier =
                                Modifier.fillMaxWidth().weight(0.15f).focusProperties {
                                    up = backFocusRequester
                                },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                ) {
                    var isPrevFocused by remember { mutableStateOf(false) }
                    IconButton(
                            onClick = {
                                val idx =
                                        radioList.indexOfFirst {
                                            it.stationuuid == currentStation?.stationuuid
                                        }
                                if (idx > 0) {
                                    exoPlayer.seekToPrevious()
                                }
                            },
                            modifier =
                                    Modifier.onFocusChanged { isPrevFocused = it.isFocused }
                                            .border(
                                                    if (isPrevFocused) 3.dp else 0.dp,
                                                    if (isPrevFocused)
                                                            MaterialTheme.colorScheme.primary
                                                    else Color.Transparent,
                                                    androidx.compose.foundation.shape.CircleShape
                                            )
                    ) {
                        Icon(
                                Icons.Default.SkipPrevious,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                        )
                    }

                    Spacer(Modifier.width(32.dp))

                    var isPlayFocused by remember { mutableStateOf(false) }
                    IconButton(
                            onClick = {
                                if (castSession != null && castSession.isConnected) {
                                    val client = castSession.remoteMediaClient
                                    if (client?.isPlaying == true) client.pause()
                                    else client?.play()
                                } else {
                                    if (isActuallyPlaying) exoPlayer.pause() else exoPlayer.play()
                                }
                            },
                            modifier =
                                    Modifier.focusRequester(playButtonFocusRequester)
                                            .onFocusChanged { isPlayFocused = it.isFocused }
                                            .border(
                                                    if (isPlayFocused) 3.dp else 0.dp,
                                                    if (isPlayFocused)
                                                            MaterialTheme.colorScheme.primary
                                                    else Color.Transparent,
                                                    androidx.compose.foundation.shape.CircleShape
                                            )
                    ) {
                        Icon(
                                if (isActuallyPlaying) Icons.Default.PauseCircleFilled
                                else Icons.Default.PlayCircleFilled,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(80.dp)
                        )
                    }

                    Spacer(Modifier.width(32.dp))

                    var isNextFocused by remember { mutableStateOf(false) }
                    IconButton(
                            onClick = {
                                val idx =
                                        radioList.indexOfFirst {
                                            it.stationuuid == currentStation?.stationuuid
                                        }
                                if (idx >= 0 && idx < radioList.size - 1) {
                                    exoPlayer.seekToNext()
                                }
                            },
                            modifier =
                                    Modifier.onFocusChanged { isNextFocused = it.isFocused }
                                            .border(
                                                    if (isNextFocused) 3.dp else 0.dp,
                                                    if (isNextFocused)
                                                            MaterialTheme.colorScheme.primary
                                                    else Color.Transparent,
                                                    androidx.compose.foundation.shape.CircleShape
                                            )
                    ) {
                        Icon(
                                Icons.Default.SkipNext,
                                null,
                                tint = Color.White,
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
                            style = MaterialTheme.typography.headlineMedium,
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
            Box(modifier = Modifier.weight(9f).fillMaxHeight().background(Color.Black)) {
                // Fond par défaut
                Icon(
                        Icons.Default.Radio,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.Center).size(160.dp),
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
                IconButton(
                        onClick = onCycleArtwork,
                        modifier =
                                Modifier.align(Alignment.BottomEnd)
                                        .padding(16.dp)
                                        .background(
                                                Color.Black.copy(alpha = 0.3f),
                                                androidx.compose.foundation.shape.CircleShape
                                        )
                                        .size(48.dp)
                ) {
                    Icon(
                            Icons.Default.Sync,
                            null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // --- NIV 3 : LYRICS OVERLAY (Flottant) ---
        // Occupe 75% de la largeur totale (soit 12/16), aligné à gauche.
        if (showLyrics) {
            val scrollState = rememberScrollState()
            val lyricsFocusRequester = remember { FocusRequester() }
            val scope = rememberCoroutineScope() // Scope pour le scroll manuel

            // Force le focus dès l'ouverture pour activer le scroll D-Pad
            LaunchedEffect(Unit) {
                try {
                    lyricsFocusRequester.requestFocus()
                } catch (e: Exception) {}
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
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onTranslate) {
                                Text(
                                        if (isTranslating) "Original" else "Translate (FR)",
                                        color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = onCloseLyrics) {
                                Icon(Icons.Default.Close, null, tint = Color.White)
                            }
                        }

                        BilingualLyrics(
                                original = lyricsText ?: "",
                                translated = translatedLyrics,
                                isTranslating = isTranslating,
                                modifier =
                                        Modifier.fillMaxSize()
                                                .focusRequester(lyricsFocusRequester)
                                                .focusable()
                                                .onKeyEvent { event ->
                                                    if (event.type == KeyEventType.KeyDown) {
                                                        val step = 300f
                                                        when (event.nativeKeyEvent.keyCode) {
                                                            android.view.KeyEvent
                                                                    .KEYCODE_DPAD_DOWN -> {
                                                                scope.launch {
                                                                    scrollState.animateScrollTo(
                                                                            (scrollState.value +
                                                                                            step)
                                                                                    .toInt()
                                                                    )
                                                                }
                                                                true
                                                            }
                                                            android.view.KeyEvent
                                                                    .KEYCODE_DPAD_UP -> {
                                                                scope.launch {
                                                                    scrollState.animateScrollTo(
                                                                            (scrollState.value -
                                                                                            step)
                                                                                    .toInt()
                                                                    )
                                                                }
                                                                true
                                                            }
                                                            else -> false
                                                        }
                                                    } else false
                                                }
                        )
                    }
                }

                // Petit bouton fermer discret en haut à droite de l'overlay pour la souris/touch
                IconButton(onClick = onCloseLyrics, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(Icons.Default.Close, "Fermer", tint = Color.Gray)
                }
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
    if (!isTranslating || translated == null) {
        Text(
                text = original,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())
        )
        return
    }

    val originalLines = original.split("\n")
    val translatedLines = translated.split("\n")

    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        for (i in originalLines.indices) {
            val orig = originalLines[i]
            if (orig.isBlank()) {
                Spacer(Modifier.height(16.dp))
                continue
            }
            Text(text = orig, style = MaterialTheme.typography.headlineSmall, color = Color.White)
            if (i < translatedLines.size && translatedLines[i].isNotBlank()) {
                Text(
                        text = translatedLines[i],
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Gray
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
