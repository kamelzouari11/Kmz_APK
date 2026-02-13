package com.example.simpleiptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.simpleiptv.data.local.entities.ChannelEntity
import com.example.simpleiptv.ui.components.ChannelItem
import com.example.simpleiptv.ui.components.SidebarItem
import com.example.simpleiptv.ui.components.VodItem
import com.example.simpleiptv.ui.viewmodel.GeneratorType
import com.example.simpleiptv.ui.viewmodel.MainViewModel
import com.example.simpleiptv.ui.viewmodel.MediaMode

@Composable
fun MainContentPortrait(viewModel: MainViewModel, onChannelClick: (ChannelEntity) -> Unit) {
    // Local state to manage Screen A1 vs Screen A2
    var isShowingChannels by remember { mutableStateOf(false) }

    // If we are showing channels, handle Back button to return to selection
    if (isShowingChannels) {
        BackHandler { isShowingChannels = false }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!isShowingChannels) {
            // --- SCREEN A1: SELECTION (Pays 2 / Categories 7) ---
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Column: Pays / Groups (Weight 2)
                LazyColumn(
                        modifier =
                                Modifier.weight(2f)
                                        .fillMaxHeight()
                                        .background(
                                                MaterialTheme.colorScheme.surface.copy(
                                                        alpha = 0.05f
                                                )
                                        )
                                        .padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        SidebarItem(
                                text = "Accueil",
                                icon = Icons.Default.Home,
                                isSelected = viewModel.selectedCountryFilter == "ALL",
                                onClick = { viewModel.selectedCountryFilter = "ALL" }
                        )
                    }
                    item {
                        Text(
                                text = "Pays",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                        )
                    }
                    lazyItems(viewModel.countryFilters.filter { it != "ALL" }, key = { it }) {
                            country ->
                        SidebarItem(
                                text = country,
                                icon = null,
                                isSelected = viewModel.selectedCountryFilter == country,
                                onClick = {
                                    viewModel.selectedCountryFilter = country
                                    viewModel.selectedCategoryId = null
                                }
                        )
                    }
                }

                VerticalDivider(color = Color.Gray.copy(alpha = 0.1f), thickness = 1.dp)

                // Right Column: Categories / Favorites / Recents (Weight 7)
                LazyColumn(
                        modifier = Modifier.weight(7f).fillMaxHeight().padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (viewModel.selectedCountryFilter == "ALL") {
                        item {
                            SidebarItem(
                                    text = "Récents",
                                    icon = Icons.Default.History,
                                    isSelected =
                                            viewModel.lastGeneratorType == GeneratorType.RECENTS,
                                    onClick = {
                                        viewModel.selectedCategoryId = null
                                        viewModel.selectedFavoriteListId = -1
                                        viewModel.searchQuery = ""
                                        viewModel.lastGeneratorType = GeneratorType.RECENTS
                                        viewModel.refreshChannels()
                                        isShowingChannels = true
                                    }
                            )
                        }
                        item {
                            Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier =
                                            Modifier.fillMaxWidth()
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                        text = "Favoris",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray,
                                        modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                        onClick = { viewModel.showAddListDialog = true },
                                        modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                            Icons.Default.Add,
                                            null,
                                            tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        lazyItems(viewModel.favoriteLists, key = { it.id }) { list ->
                            SidebarItem(
                                    text = list.name,
                                    icon = Icons.Default.Star,
                                    isSelected = viewModel.selectedFavoriteListId == list.id,
                                    onClick = {
                                        viewModel.selectedFavoriteListId = list.id
                                        viewModel.selectedCategoryId = null
                                        viewModel.searchQuery = ""
                                        viewModel.lastGeneratorType = GeneratorType.FAVORITES
                                        viewModel.refreshChannels()
                                        isShowingChannels = true
                                    },
                                    onDelete = { viewModel.removeFavoriteList(list) }
                            )
                        }
                    }

                    item {
                        Text(
                                text =
                                        if (viewModel.selectedCountryFilter == "ALL") "Catégories"
                                        else "Catégories ${viewModel.selectedCountryFilter}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                                modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp)
                        )
                    }
                    lazyItems(viewModel.filteredCategories, key = { it.category_id }) { category ->
                        SidebarItem(
                                text = category.category_name,
                                icon = null,
                                isSelected = viewModel.selectedCategoryId == category.category_id,
                                onClick = {
                                    viewModel.selectedCategoryId = category.category_id
                                    viewModel.selectedFavoriteListId = -1
                                    viewModel.searchQuery = ""
                                    viewModel.lastGeneratorType = GeneratorType.CATEGORY
                                    viewModel.refreshChannels()
                                    isShowingChannels = true
                                }
                        )
                    }
                }
            }
        } else {
            // --- SCREEN A2: CHANNEL LIST ---
            Column(modifier = Modifier.fillMaxSize()) {
                // Header for A2 to show what we are looking at and a back button
                Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { isShowingChannels = false }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                    Text(
                            text =
                                    when (viewModel.lastGeneratorType) {
                                        GeneratorType.CATEGORY ->
                                                viewModel.filteredCategories
                                                        .find {
                                                            it.category_id ==
                                                                    viewModel.selectedCategoryId
                                                        }
                                                        ?.category_name
                                                        ?: "Catégorie"
                                        GeneratorType.FAVORITES ->
                                                viewModel.favoriteLists
                                                        .find {
                                                            it.id ==
                                                                    viewModel.selectedFavoriteListId
                                                        }
                                                        ?.name
                                                        ?: "Favoris"
                                        GeneratorType.RECENTS -> "Récents"
                                        else -> "Chaînes"
                                    },
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 8.dp)
                    )
                }

                HorizontalDivider(color = Color.Gray.copy(alpha = 0.1f), thickness = 1.dp)

                val isVod = viewModel.currentMediaMode == MediaMode.VOD

                if (isVod) {
                    var isGridView by rememberSaveable { mutableStateOf(false) }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp)) {
                        if (viewModel.channels.isNotEmpty()) {
                            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                val availableHeight = maxHeight
                                if (isGridView) {
                                    // 2 columns, 3 rows = 6 items per screen
                                    val itemHeight = availableHeight / 3
                                    LazyVerticalGrid(
                                            columns = GridCells.Fixed(2),
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding =
                                                    PaddingValues(top = 48.dp, bottom = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        gridItems(viewModel.channels, key = { it.stream_id }) {
                                                channel ->
                                            val isPlaying =
                                                    viewModel.playingChannel?.stream_id ==
                                                            channel.stream_id
                                            VodItem(
                                                    channel = channel,
                                                    isPlaying = isPlaying,
                                                    onClick = { onChannelClick(channel) },
                                                    modifier = Modifier.height(itemHeight)
                                            )
                                        }
                                    }
                                } else {
                                    // Single item view (Manual snap feel)
                                    val lazyListState = rememberLazyListState()
                                    LazyColumn(
                                            state = lazyListState,
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.spacedBy(16.dp),
                                            contentPadding =
                                                    PaddingValues(top = 48.dp, bottom = 16.dp)
                                    ) {
                                        lazyItems(viewModel.channels, key = { it.stream_id }) {
                                                channel ->
                                            val isPlaying =
                                                    viewModel.playingChannel?.stream_id ==
                                                            channel.stream_id
                                            VodItem(
                                                    channel = channel,
                                                    isPlaying = isPlaying,
                                                    onClick = { onChannelClick(channel) },
                                                    modifier =
                                                            Modifier.fillMaxWidth()
                                                                    .fillParentMaxHeight(0.8f)
                                            )
                                        }
                                    }
                                }
                            }

                            // Toggle Button (Fixed at top-right over the list)
                            Surface(
                                    modifier =
                                            Modifier.align(Alignment.TopEnd)
                                                    .padding(8.dp)
                                                    .zIndex(1f),
                                    color =
                                            MaterialTheme.colorScheme.surfaceVariant.copy(
                                                    alpha = 0.8f
                                            ),
                                    shape = MaterialTheme.shapes.medium,
                                    shadowElevation = 4.dp
                            ) {
                                IconButton(onClick = { isGridView = !isGridView }) {
                                    Icon(
                                            imageVector =
                                                    if (isGridView) Icons.Default.ViewStream
                                                    else Icons.Default.GridView,
                                            contentDescription = "Toggle Grid",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Aucun film trouvé", color = Color.Gray)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                            modifier =
                                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
                    ) {
                        lazyItems(viewModel.channels, key = { it.stream_id }) { channel ->
                            val isPlaying = viewModel.playingChannel?.stream_id == channel.stream_id
                            ChannelItem(
                                    channel = channel,
                                    isPlaying = isPlaying,
                                    onClick = { onChannelClick(channel) },
                                    onFavoriteClick = { viewModel.channelToFavorite = channel }
                            )
                        }
                    }
                }
            }
        }
    }
}
