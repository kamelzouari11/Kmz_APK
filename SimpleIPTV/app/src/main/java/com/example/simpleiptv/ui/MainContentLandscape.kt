package com.example.simpleiptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.simpleiptv.data.local.entities.ChannelEntity
import com.example.simpleiptv.ui.components.ChannelItem
import com.example.simpleiptv.ui.components.SidebarItem
import com.example.simpleiptv.ui.viewmodel.GeneratorType
import com.example.simpleiptv.ui.viewmodel.MainViewModel

@Composable
fun MainContentLandscape(viewModel: MainViewModel, onChannelClick: (ChannelEntity) -> Unit) {
    Row(modifier = Modifier.fillMaxSize()) {
        // --- Column 1: Groups (Pays / Accueil) ---
        // Width reduced from 0.18f to 0.10f
        LazyColumn(
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
                        onClick = { viewModel.selectedCountryFilter = "ALL" }
                )
            }
            item {
                Text(
                        text = "Pays",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
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
        // Width increased from 0.25f to 0.33f (gained from Column 1)
        LazyColumn(
                modifier =
                        Modifier.weight(0.30f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.02f))
                                .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (viewModel.selectedCountryFilter == "ALL") {
                item {
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
                                modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                    Icons.Default.Add,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                items(viewModel.favoriteLists, key = { it.id }) { list ->
                    val isSelected = viewModel.selectedFavoriteListId == list.id
                    SidebarItem(
                            text = list.name,
                            icon = Icons.Default.Star,
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
                                if (viewModel.selectedCountryFilter == "ALL")
                                        "Toutes les catégories"
                                else "Catégories ${viewModel.selectedCountryFilter}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
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
        LazyColumn(
                modifier = Modifier.weight(0.55f).fillMaxHeight().padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(viewModel.channels, key = { it.stream_id }) { channel ->
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
