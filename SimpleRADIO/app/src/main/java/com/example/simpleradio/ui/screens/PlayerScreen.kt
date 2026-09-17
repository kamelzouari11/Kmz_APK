package com.example.simpleradio.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import com.example.simpleradio.data.api.LrcLibApi
import com.example.simpleradio.data.api.TranslationApi
import com.example.simpleradio.data.local.entities.RadioStationEntity
import com.example.simpleradio.ui.components.*
import com.example.simpleradio.ui.components.ArtworkDisplay
import com.example.simpleradio.ui.components.BilingualLyrics
import com.example.simpleradio.upnp.UpnpButton
import com.google.android.gms.cast.framework.CastSession
import kotlinx.coroutines.launch

@Composable
fun VideoPlayerView(
        exoPlayer: Player,
        onBack: () -> Unit,
        onPowerOff: () -> Unit,
        radioStation: RadioStationEntity? = null,
        lrcLibApi: LrcLibApi? = null,
        radioList: List<RadioStationEntity> = emptyList(),
        castSession: CastSession? = null,
        artist: String? = null,
        title: String? = null,
        artworkUrl: String? = null,
        showLogoValidation: Boolean = false,
        logoIsConfirmed: Boolean = false,
        logoCandidatePosition: Int = 0,
        logoCandidateCount: Int = 0,
        onArtworkLoadError: (String) -> Unit = {},
        onConfirmLogo: () -> Unit = {},
        onRejectLogo: () -> Unit = {},
        onUnconfirmLogo: () -> Unit = {},
        sleepTimerTimeLeft: Long? = null,
        onSetSleepTimer: (Int?) -> Unit = {},
        showLyrics: Boolean = false,
        onToggleLyrics: (Boolean) -> Unit = {},
        upnpManager: UpnpManager? = null
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
                        val exactResult =
                                try {
                                    lrcLibApi.getLyrics(a.trim(), t.trim())
                                } catch (exactError: Exception) {
                                    Log.w(
                                            "LyricsLookup",
                                            "LRCLIB /get failed for $a - $t; trying /search",
                                            exactError
                                    )
                                    null
                                }
                        val resp =
                                exactResult?.takeIf { !it.plainLyrics.isNullOrBlank() }
                                        ?: lrcLibApi
                                                .searchLyrics(a.trim(), t.trim())
                                                .firstOrNull {
                                                    !it.plainLyrics.isNullOrBlank()
                                                }
                        val text = resp?.plainLyrics ?: "Paroles non disponibles."
                        lyricsText = text
                        lyricsCache[key] = text
                    } catch (error: Exception) {
                        Log.e("LyricsLookup", "LRCLIB lookup failed for $a - $t", error)
                        lyricsText = "Service de paroles temporairement indisponible."
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
                    artworkUrl = artworkUrl,
                    showLogoValidation = showLogoValidation,
                    logoIsConfirmed = logoIsConfirmed,
                    logoCandidatePosition = logoCandidatePosition,
                    logoCandidateCount = logoCandidateCount,
                    onArtworkLoadError = onArtworkLoadError,
                    onConfirmLogo = onConfirmLogo,
                    onRejectLogo = onRejectLogo,
                    onUnconfirmLogo = onUnconfirmLogo,
                    exoPlayer = exoPlayer,
                    isActuallyPlaying = isActuallyPlaying,
                    onBack = onBack,
                    onPowerOff = onPowerOff,
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
                    onTranslate = { toggleTranslation() },
                    isTranslating = isTranslating,
                    translatedLyrics = translatedLyrics
            )
        } else {
            PortraitPlayerLayout(
                    currentStation = radioStation,
                    artist = artist,
                    title = title,
                    artworkUrl = artworkUrl,
                    showLogoValidation = showLogoValidation,
                    logoIsConfirmed = logoIsConfirmed,
                    logoCandidatePosition = logoCandidatePosition,
                    logoCandidateCount = logoCandidateCount,
                    onArtworkLoadError = onArtworkLoadError,
                    onConfirmLogo = onConfirmLogo,
                    onRejectLogo = onRejectLogo,
                    onUnconfirmLogo = onUnconfirmLogo,
                    exoPlayer = exoPlayer,
                    isActuallyPlaying = isActuallyPlaying,
                    onBack = onBack,
                    onPowerOff = onPowerOff,
                    onLyrics = {
                        onToggleLyrics(true)
                        fetchLyrics()
                    },
                    radioList = radioList,
                    showLyricsButton =
                            lrcLibApi != null && !artist.isNullOrBlank() && !title.isNullOrBlank(),
                    castSession = castSession,
                    upnpManager = upnpManager
            )

            // Portrait Full Screen Lyrics Overlay
            if (showLyrics) {
                PortraitLyricsOverlay(
                        lyricsText = lyricsText,
                        isFetchingLyrics = isFetchingLyrics,
                        isTranslating = isTranslating,
                        translatedLyrics = translatedLyrics,
                        onToggleTranslation = { toggleTranslation() },
                        onClose = { onToggleLyrics(false) },
                        onPowerOff = onPowerOff,
                        bilingualLyricsContent = { original, translated, translating, modifier ->
                            BilingualLyrics(
                                    original = original,
                                    translated = translated,
                                    isTranslating = translating,
                                    modifier = modifier
                            )
                        }
                )
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
        showLogoValidation: Boolean,
        logoIsConfirmed: Boolean,
        logoCandidatePosition: Int,
        logoCandidateCount: Int,
        onArtworkLoadError: (String) -> Unit,
        onConfirmLogo: () -> Unit,
        onRejectLogo: () -> Unit,
        onUnconfirmLogo: () -> Unit,
        exoPlayer: Player,
        isActuallyPlaying: Boolean,
        onBack: () -> Unit,
        onPowerOff: () -> Unit,
        onLyrics: () -> Unit,
        radioList: List<RadioStationEntity>,
        showLyricsButton: Boolean,
        castSession: CastSession? = null,
        upnpManager: UpnpManager? = null
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
                // UPnP Button
                upnpManager?.let { UpnpButton(upnpManager = it) }

                // Cast Button
                CastButton()

                PowerOffButton(onPowerOff = onPowerOff)

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
        ArtworkDisplay(
                artworkUrl = artworkUrl,
                modifier = Modifier.fillMaxWidth().weight(0.5f),
                showLogoValidation = showLogoValidation,
                logoIsConfirmed = logoIsConfirmed,
                logoCandidatePosition = logoCandidatePosition,
                logoCandidateCount = logoCandidateCount,
                onArtworkLoadError = onArtworkLoadError,
                onConfirmLogo = onConfirmLogo,
                onRejectLogo = onRejectLogo,
                onUnconfirmLogo = onUnconfirmLogo
        )

        // ZONE 2 : INFOS STATION (20% Hauteur)
        StationInfoDisplay(
                currentStation = currentStation,
                modifier = Modifier.fillMaxWidth().weight(0.2f)
        )

        // ZONE 3 : CONTRÔLES DE LECTURE
        PlaybackControls(
                exoPlayer = exoPlayer,
                currentStation = currentStation,
                radioList = radioList,
                isActuallyPlaying = isActuallyPlaying,
                castSession = castSession,
                modifier = Modifier.fillMaxWidth().weight(0.1f)
        )

        // ZONE 4 : INFOS ARTISTE / TITRE (20% Hauteur)
        MetadataInfoDisplay(
                artist = artist,
                title = title,
                currentStation = currentStation,
                modifier = Modifier.fillMaxWidth().weight(0.2f).padding(bottom = 16.dp)
        )
    }
}

@Composable
fun LandscapePlayerLayout(
        currentStation: RadioStationEntity?,
        artist: String?,
        title: String?,
        artworkUrl: String?,
        showLogoValidation: Boolean,
        logoIsConfirmed: Boolean,
        logoCandidatePosition: Int,
        logoCandidateCount: Int,
        onArtworkLoadError: (String) -> Unit,
        onConfirmLogo: () -> Unit,
        onRejectLogo: () -> Unit,
        onUnconfirmLogo: () -> Unit,
        exoPlayer: Player,
        isActuallyPlaying: Boolean,
        onBack: () -> Unit,
        onPowerOff: () -> Unit,
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
                    // ZONE 1 (15%) : Boutons Back, Timer, Cast et Lyrics
                    LandscapePlayerHeader(
                            onBack = onBack,
                            onPowerOff = onPowerOff,
                            onLyrics = onLyrics,
                            showLyricsButton = showLyricsButton,
                            sleepTimerTimeLeft = sleepTimerTimeLeft,
                            onSetSleepTimer = onSetSleepTimer,
                            modifier = Modifier.weight(0.15f)
                    )

                    // ZONE 2 (35%) : Nom Radio et Pays
                    StationInfoDisplay(
                            currentStation = currentStation,
                            modifier = Modifier.weight(0.35f).fillMaxWidth(),
                            nameMaxLines = 2,
                            nameStyle = MaterialTheme.typography.headlineMedium
                    )

                    // ZONE 3 (15%) : Boutons Lecture
                    PlaybackControls(
                            exoPlayer = exoPlayer,
                            currentStation = currentStation,
                            radioList = radioList,
                            isActuallyPlaying = isActuallyPlaying,
                            castSession = castSession,
                            buttonSize = 48.dp,
                            playButtonSize = 80.dp,
                            spacing = 32.dp,
                            modifier = Modifier.fillMaxWidth().weight(0.15f)
                    )

                    // ZONE 4 (35%) : Artist / Title
                    MetadataInfoDisplay(
                            artist = artist,
                            title = title,
                            currentStation = currentStation,
                            modifier = Modifier.weight(0.35f).fillMaxWidth(),
                            artistMaxLines = 2,
                            titleMaxLines = 3,
                            artistStyle = MaterialTheme.typography.headlineMedium,
                            titleStyle = MaterialTheme.typography.titleLarge
                    )
                }

                // DROITE (9/16) - Logo/Artwork
                ArtworkDisplay(
                        artworkUrl = artworkUrl,
                        modifier = Modifier.weight(9f).fillMaxHeight(),
                        defaultIconSize = 160.dp,
                        showLogoValidation = showLogoValidation,
                        logoIsConfirmed = logoIsConfirmed,
                        logoCandidatePosition = logoCandidatePosition,
                        logoCandidateCount = logoCandidateCount,
                        onArtworkLoadError = onArtworkLoadError,
                        onConfirmLogo = onConfirmLogo,
                        onRejectLogo = onRejectLogo,
                        onUnconfirmLogo = onUnconfirmLogo
                )
            }
        }

        // --- NIV 3 : LYRICS OVERLAY (Flottant) ---
        if (showLyrics) {
            LandscapeLyricsOverlay(
                    lyricsText = lyricsText,
                    isFetchingLyrics = isFetchingLyrics,
                    isTranslating = isTranslating,
                    translatedLyrics = translatedLyrics,
                    onTranslate = onTranslate,
                    onCloseLyrics = onCloseLyrics,
                    onPowerOff = onPowerOff
            )
        }
    }
}
