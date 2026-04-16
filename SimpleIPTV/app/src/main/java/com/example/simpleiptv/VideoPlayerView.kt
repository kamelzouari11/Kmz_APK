package com.example.simpleiptv

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.Text
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
        onProfileSelected: (Int) -> Unit = {}
) {
        var isOverlayVisible by remember { mutableStateOf(false) }
        var showFullOverlay by remember(isLandscape) { mutableStateOf(isLandscape) }
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

        LaunchedEffect(exoPlayer) {
                exoPlayer.addListener(object : androidx.media3.common.Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                                isBuffering = state == androidx.media3.common.Player.STATE_BUFFERING
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
                })
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
                                                        if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                                                                // Quand overlay visible : laisser le focus natif gérer
                                                                if (isOverlayVisible) {
                                                                        return@onPreviewKeyEvent when (event.nativeKeyEvent.keyCode) {
                                                                                android.view.KeyEvent.KEYCODE_BACK -> {
                                                                                        isOverlayVisible = false
                                                                                        true
                                                                                }
                                                                                else -> false
                                                                        }
                                                                }
                                                                // Quand overlay caché
                                                                when (event.nativeKeyEvent.keyCode) {
                                                                        android.view.KeyEvent.KEYCODE_BACK -> {
                                                                                if (!interactive) return@onPreviewKeyEvent false
                                                                                onBack()
                                                                                return@onPreviewKeyEvent true
                                                                        }
                                                                        android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                                                                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                                                                        android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                                                                        android.view.KeyEvent.KEYCODE_ENTER -> {
                                                                                if (!interactive) return@onPreviewKeyEvent false
                                                                                showFullOverlay = isLandscape
                                                                                isOverlayVisible = true
                                                                                return@onPreviewKeyEvent true
                                                                        }
                                                                        android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_CHANNEL_UP -> {
                                                                                if (!interactive) return@onPreviewKeyEvent false
                                                                                if (playingIndex in 0 until currentChannels.size - 1) {
                                                                                        onChannelSelected(currentChannels[playingIndex + 1])
                                                                                }
                                                                                return@onPreviewKeyEvent true
                                                                        }
                                                                        android.view.KeyEvent.KEYCODE_DPAD_DOWN, android.view.KeyEvent.KEYCODE_CHANNEL_DOWN -> {
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
                                        player = exoPlayer
                                        useController = false
                                        setShowNextButton(false)
                                        setShowPreviousButton(false)
                                        keepScreenOn = true
                                        isFocusable = false
                                        isFocusableInTouchMode = false
                                        playerViewRef = this
                                }
                        },
                        update = { view ->
                                view.useController = false
                                playerViewRef = view
                        },
                        modifier = Modifier.fillMaxSize()
                )

                // Indicateur de buffering (disabled for faster perceived zapping)
                if (false && isBuffering) {
                        Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                        ) {
                                Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                ) {
                                        CircularProgressIndicator(
                                                color = Color.White,
                                                modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                                text = "Chargement...",
                                                color = Color.White,
                                                style = MaterialTheme.typography.bodyMedium
                                        )
                                }
                        }
                }

                // Indicateur d'erreur
                if (playbackError != null && !isBuffering) {
                        Box(
                                modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.7f)),
                                contentAlignment = Alignment.Center
                        ) {
                                Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                ) {
                                        Text(
                                                text = "Erreur de lecture",
                                                color = Color.Red,
                                                style = MaterialTheme.typography.titleMedium
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                                text = playbackError ?: "",
                                                color = Color.White.copy(alpha = 0.7f),
                                                style = MaterialTheme.typography.bodySmall
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                                text = "Appuyez sur BACK pour revenir",
                                                color = Color.White.copy(alpha = 0.5f),
                                                style = MaterialTheme.typography.bodySmall
                                        )
                                }
                        }
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
                        Column(
                                modifier = Modifier.fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.5f))
                                        .padding(24.dp)
                        ) {
                                if (profiles.isNotEmpty()) {
                                        androidx.compose.foundation.lazy.LazyRow(
                                                modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(bottom = 16.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                                items(profiles, key = { it.id }) { profile ->
                                                        val isSelected = profile.id == activeProfileId
                                                        var isFocused by remember { mutableStateOf(false) }
                                                        Box(
                                                                modifier = Modifier
                                                                        .onFocusChanged { isFocused = it.isFocused }
                                                                        .clickable { onProfileSelected(profile.id) }
                                                                        .focusable()
                                                                        .background(
                                                                                color = when {
                                                                                        isFocused -> Color.White.copy(alpha = 0.9f)
                                                                                        isSelected -> Color.Green.copy(alpha = 0.2f)
                                                                                        else -> Color.Transparent
                                                                                },
                                                                                shape = MaterialTheme.shapes.small
                                                                        )
                                                                        .border(
                                                                                width = 1.dp,
                                                                                color = when {
                                                                                        isFocused -> Color.White
                                                                                        isSelected -> Color.Green
                                                                                        else -> Color.White.copy(alpha = 0.3f)
                                                                                },
                                                                                shape = MaterialTheme.shapes.small
                                                                        )
                                                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                                                contentAlignment = Alignment.Center
                                                        ) {
                                                                Text(
                                                                        text = profile.profileName.ifEmpty { "Profil ${profile.id}" },
                                                                        color = when {
                                                                                isFocused -> Color.Black
                                                                                isSelected -> Color.Green
                                                                                else -> Color.White
                                                                        },
                                                                        style = MaterialTheme.typography.bodyMedium
                                                                )
                                                        }
                                                }
                                        }
                                }
                                Row(
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
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
                                                        val isFav = remember(channel.profileId, channel.stream_id) {
                                                                allFavoriteIds.contains("${channel.profileId}_${channel.stream_id}")
                                                        }
                                                        var isRowFocused by remember { mutableStateOf(false) }
                                                        Row(
                                                                modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .padding(vertical = 2.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                                // Channel row (clickable + focusable)
                                                                Row(
                                                                        modifier = Modifier
                                                                                .weight(1f)
                                                                                .onFocusChanged { isRowFocused = it.isFocused }
                                                                                .clickable { 
                                                                                        if (isPlaying) {
                                                                                                isOverlayVisible = false
                                                                                        } else {
                                                                                                isOverlayVisible = false
                                                                                                onChannelSelected(channel)
                                                                                        }
                                                                                }
                                                                                .focusable()
                                                                                .background(
                                                                                        color = when {
                                                                                                isRowFocused -> Color.White
                                                                                                isPlaying -> Color.Green.copy(alpha = 0.2f)
                                                                                                else -> Color.Transparent
                                                                                        },
                                                                                        shape = MaterialTheme.shapes.small
                                                                                )
                                                                                .padding(8.dp),
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                ) {
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
                                                                                                isRowFocused -> Color.Black
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
                                                                                                color = if (isRowFocused) Color.DarkGray else Color.White.copy(alpha = 0.4f),
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
                                                                                        tint = if (isRowFocused) Color.Black else Color.Green,
                                                                                        modifier = Modifier.size(16.dp)
                                                                                )
                                                                        }
                                                                }
                                                                Spacer(Modifier.width(4.dp))
                                                                // Star button (separate focusable)
                                                                FavoriteStarButton(
                                                                        isFavorite = isFav,
                                                                        onClick = { onFavoriteClick(channel) }
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

/**
 * Bouton étoile pour ajouter/retirer des favoris dans l'overlay du player.
 */
@Composable
private fun FavoriteStarButton(
        isFavorite: Boolean,
        onClick: () -> Unit
) {
        var isStarFocused by remember { mutableStateOf(false) }

        Surface(
                modifier = Modifier
                        .size(40.dp)
                        .onFocusChanged { isStarFocused = it.isFocused }
                        .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null,
                                onClick = onClick
                        )
                        .focusable(),
                shape = MaterialTheme.shapes.small,
                color = when {
                        isStarFocused -> Color.White
                        else -> Color.Transparent
                },
                border = when {
                        isStarFocused -> BorderStroke(2.dp, Color.White)
                        else -> BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f))
                }
        ) {
                Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = if (isFavorite) "Retirer des favoris" else "Ajouter aux favoris",
                        tint = if (isStarFocused) Color.Black else if (isFavorite) Color.Green else Color.Gray,
                        modifier = Modifier.size(24.dp)
                )
        }
}
