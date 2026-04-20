package com.example.simpleiptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
        countryScrollState: LazyListState,
        categoryScrollState: LazyListState,
        channelScrollState: LazyListState
) {
    val accueilFocusRequester = remember { FocusRequester() }

    Row(modifier = Modifier.fillMaxSize()) {
        // --- Column 1: Countries / Home ---
        CountrySidebar(
                viewModel = viewModel,
                scrollState = countryScrollState,
                accueilFocusRequester = accueilFocusRequester,
                modifier = Modifier.weight(0.15f)
        )

        VerticalDivider(color = Color.Gray.copy(alpha = 0.1f), thickness = 1.dp)

        // --- Column 2: Categories / Favorites / Recents ---
        CategorySidebar(
                viewModel = viewModel,
                scrollState = categoryScrollState,
                modifier = Modifier.weight(0.30f)
        )

        VerticalDivider(color = Color.Gray.copy(alpha = 0.1f), thickness = 1.dp)

        // --- Column 3: Channels ---
        ChannelListPanel(
                viewModel = viewModel,
                onChannelClick = onChannelClick,
                scrollState = channelScrollState,
                modifier = Modifier.weight(0.55f)
        )
    }
}

// ===================== Column 1: Countries =====================

@Composable
private fun CountrySidebar(
        viewModel: MainViewModel,
        scrollState: LazyListState,
        accueilFocusRequester: FocusRequester,
        modifier: Modifier = Modifier
) {
    LazyColumn(
            state = scrollState,
            modifier = modifier
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.05f))
                    .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            SidebarItem(
                    text = "Accueil",
                    icon = Icons.Default.Home,
                    isSelected = viewModel.uiState.selectedCountryFilter == "ALL",
                    onClick = { viewModel.setCountryFilter("ALL") },
                    modifier = Modifier.focusRequester(accueilFocusRequester)
            )
        }
        item {
            Text(
                    text = "Pays",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp)
            )
        }
        items(viewModel.uiState.countryFilters.filter { it != "ALL" }, key = { it }, contentType = { "country" }) { country ->
            SidebarItem(
                    text = country,
                    icon = null,
                    isSelected = viewModel.uiState.selectedCountryFilter == country,
                    onClick = {
                        viewModel.setCountryFilter(country)
                        viewModel.selectCategory(null)
                    }
            )
        }
    }
}

// ===================== Column 2: Categories =====================

@Composable
private fun CategorySidebar(
        viewModel: MainViewModel,
        scrollState: LazyListState,
        modifier: Modifier = Modifier
) {
    LazyColumn(
            state = scrollState,
            modifier = modifier
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.02f))
                    .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Recents + Favorites only visible when "ALL" country filter
        if (viewModel.uiState.selectedCountryFilter == "ALL") {
            item {
                RecentsSection(
                        isSelected = viewModel.uiState.lastGeneratorType == GeneratorType.RECENTS,
                        recentScope = viewModel.uiState.recentScope,
                        onShowRecents = { viewModel.showRecents() },
                        onClearRecents = { viewModel.clearRecents() },
                        onToggleRecentScope = { viewModel.toggleRecentScope() }
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
        }

        item {
            Text(
                    text = if (viewModel.uiState.selectedCountryFilter == "ALL") "Catégories"
                           else "Catégories ${viewModel.uiState.selectedCountryFilter}",
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

// ===================== Column 3: Channels =====================

@Composable
private fun ChannelListPanel(
        viewModel: MainViewModel,
        onChannelClick: (ChannelEntity) -> Unit,
        scrollState: LazyListState,
        modifier: Modifier = Modifier
) {
    val isVod = viewModel.uiState.currentMediaMode == MediaMode.VOD
    val isGlobalSearch = viewModel.uiState.lastGeneratorType == GeneratorType.GLOBAL_SEARCH

    when {
        isGlobalSearch -> GlobalSearchList(viewModel, onChannelClick, scrollState, modifier)
        isVod -> VodGrid(viewModel, onChannelClick, modifier)
        else -> LiveChannelList(viewModel, onChannelClick, scrollState, modifier)
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
                                   else "Aucune chaîne trouvée pour \u00ab ${viewModel.uiState.searchQuery} \u00bb",
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

            // Indicateur de chargement infini
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
                gridItems(viewModel.uiState.channels, key = { it.stream_id }, contentType = { "vod" }) { channel ->
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
    val scope = rememberCoroutineScope()

    LazyColumn(
            state = scrollState,
            modifier = modifier.fillMaxHeight().padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(viewModel.uiState.channels, key = { it.stream_id }, contentType = { "channel" }) { channel ->
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

        // Indicateur de chargement infini
        if (viewModel.uiState.isLoadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            }
        }

        // Déclenche le chargement infini quand on approche de la fin
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
