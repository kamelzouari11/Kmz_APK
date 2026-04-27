package com.example.simpleiptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.simpleiptv.data.local.entities.ChannelEntity
import com.example.simpleiptv.data.local.entities.CategoryEntity
import com.example.simpleiptv.ui.components.ChannelItem
import com.example.simpleiptv.ui.components.GlobalChannelItem
import com.example.simpleiptv.ui.components.RecentsSection
import com.example.simpleiptv.ui.components.SidebarItem
import com.example.simpleiptv.ui.components.VodItem
import com.example.simpleiptv.ui.viewmodel.GeneratorType
import com.example.simpleiptv.ui.viewmodel.MainViewModel
import com.example.simpleiptv.ui.viewmodel.MediaMode
import kotlinx.coroutines.launch

enum class PortraitNavigationState {
    COUNTRIES, CATEGORIES, CHANNELS, SEARCH_RESULTS
}

@Composable
fun MainContentPortrait(
    viewModel: MainViewModel,
    onChannelClick: (ChannelEntity) -> Unit,
    channelScrollState: LazyListState, playerReturnFocusRequester: FocusRequester
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = pagerState.currentPage == 0,
                onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                text = { Text("Live TV") },
                icon = { Icon(Icons.Default.LiveTv, contentDescription = "Live TV") }
            )
            Tab(
                selected = pagerState.currentPage == 1,
                onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                text = { Text("VOD") },
                icon = { Icon(Icons.Default.Movie, contentDescription = "VOD") }
            )
            Tab(
                selected = pagerState.currentPage == 2,
                onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                text = { Text("Favoris") },
                icon = { Icon(Icons.Default.Star, contentDescription = "Favoris") }
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> LiveTvTab(viewModel, onChannelClick, channelScrollState, playerReturnFocusRequester)
                1 -> VodTab(viewModel, onChannelClick)
                2 -> FavoritesTab(viewModel, onChannelClick, channelScrollState, playerReturnFocusRequester)
            }
        }
    }
}

@Composable
private fun LiveTvTab(
    viewModel: MainViewModel,
    onChannelClick: (ChannelEntity) -> Unit,
    scrollState: LazyListState,
    playerReturnFocusRequester: FocusRequester
) {
    var navState by remember { mutableStateOf(PortraitNavigationState.COUNTRIES) }

    BackHandler(enabled = true) {
        navState = when (navState) {
            PortraitNavigationState.CHANNELS -> PortraitNavigationState.CATEGORIES
            PortraitNavigationState.CATEGORIES -> PortraitNavigationState.COUNTRIES
            PortraitNavigationState.SEARCH_RESULTS -> PortraitNavigationState.COUNTRIES
            PortraitNavigationState.COUNTRIES -> PortraitNavigationState.COUNTRIES
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (viewModel.uiState.searchQuery.isNotBlank()) {
            SearchResultListPortrait(viewModel, onChannelClick, scrollState)
        } else {
            when (navState) {
                PortraitNavigationState.COUNTRIES -> {
                    CountryListVertical(
                        countries = viewModel.uiState.countryFilters,
                        selectedCountry = viewModel.uiState.selectedCountryFilter,
                        onCountrySelected = {
                            viewModel.setCountryFilter(it)
                            navState = PortraitNavigationState.CATEGORIES
                        }
                    )
                }
                PortraitNavigationState.CATEGORIES -> {
                    CategoryListVertical(
                        categories = viewModel.uiState.filteredCategories,
                        selectedCategory = viewModel.uiState.selectedCategoryId,
                        onCategorySelected = {
                            viewModel.selectCategory(it)
                            navState = PortraitNavigationState.CHANNELS
                        }
                    )
                }
                PortraitNavigationState.CHANNELS -> {
                    LiveChannelListPortrait(viewModel, onChannelClick, scrollState, playerReturnFocusRequester)
                }
                PortraitNavigationState.SEARCH_RESULTS -> {
                    SearchResultListPortrait(viewModel, onChannelClick, scrollState)
                }
            }
        }
    }
}

@Composable
private fun CountryListVertical(
    countries: List<String>,
    selectedCountry: String,
    onCountrySelected: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("Sélectionnez un pays", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))
        }
        items(countries) { country ->
            FilterChip(
                selected = country == selectedCountry,
                onClick = { onCountrySelected(country) },
                label = { Text(country) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CategoryListVertical(
    categories: List<CategoryEntity>,
    selectedCategory: String?,
    onCategorySelected: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("Sélectionnez une catégorie", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))
        }
        items(categories) { category ->
            AssistChip(
                onClick = { onCategorySelected(category.category_id) },
                label = { Text(category.category_name) },
                modifier = Modifier.fillMaxWidth(),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (category.category_id == selectedCategory)
                        MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                )
            )
        }
    }
}

@Composable
private fun SearchResultListPortrait(
    viewModel: MainViewModel,
    onChannelClick: (ChannelEntity) -> Unit,
    scrollState: LazyListState
) {
    val profileMap = remember(viewModel.uiState.profiles) {
        viewModel.uiState.profiles.associateBy { it.id }
    }

    LazyColumn(
        state = scrollState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Text(
                text = "Résultats pour : ${viewModel.uiState.searchQuery}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        val channelsToDisplay = if (viewModel.uiState.lastGeneratorType == GeneratorType.GLOBAL_SEARCH) {
            viewModel.uiState.globalSearchResults.map { it.toChannelEntity() }
        } else {
            viewModel.uiState.channels
        }

        items(channelsToDisplay, key = { it.stream_id }) { channel ->
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
    }
}

@Composable
private fun LiveChannelListPortrait(
    viewModel: MainViewModel,
    onChannelClick: (ChannelEntity) -> Unit,
    scrollState: LazyListState,
    playerReturnFocusRequester: FocusRequester
) {
    val profileMap = remember(viewModel.uiState.profiles) {
        viewModel.uiState.profiles.associateBy { it.id }
    }

    LazyColumn(
        state = scrollState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (viewModel.uiState.selectedCountryFilter == "ALL" &&
            viewModel.uiState.selectedCategoryId == null) {
            item {
                RecentsSection(
                    isSelected = viewModel.uiState.lastGeneratorType == GeneratorType.RECENTS,
                    recentScope = viewModel.uiState.recentScope,
                    onShowRecents = { viewModel.showRecents() },
                    onClearRecents = { viewModel.clearRecents() },
                    onToggleRecentScope = { viewModel.toggleRecentScope() }, upFocusRequester = playerReturnFocusRequester, leftFocusRequester = androidx.compose.ui.focus.FocusRequester()
                )
            }
        }

        items(viewModel.uiState.channels, key = { it.stream_id }) { channel ->
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
                    if (totalItems > 0 && lastVisibleItem >= totalItems - 5 &&
                        !viewModel.uiState.isLoadingMore && viewModel.uiState.hasMore) {
                        viewModel.loadMoreChannels()
                    }
                }
            }
        }
    }
}

@Composable
private fun VodTab(
    viewModel: MainViewModel,
    onChannelClick: (ChannelEntity) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        if (viewModel.uiState.channels.isNotEmpty()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(viewModel.uiState.channels, key = { it.stream_id }) { channel ->
                    val isPlaying = viewModel.uiState.playingChannel?.stream_id == channel.stream_id
                    VodItem(
                        channel = channel,
                        isPlaying = isPlaying,
                        onClick = { onChannelClick(channel) }
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
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Sélectionnez une catégorie pour afficher les films", color = Color.Gray)
            }
        }
    }
}

@Composable
private fun FavoritesTab(
    viewModel: MainViewModel,
    onChannelClick: (ChannelEntity) -> Unit,
    scrollState: LazyListState,
    playerReturnFocusRequester: FocusRequester
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            items(viewModel.uiState.filteredFavoriteLists, key = { it.id }) { list ->
                FilterChip(
                    selected = viewModel.uiState.selectedFavoriteListId == list.id,
                    onClick = { viewModel.selectFavoriteList(list.id) },
                    label = { Text(list.name) },
                    leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, Modifier.size(18.dp)) }
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            LiveChannelListPortrait(viewModel, onChannelClick, scrollState, playerReturnFocusRequester)
        }
    }
}
