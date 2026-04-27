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
import androidx.compose.ui.Alignment
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
import com.example.simpleiptv.ui.player.PlaybackErrorOverlay
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
        val isVod = playingChannel?.type == "VOD"
        var isOverlayVisible by remember { mutableStateOf(false) }
        var showExoController by remember(isVod) { mutableStateOf<Boolean>(isVod) }
        var showFullOverlay by remember(isLandscape) { mutableStateOf(isLandscape) }
        val scope = rememberCoroutineScope()
        val boxFocusRequester = remember { FocusRequester() }
        val categoryFocusRequester = remember { FocusRequester() }
        val channelFocusRequester = remember { FocusRequester() }
        val vodFocusRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
                try { boxFocusRequester.requestFocus() } catch (e: Exception) {}
        }

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

        LaunchedEffect(isVod, interactive) {
                if (!isVod && interactive) {
                        try { boxFocusRequester.requestFocus() } catch (e: Exception) {}
                }
        }

        val playingIndex = remember(currentChannels, playingChannel) {
                currentChannels.indexOfFirst { it.stream_id == playingChannel?.stream_id }
        }

        LaunchedEffect(isOverlayVisible, isVod, interactive) {
                if (isVod && interactive) {
                        try { vodFocusRequester.requestFocus() } catch (e: Exception) {}
                } else if (isOverlayVisible && !isVod && interactive) {
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
                } else if (!isOverlayVisible && !isVod && interactive) {
                        try { boxFocusRequester.requestFocus() } catch (e: Exception) {}
                }
        }

        if (isOverlayVisible && !isVod) {
                BackHandler { isOverlayVisible = false }
        }

        if (isVod && interactive) {
                BackHandler { onBack() }
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
                                                        if (isOverlayVisible) {
                                                                return@onPreviewKeyEvent when (event.nativeKeyEvent.keyCode) {
                                                                        KeyEvent.KEYCODE_BACK -> {
                                                                                isOverlayVisible = false
                                                                                true
                                                                        }
                                                                        else -> false
                                                                }
                                                        }
                                                        when (event.nativeKeyEvent.keyCode) {
                                                                KeyEvent.KEYCODE_BACK -> {
                                                                        if (!interactive) return@onPreviewKeyEvent false
                                                                        onBack()
                                                                        return@onPreviewKeyEvent true
                                                                }
                                                                  KeyEvent.KEYCODE_DPAD_LEFT,
                                                                  KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                                                        if (!interactive) return@onPreviewKeyEvent false
                                                                        showFullOverlay = isLandscape
                                                                        isOverlayVisible = true
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
                                                                   KeyEvent.KEYCODE_DPAD_CENTER,
                                                                   KeyEvent.KEYCODE_ENTER -> {
                                                                        if (!interactive) return@onPreviewKeyEvent false
                                                                        showFullOverlay = isLandscape
                                                                        isOverlayVisible = true
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
                var isPlayerFocused by remember { mutableStateOf(false) }

                AndroidView(
                        factory = { context ->
                                PlayerView(context).apply {
                                        useController = showExoController
                                        setShowNextButton(false)
                                        setShowPreviousButton(false)
                                        keepScreenOn = true
                                        isFocusable = isVod || showExoController
                                        isFocusableInTouchMode = isVod || showExoController
                                        }
                        },
                        update = { view ->
                                view.player = exoPlayer
                                view.useController = showExoController
                        },
                        modifier = Modifier.fillMaxSize()
                                .focusRequester(vodFocusRequester)
                                .onFocusChanged { isPlayerFocused = it.isFocused }
                                .focusable()
                )


                if (playbackError != null && !isBuffering) {
                        PlaybackErrorOverlay(errorMessage = playbackError)
                }

                if (isVod && isPlayerFocused) {
                        Box(
                                modifier = Modifier.fillMaxSize().padding(2.dp)
                                        .background(Color.Cyan.copy(alpha = 0.05f))
                                        .border(2.dp, Color.Cyan.copy(alpha = 0.3f), MaterialTheme.shapes.small)
                        )
                }

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
        }
}
