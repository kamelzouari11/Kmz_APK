package com.example.simpleiptv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
 * Shows profile selector, country/category/channel columns.
 */
@Composable
fun PlayerOverlay(
        profiles: List<ProfileEntity>,
        activeProfileId: Int,
        onProfileSelected: (Int) -> Unit,
        countries: List<String>,
        selectedCountry: String,
        onCountrySelected: (String) -> Unit,
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
        countryState: LazyListState,
        categoryState: LazyListState,
        channelState: LazyListState,
        categoryFocusRequester: FocusRequester,
        channelFocusRequester: FocusRequester,
        viewModel: MainViewModel
) {
        Column(
                modifier = Modifier.fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(24.dp)
        ) {
                // Profile selector row
                                 if (profiles.isNotEmpty()) {
                                         ProfileSelectorRow(
                                             profiles = profiles,
                                             activeProfileId = activeProfileId,
                                             onProfileSelected = onProfileSelected,
                                             loadedProfileIds = viewModel.uiState.loadedProfileIds
                                         )
                                     }

                Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = if (showFullOverlay) Arrangement.Start else Arrangement.End
                ) {
                        if (showFullOverlay) {
                                // Country list
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

                                // Category list
                                 OverlayColumn(title = "Catégories", width = 280.dp) {
                                         LazyColumn(state = categoryState, modifier = Modifier.weight(1f)) {
                                                 item {
                                                      OverlayListItem(
                                                          text = "Récents",
                                                          isSelected = viewModel.uiState.lastGeneratorType == com.example.simpleiptv.ui.viewmodel.GeneratorType.RECENTS,
                                                          onClick = { 
                                                               viewModel.showRecents()
                                                          },
                                                          content = { isFocused ->
                                                              Row(verticalAlignment = Alignment.CenterVertically) {
                                                                  Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp), tint = if (isFocused) Color.Black else Color.White)
                                                                  Spacer(Modifier.width(8.dp))
                                                                  Text("Récents", color = if (isFocused) Color.Black else Color.White)
                                                              }
                                                              }
                                                      )
                                                 }
                                                 itemsIndexed(categories, key = { _, cat -> cat.category_id }) { index, category ->
                                                         val isSelected = category.category_id == selectedCategoryId
                                                         val isInitialFocus = if (selectedCategoryId != null) isSelected else index == 1
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

                        // Channel list
                        OverlayColumn(
                                title = "Chaînes",
                                width = if (showFullOverlay) 320.dp else 400.dp
                        ) {
                                OverlayChannelList(
                                        channels = currentChannels,
                                        playingChannel = playingChannel,
                                        profiles = profiles,
                                        allFavoriteIds = allFavoriteIds,
                                        onChannelSelected = onChannelSelected,
                                        onFavoriteClick = onFavoriteClick,
                                        onOverlayDismiss = onOverlayDismiss,
                                        channelState = channelState
                                )
                        }
                }
        }
}

/**
 * Profile selector horizontal row in the overlay.
 */
@Composable
private fun ProfileSelectorRow(
        profiles: List<ProfileEntity>,
        activeProfileId: Int,
        onProfileSelected: (Int) -> Unit,
        loadedProfileIds: Set<Int>
) {
        androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                items(profiles, key = { it.id }) { profile ->
                        val isSelected = profile.id == activeProfileId
                        val isLoaded = loadedProfileIds.contains(profile.id)
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
                                                          isLoaded -> Color(0xFFADD8E6).copy(alpha = 0.4f)
                                                         else -> Color.Transparent
                                                 },
                                                 shape = MaterialTheme.shapes.small
                                         )
                                         .border(
                                                 width = 1.dp,
                                                 color = when {
                                                         isFocused -> Color.White
                                                         isSelected -> Color.Green
                                                          isLoaded -> Color(0xFFADD8E6)
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
        channelState: LazyListState
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
                                                .onFocusChanged { isRowFocused = it.isFocused }
                                                .clickable {
                                                        if (isPlaying) {
                                                                onOverlayDismiss()
                                                        } else {
                                                                onOverlayDismiss()
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
                                                                fontSize = TextUnit(10f, TextUnitType.Sp)
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
