package com.example.myiptv.ui

import android.media.AudioManager
import android.content.res.Configuration
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import androidx.media3.exoplayer.ExoPlayer
import com.example.myiptv.data.BrowseMode
import com.example.myiptv.data.EpgProgram
import com.example.myiptv.data.SavedChannel
import com.example.myiptv.ui.theme.LocalTvTextSizes
import com.example.myiptv.ui.theme.MyIptvPalette
import kotlinx.coroutines.delay

@Composable
fun MainRoute(
    viewModel: MainViewModel,
    onQuit: () -> Unit,
    onPlayerModeChanged: (Boolean) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val epgSearchState by viewModel.epgSearch.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val player = rememberTvPlayer()
    KeepScreenAwake(enabled = !state.streamUrl.isNullOrBlank())
    val stopStreaming: () -> Unit = {
        // Stop network loading immediately, then clear the source so recomposition cannot resume it.
        player.playWhenReady = false
        player.stop()
        player.clearMediaItems()
        viewModel.stopStreaming()
    }
    var showProfile by remember { mutableStateOf(false) }
    var startupProfileDismissed by remember { mutableStateOf(false) }
    var showFavorites by remember { mutableStateOf(false) }
    var editChannelFavorites by remember { mutableStateOf(false) }
    var returnToEpg by rememberSaveable { mutableStateOf(false) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showEpgSearch by rememberSaveable { mutableStateOf(false) }
    var showCinema by rememberSaveable { mutableStateOf(false) }
    var cinemaPlayback by rememberSaveable { mutableStateOf(false) }
    var showSports by rememberSaveable { mutableStateOf(false) }
    var sportsPlayback by rememberSaveable { mutableStateOf(false) }
    val epgListState = rememberLazyListState()
    var epgQuery by rememberSaveable { mutableStateOf("") }
    var showClearSearchHistoryConfirmation by remember { mutableStateOf(false) }
    var showClearRecentHistoryConfirmation by remember { mutableStateOf(false) }
    var showQuitConfirmation by remember { mutableStateOf(false) }
    var backupUploadToConfirm by remember { mutableStateOf<Boolean?>(null) }
    var epgDatabaseUploadToConfirm by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(state.isFullScreen) {
        onPlayerModeChanged(state.isFullScreen)
    }

    BackHandler(enabled = !state.initializing) {
        showQuitConfirmation = true
    }

    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(2_500)
            viewModel.clearMessage()
        }
    }

    when {
        state.initializing -> LoadingScreen()
        configuration.orientation == Configuration.ORIENTATION_PORTRAIT && !state.isFullScreen ->
            MainPortraitScreen(
                state = state,
                player = player,
                onStop = stopStreaming,
                onEpg = { showEpgSearch = true },
                onCinema = { showCinema = true; cinemaPlayback = false; returnToEpg = false },
                onSports = { showSports = true; sportsPlayback = false; returnToEpg = false },
                onEpgResults = viewModel::showEpgSearchResults,
                onCountry = viewModel::selectCountry,
                onCategory = viewModel::selectCategory,
                onChannelFocused = viewModel::focusChannelImmediately,
                onChannelPlay = viewModel::play,
                onChannelEpg = viewModel::showFullEpg,
                onDismissFullEpg = viewModel::dismissFullEpg,
                onChannelLongPress = { channel ->
                    viewModel.focusChannelImmediately(channel)
                    editChannelFavorites = true
                    showFavorites = true
                },
                onLive = viewModel::showLive,
                onRecent = viewModel::showRecent,
                onFavorites = {
                    editChannelFavorites = false
                    showFavorites = true
                },
                onSearch = { showSearch = true },
                onRefresh = viewModel::refresh,
                onProfile = { showProfile = true },
                onUpload = { backupUploadToConfirm = true },
                onDownload = { backupUploadToConfirm = false },
                onQuit = { showQuitConfirmation = true },
                onFullScreen = { viewModel.setFullScreen(true) },
                onClearRecentHistory = { showClearRecentHistoryConfirmation = true },
            )
        else -> MainTvScreen(
            state = state,
            player = player,
            onStop = stopStreaming,
            onEpg = { showEpgSearch = true },
            onCinema = { showCinema = true; cinemaPlayback = false; returnToEpg = false },
            onSports = { showSports = true; sportsPlayback = false; returnToEpg = false },
            onEpgResults = viewModel::showEpgSearchResults,
            onCountry = viewModel::selectCountry,
            onCategoryFocused = viewModel::focusCategory,
            onCategory = viewModel::selectCategory,
            onChannelFocused = viewModel::focusChannel,
            onChannelPlay = viewModel::play,
            onChannelLongPress = { channel ->
                viewModel.focusChannelImmediately(channel)
                editChannelFavorites = true
                showFavorites = true
            },
            onLive = viewModel::showLive,
            onRecent = viewModel::showRecent,
            onFavorites = {
                editChannelFavorites = false
                showFavorites = true
            },
            onSearch = { showSearch = true },
            onRefresh = viewModel::refresh,
            onProfile = { showProfile = true },
            onUpload = { backupUploadToConfirm = true },
            onDownload = { backupUploadToConfirm = false },
            onQuit = { showQuitConfirmation = true },
            onFullScreen = { viewModel.setFullScreen(true) },
            onNextChannel = viewModel::playNextChannel,
            onPreviousChannel = viewModel::playPreviousChannel,
            onClearRecentHistory = { showClearRecentHistoryConfirmation = true },
            onExitFullScreen = { viewModel.setFullScreen(false) },
        )
    }

    if (showCinema) {
        CinemaScreen(
            visible = !cinemaPlayback,
            onPlay = { channel ->
                viewModel.playEpgChannel(channel)
                cinemaPlayback = true
            },
            onDismiss = { showCinema = false; cinemaPlayback = false },
        )
    }
    BackHandler(enabled = showCinema && cinemaPlayback) {
        viewModel.setFullScreen(false)
        cinemaPlayback = false
    }
    if (showSports) {
        SportsScreen(
            visible = !sportsPlayback,
            onPlay = { channel ->
                viewModel.playEpgChannel(channel)
                sportsPlayback = true
            },
            onDismiss = { showSports = false; sportsPlayback = false },
        )
    }
    BackHandler(enabled = showSports && sportsPlayback) {
        viewModel.setFullScreen(false)
        sportsPlayback = false
    }

    val showStartupProfile = state.profiles.isEmpty() && !startupProfileDismissed
    if (!state.initializing && (showProfile || showStartupProfile)) {
        ProfileDialog(
            profiles = state.profiles,
            activeProfile = state.profile,
            isLoading = state.isLoading,
            onDismiss = {
                showProfile = false
                startupProfileDismissed = true
            },
            onActivate = viewModel::activateProfile,
            onSave = viewModel::saveProfile,
            onDelete = viewModel::deleteProfile,
        )
    }
    if (showFavorites) {
        if (editChannelFavorites) {
            ChannelFavoriteGroupsDialog(
                channel = state.selectedChannel,
                groups = state.favoriteGroups,
                checkedGroupIds = state.favoriteGroupIdsForChannel,
                onToggle = viewModel::toggleFavorite,
                onDismiss = { showFavorites = false },
            )
        } else {
            FavoriteGroupsDialog(
                groups = state.favoriteGroups,
                onBrowse = { group ->
                    viewModel.showFavoriteGroup(group)
                    showFavorites = false
                },
                onCreate = { name -> viewModel.createFavoriteGroup(name) },
                onRename = viewModel::renameFavoriteGroup,
                onDelete = viewModel::deleteFavoriteGroup,
                onDismiss = { showFavorites = false },
            )
        }
    }
    if (showSearch) {
        SearchDialog(
            query = state.searchQuery,
            resultCount = state.visibleChannels.size,
            searchHistory = state.searchHistory,
            onQueryChange = viewModel::search,
            onSubmit = { query ->
                viewModel.submitSearch(query)
                showSearch = false
            },
            onClearHistoryRequest = {
                showSearch = false
                showClearSearchHistoryConfirmation = true
            },
            onClear = {
                viewModel.showLive()
                showSearch = false
            },
            onDismiss = { showSearch = false },
        )
    }
    if (showEpgSearch) {
        EpgSearchScreen(
            onLoadCountries = viewModel::loadEpgCountries,
            onLoadGuideChannels = viewModel::loadEpgGuideChannels,
            onApplyFilters = viewModel::applyEpgFilters,
            listState = epgListState,
            searchState = epgSearchState,
            onSearch = { viewModel.searchEpg(epgQuery) },
            onRefreshSearch = {
                viewModel.searchEpg(epgQuery, refresh = true)
            },
            onExportDatabase = { epgDatabaseUploadToConfirm = true },
            onImportDatabase = { epgDatabaseUploadToConfirm = false },
            onUseSearchChannels = {
                viewModel.showEpgSearchResults()
                showEpgSearch = false
                returnToEpg = true
            },
            onLoadGuide = { channel, refresh ->
                viewModel.loadEpgGuide(channel, refresh)
            },
            onCancelLoading = viewModel::cancelEpgLoading,
            playingChannel = state.playingChannel,
            onPlay = { channel ->
                viewModel.playEpgChannel(channel)
                showEpgSearch = false
                returnToEpg = true
            },
            query = epgQuery,
            onQueryChange = { epgQuery = it },
            onDismiss = { showEpgSearch = false; returnToEpg = false },
        )
    }
    BackHandler(enabled = returnToEpg && !showEpgSearch && !showSearch) {
        showEpgSearch = true
        returnToEpg = false
    }
    if (showClearSearchHistoryConfirmation) {
        ClearSearchHistoryConfirmationDialog(
            searchCount = state.searchHistory.size,
            onConfirm = {
                viewModel.clearSearchHistory()
                showClearSearchHistoryConfirmation = false
            },
            onDismiss = {
                showClearSearchHistoryConfirmation = false
                showSearch = true
            },
        )
    }
    if (showClearRecentHistoryConfirmation) {
        ClearRecentHistoryConfirmationDialog(
            recentCount = state.visibleChannels.size,
            onConfirm = {
                viewModel.clearRecentHistory()
                showClearRecentHistoryConfirmation = false
            },
            onDismiss = { showClearRecentHistoryConfirmation = false },
        )
    }
    if (showQuitConfirmation) {
        QuitConfirmationDialog(
            onConfirm = onQuit,
            onDismiss = { showQuitConfirmation = false },
        )
    }
    backupUploadToConfirm?.let { upload ->
        GitHubBackupConfirmationDialog(
            upload = upload,
            onConfirm = {
                backupUploadToConfirm = null
                if (upload) {
                    viewModel.uploadBackupToGitHub()
                } else {
                    viewModel.downloadBackupFromGitHub()
                }
            },
            onDismiss = { backupUploadToConfirm = null },
        )
    }
    epgDatabaseUploadToConfirm?.let { upload ->
        EpgGitHubConfirmationDialog(
            upload = upload,
            onConfirm = {
                epgDatabaseUploadToConfirm = null
                if (upload) {
                    viewModel.exportEpgDatabaseToGitHub()
                } else {
                    viewModel.importEpgDatabaseFromGitHub()
                }
            },
            onDismiss = { epgDatabaseUploadToConfirm = null },
        )
    }
}

@Composable
private fun KeepScreenAwake(enabled: Boolean) {
    val rootView = LocalView.current
    DisposableEffect(rootView, enabled) {
        if (enabled) {
            val previousValue = rootView.keepScreenOn
            rootView.keepScreenOn = true
            onDispose {
                rootView.keepScreenOn = previousValue
            }
        } else {
            onDispose { }
        }
    }
}

@Composable
internal fun rememberCurrentEpgEpochSeconds(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1_000L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis() / 1_000L
        }
    }
    return now
}

@Composable
private fun MainTvScreen(
    state: MainUiState,
    player: ExoPlayer,
    onStop: () -> Unit,
    onEpg: () -> Unit,
    onCinema: () -> Unit,
    onSports: () -> Unit,
    onEpgResults: () -> Unit,
    onCountry: (String) -> Unit,
    onCategoryFocused: (CategoryItem) -> Unit,
    onCategory: (CategoryItem) -> Unit,
    onChannelFocused: (SavedChannel) -> Unit,
    onChannelPlay: (SavedChannel) -> Unit,
    onChannelLongPress: (SavedChannel) -> Unit,
    onLive: () -> Unit,
    onRecent: () -> Unit,
    onFavorites: () -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onProfile: () -> Unit,
    onUpload: () -> Unit,
    onDownload: () -> Unit,
    onQuit: () -> Unit,
    onFullScreen: () -> Unit,
    onNextChannel: () -> Unit,
    onPreviousChannel: () -> Unit,
    onClearRecentHistory: () -> Unit,
    onExitFullScreen: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val packageManager = LocalContext.current.packageManager
    val isTelevision =
        configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .then(if (state.isFullScreen) Modifier else Modifier.navigationBarsPadding())
            .background(MyIptvPalette.DarkEmerald),
    ) {
        val gridUnit = maxHeight / 9f
        if (!state.isFullScreen) {
            Column(Modifier.fillMaxSize()) {
                AppMenu(
                    state = state,
                    onStop = onStop,
                    onEpg = onEpg,
                    onCinema = onCinema,
                    onSports = onSports,
                    onEpgResults = onEpgResults,
                    onLive = onLive,
                    onRecent = onRecent,
                    onFavorites = onFavorites,
                    onSearch = onSearch,
                    onRefresh = onRefresh,
                    onProfile = onProfile,
                    onUpload = onUpload,
                    onDownload = onDownload,
                    onQuit = onQuit,
                    onFullScreen = onFullScreen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(gridUnit * 1.5f),
                )
                Spacer(Modifier.height(gridUnit * 0.08f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(gridUnit * 7.42f),
                ) {
                    CountryPanel(
                        state = state,
                        onCountry = onCountry,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                    CategoryPanel(
                        state = state,
                        onCategoryFocused = onCategoryFocused,
                        onCategory = onCategory,
                        modifier = Modifier
                            .weight(4f)
                            .fillMaxHeight(),
                    )
                    ChannelPanel(
                        state = state,
                        onFocused = onChannelFocused,
                        onPlay = onChannelPlay,
                        onLongPress = onChannelLongPress,
                        onClearRecentHistory = onClearRecentHistory,
                        modifier = Modifier
                            .weight(5f)
                            .fillMaxHeight(),
                    )
                    Column(
                        modifier = Modifier
                            .weight(6f)
                            .fillMaxHeight(),
                    ) {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(gridUnit * 3.5f),
                        )
                        Spacer(Modifier.height(gridUnit * 0.25f))
                        EpgPanel(
                            channel = state.selectedChannel,
                            programs = state.epg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(gridUnit * 3.5f),
                        )
                    }
                }
            }
        }
        val miniPlayerModifier = Modifier
            .offset(
                x = maxWidth * (10f / 16f),
                y = gridUnit * 1.75f,
            )
            .width(maxWidth * (6f / 16f))
            .height(gridUnit * 3.5f)
        TvPlayer(
            streamUrl = state.streamUrl,
            player = player,
            // Full screen retains the stream's ratio on tablets and Android TV.
            stretchToFill = false,
            modifier = if (state.isFullScreen) {
                Modifier.fillMaxSize()
            } else {
                miniPlayerModifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MyIptvPalette.Emerald, RoundedCornerShape(8.dp))
            },
        )
        if (state.isFullScreen) {
            FullScreenControls(
                state = state,
                onChannelPlay = onChannelPlay,
                onNextChannel = onNextChannel,
                onPreviousChannel = onPreviousChannel,
                onExit = onExitFullScreen,
            )
        } else {
            MiniPlayerPanel(
                state = state,
                onFullScreen = onFullScreen,
                modifier = miniPlayerModifier,
            )
        }
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MyIptvPalette.DarkEmerald.copy(alpha = 0.84f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MyIptvPalette.EmeraldAccent)
            }
        }
        state.message?.let { message ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(18.dp),
                containerColor = MyIptvPalette.CardHover,
                contentColor = MyIptvPalette.White,
            ) {
                Text(message)
            }
        }
    }
}

@Composable
private fun AppMenu(
    state: MainUiState,
    onStop: () -> Unit,
    onEpg: () -> Unit,
    onCinema: () -> Unit,
    onSports: () -> Unit,
    onEpgResults: () -> Unit,
    onLive: () -> Unit,
    onRecent: () -> Unit,
    onFavorites: () -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onProfile: () -> Unit,
    onUpload: () -> Unit,
    onDownload: () -> Unit,
    onQuit: () -> Unit,
    onFullScreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MyIptvPalette.Anthracite,
        border = BorderStroke(1.dp, MyIptvPalette.Border),
    ) {
        BoxWithConstraints {
            val compact = maxWidth < 1_100.dp
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (compact) 8.dp else 18.dp,
                        vertical = if (compact) 6.dp else 12.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (compact) "IPTV" else "MY IPTV",
                        color = MyIptvPalette.EmeraldAccent,
                        style = if (compact) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.headlineMedium
                        },
                        maxLines = 1,
                    )
                    Text(
                        text = when {
                            state.profile == null && compact -> "Sans profil"
                            state.profile == null -> "AUCUN PROFIL · ↓ GitHub ou Profil"
                            compact -> "${state.profile.name} · ${state.visibleChannels.size} chaînes"
                            else -> "${state.profile.name} · LIVE · ${state.visibleChannels.size} chaînes"
                        },
                        color = MyIptvPalette.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                }
                MenuActionButton(
                    label = "Live",
                    icon = Icons.Rounded.LiveTv,
                    showLabel = !compact,
                    active = state.browseMode == BrowseMode.LIVE,
                    onClick = onLive,
                )
                MenuActionButton(
                    label = "Récents",
                    icon = Icons.Rounded.History,
                    showLabel = !compact,
                    active = state.browseMode == BrowseMode.RECENT,
                    onClick = onRecent,
                )
                MenuActionButton(
                    label = "Favoris",
                    icon = Icons.Rounded.Favorite,
                    showLabel = !compact,
                    active = state.browseMode == BrowseMode.FAVORITES,
                    onClick = onFavorites,
                )
                MenuActionButton(
                    label = "Rechercher",
                    icon = Icons.Rounded.Search,
                    showLabel = false,
                    active = state.browseMode == BrowseMode.SEARCH,
                    onClick = onSearch,
                )
                MenuActionButton(
                    label = "EPG",
                    icon = Icons.Rounded.DateRange,
                    showLabel = !compact,
                    onClick = onEpg,
                )
                MenuActionButton(
                    label = "Cinéma",
                    icon = Icons.Rounded.LiveTv,
                    showLabel = true,
                    onClick = onCinema,
                )
                MenuActionButton(
                    label = "Sports",
                    icon = Icons.Rounded.LiveTv,
                    showLabel = true,
                    onClick = onSports,
                )
                if (state.epgSearchResultCount > 0) {
                    MenuActionButton(
                        label = "Résultats EPG",
                        icon = Icons.AutoMirrored.Rounded.FormatListBulleted,
                        showLabel = false,
                        active = state.browseMode == BrowseMode.EPG_SEARCH,
                        onClick = onEpgResults,
                    )
                }
                MenuActionButton(
                    label = "Sync",
                    icon = Icons.Rounded.Refresh,
                    showLabel = false,
                    enabled = state.profile != null && !state.isLoading,
                    onClick = onRefresh,
                )
                MenuActionButton(
                    label = "Profils",
                    icon = Icons.Rounded.Person,
                    showLabel = !compact,
                    onClick = onProfile,
                )
                MenuActionButton(
                    label = "Envoyer vers GitHub",
                    icon = Icons.Rounded.CloudUpload,
                    showLabel = false,
                    enabled = state.profile != null && !state.isLoading,
                    onClick = onUpload,
                )
                MenuActionButton(
                    label = "Télécharger depuis GitHub",
                    icon = Icons.Rounded.CloudDownload,
                    showLabel = false,
                    enabled = !state.isLoading,
                    onClick = onDownload,
                )
                MenuActionButton(
                    label = "Plein écran",
                    icon = Icons.Rounded.Fullscreen,
                    showLabel = false,
                    enabled = state.streamUrl != null,
                    onClick = onFullScreen,
                )
                if (state.streamUrl != null) {
                    StopStreamButton(onClick = onStop)
                }
                MenuActionButton(
                    label = "Quitter",
                    icon = Icons.Rounded.PowerSettingsNew,
                    showLabel = !compact,
                    onClick = onQuit,
                )
            }
        }
    }
}

@Composable
private fun CountryPanel(
    state: MainUiState,
    onCountry: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Panel(modifier) {
        PanelTitle("PAYS", centered = true)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            items(state.countries, key = { it }) { country ->
                TvListItem(
                    selected = state.browseMode == BrowseMode.LIVE && state.selectedCountry == country,
                    onFocused = { onCountry(country) },
                    onClick = { onCountry(country) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 10.dp),
                ) { _, color ->
                    Text(
                        text = if (country == ALL_COUNTRIES_CODE) "** ALL" else country,
                        modifier = Modifier.fillMaxWidth(),
                        color = color,
                        fontSize = LocalTvTextSizes.current.country,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryPanel(
    state: MainUiState,
    onCategoryFocused: (CategoryItem) -> Unit,
    onCategory: (CategoryItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Panel(modifier) {
        PanelTitle(
            when (state.browseMode) {
                BrowseMode.LIVE -> if (state.selectedCountry == ALL_COUNTRIES_CODE) {
                    "CATÉGORIES · ALL"
                } else {
                    "CATÉGORIES · ${state.selectedCountry.orEmpty()}"
                }
                BrowseMode.RECENT -> "RÉCENTS"
                BrowseMode.FAVORITES -> "FAVORIS"
                BrowseMode.SEARCH -> "RECHERCHE · ${state.searchQuery}"
                BrowseMode.EPG_SEARCH -> "RÉSULTATS EPG · ${state.epgSearchQuery}"
            },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(state.categories, key = { it.id }) { category ->
                TvListItem(
                    selected = state.selectedCategoryId == category.id,
                    onFocused = { onCategoryFocused(category) },
                    onClick = { onCategory(category) },
                ) { _, color ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = category.name,
                            modifier = Modifier
                                .weight(1f)
                                .basicMarquee(
                                    animationMode = MarqueeAnimationMode.WhileFocused,
                                    iterations = Int.MAX_VALUE,
                                    repeatDelayMillis = 1_500,
                                    initialDelayMillis = 1_000,
                                ),
                            color = color,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                        )
                        if (category.epgChannelCount > 0) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${category.epgChannelCount} EPG",
                                color = MyIptvPalette.Positive,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelPanel(
    state: MainUiState,
    onFocused: (SavedChannel) -> Unit,
    onPlay: (SavedChannel) -> Unit,
    onLongPress: (SavedChannel) -> Unit,
    onClearRecentHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstRecentFocusRequester = remember { FocusRequester() }
    val firstChannelId = state.visibleChannels.firstOrNull()?.streamId
    val epgNow = rememberCurrentEpgEpochSeconds()
    Panel(modifier) {
        PanelTitle(
            if (state.browseMode == BrowseMode.FAVORITES && state.selectedFavoriteGroup != null) {
                "${state.selectedFavoriteGroup.name} : ${state.visibleChannels.size} favoris"
            } else {
                "CHAÎNES · ${state.visibleChannels.size} · OK long : favoris"
            },
        )
        if (state.visibleChannels.isEmpty()) {
            EmptyPanelText("Aucune chaîne dans cette sélection")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (state.browseMode == BrowseMode.RECENT) {
                    item(key = "@clear_recent_history") {
                        TvListItem(
                            selected = false,
                            onClick = onClearRecentHistory,
                        ) { _, _ ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteSweep,
                                    contentDescription = null,
                                    tint = MyIptvPalette.Negative,
                                )
                                Text(
                                    text = "Effacer tous les récents",
                                    color = MyIptvPalette.Negative,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
                items(state.visibleChannels, key = { it.streamId }) { channel ->
                    val currentProgram = state.currentEpgProgramFor(channel, epgNow)
                    val hasEpg = channel.epgAvailabilityKey() in state.epgAvailableChannels
                    val isFirstRecent =
                        state.browseMode == BrowseMode.RECENT && channel.streamId == firstChannelId
                    if (isFirstRecent) {
                        LaunchedEffect(state.browseMode, firstChannelId) {
                            repeat(3) {
                                withFrameNanos { }
                                val requestSucceeded = runCatching {
                                    firstRecentFocusRequester.requestFocus()
                                }.isSuccess
                                if (requestSucceeded) return@LaunchedEffect
                            }
                        }
                    }
                    TvListItem(
                        selected = state.playingChannel?.streamId == channel.streamId,
                        modifier = if (isFirstRecent) {
                            Modifier.focusRequester(firstRecentFocusRequester)
                        } else {
                            Modifier
                        },
                        onFocused = { onFocused(channel) },
                        onClick = { onPlay(channel) },
                        onLongClick = { onLongPress(channel) },
                    ) { _, color ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            AsyncImage(
                                model = channel.iconUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MyIptvPalette.CardHover)
                                    .padding(3.dp),
                                contentScale = ContentScale.Fit,
                            )
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = channel.name,
                                        modifier = Modifier.weight(1f),
                                        color = color,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (hasEpg) {
                                        Text(
                                            text = "EPG",
                                            modifier = Modifier.padding(start = 8.dp),
                                            color = MyIptvPalette.Positive,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                                currentProgram?.let { program ->
                                    Text(
                                        text = program.title,
                                        modifier = Modifier.padding(top = 2.dp),
                                        color = MyIptvPalette.TextSecondary,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniPlayerPanel(
    state: MainUiState,
    onFullScreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .border(1.dp, MyIptvPalette.Emerald, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp)),
    ) {
        state.playingChannel?.let { channel ->
            Text(
                text = channel.name,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(MyIptvPalette.Anthracite.copy(alpha = 0.88f))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                color = MyIptvPalette.EmeraldLight,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (state.streamUrl != null) {
            MenuActionButton(
                label = "Plein écran",
                icon = Icons.Rounded.Fullscreen,
                showLabel = false,
                onClick = onFullScreen,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun EpgPanel(
    channel: SavedChannel?,
    programs: List<EpgProgram>,
    modifier: Modifier = Modifier,
) {
    var selectedProgram by remember(channel?.streamId) { mutableStateOf<EpgProgram?>(null) }
    var expandedProgram by remember(channel?.streamId) { mutableStateOf<EpgProgram?>(null) }
    Panel(modifier) {
        PanelTitle("EPG · ${channel?.name.orEmpty()}")
        if (programs.isEmpty()) {
            EmptyPanelText("Programme indisponible")
        } else {
            EpgProgramPager(
                pageKey = "${channel?.profileId}:${channel?.streamId}",
                count = programs.size,
                modifier = Modifier.fillMaxSize().padding(8.dp),
            ) { index ->
                val program = programs[index]
                TvListItem(
                    selected = selectedProgram == program,
                    onClick = {
                        selectedProgram = program
                        expandedProgram = program
                    },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(9.dp),
                ) { _, _ ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(program.timeRange, color = MyIptvPalette.EmeraldAccent, fontWeight = FontWeight.Bold)
                        Text(program.title, color = MyIptvPalette.EmeraldAccent, fontWeight = FontWeight.SemiBold)
                        if (program.description.isNotBlank()) {
                            Text(program.description, color = MyIptvPalette.White)
                        }
                    }
                }
            }
        }
    }
    expandedProgram?.let { program ->
        EpgStoryDialog(
            program = program,
            programs = programs,
            channel = channel,
            onDismiss = { expandedProgram = null },
        )
    }
}

@Composable
private fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.padding(horizontal = 2.dp),
        color = MyIptvPalette.Anthracite,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MyIptvPalette.Border),
    ) {
        Column(content = content)
    }
}

@Composable
private fun PanelTitle(text: String, centered: Boolean = false) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .background(MyIptvPalette.Card)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        color = MyIptvPalette.TextPrimary,
        style = MaterialTheme.typography.titleMedium,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun EmptyPanelText(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            color = MyIptvPalette.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FullScreenControls(
    state: MainUiState,
    onChannelPlay: (SavedChannel) -> Unit,
    onNextChannel: () -> Unit,
    onPreviousChannel: () -> Unit,
    onExit: () -> Unit,
) {
    var showChannelList by remember { mutableStateOf(false) }
    var playerHasFocus by remember { mutableStateOf(false) }
    var showTouchControls by remember { mutableStateOf(true) }
    var touchControlInteraction by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val configuration = LocalConfiguration.current
    val packageManager = LocalContext.current.packageManager
    val isTelevision =
        configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    val isTouchLayout = !isTelevision

    BackHandler {
        if (showChannelList) {
            showChannelList = false
        } else {
            onExit()
        }
    }
    LaunchedEffect(
        showChannelList,
        state.playingChannel?.streamId,
        state.streamUrl,
        playerHasFocus,
    ) {
        if (!showChannelList && !playerHasFocus) {
            repeat(4) {
                withFrameNanos { }
                runCatching { focusRequester.requestFocus() }
            }
        }
    }
    LaunchedEffect(
        isTouchLayout,
        showTouchControls,
        showChannelList,
        state.playingChannel?.streamId,
        touchControlInteraction,
    ) {
        if (isTouchLayout && showTouchControls && !showChannelList) {
            delay(3_500)
            showTouchControls = false
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (isTouchLayout) {
                    Modifier.clickable {
                        showTouchControls = true
                        touchControlInteraction++
                    }
                } else {
                    Modifier
                },
            )
            .focusRequester(focusRequester)
            .onFocusChanged { playerHasFocus = it.hasFocus }
            .onPreviewKeyEvent { event ->
                when {
                    !showChannelList &&
                        event.type == KeyEventType.KeyDown &&
                        event.key == Key.DirectionUp -> {
                        onNextChannel()
                        true
                    }
                    !showChannelList &&
                        event.type == KeyEventType.KeyDown &&
                        event.key == Key.DirectionDown -> {
                        onPreviousChannel()
                        true
                    }
                    !showChannelList &&
                        event.type == KeyEventType.KeyUp &&
                        (event.key == Key.DirectionCenter || event.key == Key.Enter) -> {
                        showChannelList = true
                        true
                    }
                    event.key == Key.Back && event.type == KeyEventType.KeyDown -> true
                    event.key == Key.Back && event.type == KeyEventType.KeyUp -> {
                        if (showChannelList) {
                            showChannelList = false
                        } else {
                            onExit()
                        }
                        true
                    }
                    else -> false
                }
            }
            .focusable(),
    ) {
        if (showChannelList) {
            FullScreenChannelList(
                channels = state.visibleChannels,
                playingChannel = state.playingChannel,
                onPlay = { channel ->
                    onChannelPlay(channel)
                    showChannelList = false
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxWidth(0.5f)
                    .fillMaxHeight()
                    .background(MyIptvPalette.SurfaceSecondary.copy(alpha = 0.82f))
                    .padding(horizontal = 24.dp, vertical = 22.dp),
            )
        } else {
            Text(
                text = state.playingChannel?.name.orEmpty(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                color = MyIptvPalette.EmeraldLight,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
        if (isTouchLayout && (showTouchControls || showChannelList)) {
            IconButton(
                onClick = onExit,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MyIptvPalette.Surface.copy(alpha = 0.78f)),
            ) {
                Icon(
                    Icons.Rounded.FullscreenExit,
                    contentDescription = "Quitter le plein écran",
                    tint = MyIptvPalette.TextPrimary,
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MyIptvPalette.Surface.copy(alpha = 0.78f))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    onClick = {
                        showTouchControls = true
                        touchControlInteraction++
                        onPreviousChannel()
                    },
                ) {
                    Icon(
                        Icons.Rounded.SkipPrevious,
                        contentDescription = "Chaîne précédente",
                        tint = MyIptvPalette.TextPrimary,
                    )
                }
                IconButton(
                    onClick = {
                        showTouchControls = true
                        touchControlInteraction++
                        showChannelList = !showChannelList
                    },
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.FormatListBulleted,
                        contentDescription = "Liste des chaînes",
                        tint = MyIptvPalette.Primary,
                    )
                }
                IconButton(
                    onClick = {
                        showTouchControls = true
                        touchControlInteraction++
                        onNextChannel()
                    },
                ) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "Chaîne suivante",
                        tint = MyIptvPalette.TextPrimary,
                    )
                }
            }
            if (!showChannelList) {
                NativeVolumeButton(
                    onInteraction = {
                        showTouchControls = true
                        touchControlInteraction++
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun NativeVolumeButton(
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val audioManager = remember(context) {
        context.getSystemService(AudioManager::class.java)
    }
    IconButton(
        onClick = {
            onInteraction()
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_SAME,
                AudioManager.FLAG_SHOW_UI,
            )
        },
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MyIptvPalette.Surface.copy(alpha = 0.52f)),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
            contentDescription = "Afficher le volume multimédia",
            tint = MyIptvPalette.TextPrimary,
        )
    }
}

@Composable
private fun FullScreenChannelList(
    channels: List<SavedChannel>,
    playingChannel: SavedChannel?,
    onPlay: (SavedChannel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playingChannelId = playingChannel?.streamId
    val selectedIndex = channels.indexOfFirst { it.streamId == playingChannelId }
        .takeIf { it >= 0 }
        ?: 0
    val selectedFocusRequester = remember(playingChannelId, channels) { FocusRequester() }
    val listState = rememberLazyListState()
    val textShadow = Shadow(
        color = Color.Black,
        offset = Offset(2f, 2f),
        blurRadius = 5f,
    )

    LaunchedEffect(playingChannelId, channels) {
        if (channels.isEmpty()) return@LaunchedEffect
        listState.scrollToItem((selectedIndex - 2).coerceAtLeast(0))
        repeat(3) {
            withFrameNanos { }
            if (runCatching { selectedFocusRequester.requestFocus() }.isSuccess) {
                return@LaunchedEffect
            }
        }
    }

    Column(modifier = modifier) {
        Text(
            text = "CHAÎNES · ${channels.size}",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = MyIptvPalette.EmeraldAccent,
            style = MaterialTheme.typography.titleMedium.copy(shadow = textShadow),
            fontWeight = FontWeight.Bold,
        )
        if (channels.isEmpty()) {
            Text(
                text = "Aucune chaîne dans cette sélection",
                modifier = Modifier.padding(12.dp),
                color = MyIptvPalette.White,
                style = MaterialTheme.typography.bodyLarge.copy(shadow = textShadow),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(channels, key = { it.streamId }) { channel ->
                    val isPlaying = channel.streamId == playingChannelId
                    FullScreenChannelItem(
                        channel = channel,
                        isPlaying = isPlaying,
                        onClick = { onPlay(channel) },
                        textShadow = textShadow,
                        modifier = if (channel.streamId == channels[selectedIndex].streamId) {
                            Modifier.focusRequester(selectedFocusRequester)
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FullScreenChannelItem(
    channel: SavedChannel,
    isPlaying: Boolean,
    onClick: () -> Unit,
    textShadow: Shadow,
    modifier: Modifier = Modifier,
) {
    var focused by remember(channel.streamId) { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    val channelColor = if (isPlaying) MyIptvPalette.EmeraldAccent else MyIptvPalette.White

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) MyIptvPalette.CardHover else Color.Transparent)
            .border(
                if (focused) 3.dp else 0.dp,
                if (focused) MyIptvPalette.Negative else Color.Transparent,
                shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = if (isPlaying) "▶" else "",
            modifier = Modifier.width(18.dp),
            color = MyIptvPalette.EmeraldAccent,
            style = MaterialTheme.typography.bodyMedium.copy(shadow = textShadow),
        )
        AsyncImage(
            model = channel.iconUrl,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Fit,
        )
        Text(
            text = channel.name,
            color = channelColor,
            style = MaterialTheme.typography.bodyLarge.copy(shadow = textShadow),
            fontWeight = if (isPlaying || focused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MyIptvPalette.DarkEmerald),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MyIptvPalette.EmeraldAccent)
    }
}

@Composable
private fun BrandedBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MyIptvPalette.DarkEmerald),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "MY IPTV",
            color = MyIptvPalette.EmeraldAccent,
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}
