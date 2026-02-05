package com.example.simpleradio.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.mediarouter.app.MediaRouteButton
import coil.compose.AsyncImage
import com.example.simpleradio.data.api.ImageScraper
import com.example.simpleradio.data.api.LrcLibApi
import com.example.simpleradio.data.api.TranslationApi
import com.example.simpleradio.data.local.entities.RadioStationEntity
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    // Keep Screen On Logic
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

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
                        } else if (intent?.action == "com.example.simpleradio.PREV") {
                            if (currentIndex > 0) nextStation = radioList[currentIndex - 1]
                        }

                        if (nextStation != null) {
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
                        val text = resp.plainLyrics ?: "Paroles non disponibles."
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
                    castSession = castSession,
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
        exoPlayer: Player,
        isActuallyPlaying: Boolean,
        onBack: () -> Unit,
        onLyrics: () -> Unit,
        radioList: List<RadioStationEntity>,
        showLyricsButton: Boolean,
        castSession: CastSession? = null,
        onCycleArtwork: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header Bar (Single line: Back + Cast + Lyrics)
        Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
        ) {
            // Back Button
            IconButton(onClick = onBack) {
                Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        "Retour",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                )
            }

            Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                // Cast Button
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    AndroidView(
                            factory = { ctx ->
                                MediaRouteButton(ctx).apply {
                                    CastButtonFactory.setUpMediaRouteButton(ctx, this)
                                    try {
                                        @Suppress("DEPRECATION") this.setAlwaysVisible(true)
                                    } catch (_: Exception) {}
                                }
                            }
                    )
                }

                // Lyrics Button (Pill style)
                if (showLyricsButton) {
                    Surface(
                            onClick = onLyrics,
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            modifier = Modifier.height(40.dp)
                    ) {
                        Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                    Icons.AutoMirrored.Filled.Subject,
                                    "Paroles",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                            )
                            Text(
                                    "PAROLES",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
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
                        tint = if (isSyncFocused) Color.White else Color.White.copy(alpha = 0.5f),
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
            // PREVIOUS
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
                                    .background(
                                            if (isPrevFocused) Color.White else Color.Transparent,
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
                            if (client?.isPlaying == true) client.pause() else client?.play()
                        } else {
                            if (isActuallyPlaying) exoPlayer.pause() else exoPlayer.play()
                        }
                    },
                    modifier =
                            Modifier.onFocusChanged { isPlayFocused = it.isFocused }
                                    .background(
                                            if (isPlayFocused) Color.White else Color.Transparent,
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
                                    it.stationuuid == currentStation?.stationuuid
                                }
                        if (idx >= 0 && idx < radioList.size - 1) {
                            exoPlayer.seekToNext()
                        }
                    },
                    modifier =
                            Modifier.onFocusChanged { isNextFocused = it.isFocused }
                                    .background(
                                            if (isNextFocused) Color.White else Color.Transparent,
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
                        modifier = Modifier.weight(7f).fillMaxHeight().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ZONE 1 (15%) : Boutons Back, Timer et Lyrics
                    Row(
                            modifier = Modifier.fillMaxWidth().weight(0.15f).padding(top = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        var isBackFocused by remember { mutableStateOf(false) }
                        IconButton(
                                onClick = onBack,
                                modifier =
                                        Modifier.onFocusChanged { isBackFocused = it.isFocused }
                                                .background(
                                                        if (isBackFocused) Color.White
                                                        else Color.White.copy(alpha = 0.15f),
                                                        MaterialTheme.shapes.small
                                                )
                        ) {
                            Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    null,
                                    tint = if (isBackFocused) Color.Black else Color.White,
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
                                                            else Color.White.copy(alpha = 0.15f),
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

                        // --- CAST BUTTON (Always Visible) ---
                        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            AndroidView(
                                    factory = { ctx ->
                                        MediaRouteButton(ctx).apply {
                                            CastButtonFactory.setUpMediaRouteButton(ctx, this)
                                            try {
                                                @Suppress("DEPRECATION") this.setAlwaysVisible(true)
                                            } catch (_: Exception) {}
                                        }
                                    }
                            )
                        }

                        // --- LYRICS BUTTON (Always Reachable - Box Style)
                        // ---
                        val context = LocalContext.current
                        var isLyricsFocused by remember { mutableStateOf(false) }

                        Box(
                                modifier =
                                        Modifier.height(40.dp)
                                                .onFocusChanged { isLyricsFocused = it.isFocused }
                                                .background(
                                                        color =
                                                                if (isLyricsFocused) Color.White
                                                                else if (showLyricsButton)
                                                                        MaterialTheme.colorScheme
                                                                                .surfaceVariant
                                                                                .copy(alpha = 0.3f)
                                                                else Color.Transparent,
                                                        shape = MaterialTheme.shapes.small
                                                )
                                                .border(
                                                        width = 1.dp,
                                                        color =
                                                                if (isLyricsFocused)
                                                                        Color.Transparent
                                                                else if (showLyricsButton)
                                                                        MaterialTheme.colorScheme
                                                                                .primary.copy(
                                                                                alpha = 0.5f
                                                                        )
                                                                else Color.Gray.copy(alpha = 0.3f),
                                                        shape = MaterialTheme.shapes.small
                                                )
                                                .clickable {
                                                    if (showLyricsButton) {
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
                                                .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Subject,
                                        contentDescription = "Lyrics",
                                        tint =
                                                if (isLyricsFocused) Color.Black
                                                else if (showLyricsButton)
                                                        MaterialTheme.colorScheme.primary
                                                else Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                        text = "PAROLES",
                                        style = MaterialTheme.typography.labelMedium,
                                        color =
                                                if (isLyricsFocused) Color.Black
                                                else if (showLyricsButton)
                                                        MaterialTheme.colorScheme.primary
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
                            modifier = Modifier.fillMaxWidth().weight(0.15f),
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
                    // --- BOUTON SYNC (Cycling - Landscape) ---
                    var isSyncFocused by remember { mutableStateOf(false) }
                    IconButton(
                            onClick = onCycleArtwork,
                            modifier =
                                    Modifier.align(Alignment.BottomEnd)
                                            .padding(end = 16.dp, bottom = 48.dp)
                                            .onFocusChanged { isSyncFocused = it.isFocused }
                                            .background(
                                                    if (isSyncFocused) Color.Black
                                                    else Color.Black.copy(alpha = 0.3f),
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
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            var isTranslateFocused by remember { mutableStateOf(false) }
                            TextButton(
                                    onClick = onTranslate,
                                    modifier =
                                            Modifier.onFocusChanged {
                                                        isTranslateFocused = it.isFocused
                                                    }
                                                    .background(
                                                            if (isTranslateFocused) Color.White
                                                            else Color.Transparent,
                                                            MaterialTheme.shapes.small
                                                    )
                            ) {
                                Text(
                                        if (isTranslating) "Original" else "Translate (FR)",
                                        color =
                                                if (isTranslateFocused) Color.Black
                                                else MaterialTheme.colorScheme.primary
                                )
                            }
                            var isCloseFocused by remember { mutableStateOf(false) }
                            IconButton(
                                    onClick = onCloseLyrics,
                                    modifier =
                                            Modifier.focusRequester(closeButtonFocusRequester)
                                                    .onFocusChanged {
                                                        isCloseFocused = it.isFocused
                                                    }
                                                    .background(
                                                            if (isCloseFocused) Color.White
                                                            else Color.Transparent,
                                                            CircleShape
                                                    )
                            ) {
                                Icon(
                                        Icons.Default.Close,
                                        null,
                                        tint = if (isCloseFocused) Color.Black else Color.White
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
                                                        Color(0xFF252525) // Gris foncé proche dark
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
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}
