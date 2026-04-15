package com.example.simpleiptv

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.simpleiptv.data.local.entities.CategoryEntity
import com.example.simpleiptv.data.local.entities.ChannelEntity
import com.example.simpleiptv.data.local.entities.ProfileEntity
import com.example.simpleiptv.ui.components.OverlayListItem

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
        interactive: Boolean = true,
        isLandscape: Boolean = true,
        playingChannel: ChannelEntity? = null,
        countriesScrollState: LazyListState? = null,
        categoriesScrollState: LazyListState? = null,
        channelsScrollState: LazyListState? = null,
        listLabel: String = "",
        profiles: List<ProfileEntity> = emptyList()
) {
        var isOverlayVisible by remember { mutableStateOf(false) }
        var showFullOverlay by remember(isLandscape) { mutableStateOf(isLandscape) }
        val boxFocusRequester = remember { FocusRequester() }
        val categoryFocusRequester = remember { FocusRequester() }
        val channelFocusRequester = remember { FocusRequester() }
        val vodFocusRequester = remember { FocusRequester() }
        val isVod = playingChannel?.type == "VOD"

        var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

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
                        .then(
                                if (!isVod) {
                                        Modifier.onPreviewKeyEvent { event: androidx.compose.ui.input.key.KeyEvent ->
                                                        if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                                                                when (event.nativeKeyEvent.keyCode) {
                                                                        android.view.KeyEvent.KEYCODE_BACK -> {
                                                                                if (!interactive) return@onPreviewKeyEvent false
                                                                                if (isOverlayVisible) {
                                                                                        isOverlayVisible = false
                                                                                        return@onPreviewKeyEvent true
                                                                                }
                                                                                onBack()
                                                                                return@onPreviewKeyEvent true
                                                                        }
                                                                        android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_CHANNEL_UP -> {
                                                                                if (!interactive || isOverlayVisible) return@onPreviewKeyEvent false
                                                                                if (playingIndex in 0 until currentChannels.size - 1) {
                                                                                        onChannelSelected(currentChannels[playingIndex + 1])
                                                                                }
                                                                                return@onPreviewKeyEvent true
                                                                        }
                                                                        android.view.KeyEvent.KEYCODE_DPAD_DOWN, android.view.KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                                                                                if (!interactive || isOverlayVisible) return@onPreviewKeyEvent false
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
                                                .clickable(enabled = interactive) {
                                                        if (!isOverlayVisible) {
                                                                showFullOverlay = isLandscape
                                                                isOverlayVisible = true
                                                        } else {
                                                                isOverlayVisible = false
                                                        }
                                                }
                                } else Modifier
                        )
        ) {
                // 1. Video Surface
                var isPlayerFocused by remember { mutableStateOf(false) }

                AndroidView(
                        factory = { context ->
                                PlayerView(context).apply {
                                        player = exoPlayer
                                        useController = isVod
                                        keepScreenOn = true
                                        isFocusable = isVod
                                        isFocusableInTouchMode = isVod
                                        playerViewRef = this
                                }
                        },
                        update = { view ->
                                view.useController = isVod
                                playerViewRef = view
                        },
                        modifier = Modifier.fillMaxSize()
                                .onFocusChanged { isPlayerFocused = it.isFocused }
                                .then(
                                        if (isVod) {
                                                Modifier.focusRequester(vodFocusRequester)
                                                        .focusable()
                                                        .clickable { playerViewRef?.showController() }
                                                        .onKeyEvent { event ->
                                                                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                                                        when (event.nativeKeyEvent.keyCode) {
                                                                                KeyEvent.KEYCODE_BACK -> {
                                                                                        if (playerViewRef?.isControllerFullyVisible == true) {
                                                                                                playerViewRef?.hideController()
                                                                                                true
                                                                                        } else { onBack(); true }
                                                                                }
                                                                                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                                                                                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                                                                                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                                                                        playerViewRef?.showController()
                                                                                        false
                                                                                }
                                                                                else -> false
                                                                        }
                                                                } else false
                                                        }
                                        } else Modifier
                                )
                )

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
                        Row(
                                modifier = Modifier.fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .padding(24.dp),
                                horizontalArrangement = if (showFullOverlay) Arrangement.Start else Arrangement.End
                        ) {
                                if (showFullOverlay) {
                                        // Country List
                                        if (countries.size > 1) {
                                                OverlayColumn(title = "Pays", width = 150.dp) {
                                                        LazyColumn(state = countryState, modifier = Modifier.weight(1f)) {
                                                                items(countries, key = { it }) { country ->
                                                                        OverlayListItem(
                                                                                text = country,
                                                                                isSelected = country == selectedCountry,
                                                                                onClick = { onCountrySelected(country) }
                                                                        )
                                                                }
                                                        }
                                                }
                                                Spacer(Modifier.width(16.dp))
                                        }

                                        // Category List
                                        OverlayColumn(title = "Catégories", width = 280.dp) {
                                                LazyColumn(state = categoryState, modifier = Modifier.weight(1f)) {
                                                        itemsIndexed(categories, key = { _, cat -> cat.category_id }) { index, category ->
                                                                val isSelected = category.category_id == selectedCategoryId
                                                                val isInitialFocus = if (selectedCategoryId != null) isSelected else index == 0
                                                                OverlayListItem(
                                                                        text = category.category_name,
                                                                        isSelected = isSelected,
                                                                        onClick = { onCategorySelected(category) },
                                                                        focusRequester = if (isInitialFocus) categoryFocusRequester else null
                                                                )
                                                        }
                                                }
                                        }

                                        Spacer(Modifier.width(16.dp))
                                }

                                // Channel List
                                OverlayColumn(
                                        title = "Chaînes",
                                        width = if (showFullOverlay) 320.dp else 400.dp
                                ) {
                                        LazyColumn(state = channelState, modifier = Modifier.weight(1f)) {
                                                items(currentChannels, key = { it.stream_id }) { channel ->
                                                        val isPlaying = channel.stream_id == playingChannel?.stream_id
                                                        val profile = remember(channel.profileId, profiles) {
                                                                profiles.find { it.id == channel.profileId }
                                                        }
                                                        OverlayListItem(
                                                                text = channel.name,
                                                                isSelected = isPlaying,
                                                                onClick = { isOverlayVisible = false; onChannelSelected(channel) },
                                                                focusRequester = if (isPlaying) channelFocusRequester else null,
                                                                modifier = Modifier.padding(horizontal = 0.dp)
                                                        ) { isFocused ->
                                                                AsyncImage(
                                                                        model = channel.stream_icon,
                                                                        contentDescription = null,
                                                                        modifier = Modifier.size(24.dp),
                                                                        contentScale = ContentScale.Fit
                                                                )
                                                                Spacer(Modifier.width(10.dp))
                                                                Column(modifier = Modifier.weight(1f)) {
                                                                        Text(
                                                                                text = channel.name,
                                                                                color = when {
                                                                                        isFocused -> Color.Black
                                                                                        isPlaying -> Color.Green
                                                                                        else -> Color.White
                                                                                },
                                                                                maxLines = 1,
                                                                                style = MaterialTheme.typography.bodyMedium,
                                                                                overflow = TextOverflow.Ellipsis
                                                                        )
                                                                        if (profile != null) {
                                                                                Text(
                                                                                        text = "${profile.profileName}  •  ${profile.url}",
                                                                                        color = if (isFocused) Color.DarkGray else Color.White.copy(alpha = 0.4f),
                                                                                        maxLines = 1,
                                                                                        overflow = TextOverflow.Ellipsis,
                                                                                        style = MaterialTheme.typography.labelSmall,
                                                                                        fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp)
                                                                                )
                                                                        }
                                                                }
                                                                if (isPlaying) {
                                                                        Icon(
                                                                                imageVector = Icons.Default.PlayArrow,
                                                                                contentDescription = null,
                                                                                tint = if (isFocused) Color.Black else Color.Green,
                                                                                modifier = Modifier.size(16.dp)
                                                                        )
                                                                }
                                                        }
                                                }
                                        }
                                }
                        }
                }

                // 3. Info bar when overlay hidden
                if (!isOverlayVisible && interactive) {
                        val activeProfile = remember(playingChannel, profiles) {
                                profiles.find { it.id == playingChannel?.profileId }
                        }
                        Box(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                contentAlignment = Alignment.BottomStart
                        ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                        Text(
                                                text = channelName,
                                                color = Color.White.copy(alpha = 0.85f),
                                                style = MaterialTheme.typography.titleMedium
                                        )
                                        if (activeProfile != null) {
                                                Text(
                                                        text = "${activeProfile.profileName}  •  ${activeProfile.url}",
                                                        color = Color.Green.copy(alpha = 0.85f),
                                                        style = MaterialTheme.typography.labelMedium
                                                )
                                        }
                                        if (listLabel.isNotEmpty()) {
                                                Text(
                                                        text = listLabel,
                                                        color = Color.White.copy(alpha = 0.45f),
                                                        style = MaterialTheme.typography.bodySmall
                                                )
                                        }
                                }
                        }
                }
        }
}

/**
 * Helper composable for overlay columns (title + content).
 */
@Composable
private fun OverlayColumn(
        title: String,
        width: androidx.compose.ui.unit.Dp,
        content: @Composable ColumnScope.() -> Unit
) {
        Column(modifier = Modifier.fillMaxHeight().width(width)) {
                Text(
                        text = title,
                        color = Color.White,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleSmall
                )
                content()
        }
}
