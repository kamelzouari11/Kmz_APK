package com.example.simpleiptv.ui.player

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.simpleiptv.data.local.entities.CategoryEntity
import com.example.simpleiptv.data.local.entities.ChannelEntity
import com.example.simpleiptv.data.local.entities.ProfileEntity
import com.example.simpleiptv.ui.components.OverlayListItem
import com.example.simpleiptv.ui.viewmodel.MainViewModel

/**
 * Full overlay displayed on top of the video player in LIVE mode.
 * Shows profile selector, category/channel columns.
 */
@Composable
fun PlayerOverlay(
        profiles: List<ProfileEntity>,
        activeProfileId: Int,
        onProfileSelected: (Int) -> Unit,
        onProfileToggle: (Int) -> Unit,
        categories: List<CategoryEntity>,
        selectedCategoryId: String?,
        onCategorySelected: (CategoryEntity) -> Unit,
        currentChannels: List<ChannelEntity>,
        playingChannel: ChannelEntity?,
        allFavoriteIds: Set<String>,
        onChannelSelected: (ChannelEntity) -> Unit,
        onFavoriteClick: (ChannelEntity) -> Unit,
        onOverlayDismiss: () -> Unit,
        showFullOverlay: Boolean,
        categoryState: LazyListState,
        channelState: LazyListState,
        channelFocusRequester: FocusRequester,
        viewModel: MainViewModel
) {
    val recentsFocusRequester = remember { FocusRequester() }

    // Focus initial : chaîne en lecture (si présente) sinon Récents.
    // Évite que le système donne le focus au premier profil par défaut.
    LaunchedEffect(Unit) {
        val playingIndex = currentChannels.indexOfFirst { it.stream_id == playingChannel?.stream_id }
        try {
            if (playingIndex >= 0 && playingChannel != null) {
                channelState.scrollToItem(playingIndex)
                kotlinx.coroutines.delay(50) // laisse le LazyColumn poser l'item
                try { channelFocusRequester.requestFocus() }
                catch (_: Exception) { recentsFocusRequester.requestFocus() }
            } else {
                recentsFocusRequester.requestFocus()
            }
        } catch (_: Exception) {
            try { recentsFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    Column(
            modifier = Modifier.fillMaxSize()
                    .background(Color(0xFF121212).copy(alpha = 0.7f))
                    .padding(32.dp)
    ) {
                // Profile selector row
                if (profiles.isNotEmpty()) {
                    ProfileSelectorRow(
                        profiles = profiles,
                        activeProfileId = activeProfileId,
                        loadedProfileIds = viewModel.uiState.loadedProfileIds,
                        loadingProfileId = viewModel.uiState.loadingProfileId,
                        onProfileSelected = onProfileSelected,
                        onProfileToggle = onProfileToggle
                    )
                }

                Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = if (showFullOverlay) Arrangement.Start else Arrangement.End
                ) {
                        if (showFullOverlay) {
                                OverlayColumn(title = "Catégories", width = 300.dp) {
                                         LazyColumn(state = categoryState, modifier = Modifier.weight(1f)) {
                                                 item {
                                                     OverlayListItem(
                                                         text = "Récents",
                                                         isSelected = viewModel.uiState.lastGeneratorType == com.example.simpleiptv.ui.viewmodel.GeneratorType.RECENTS,
                                                         onClick = { viewModel.showRecents() },
                                                         focusRequester = recentsFocusRequester,
                                                         content = { focused ->
                                                              Row(verticalAlignment = Alignment.CenterVertically) {
                                                                  Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp), tint = if (focused) Color.Black else Color.White)
                                                                  Spacer(Modifier.width(8.dp))
                                                                  Text("Récents", color = if (focused) Color.Black else Color.White)
                                                              }
                                                         }
                                                     )
                                                 }
                                                 itemsIndexed(categories, key = { _, cat -> cat.category_id }) { _, category ->
                                                     val isSelected = category.category_id == selectedCategoryId
                                                     OverlayListItem(
                                                         text = category.category_name,
                                                         isSelected = isSelected,
                                                         onClick = { onCategorySelected(category) }
                                                     )
                                                 }
                                         }
                                }
                                Spacer(Modifier.width(20.dp))
                        }

                        // Colonne chaînes (+50%)
                        OverlayColumn(
                            title = "Chaînes",
                            width = if (showFullOverlay) 480.dp else 600.dp
                        ) {
                                OverlayChannelList(
                                        channels = currentChannels,
                                        playingChannel = playingChannel,
                                        profiles = profiles,
                                        allFavoriteIds = allFavoriteIds,
                                        onChannelSelected = onChannelSelected,
                                        onFavoriteClick = onFavoriteClick,
                                        onOverlayDismiss = onOverlayDismiss,
                                        channelState = channelState,
                                        channelFocusRequester = channelFocusRequester
                                )
                        }
                }
        }
}

/**
 * Profile selector row in the overlay.
 *
 * States per profile (priority order):
 *   focused  → white bg, black text
 *   loading  → amber pulsing border + spinner
 *   active   → purple bg, white bold text              (1 max)
 *   enabled + loaded   → green border, green text
 *   enabled + unloaded → dim border, gray text
 *   disabled           → red-dim border, strikethrough text
 *
 * Short press / OK  → toggleProfileEnabled (ON/OFF, inclusive)
 * Long press / hold → selectProfile (active, exclusive)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfileSelectorRow(
    profiles: List<ProfileEntity>,
    activeProfileId: Int,
    loadedProfileIds: Set<Int>,
    loadingProfileId: Int,
    onProfileSelected: (Int) -> Unit,
    onProfileToggle: (Int) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(profiles, key = { it.id }) { profile ->
            val isActive   = profile.id == activeProfileId
            val isLoaded   = loadedProfileIds.contains(profile.id)
            val isLoading  = profile.id == loadingProfileId
            val isEnabled  = profile.isEnabled
            var isFocused  by remember { mutableStateOf(false) }

            val pulseAlpha by if (isLoading) {
                rememberInfiniteTransition(label = "pulse").animateFloat(
                    initialValue = 0.4f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        tween(600, easing = LinearEasing), RepeatMode.Reverse
                    ), label = "a"
                )
            } else remember { mutableStateOf(1f) }

            // ── couleurs ────────────────────────────────────────────────
            val bgColor = when {
                isFocused  -> Color.White
                isLoading  -> Color.Transparent
                isActive   -> Color(0xFFBB86FC).copy(alpha = 0.35f)
                isEnabled && isLoaded -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                else       -> Color.Transparent
            }
            val borderColor = when {
                isFocused  -> Color.White
                isLoading  -> Color(0xFFFFB300).copy(alpha = pulseAlpha)
                isActive   -> Color(0xFFBB86FC)
                isEnabled && isLoaded   -> Color(0xFF4CAF50)
                isEnabled && !isLoaded  -> Color.White.copy(alpha = 0.25f)
                else /* disabled */     -> Color(0xFFEF5350).copy(alpha = 0.45f)
            }
            val borderWidth = if (isActive || isFocused || isLoading) 2.dp else 1.dp
            val textColor = when {
                isFocused  -> Color.Black
                isLoading  -> Color(0xFFFFB300)
                isActive   -> Color.White
                isEnabled && isLoaded   -> Color(0xFF66BB6A)
                isEnabled && !isLoaded  -> Color.White.copy(alpha = 0.5f)
                else /* disabled */     -> Color.White.copy(alpha = 0.3f)
            }
            val textDecoration = if (!isEnabled && !isActive) TextDecoration.LineThrough else null
            // ─────────────────────────────────────────────────────────────

            Box(
                modifier = Modifier
                    .onFocusChanged { isFocused = it.hasFocus }
                    .onKeyEvent { event ->
                        // Long-press OK sur DPAD → make active
                        val isOk = event.key == Key.DirectionCenter || event.key == Key.Enter
                        if (isOk && event.nativeKeyEvent.isLongPress) {
                            onProfileSelected(profile.id)
                            true
                        } else false
                    }
                    .combinedClickable(
                        onClick = { onProfileToggle(profile.id) },
                        onLongClick = { onProfileSelected(profile.id) }
                    )
                    .background(bgColor, MaterialTheme.shapes.small)
                    .border(borderWidth, borderColor, MaterialTheme.shapes.small)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(11.dp),
                            color = Color(0xFFFFB300),
                            strokeWidth = 1.5.dp
                        )
                    }
                    Text(
                        text = profile.profileName.ifEmpty { "P${profile.id}" },
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        textDecoration = textDecoration,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Indicateur ON/OFF discret (uniquement si pas actif)
                    if (!isActive && !isLoading) {
                        Text(
                            text = if (isEnabled) "●" else "○",
                            color = if (isEnabled) Color(0xFF4CAF50).copy(alpha = 0.8f)
                                    else Color(0xFFEF5350).copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

/**
 * Channel list inside the overlay with favorite star buttons.
 */
@Composable
private fun ColumnScope.OverlayChannelList(
        channels: List<ChannelEntity>,
        playingChannel: ChannelEntity?,
        profiles: List<ProfileEntity>,
        allFavoriteIds: Set<String>,
        onChannelSelected: (ChannelEntity) -> Unit,
        onFavoriteClick: (ChannelEntity) -> Unit,
        onOverlayDismiss: () -> Unit,
        channelState: LazyListState,
        channelFocusRequester: FocusRequester
) {
        LazyColumn(state = channelState, modifier = Modifier.weight(1f)) {
                items(channels, key = { "${it.profileId}_${it.stream_id}" }, contentType = { "overlay_channel" }) { channel ->
                        val isPlaying = channel.stream_id == playingChannel?.stream_id
                        val profile = remember(channel.profileId, profiles) {
                                profiles.find { it.id == channel.profileId }
                        }
                        val isFav = remember(channel.profileId, channel.stream_id, allFavoriteIds) {
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
                                                .then(if (isPlaying) Modifier.focusRequester(channelFocusRequester) else Modifier)
                                                .onFocusChanged { isRowFocused = it.hasFocus }
                                                .clickable {
                                                        if (isPlaying) {
                                                                onOverlayDismiss()
                                                        } else {
                                                                onOverlayDismiss()
                                                                onChannelSelected(channel)
                                                        }
                                                }
                                                .background(
                                                        color = when {
                                                                isRowFocused -> Color.White
                                                                isPlaying -> Color(0xFFBB86FC).copy(alpha = 0.2f)
                                                                else -> Color.Transparent
                                                        },
                                                        shape = MaterialTheme.shapes.small
                                                )
                                                .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        AsyncImage(
                                                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                                        .data(channel.stream_icon)
                                                        .size(48)
                                                        .crossfade(true)
                                                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                                        .build(),
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
                                                                isPlaying -> Color(0xFFBB86FC)
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
                                                                fontSize = TextUnit(10f, TextUnitType.Sp)
                                                        )
                                                }
                                        }
                                        if (isPlaying) {
                                                Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = null,
                                                        tint = if (isRowFocused) Color.Black else Color(0xFFBB86FC),
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
