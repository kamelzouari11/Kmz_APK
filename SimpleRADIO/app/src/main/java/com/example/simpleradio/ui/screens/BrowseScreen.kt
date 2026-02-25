package com.example.simpleradio.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.simpleradio.data.local.entities.RadioFavoriteListEntity
import com.example.simpleradio.data.local.entities.RadioStationEntity
import com.example.simpleradio.data.model.RadioCountry
import com.example.simpleradio.data.model.RadioTag
import com.example.simpleradio.ui.components.RadioStationList
import com.example.simpleradio.ui.components.SidebarItem
import com.example.simpleradio.ui.components.filterSidebarItems

@Composable
fun BrowseScreen(
        isPortrait: Boolean,
        radioCountries: List<RadioCountry>,
        radioTags: List<RadioTag>,
        currentRadioList: List<RadioStationEntity>,
        selectedRadioCountry: String?,
        selectedRadioTag: String?,
        selectedRadioBitrate: Int?,
        radioSearchQuery: String,
        isQualityExpanded: Boolean,
        isCountryExpanded: Boolean,
        isGenreExpanded: Boolean,
        isViewingRadioResults: Boolean,
        playingRadio: RadioStationEntity?,
        listFocusRequester: FocusRequester,
        resultsListState: LazyListState,
        // Favoris
        radioFavoriteLists: List<RadioFavoriteListEntity>,
        selectedRadioFavoriteListId: Int?,
        onFavoriteListSelected: (Int) -> Unit,
        onDeleteFavoriteList: (RadioFavoriteListEntity) -> Unit,
        onCreateFavoriteList: () -> Unit,
        // Callbacks
        onCountrySelected: (String?) -> Unit,
        onTagSelected: (String?) -> Unit,
        onBitrateSelected: (Int?) -> Unit,
        onToggleQualityExpanded: () -> Unit,
        onToggleCountryExpanded: () -> Unit,
        onToggleGenreExpanded: () -> Unit,
        onToggleViewingResults: (Boolean) -> Unit,
        onRetourAuxCategories: () -> Unit,
        onRadioSelected: (RadioStationEntity) -> Unit,
        onAddFavorite: (RadioStationEntity) -> Unit,
        onResetFilters: () -> Unit,
        onSearchClick: () -> Unit,
        onApplyFilters: () -> Unit
) {
    if (isPortrait) {
        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            // En portrait : on montre les catégories si on ne visualise pas les résultats
            val showCategories =
                    !isViewingRadioResults &&
                            radioSearchQuery.isBlank() &&
                            selectedRadioFavoriteListId == null
            if (showCategories) {
                // ÉCRAN A : CATÉGORIES + FILTRES (PORTRAIT)
                LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // ===== SECTION FAVORIS =====
                    item { FavoritesSectionHeader(onCreateFavoriteList = onCreateFavoriteList) }
                    if (radioFavoriteLists.isEmpty()) {
                        item {
                            Text(
                                    "Aucune liste de favoris. Créez-en une avec +",
                                    color = Color.White.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        radioFavoriteLists.forEach { list ->
                            item(key = list.id) {
                                SidebarItem(
                                        text = list.name,
                                        icon = Icons.Default.Star,
                                        isSelected = selectedRadioFavoriteListId == list.id,
                                        onClick = { onFavoriteListSelected(list.id) },
                                        onDelete = { onDeleteFavoriteList(list) }
                                )
                            }
                        }
                    }

                    item { Divider(color = Color.White.copy(alpha = 0.15f), thickness = 1.dp) }

                    // ===== SECTION FILTRES / RECHERCHE =====
                    filterSidebarItems(
                            radioCountries = radioCountries,
                            radioTags = radioTags,
                            selectedRadioCountry = selectedRadioCountry,
                            selectedRadioTag = selectedRadioTag,
                            selectedRadioBitrate = selectedRadioBitrate,
                            radioSearchQuery = radioSearchQuery,
                            isQualityExpanded = isQualityExpanded,
                            isCountryExpanded = isCountryExpanded,
                            isGenreExpanded = isGenreExpanded,
                            onCountrySelected = onCountrySelected,
                            onTagSelected = onTagSelected,
                            onBitrateSelected = onBitrateSelected,
                            onToggleQualityExpanded = onToggleQualityExpanded,
                            onToggleCountryExpanded = onToggleCountryExpanded,
                            onToggleGenreExpanded = onToggleGenreExpanded,
                            onSearchClick = onSearchClick,
                            onApplyFilters = onApplyFilters,
                            onResetFilters = onResetFilters
                    )
                }
            } else {
                // ÉCRAN B : LISTE DE RADIOS (PORTRAIT)
                Column(Modifier.fillMaxSize()) {
                    Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                                // Toujours revenir à l'écran A quelles que soient les conditions
                                onClick = onRetourAuxCategories,
                                modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Retour aux catégories")
                        }
                    }
                    RadioStationList(
                            currentRadioList = currentRadioList,
                            playingRadio = playingRadio,
                            resultsListState = resultsListState,
                            listFocusRequester = listFocusRequester,
                            onRadioSelected = onRadioSelected,
                            onAddFavorite = onAddFavorite,
                            modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    } else {
        // LANDSCAPE LAYOUT (Row: Sidebar + List)
        Row(modifier = Modifier.fillMaxSize()) {
            // LEFT SIDEBAR
            Column(modifier = Modifier.weight(0.3f).fillMaxHeight().padding(8.dp)) {
                // Bouton "Retour aux catégories" en landscape (visible quand on est sur une liste)
                val showBackButton =
                        isViewingRadioResults ||
                                selectedRadioFavoriteListId != null ||
                                radioSearchQuery.isNotBlank()
                if (showBackButton) {
                    var isBackFocused by remember { mutableStateOf(false) }
                    Card(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .padding(bottom = 8.dp)
                                            .onFocusChanged { isBackFocused = it.isFocused }
                                            .clickable { onRetourAuxCategories() },
                            colors =
                                    CardDefaults.cardColors(
                                            containerColor =
                                                    if (isBackFocused) Color.White
                                                    else
                                                            MaterialTheme.colorScheme.primary.copy(
                                                                    alpha = 0.3f
                                                            )
                                    )
                    ) {
                        Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    null,
                                    tint = if (isBackFocused) Color.Black else Color.White
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                    "Retour aux catégories",
                                    color = if (isBackFocused) Color.Black else Color.White,
                                    maxLines = 1
                            )
                        }
                    }
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // ===== SECTION FAVORIS =====
                    item { FavoritesSectionHeader(onCreateFavoriteList = onCreateFavoriteList) }
                    if (radioFavoriteLists.isEmpty()) {
                        item {
                            Text(
                                    "Aucune liste. Créez avec +",
                                    color = Color.White.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        radioFavoriteLists.forEach { list ->
                            item(key = list.id) {
                                SidebarItem(
                                        text = list.name,
                                        icon = Icons.Default.Star,
                                        isSelected = selectedRadioFavoriteListId == list.id,
                                        onClick = { onFavoriteListSelected(list.id) },
                                        onDelete = { onDeleteFavoriteList(list) }
                                )
                            }
                        }
                    }

                    item { Divider(color = Color.White.copy(alpha = 0.15f), thickness = 1.dp) }

                    // ===== SECTION FILTRES =====
                    filterSidebarItems(
                            radioCountries = radioCountries,
                            radioTags = radioTags,
                            selectedRadioCountry = selectedRadioCountry,
                            selectedRadioTag = selectedRadioTag,
                            selectedRadioBitrate = selectedRadioBitrate,
                            radioSearchQuery = radioSearchQuery,
                            isQualityExpanded = isQualityExpanded,
                            isCountryExpanded = isCountryExpanded,
                            isGenreExpanded = isGenreExpanded,
                            onCountrySelected = onCountrySelected,
                            onTagSelected = onTagSelected,
                            onBitrateSelected = onBitrateSelected,
                            onToggleQualityExpanded = onToggleQualityExpanded,
                            onToggleCountryExpanded = onToggleCountryExpanded,
                            onToggleGenreExpanded = onToggleGenreExpanded,
                            onSearchClick = onSearchClick,
                            onApplyFilters = onApplyFilters,
                            onResetFilters = onResetFilters
                    )
                }
            }

            // RIGHT LIST
            Column(modifier = Modifier.weight(0.7f).fillMaxHeight().padding(8.dp)) {
                RadioStationList(
                        currentRadioList = currentRadioList,
                        playingRadio = playingRadio,
                        resultsListState = resultsListState,
                        listFocusRequester = listFocusRequester,
                        onRadioSelected = onRadioSelected,
                        onAddFavorite = onAddFavorite
                )
            }
        }
    }
}

/** En-tête de la section Favoris avec le bouton + pour créer une nouvelle liste. */
@Composable
fun FavoritesSectionHeader(onCreateFavoriteList: () -> Unit) {
    Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                    Icons.Default.Star,
                    null,
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                    "MES FAVORIS",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFFFD700)
            )
        }
        // Bouton + pour créer une nouvelle liste
        var isFocused by remember { mutableStateOf(false) }
        IconButton(
                onClick = onCreateFavoriteList,
                modifier = Modifier.size(36.dp).onFocusChanged { isFocused = it.isFocused }
        ) {
            Icon(
                    Icons.Default.AddCircle,
                    "Nouvelle liste de favoris",
                    tint = if (isFocused) Color.White else Color(0xFFFFD700),
                    modifier = Modifier.size(28.dp)
            )
        }
    }
}
