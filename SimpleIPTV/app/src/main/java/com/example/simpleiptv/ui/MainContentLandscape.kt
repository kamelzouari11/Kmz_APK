package com.example.simpleiptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.example.simpleiptv.data.local.entities.ChannelEntity
import com.example.simpleiptv.ui.components.ChannelItem
import com.example.simpleiptv.ui.components.GlobalChannelItem
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
    val scope = rememberCoroutineScope()
    val accueilFocusRequester = remember { FocusRequester() }
    
    Row(modifier = Modifier.fillMaxSize()) {
        // --- Column 1: Groups (Pays / Accueil) ---
        LazyColumn(
                state = countryScrollState,
                modifier =
                        Modifier.weight(0.15f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.05f))
                                .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                SidebarItem(
                        text = "Accueil",
                        icon = Icons.Default.Home,
                        isSelected = viewModel.selectedCountryFilter == "ALL",
                        onClick = { viewModel.selectedCountryFilter = "ALL" },
                        modifier = Modifier
                            .focusRequester(accueilFocusRequester)
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
            items(viewModel.countryFilters.filter { it != "ALL" }, key = { it }) { country ->
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

        // --- Column 2: Categories ---
        LazyColumn(
                state = categoryScrollState,
                modifier =
                        Modifier.weight(0.30f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.02f))
                                .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (viewModel.selectedCountryFilter == "ALL") {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SidebarItem(
                                text = "Récents",
                                icon = Icons.Default.History,
                                isSelected = viewModel.lastGeneratorType == GeneratorType.RECENTS,
                                onClick = {
                                    viewModel.selectedCategoryId = null
                                    viewModel.selectedFavoriteListId = -1
                                    viewModel.searchQuery = ""
                                    viewModel.lastGeneratorType = GeneratorType.RECENTS
                                    viewModel.refreshChannels()
                                },
                                onDelete = { viewModel.clearRecents() },
                                modifier = Modifier.weight(1f)
                        )
                        // Bouton scope recents
                        if (viewModel.lastGeneratorType == GeneratorType.RECENTS) {
                            IconButton(
                                onClick = { viewModel.toggleRecentScope() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (viewModel.recentScope == SearchScope.ALL_PROFILES) Icons.Default.Groups else Icons.Default.Person,
                                    contentDescription = if (viewModel.recentScope == SearchScope.ALL_PROFILES) "Tous profils" else "Profil actif",
                                    tint = if (viewModel.recentScope == SearchScope.ALL_PROFILES) Color(0xFF4CAF50) else Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
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
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                        )
                        // Bouton scope favoris
                        if (viewModel.lastGeneratorType == GeneratorType.FAVORITES) {
                            IconButton(
                                onClick = { viewModel.toggleFavoriteScope() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (viewModel.favoriteScope == SearchScope.ALL_PROFILES) Icons.Default.Groups else Icons.Default.Person,
                                    contentDescription = if (viewModel.favoriteScope == SearchScope.ALL_PROFILES) "Tous profils" else "Profil actif",
                                    tint = if (viewModel.favoriteScope == SearchScope.ALL_PROFILES) Color(0xFF4CAF50) else Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.toggleFavoriteListScope() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (viewModel.favoriteListScope == com.example.simpleiptv.ui.viewmodel.FavoriteListScope.ALL_LISTS) Icons.Default.Star else Icons.Default.Person,
                                    contentDescription = if (viewModel.favoriteListScope == com.example.simpleiptv.ui.viewmodel.FavoriteListScope.ALL_LISTS) "Toutes listes" else "Liste profil",
                                    tint = if (viewModel.favoriteListScope == com.example.simpleiptv.ui.viewmodel.FavoriteListScope.ALL_LISTS) Color(0xFF4CAF50) else Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        var isAddFocused by remember { mutableStateOf(false) }
                        IconButton(
                                onClick = { viewModel.showAddListDialog = true },
                                modifier =
                                        Modifier.size(40.dp).onFocusChanged {
                                            isAddFocused = it.isFocused
                                        }
                        ) {
                            Icon(
                                    Icons.Default.Add,
                                    null,
                                    tint =
                                            if (isAddFocused) Color.Black
                                            else MaterialTheme.colorScheme.primary,
                                    modifier =
                                            Modifier.size(24.dp)
                                                    .background(
                                                            if (isAddFocused) Color.White
                                                            else Color.Transparent,
                                                            MaterialTheme.shapes.small
                                                    )
                            )
                        }
                    }
                }
                items(viewModel.filteredFavoriteLists, key = { it.id }) { list ->
                    val isSelected = viewModel.selectedFavoriteListId == list.id
                    SidebarItem(
                            text = list.name,
                            icon = if (list.profileId == null) Icons.Default.Star else Icons.Default.Star,
                            isSelected = isSelected,
                            onClick = {
                                viewModel.selectedFavoriteListId = list.id
                                viewModel.selectedCategoryId = null
                                viewModel.searchQuery = ""
                                viewModel.lastGeneratorType = GeneratorType.FAVORITES
                                viewModel.refreshChannels()
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
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp)
                )
            }
            items(viewModel.filteredCategories, key = { it.category_id }) { category ->
                val isSelected = viewModel.selectedCategoryId == category.category_id
                SidebarItem(
                        text = category.category_name,
                        icon = null,
                        isSelected = isSelected,
                        onClick = {
                            viewModel.selectedCategoryId = category.category_id
                            viewModel.selectedFavoriteListId = -1
                            viewModel.searchQuery = ""
                            viewModel.lastGeneratorType = GeneratorType.CATEGORY
                            viewModel.refreshChannels()
                        }
                )
            }
        }

        VerticalDivider(color = Color.Gray.copy(alpha = 0.1f), thickness = 1.dp)

        // --- Column 3: Channels ---
        val isVod = viewModel.currentMediaMode == MediaMode.VOD
        val isGlobalSearch = viewModel.lastGeneratorType == GeneratorType.GLOBAL_SEARCH

        if (isGlobalSearch) {
            // Affichage des résultats de la recherche globale multi-profils
            LazyColumn(
                    state = channelScrollState,
                    modifier = Modifier.weight(0.55f).fillMaxHeight().padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (viewModel.globalSearchResults.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (viewModel.searchQuery.isBlank()) "Entrez plusieurs mots pour une recherche globale"
                                       else "Aucune chaîne trouvée pour \u00ab ${viewModel.searchQuery} \u00bb",
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    items(viewModel.globalSearchResults, key = { "${it.profileId}_${it.stream_id}" }) { item ->
                        val isPlaying = viewModel.playingChannel?.stream_id == item.stream_id &&
                                        viewModel.activeProfileId == item.profileId
                        val isFav = viewModel.allFavoriteIds.contains("${item.profileId}_${item.stream_id}")
                        GlobalChannelItem(
                            item = item,
                            isPlaying = isPlaying,
                            isFavorite = isFav,
                            onClick = { onChannelClick(item.toChannelEntity()) },
                            onFavoriteClick = { viewModel.initFavoriteAction(item.toChannelEntity()) }
                        )
                    }
                }
            }
        } else if (isVod) {
            Box(modifier = Modifier.weight(0.55f).fillMaxHeight().padding(4.dp)) {
                if (viewModel.channels.isNotEmpty()) {
                    LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        gridItems(viewModel.channels, key = { it.stream_id }) { channel ->
                            val isPlaying = viewModel.playingChannel?.stream_id == channel.stream_id
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
        } else {
            LazyColumn(
                    state = channelScrollState,
                    modifier = Modifier.weight(0.55f).fillMaxHeight().padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(viewModel.channels, key = { it.stream_id }) { channel ->
                    val isPlaying = viewModel.playingChannel?.stream_id == channel.stream_id
                    val isFav = viewModel.allFavoriteIds.contains("${channel.profileId}_${channel.stream_id}")
                    val channelProfile = viewModel.profiles.find { it.id == channel.profileId }
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
    }
}
