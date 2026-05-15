package com.example.simpleiptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.simpleiptv.data.local.entities.ChannelEntity
import com.example.simpleiptv.ui.components.ChannelItem
import com.example.simpleiptv.ui.components.FavoritesHeader
import com.example.simpleiptv.ui.components.GlobalChannelItem
import com.example.simpleiptv.ui.components.RecentsSection
import com.example.simpleiptv.ui.components.SidebarItem
import com.example.simpleiptv.ui.components.VodItem
import com.example.simpleiptv.ui.viewmodel.GeneratorType
import com.example.simpleiptv.ui.viewmodel.MainViewModel
import com.example.simpleiptv.ui.viewmodel.MediaMode
import com.example.simpleiptv.ui.viewmodel.SearchScope

@Composable
fun MainContentLandscape(
        viewModel: MainViewModel,
        onChannelClick: (ChannelEntity) -> Unit,
        categoryScrollState: LazyListState,
        channelScrollState: LazyListState,
        playerReturnFocusRequester: FocusRequester
) {
    val categoryFocusRequester = remember { FocusRequester() }
    val channelFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    // 0 = catégories, 1 = chaînes
    var focusedColumn by remember { mutableIntStateOf(0) }

    // BACK ramène le focus une colonne en arrière (chaînes → catégories)
    BackHandler(enabled = focusedColumn > 0) {
        focusedColumn--
        scope.launch {
            try { categoryFocusRequester.requestFocus() } catch (e: Exception) {}
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // --- Colonne 1 : Catégories / Favoris / Récents ---
        Box(
            modifier = Modifier
                .weight(0.35f)
                .onFocusChanged { if (it.hasFocus) focusedColumn = 0 }
        ) {
            CategorySidebar(
                viewModel = viewModel,
                scrollState = categoryScrollState,
                channelFocusRequester = channelFocusRequester,
                playerReturnFocusRequester = playerReturnFocusRequester
            )
        }

        VerticalDivider(color = Color.Gray.copy(alpha = 0.1f), thickness = 1.dp)

        // --- Colonne 2 : Chaînes ---
        Box(
            modifier = Modifier
                .weight(0.65f)
                .onFocusChanged { if (it.hasFocus) focusedColumn = 1 }
        ) {
            ChannelListPanel(
                viewModel = viewModel,
                onChannelClick = onChannelClick,
                scrollState = channelScrollState,
                categoryFocusRequester = categoryFocusRequester
            )
        }
    }
}

// ===================== Colonne 1 : Catégories =====================

@Composable
private fun CategorySidebar(
        viewModel: MainViewModel,
        scrollState: LazyListState,
        channelFocusRequester: FocusRequester,
        playerReturnFocusRequester: FocusRequester,
        modifier: Modifier = Modifier
) {
    LazyColumn(
            state = scrollState,
            modifier = modifier
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.02f))
                    .padding(4.dp)
                    .focusProperties {
                        right = channelFocusRequester
                    },
            verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            RecentsSection(
                isSelected = viewModel.uiState.lastGeneratorType == GeneratorType.RECENTS,
                recentScope = viewModel.uiState.recentScope,
                onShowRecents = { viewModel.showRecents() },
                onClearRecents = { viewModel.clearRecents() },
                onToggleRecentScope = { viewModel.toggleRecentScope() },
                upFocusRequester = playerReturnFocusRequester
            )
        }
        item {
            FavoritesHeader(
                isSelected = viewModel.uiState.lastGeneratorType == GeneratorType.FAVORITES,
                favoriteScope = viewModel.uiState.favoriteScope,
                favoriteListScope = viewModel.uiState.favoriteListScope,
                onToggleFavoriteScope = { viewModel.toggleFavoriteScope() },
                onToggleFavoriteListScope = { viewModel.toggleFavoriteListScope() },
                onShowAddListDialog = { viewModel.showAddListDialog() }
            )
        }
        items(viewModel.uiState.filteredFavoriteLists, key = { it.id }, contentType = { "fav" }) { list ->
            val isSelected = viewModel.uiState.selectedFavoriteListId == list.id
            SidebarItem(
                text = list.name,
                icon = Icons.Default.Star,
                isSelected = isSelected,
                onClick = { viewModel.selectFavoriteList(list.id) },
                onDelete = { viewModel.requestRemoveFavoriteList(list) }
            )
        }

        item {
            Text(
                text = "Catégories",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp)
            )
        }
        items(viewModel.uiState.filteredCategories, key = { it.category_id }, contentType = { "cat" }) { category ->
            val isSelected = viewModel.uiState.selectedCategoryId == category.category_id
            SidebarItem(
                text = category.category_name,
                icon = null,
                isSelected = isSelected,
                onClick = { viewModel.selectCategory(category.category_id) }
            )
        }
    }
}

// ===================== Colonne 2 : Chaînes =====================

@Composable
private fun ChannelListPanel(
        viewModel: MainViewModel,
        onChannelClick: (ChannelEntity) -> Unit,
        scrollState: LazyListState,
        categoryFocusRequester: FocusRequester,
        modifier: Modifier = Modifier
) {
    val isVod = viewModel.uiState.currentMediaMode == MediaMode.VOD
    val isGlobalSearch = viewModel.uiState.lastGeneratorType == GeneratorType.GLOBAL_SEARCH

    Box(
        modifier = modifier
            .focusProperties {
                left = categoryFocusRequester
            }
    ) {
        when {
            isGlobalSearch -> GlobalSearchList(viewModel, onChannelClick, scrollState, Modifier.fillMaxSize())
            isVod -> VodGrid(viewModel, onChannelClick, Modifier.fillMaxSize())
            else -> LiveChannelList(viewModel, onChannelClick, scrollState, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun GlobalSearchList(
        viewModel: MainViewModel,
        onChannelClick: (ChannelEntity) -> Unit,
        scrollState: LazyListState,
        modifier: Modifier = Modifier
) {
    LazyColumn(
            state = scrollState,
            modifier = modifier.fillMaxHeight().padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (viewModel.uiState.globalSearchResults.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    Text(
                            text = if (viewModel.uiState.searchQuery.isBlank()) "Entrez plusieurs mots pour une recherche globale"
                                   else "Aucune chaîne trouvée pour « ${viewModel.uiState.searchQuery} »",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            items(viewModel.uiState.globalSearchResults, key = { "${it.profileId}_${it.stream_id}" }, contentType = { "global_result" }) { item ->
                val isPlaying = viewModel.uiState.playingChannel?.stream_id == item.stream_id &&
                                viewModel.uiState.activeProfileId == item.profileId
                val isFav = viewModel.uiState.allFavoriteIds.contains("${item.profileId}_${item.stream_id}")
                GlobalChannelItem(
                        item = item,
                        isPlaying = isPlaying,
                        isFavorite = isFav,
                        onClick = { onChannelClick(item.toChannelEntity()) },
                        onFavoriteClick = { viewModel.initFavoriteAction(item.toChannelEntity()) }
                )
            }

            if (viewModel.uiState.isLoadingMore) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun VodGrid(
        viewModel: MainViewModel,
        onChannelClick: (ChannelEntity) -> Unit,
        modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxHeight().padding(4.dp)) {
        if (viewModel.uiState.channels.isNotEmpty()) {
            LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                gridItems(viewModel.uiState.channels, key = { "${it.profileId}_${it.stream_id}" }, contentType = { "vod" }) { channel ->
                    val isPlaying = viewModel.uiState.playingChannel?.stream_id == channel.stream_id
                    VodItem(
                            channel = channel,
                            isPlaying = isPlaying,
                            onClick = { onChannelClick(channel) }
                    )
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Aucun film trouvé", color = Color.Gray)
            }
        }
    }
}

@Composable
private fun LiveChannelList(
        viewModel: MainViewModel,
        onChannelClick: (ChannelEntity) -> Unit,
        scrollState: LazyListState,
        modifier: Modifier = Modifier
) {
    val profileMap = remember(viewModel.uiState.profiles) {
        viewModel.uiState.profiles.associateBy { it.id }
    }

    LazyColumn(
            state = scrollState,
            modifier = modifier.fillMaxHeight().padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(viewModel.uiState.channels, key = { "${it.profileId}_${it.stream_id}" }, contentType = { "channel" }) { channel ->
            val isPlaying = viewModel.uiState.playingChannel?.stream_id == channel.stream_id
            val isFav = viewModel.uiState.allFavoriteIds.contains("${channel.profileId}_${channel.stream_id}")
            val channelProfile = profileMap[channel.profileId]
            ChannelItem(
                    channel = channel,
                    isPlaying = isPlaying,
                    isFavorite = isFav,
                    onClick = { onChannelClick(channel) },
                    onFavoriteClick = { viewModel.initFavoriteAction(channel) },
                    profile = channelProfile
            )
        }

        if (viewModel.uiState.isLoadingMore) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            }
        }

        item {
            LaunchedEffect(scrollState) {
                snapshotFlow { scrollState.layoutInfo }.collect { layoutInfo ->
                    val totalItems = layoutInfo.totalItemsCount
                    val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                    if (totalItems > 0 && lastVisibleItem >= totalItems - 5 && !viewModel.uiState.isLoadingMore && viewModel.uiState.hasMore) {
                        viewModel.loadMoreChannels()
                    }
                }
            }
        }
    }
}
