package com.example.simpleradio

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.example.simpleradio.data.CsvDataLoader
import com.example.simpleradio.data.RadioRepository
import com.example.simpleradio.data.api.LrcLibApi
import com.example.simpleradio.data.api.LyricsClient
import com.example.simpleradio.data.api.RadioClient
import com.example.simpleradio.data.local.AppDatabase
import com.example.simpleradio.ui.MainViewModel
import com.example.simpleradio.ui.MainViewModelFactory
import com.example.simpleradio.ui.components.*
import com.example.simpleradio.ui.screens.BrowseScreen
import com.example.simpleradio.ui.screens.VideoPlayerView
import com.example.simpleradio.ui.theme.SimpleRADIOTheme
import com.example.simpleradio.utils.toMediaItem
import com.google.android.gms.cast.framework.CastContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
                val prefs = getSharedPreferences("SimpleRadioPrefs", Context.MODE_PRIVATE)
                val viewModel =
                        ViewModelProvider(this, MainViewModelFactory(radioRepository, prefs))[
                                MainViewModel::class.java]
                setContent {
                        SimpleRADIOTheme(darkTheme = true) {
                                MainScreen(viewModel, radioRepository, lrcLibApi)
                        }
                }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel, radioRepository: RadioRepository, lrcLibApi: LrcLibApi) {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        // --- Sauvegarde/Restauration via Helper ---
        val dataManagement = rememberDataManagement(radioRepository)

        val prefs = context.getSharedPreferences("SimpleRadioPrefs", Context.MODE_PRIVATE)
        val resultsListState = rememberLazyListState()
        val listFocusRequester = remember { FocusRequester() }

        // Removed local aliases to use viewModel directly

        val exoPlayer = rememberRadioPlayer(context, viewModel, radioRepository, prefs)
        val (castHelper, castSession) = rememberCastManagement(context, viewModel, exoPlayer)
        val upnpManager = rememberUpnpManagement(context, viewModel)

        PlaybackManagement(
                viewModel,
                radioRepository,
                context,
                prefs,
                exoPlayer,
                castHelper,
                castSession
        )

        // Removed local aliases to use viewModel directly

        var showLyricsGlobal by remember { mutableStateOf(false) }
        var showAddCustomUrlDialog by remember { mutableStateOf(false) }
        var showSearchDialog by remember { mutableStateOf(false) }

        LaunchedEffect(viewModel.isFullScreenPlayer) {
                if (!viewModel.isFullScreenPlayer) {
                        delay(500)
                        try {
                                listFocusRequester.requestFocus()
                        } catch (_: Exception) {}
                }
        }

        // --- RADIO STATE ---
        val radioFavoriteLists by
                radioRepository.allFavoriteLists.collectAsState(initial = emptyList())
        val recentRadios by radioRepository.recentRadios.collectAsState(initial = emptyList())

        // --- NAVIGATION BACK ---
        BackHandler(enabled = true) {
                if (showLyricsGlobal) {
                        showLyricsGlobal = false
                } else if (viewModel.isFullScreenPlayer) {
                        viewModel.isFullScreenPlayer = false
                } else if (viewModel.isViewingRadioResults) {
                        // Retour intelligent vers les catégories sans reset
                        viewModel.isViewingRadioResults = false
                } else {
                        (context as? Activity)?.moveTaskToBack(true)
                }
        }

        // --- HELPER PLAYLIST ---

        val favFlow =
                remember(viewModel.selectedRadioFavoriteListId) {
                        if (viewModel.selectedRadioFavoriteListId != null) {
                                radioRepository.getRadiosByFavoriteList(
                                        viewModel.selectedRadioFavoriteListId!!
                                )
                        } else {
                                kotlinx.coroutines.flow.flowOf(emptyList())
                        }
                }
        val radioFavStations by favFlow.collectAsState(initial = emptyList())
        val currentRadioList =
                when {
                        viewModel.selectedRadioFavoriteListId != null -> radioFavStations
                        viewModel.showRecentRadiosOnly -> recentRadios
                        else ->
                                viewModel
                                        .radioStations // Toujours afficher radioStations par défaut
                }

        LaunchedEffect(viewModel.isViewingRadioResults, viewModel.playingRadio) {
                if (viewModel.isViewingRadioResults && viewModel.playingRadio != null) {
                        try {
                                val index =
                                        currentRadioList.indexOfFirst {
                                                it.stationuuid ==
                                                        viewModel.playingRadio?.stationuuid
                                        }
                                if (index != -1) {
                                        resultsListState.scrollToItem(index)
                                }
                        } catch (_: Exception) {}
                }
        }

        // Removed local radioToFavorite aliases

        LaunchedEffect(
                viewModel.searchTrigger,
                viewModel.selectedRadioFavoriteListId,
                viewModel.showRecentRadiosOnly
        ) { viewModel.searchStations(context) }

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(modifier = Modifier.fillMaxSize()) {
                        if (!viewModel.isFullScreenPlayer) {
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
                                                MainHeader(
                                                        onExportFavorites = {
                                                                dataManagement.exportFavorites()
                                                        },
                                                        onImportFavorites = {
                                                                dataManagement.importFavorites()
                                                        },
                                                        onPowerOff = {
                                                                exoPlayer?.stop()
                                                                context.stopService(
                                                                        Intent(
                                                                                context,
                                                                                PlaybackService::class
                                                                                        .java
                                                                        )
                                                                )
                                                                (context as? Activity)?.finish()
                                                        },
                                                        onRecentClick = {
                                                                viewModel.onRecentClick()
                                                        },
                                                        onPlayerClick = {
                                                                if (!viewModel.isViewingRadioResults
                                                                )
                                                                        viewModel
                                                                                .isViewingRadioResults =
                                                                                true
                                                                else if (!viewModel
                                                                                .isFullScreenPlayer
                                                                )
                                                                        viewModel
                                                                                .isFullScreenPlayer =
                                                                                true
                                                        },
                                                        showPlayerButton =
                                                                viewModel.playingRadio != null,
                                                        playerIsPlaying = viewModel.playerIsPlaying
                                                )
                                        }

                                        if (viewModel.isLoading)
                                                LinearProgressIndicator(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(horizontal = 8.dp)
                                                )

                                        BrowseScreen(
                                                isPortrait =
                                                        LocalConfiguration.current.orientation ==
                                                                Configuration.ORIENTATION_PORTRAIT,
                                                radioCountries = viewModel.radioCountries,
                                                radioTags = viewModel.radioTags,
                                                currentRadioList = currentRadioList,
                                                selectedRadioCountry =
                                                        viewModel.selectedRadioCountry,
                                                selectedRadioTag = viewModel.selectedRadioTag,
                                                selectedRadioBitrate =
                                                        viewModel.selectedRadioBitrate,
                                                radioSearchQuery = viewModel.radioSearchQuery,
                                                isQualityExpanded = viewModel.isQualityExpanded,
                                                isCountryExpanded = viewModel.isCountryExpanded,
                                                isGenreExpanded = viewModel.isGenreExpanded,
                                                isViewingRadioResults =
                                                        viewModel.isViewingRadioResults,
                                                playingRadio = viewModel.playingRadio,
                                                listFocusRequester = listFocusRequester,
                                                resultsListState = resultsListState,
                                                onCountrySelected = {
                                                        viewModel.setSelectedCountry(it)
                                                },
                                                onTagSelected = { viewModel.setSelectedTag(it) },
                                                onBitrateSelected = {
                                                        viewModel.setSelectedBitrate(it)
                                                },
                                                onToggleQualityExpanded = {
                                                        viewModel.isQualityExpanded =
                                                                !viewModel.isQualityExpanded
                                                },
                                                onToggleCountryExpanded = {
                                                        viewModel.isCountryExpanded =
                                                                !viewModel.isCountryExpanded
                                                },
                                                onToggleGenreExpanded = {
                                                        viewModel.isGenreExpanded =
                                                                !viewModel.isGenreExpanded
                                                },
                                                onToggleViewingResults = { shouldView ->
                                                        viewModel.isViewingRadioResults = shouldView
                                                },
                                                onRadioSelected = { radio ->
                                                        viewModel.currentArtist = null
                                                        viewModel.currentTitle = null
                                                        viewModel.currentArtworkUrl = null
                                                        viewModel.playingRadio = radio
                                                        val snapshot = currentRadioList.toList()
                                                        viewModel.navRadioList = snapshot
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
                                                        viewModel.isFullScreenPlayer = true
                                                        scope.launch {
                                                                radioRepository.addToRecents(
                                                                        radio.stationuuid
                                                                )
                                                        }
                                                },
                                                onAddFavorite = { viewModel.radioToFavorite = it },
                                                onResetFilters = { viewModel.onResetFilters() },
                                                onSearchClick = { showSearchDialog = true },
                                                onApplyFilters = { viewModel.onApplyFilters() }
                                        )
                                }
                        }

                        if (viewModel.isFullScreenPlayer &&
                                        viewModel.playingRadio != null &&
                                        exoPlayer != null
                        ) {
                                VideoPlayerView(
                                        exoPlayer = exoPlayer!!,
                                        onBack = { viewModel.isFullScreenPlayer = false },
                                        radioStation = viewModel.playingRadio,
                                        radioList = viewModel.navRadioList,
                                        lrcLibApi = lrcLibApi,
                                        castSession = castSession,
                                        artist = viewModel.currentArtist,
                                        title = viewModel.currentTitle,
                                        artworkUrl = viewModel.currentArtworkUrl,
                                        sleepTimerTimeLeft = viewModel.sleepTimerTimeLeft,
                                        onSetSleepTimer = { mins -> viewModel.setSleepTimer(mins) },
                                        showLyrics = showLyricsGlobal,
                                        onToggleLyrics = { show: Boolean ->
                                                showLyricsGlobal = show
                                        },
                                        upnpManager = upnpManager
                                )
                        }

                        MainDialogs(
                                radioRepository = radioRepository,
                                showAddCustomUrlDialog = showAddCustomUrlDialog,
                                onSetShowAddCustomUrlDialog = { showAddCustomUrlDialog = it },
                                showAddListDialog = viewModel.showAddListDialog,
                                onSetShowAddListDialog = { viewModel.showAddListDialog = it },
                                showSearchDialog = showSearchDialog,
                                onSetShowSearchDialog = { showSearchDialog = it },
                                radioSearchQuery = viewModel.radioSearchQuery,
                                onSetRadioSearchQuery = { viewModel.updateSearchQuery(it) },
                                onSetShowRecentRadiosOnly = { viewModel.showRecentRadiosOnly = it },
                                onSetIsViewingRadioResults = {
                                        viewModel.isViewingRadioResults = it
                                },
                                onIncrementSearchTrigger = { viewModel.incrementSearchTrigger() },
                                radioToFavorite = viewModel.radioToFavorite,
                                onSetRadioToFavorite = { viewModel.radioToFavorite = it },
                                radioFavoriteLists = radioFavoriteLists
                        )
                }
        }
}
                                onSetRadioToFavorite = { viewModel.radioToFavorite = it },
                                radioFavoriteLists = radioFavoriteLists
                        )
                }
        }
}
