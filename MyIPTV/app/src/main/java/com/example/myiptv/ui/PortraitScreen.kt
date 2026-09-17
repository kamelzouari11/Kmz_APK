package com.example.myiptv.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.example.myiptv.data.BrowseMode
import com.example.myiptv.data.SavedChannel
import com.example.myiptv.ui.theme.MyIptvPalette
import kotlin.math.abs

private enum class PortraitPage {
    FILTERS,
    CHANNELS,
}

@Composable
fun MainPortraitScreen(
    state: MainUiState,
    player: ExoPlayer,
    onStop: () -> Unit,
    onEpg: () -> Unit,
    onCinema: () -> Unit,
    onSports: () -> Unit,
    onEpgResults: () -> Unit,
    onCountry: (String) -> Unit,
    onCategory: (CategoryItem) -> Unit,
    onChannelFocused: (SavedChannel) -> Unit,
    onChannelPlay: (SavedChannel) -> Unit,
    onChannelEpg: (SavedChannel) -> Unit,
    onDismissFullEpg: () -> Unit,
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
    onClearRecentHistory: () -> Unit,
) {
    var pageName by rememberSaveable {
        mutableStateOf(PortraitPage.CHANNELS.name)
    }
    val page = PortraitPage.valueOf(pageName)

    LaunchedEffect(state.browseMode) {
        if (state.browseMode != BrowseMode.LIVE) pageName = PortraitPage.CHANNELS.name
    }
    BackHandler(enabled = page == PortraitPage.FILTERS) {
        pageName = PortraitPage.CHANNELS.name
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MyIptvPalette.Background)
            .safeDrawingPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            PortraitHeader(
                state = state,
                onStop = onStop,
                onEpg = onEpg,
                onCinema = onCinema,
                onSports = onSports,
                onSearch = onSearch,
                onRefresh = onRefresh,
                onProfile = onProfile,
                onUpload = onUpload,
                onDownload = onDownload,
                onQuit = onQuit,
            )
            PortraitPlayer(
                state = state,
                player = player,
                onFullScreen = onFullScreen,
                landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE,
            )
            PortraitModeBar(
                state = state,
                onLive = {
                    onLive()
                    pageName = PortraitPage.FILTERS.name
                },
                onEpgResults = onEpgResults,
                onRecent = {
                    onRecent()
                    pageName = PortraitPage.CHANNELS.name
                },
                onFavorites = onFavorites,
            )
            when (page) {
                PortraitPage.FILTERS -> PortraitFilters(
                    state = state,
                    onCountry = onCountry,
                    onCategory = { category ->
                        onCategory(category)
                        pageName = PortraitPage.CHANNELS.name
                    },
                    modifier = Modifier.weight(1f),
                )
                PortraitPage.CHANNELS -> PortraitChannels(
                    state = state,
                    onEditFilters = {
                        onLive()
                        pageName = PortraitPage.FILTERS.name
                    },
                    onFocused = onChannelFocused,
                    onPlay = onChannelPlay,
                    onShowEpg = onChannelEpg,
                    onLongPress = onChannelLongPress,
                    onClearRecentHistory = onClearRecentHistory,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MyIptvPalette.Background.copy(alpha = 0.84f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MyIptvPalette.Primary)
            }
        }
        state.message?.let { message ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                containerColor = MyIptvPalette.CardHover,
                contentColor = MyIptvPalette.TextPrimary,
            ) {
                Text(message)
            }
        }
        state.fullEpgChannel?.let { channel ->
            PortraitFullEpgOverlay(
                channel = channel,
                programs = state.fullEpg,
                loading = state.fullEpgLoading,
                error = state.fullEpgError,
                onRetry = { onChannelEpg(channel) },
                onPlay = {
                    onChannelPlay(channel)
                    onDismissFullEpg()
                },
                onDismiss = onDismissFullEpg,
            )
        }
    }
}

@Composable
private fun PortraitHeader(
    state: MainUiState,
    onStop: () -> Unit,
    onEpg: () -> Unit,
    onCinema: () -> Unit,
    onSports: () -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onProfile: () -> Unit,
    onUpload: () -> Unit,
    onDownload: () -> Unit,
    onQuit: () -> Unit,
) {
    var showMore by remember { mutableStateOf(false) }
    Surface(
        color = MyIptvPalette.Surface,
        border = BorderStroke(1.dp, MyIptvPalette.Border),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "MY IPTV",
                    color = MyIptvPalette.Primary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = state.profile?.name ?: "Aucun profil",
                    color = MyIptvPalette.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onSearch) {
                Icon(Icons.Rounded.Search, contentDescription = "Rechercher")
            }
            TextButton(onClick = onCinema) { Text("Cinéma") }
            IconButton(onClick = onEpg) {
                Icon(Icons.Rounded.DateRange, contentDescription = "Guide EPG")
            }
            if (state.streamUrl != null) {
                StopStreamButton(onClick = onStop)
            }
            Box {
                IconButton(onClick = { showMore = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Plus d’actions")
                }
                DropdownMenu(
                    expanded = showMore,
                    onDismissRequest = { showMore = false },
                ) {
                    PortraitMenuItem(
                        text = "Sports",
                        icon = Icons.Rounded.LiveTv,
                        onClick = {
                            showMore = false
                            onSports()
                        },
                    )
                    PortraitMenuItem(
                        text = "Synchroniser les chaînes",
                        icon = Icons.Rounded.Refresh,
                        enabled = state.profile != null && !state.isLoading,
                        onClick = {
                            showMore = false
                            onRefresh()
                        },
                    )
                    PortraitMenuItem(
                        text = "Profils",
                        icon = Icons.Rounded.Person,
                        onClick = {
                            showMore = false
                            onProfile()
                        },
                    )
                    PortraitMenuItem(
                        text = "Envoyer vers GitHub",
                        icon = Icons.Rounded.CloudUpload,
                        enabled = state.profile != null && !state.isLoading,
                        onClick = {
                            showMore = false
                            onUpload()
                        },
                    )
                    PortraitMenuItem(
                        text = "Télécharger depuis GitHub",
                        icon = Icons.Rounded.CloudDownload,
                        enabled = !state.isLoading,
                        onClick = {
                            showMore = false
                            onDownload()
                        },
                    )
                    PortraitMenuItem(
                        text = "Quitter",
                        icon = Icons.Rounded.PowerSettingsNew,
                        onClick = {
                            showMore = false
                            onQuit()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PortraitMenuItem(
    text: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
private fun PortraitPlayer(
    state: MainUiState,
    player: ExoPlayer,
    onFullScreen: () -> Unit,
    landscape: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (landscape) Modifier.height(220.dp) else Modifier.aspectRatio(16f / 9f),
            )
            .background(MyIptvPalette.Surface),
    ) {
        TvPlayer(
            streamUrl = state.streamUrl,
            player = player,
            modifier = Modifier.fillMaxSize(),
        )
        state.playingChannel?.let { channel ->
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(MyIptvPalette.Surface.copy(alpha = 0.88f))
                    .padding(start = 12.dp, end = 62.dp, top = 6.dp, bottom = 6.dp),
            ) {
                Text(
                    text = channel.name,
                    color = MyIptvPalette.TextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                state.epg.firstOrNull()?.let { program ->
                    Text(
                        text = "${program.timeRange} · ${program.title}",
                        color = MyIptvPalette.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        IconButton(
            enabled = state.streamUrl != null,
            onClick = onFullScreen,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(5.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MyIptvPalette.CardHover.copy(alpha = 0.92f)),
        ) {
            Icon(
                Icons.Rounded.Fullscreen,
                contentDescription = "Plein écran paysage",
                tint = MyIptvPalette.Primary,
            )
        }
    }
}

@Composable
private fun PortraitModeBar(
    state: MainUiState,
    onLive: () -> Unit,
    onRecent: () -> Unit,
    onFavorites: () -> Unit,
    onEpgResults: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MyIptvPalette.Surface)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PortraitModeButton(
            text = "Live",
            icon = Icons.Rounded.LiveTv,
            selected = state.browseMode == BrowseMode.LIVE,
            onClick = onLive,
            modifier = Modifier.weight(1f),
        )
        PortraitModeButton(
            text = "Récent",
            icon = Icons.Rounded.History,
            selected = state.browseMode == BrowseMode.RECENT,
            onClick = onRecent,
            modifier = Modifier.weight(1f),
        )
        PortraitModeButton(
            text = "Favoris",
            icon = Icons.Rounded.Favorite,
            selected = state.browseMode == BrowseMode.FAVORITES,
            onClick = onFavorites,
            modifier = Modifier.weight(1f),
        )
        if (state.epgSearchResultCount > 0) {
            PortraitModeButton(
                text = "EPG",
                icon = Icons.AutoMirrored.Rounded.FormatListBulleted,
                selected = state.browseMode == BrowseMode.EPG_SEARCH,
                onClick = onEpgResults,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PortraitModeButton(
    text: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = if (selected) MyIptvPalette.ActiveSurface else MyIptvPalette.Card,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (selected) MyIptvPalette.Primary else MyIptvPalette.Border,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                tint = if (selected) MyIptvPalette.Primary else MyIptvPalette.TextPrimary,
            )
            Text(
                text = text,
                modifier = Modifier.padding(start = 6.dp),
                color = if (selected) MyIptvPalette.Primary else MyIptvPalette.TextPrimary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun PortraitFilters(
    state: MainUiState,
    onCountry: (String) -> Unit,
    onCategory: (CategoryItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Text(
            text = "PAYS",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            color = MyIptvPalette.TextSecondary,
            style = MaterialTheme.typography.labelLarge,
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.countries, key = { it }) { country ->
                val selected = state.selectedCountry == country
                Surface(
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onCountry(country) },
                    color = if (selected) MyIptvPalette.ActiveSurface else MyIptvPalette.Card,
                    border = BorderStroke(
                        1.dp,
                        if (selected) MyIptvPalette.Primary else MyIptvPalette.Border,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = if (country == ALL_COUNTRIES_CODE) "ALL" else country,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                        color = if (selected) MyIptvPalette.Primary else MyIptvPalette.TextPrimary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Text(
            text = "CATÉGORIES · ${state.selectedCountry.orEmpty()}",
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 13.dp, bottom = 5.dp),
            color = MyIptvPalette.TextSecondary,
            style = MaterialTheme.typography.labelLarge,
        )
        if (state.categories.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Aucune catégorie", color = MyIptvPalette.TextSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    bottom = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                items(state.categories, key = { it.id }) { category ->
                    val selected = state.selectedCategoryId == category.id
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onCategory(category) },
                        color = if (selected) MyIptvPalette.ActiveSurface else MyIptvPalette.Card,
                        border = BorderStroke(
                            1.dp,
                            if (selected) MyIptvPalette.Primary else MyIptvPalette.Border,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 15.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = category.name,
                                modifier = Modifier.weight(1f),
                                color = if (selected) MyIptvPalette.Primary else MyIptvPalette.TextPrimary,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (category.epgChannelCount > 0) {
                                Text(
                                    text = "${category.epgChannelCount} EPG",
                                    modifier = Modifier.padding(start = 10.dp),
                                    color = MyIptvPalette.Positive,
                                    fontSize = 10.sp,
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
}

@Composable
private fun PortraitChannels(
    state: MainUiState,
    onEditFilters: () -> Unit,
    onFocused: (SavedChannel) -> Unit,
    onPlay: (SavedChannel) -> Unit,
    onShowEpg: (SavedChannel) -> Unit,
    onLongPress: (SavedChannel) -> Unit,
    onClearRecentHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val epgNow = rememberCurrentEpgEpochSeconds()
    Column(modifier = modifier.fillMaxWidth()) {
        if (state.browseMode == BrowseMode.LIVE) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onEditFilters),
                color = MyIptvPalette.Card,
                border = BorderStroke(1.dp, MyIptvPalette.Border),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = null,
                        tint = MyIptvPalette.Primary,
                    )
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(
                            text = "${state.selectedCountry.orEmpty()} · ${state.categories.firstOrNull { it.id == state.selectedCategoryId }?.name.orEmpty()}",
                            color = MyIptvPalette.TextPrimary,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "Modifier le pays ou la catégorie",
                            color = MyIptvPalette.TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        } else {
            Text(
                text = portraitChannelTitle(state),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = MyIptvPalette.TextSecondary,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (state.visibleChannels.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Aucune chaîne dans cette sélection",
                    color = MyIptvPalette.TextSecondary,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 10.dp,
                    end = 10.dp,
                    bottom = 18.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                if (state.browseMode == BrowseMode.RECENT) {
                    item(key = "@portrait_clear_recent") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onClearRecentHistory),
                            color = MyIptvPalette.Card,
                            border = BorderStroke(1.dp, MyIptvPalette.Border),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.DeleteSweep,
                                    contentDescription = null,
                                    tint = MyIptvPalette.Negative,
                                )
                                Text(
                                    text = "Effacer tous les récents",
                                    modifier = Modifier.padding(start = 10.dp),
                                    color = MyIptvPalette.Negative,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                }
                items(state.visibleChannels, key = { it.streamId }) { channel ->
                    val hasEpg = channel.epgAvailabilityKey() in state.epgAvailableChannels
                    val currentProgram = state.currentEpgProgramFor(channel, epgNow)
                    TvListItem(
                        selected = state.playingChannel?.streamId == channel.streamId,
                        onFocused = { onFocused(channel) },
                        onClick = {
                            onFocused(channel)
                            onPlay(channel)
                        },
                        onLongClick = { onLongPress(channel) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 12.dp,
                            vertical = 8.dp,
                        ),
                    ) { _, color ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AsyncImage(
                                model = channel.iconUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(MyIptvPalette.CardHover)
                                    .padding(3.dp),
                                contentScale = ContentScale.Fit,
                            )
                            Column(
                                Modifier
                                    .weight(1f)
                                    .then(
                                        if (hasEpg) {
                                            Modifier.pointerInput(channel.streamId) {
                                                var horizontalDistance = 0f
                                                val swipeThreshold = 72.dp.toPx()
                                                detectHorizontalDragGestures(
                                                    onDragStart = { horizontalDistance = 0f },
                                                    onDragCancel = { horizontalDistance = 0f },
                                                    onDragEnd = {
                                                        if (abs(horizontalDistance) >= swipeThreshold) {
                                                            onShowEpg(channel)
                                                        }
                                                        horizontalDistance = 0f
                                                    },
                                                    onHorizontalDrag = { change, dragAmount ->
                                                        horizontalDistance += dragAmount
                                                        change.consume()
                                                    },
                                                )
                                            }
                                        } else {
                                            Modifier
                                        },
                                    ),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
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
                                            color = MyIptvPalette.Positive,
                                            fontSize = 10.sp,
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

private fun portraitChannelTitle(state: MainUiState): String = when (state.browseMode) {
    BrowseMode.LIVE -> "CHAÎNES · ${state.visibleChannels.size}"
    BrowseMode.RECENT -> "RÉCENTS · ${state.visibleChannels.size}"
    BrowseMode.FAVORITES -> "${state.selectedFavoriteGroup?.name ?: "FAVORIS"} · ${state.visibleChannels.size}"
    BrowseMode.SEARCH -> "RECHERCHE · ${state.searchQuery} · ${state.visibleChannels.size}"
    BrowseMode.EPG_SEARCH -> "RÉSULTATS EPG · ${state.epgSearchQuery} · ${state.visibleChannels.size}"
}
