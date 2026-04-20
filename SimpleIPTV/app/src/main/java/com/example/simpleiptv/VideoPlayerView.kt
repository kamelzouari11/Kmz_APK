package com.example.simpleiptv

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.example.simpleiptv.data.local.entities.CategoryEntity
import com.example.simpleiptv.data.local.entities.ChannelEntity
import com.example.simpleiptv.data.local.entities.ProfileEntity
import com.example.simpleiptv.ui.player.BufferingOverlay
import com.example.simpleiptv.ui.player.PlaybackErrorOverlay
import com.example.simpleiptv.ui.player.PlayerInfoBar
import com.example.simpleiptv.ui.player.PlayerOverlay
import kotlinx.coroutines.launch


@Composable
fun VideoPlayerView(
        exoPlayer: Player,
        channelName: String,
        currentChannels: List<ChannelEntity>,
        categories: List<CategoryEntity>,
        selectedCategoryId: String?,
        countries: List<String> = emptyList(),
        selectedCountry: String = "ALL",
        onCountrySelected: (String) -> Unit = {},
        onChannelSelected: (ChannelEntity) -> Unit,
        onCategorySelected: (CategoryEntity) -> Unit,
        onBack: () -> Unit,
        onFavoriteClick: (ChannelEntity) -> Unit = {},
        interactive: Boolean = true,
        isLandscape: Boolean = true,
        playingChannel: ChannelEntity? = null,
        allFavoriteIds: Set<String> = emptySet(),
        countriesScrollState: LazyListState? = null,
        categoriesScrollState: LazyListState? = null,
        channelsScrollState: LazyListState? = null,
        listLabel: String = "",
        profiles: List<ProfileEntity> = emptyList(),
        activeProfileId: Int = -1,
        onProfileSelected: (Int) -> Unit = {},
        viewModel: com.example.simpleiptv.ui.viewmodel.MainViewModel
) {
        var isOverlayVisible by remember { mutableStateOf(false) }
        var showFullOverlay by remember(isLandscape) { mutableStateOf(isLandscape) }
        val scope = rememberCoroutineScope()
        val boxFocusRequester = remember { FocusRequester() }
        val categoryFocusRequester = remember { FocusRequester() }
        val channelFocusRequester = remember { FocusRequester() }
        val vodFocusRequester = remember { FocusRequester() }
        val isVod = playingChannel?.type == "VOD"

        var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

        // Demander le focus sur le player
        LaunchedEffect(Unit) {
                try { boxFocusRequester.requestFocus() } catch (e: Exception) {}
        }

        // Observation de l'état du player pour le buffering
        var isBuffering by remember { mutableStateOf(false) }
        var playbackError by remember { mutableStateOf<String?>(null) }

        DisposableEffect(exoPlayer) {
                val listener = object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                                isBuffering = state == Player.STATE_BUFFERING
                        }
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                playbackError = error.message ?: "Erreur de lecture"
                        }
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                                if (isPlaying) {
                                        isBuffering = false
                                        playbackError = null
                                }
                        }
                }
                exoPlayer.addListener(listener)
                onDispose {
                        exoPlayer.removeListener(listener)
                }
        }

        val countryState = countriesScrollState ?: rememberLazyListState()
        val categoryState = categoriesScrollState ?: rememberLazyListState()
        val channelState = channelsScrollState ?: rememberLazyListState()

        // Focus management
        LaunchedEffect(isVod) {
                if (!isVod && interactive) {
                        try { boxFocusRequester.requestFocus() } catch (e: Exception) {}
                }
        }

        val playingIndex = remember(currentChannels, playingChannel) {
                currentChannels.indexOfFirst { it.stream_id == playingChannel?.stream_id }
        }

        LaunchedEffect(isOverlayVisible, isVod) {
                if (isVod) {
                        try { vodFocusRequester.requestFocus() } catch (e: Exception) {}
                } else if (isOverlayVisible && interactive) {
                        if (playingIndex >= 0) {
                                try {
                                        channelState.scrollToItem(playingIndex)
                                        channelFocusRequester.requestFocus()
                                } catch (e: Exception) {
                                        try { categoryFocusRequester.requestFocus() } catch (e2: Exception) {}
                                }
                        } else {
                                try { categoryFocusRequester.requestFocus() } catch (e: Exception) {}
                        }
                } else if (!isOverlayVisible && interactive) {
                        try { boxFocusRequester.requestFocus() } catch (e: Exception) {}
                }
        }

        if (isOverlayVisible && !isVod) {
                BackHandler { isOverlayVisible = false }
        }

        Box(
                modifier = Modifier.fillMaxSize()
                        .background(Color.Black)
                        .focusRequester(boxFocusRequester)
                        .focusable()
                        .then(
                                if (!isVod) {
                                        Modifier.onPreviewKeyEvent { event: androidx.compose.ui.input.key.KeyEvent ->
                                                        if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                                                // Quand overlay visible : laisser le focus natif gérer
                                                                if (isOverlayVisible) {
                                                                        return@onPreviewKeyEvent when (event.nativeKeyEvent.keyCode) {
                                                                                KeyEvent.KEYCODE_BACK -> {
                                                                                        isOverlayVisible = false
                                                                                        true
                                                                                }
                                                                                else -> false
                                                                        }
                                                                }
                                                                // Quand overlay caché
                                                                when (event.nativeKeyEvent.keyCode) {
                                                                        KeyEvent.KEYCODE_BACK -> {
                                                                                if (!interactive) return@onPreviewKeyEvent false
                                                                                onBack()
                                                                                return@onPreviewKeyEvent true
                                                                        }
                                                                         KeyEvent.KEYCODE_DPAD_LEFT,
                                                                         KeyEvent.KEYCODE_DPAD_RIGHT,
                                                                         KeyEvent.KEYCODE_DPAD_CENTER,
                                                                         KeyEvent.KEYCODE_ENTER -> {
                                                                                 if (!interactive) return@onPreviewKeyEvent false
                                                                                 showFullOverlay = isLandscape
                                                                                 isOverlayVisible = true
                                                                                 // Force le focus sur la chaîne en cours de lecture pour faciliter le zapping
                                                                                 if (playingIndex >= 0) {
                                                                                     scope.launch {
                                                                                         try {
                                                                                             channelState.scrollToItem(playingIndex)
                                                                                             channelFocusRequester.requestFocus()
                                                                                         } catch (e: Exception) {
                                                                                             try { categoryFocusRequester.requestFocus() } catch (e2: Exception) {}
                                                                                         }
                                                                                     }
                                                                                 } else {
                                                                                     try { categoryFocusRequester.requestFocus() } catch (e: Exception) {}
                                                                                 }
                                                                                 return@onPreviewKeyEvent true
                                                                         }
                                                                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                                                                                if (!interactive) return@onPreviewKeyEvent false
                                                                                if (playingIndex in 0 until currentChannels.size - 1) {
                                                                                        onChannelSelected(currentChannels[playingIndex + 1])
                                                                                }
                                                                                return@onPreviewKeyEvent true
                                                                        }
                                                                        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                                                                                if (!interactive) return@onPreviewKeyEvent false
                                                                                if (playingIndex > 0) {
                                                                                        onChannelSelected(currentChannels[playingIndex - 1])
                                                                                }
                                                                                return@onPreviewKeyEvent true
                                                                        }
                                                                }
                                                        }
                                                        false
                                                }
                                                .focusRequester(boxFocusRequester)
                                                .focusable()
                                } else Modifier
                        )
        ) {
                // 1. Video Surface
                var isPlayerFocused by remember { mutableStateOf(false) }

                AndroidView(
                        factory = { context ->
                                PlayerView(context).apply {
                                        useController = false
                                        setShowNextButton(false)
                                        setShowPreviousButton(false)
                                        keepScreenOn = true
                                        isFocusable = false
                                        isFocusableInTouchMode = false
                                }
                        },
                        update = { view ->
                                view.player = exoPlayer
                                view.useController = false
                                playerViewRef = view
                        },
                        modifier = Modifier.fillMaxSize()
                )

                // Indicateur de buffering (disabled for faster perceived zapping)
                if (false && isBuffering) {
                        BufferingOverlay()
                }

                // Indicateur d'erreur
                if (playbackError != null && !isBuffering) {
                        PlaybackErrorOverlay(errorMessage = playbackError)
                }

                // VOD focus indicator
                if (isVod && isPlayerFocused) {
                        Box(
                                modifier = Modifier.fillMaxSize().padding(2.dp)
                                        .background(Color.Cyan.copy(alpha = 0.05f))
                                        .border(2.dp, Color.Cyan.copy(alpha = 0.3f), MaterialTheme.shapes.small)
                        )
                }

                // 2. Overlay (LIVE only)
                if (isOverlayVisible && !isVod) {
                        PlayerOverlay(
                                profiles = profiles,
                                activeProfileId = activeProfileId,
                                onProfileSelected = onProfileSelected,
                                countries = countries,
                                selectedCountry = selectedCountry,
                                onCountrySelected = onCountrySelected,
                                categories = categories,
                                selectedCategoryId = selectedCategoryId,
                                onCategorySelected = onCategorySelected,
                                currentChannels = currentChannels,
                                playingChannel = playingChannel,
                                allFavoriteIds = allFavoriteIds,
                                onChannelSelected = onChannelSelected,
                                onFavoriteClick = onFavoriteClick,
                                         onOverlayDismiss = { isOverlayVisible = false },
                                         showFullOverlay = showFullOverlay,
                                         countryState = countryState,
                                         categoryState = categoryState,
                                         channelState = channelState,
                                         categoryFocusRequester = categoryFocusRequester,
                                         channelFocusRequester = channelFocusRequester,
                                         viewModel = viewModel
                                     )
                }

                // 3. Info bar when overlay hidden
                if (!isOverlayVisible && interactive) {
                        PlayerInfoBar(
                                channelName = channelName,
                                playingChannel = playingChannel,
                                profiles = profiles,
                                listLabel = listLabel
                        )
                }
        }
}
