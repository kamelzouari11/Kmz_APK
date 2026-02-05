package com.example.simpleradio.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.example.simpleradio.data.local.entities.RadioStationEntity
import com.example.simpleradio.data.model.RadioCountry
import com.example.simpleradio.data.model.RadioTag
import com.example.simpleradio.ui.components.MainItem
import com.example.simpleradio.ui.components.SidebarItem

@Composable
fun BrowseScreen(
        isPortrait: Boolean,
        radioCountries: List<RadioCountry>,
        radioTags: List<RadioTag>,
        currentRadioList: List<RadioStationEntity>,
        selectedRadioCountry: String?,
        selectedRadioTag: String?,
        selectedRadioBitrate: Int?,
        showRecentRadiosOnly: Boolean,
        radioSearchQuery: String,
        sidebarCountrySearch: String,
        sidebarTagSearch: String,
        isQualityExpanded: Boolean,
        isCountryExpanded: Boolean,
        isGenreExpanded: Boolean,
        isViewingRadioResults: Boolean,
        playingRadio: RadioStationEntity?,
        listFocusRequester: FocusRequester,
        resultsListState: LazyListState,
        onCountrySelected: (String?) -> Unit,
        onTagSelected: (String?) -> Unit,
        onBitrateSelected: (Int?) -> Unit,
        onSidebarCountrySearchChanged: (String) -> Unit,
        onSidebarTagSearchChanged: (String) -> Unit,
        onToggleQualityExpanded: () -> Unit,
        onToggleCountryExpanded: () -> Unit,
        onToggleGenreExpanded: () -> Unit,
        onToggleRecentOnly: (Boolean) -> Unit,
        onToggleViewingResults: (Boolean) -> Unit,
        onRadioSelected: (RadioStationEntity) -> Unit,
        onAddFavorite: (RadioStationEntity) -> Unit,
        onResetFilters: () -> Unit
) {
    if (isPortrait) {
        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            if (!isViewingRadioResults && radioSearchQuery.isBlank()) {
                // CATEGORY LIST (PORTRAIT)
                LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Récents
                    item {
                        SidebarItem(
                                text = "Récents",
                                icon = Icons.Default.History,
                                isSelected = showRecentRadiosOnly && isViewingRadioResults,
                                onClick = { onToggleRecentOnly(true) }
                        )
                    }

                    // 2. Qualité
                    item {
                        SidebarItem(
                                text =
                                        "Qualité : ${if (selectedRadioBitrate != null) "$selectedRadioBitrate kbps" else "Tous"}",
                                icon = Icons.Default.SignalCellularAlt,
                                isSelected = selectedRadioBitrate != null,
                                onClick = onToggleQualityExpanded
                        )
                    }
                    if (isQualityExpanded) {
                        item {
                            SidebarItem(
                                    text = "Toutes qualités",
                                    isSelected = selectedRadioBitrate == null,
                                    onClick = {
                                        onBitrateSelected(null)
                                        onToggleQualityExpanded()
                                    }
                            )
                        }
                        item {
                            Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                listOf(64, 128, 192, 320).forEach { kbps ->
                                    Button(
                                            onClick = {
                                                onBitrateSelected(kbps)
                                                onToggleQualityExpanded()
                                            },
                                            colors =
                                                    ButtonDefaults.buttonColors(
                                                            containerColor =
                                                                    if (selectedRadioBitrate == kbps
                                                                    )
                                                                            MaterialTheme
                                                                                    .colorScheme
                                                                                    .primary
                                                                    else
                                                                            MaterialTheme
                                                                                    .colorScheme
                                                                                    .surfaceVariant,
                                                            contentColor =
                                                                    if (selectedRadioBitrate == kbps
                                                                    )
                                                                            MaterialTheme
                                                                                    .colorScheme
                                                                                    .onPrimary
                                                                    else
                                                                            MaterialTheme
                                                                                    .colorScheme
                                                                                    .onSurfaceVariant
                                                    )
                                    ) { Text("$kbps") }
                                }
                            }
                        }
                    }

                    // 3. Pays
                    item {
                        SidebarItem(
                                text =
                                        "Pays : ${radioCountries.find { it.iso_3166_1 == selectedRadioCountry }?.name ?: "Tous"}",
                                icon = Icons.Default.Public,
                                isSelected = selectedRadioCountry != null,
                                onClick = onToggleCountryExpanded
                        )
                    }
                    if (isCountryExpanded) {
                        item {
                            SidebarItem(
                                    text = "Tous les pays",
                                    isSelected = selectedRadioCountry == null,
                                    onClick = {
                                        onCountrySelected(null)
                                        onToggleCountryExpanded()
                                    }
                            )
                        }
                        items(
                                radioCountries
                                        .filter {
                                            it.name.contains(
                                                    sidebarCountrySearch,
                                                    ignoreCase = true
                                            )
                                        }
                                        .take(50)
                        ) { country ->
                            SidebarItem(
                                    text = country.name,
                                    isSelected = selectedRadioCountry == country.iso_3166_1,
                                    onClick = {
                                        onCountrySelected(country.iso_3166_1)
                                        onToggleCountryExpanded()
                                    }
                            )
                        }
                    }

                    // 4. Genre
                    item {
                        SidebarItem(
                                text = "Genre : ${selectedRadioTag ?: "Tous"}",
                                icon = Icons.AutoMirrored.Filled.Label,
                                isSelected = selectedRadioTag != null,
                                onClick = onToggleGenreExpanded
                        )
                    }
                    if (isGenreExpanded) {
                        item {
                            SidebarItem(
                                    text = "Tous les genres",
                                    isSelected = selectedRadioTag == null,
                                    onClick = {
                                        onTagSelected(null)
                                        onToggleGenreExpanded()
                                    }
                            )
                        }
                        items(
                                radioTags
                                        .filter {
                                            it.name.contains(sidebarTagSearch, ignoreCase = true)
                                        }
                                        .take(50)
                        ) { tag ->
                            SidebarItem(
                                    text = tag.name,
                                    isSelected = selectedRadioTag == tag.name,
                                    onClick = {
                                        onTagSelected(tag.name)
                                        onToggleGenreExpanded()
                                    }
                            )
                        }
                    }

                    // Spacer
                    item { Spacer(modifier = Modifier.height(120.dp)) }
                }
            } else {
                // RADIO LIST (PORTRAIT)
                Column(Modifier.fillMaxSize()) {
                    Button(
                            onClick = { onToggleViewingResults(false) },
                            modifier = Modifier.fillMaxWidth().padding(8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Retour aux catégories")
                    }
                    LazyColumn(
                            state = resultsListState,
                            modifier =
                                    Modifier.fillMaxSize()
                                            .padding(8.dp)
                                            .focusRequester(listFocusRequester),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(currentRadioList) { radio ->
                            MainItem(
                                    title = radio.name,
                                    subtitle =
                                            "${radio.country ?: ""} | ${radio.bitrate ?: "?"} kbps",
                                    iconUrl = radio.favicon,
                                    isPlaying = playingRadio?.stationuuid == radio.stationuuid,
                                    onClick = { onRadioSelected(radio) },
                                    onAddFavorite = { onAddFavorite(radio) }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(120.dp)) }
                    }
                }
            }
        }
    } else {
        // LANDSCAPE LAYOUT (Row: Sidebar + List)
        Row(modifier = Modifier.fillMaxSize()) {
            // LEFT SIDEBAR
            Column(modifier = Modifier.weight(0.3f).fillMaxHeight().padding(8.dp)) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Récents
                    item {
                        SidebarItem(
                                text = "Récents",
                                icon = Icons.Default.History,
                                isSelected = showRecentRadiosOnly,
                                onClick = { onToggleRecentOnly(true) }
                        )
                    }
                    // Qualité
                    item {
                        SidebarItem(
                                text =
                                        "Qualité : ${if (selectedRadioBitrate != null) "$selectedRadioBitrate kbps" else "Tous"}",
                                icon = Icons.Default.SignalCellularAlt,
                                isSelected = selectedRadioBitrate != null,
                                onClick = onToggleQualityExpanded
                        )
                    }
                    if (isQualityExpanded) {
                        item {
                            SidebarItem(
                                    text = "Toutes qualités",
                                    isSelected = selectedRadioBitrate == null,
                                    onClick = {
                                        onBitrateSelected(null)
                                        onToggleQualityExpanded()
                                    }
                            )
                        }
                        item {
                            Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                listOf(64, 128, 192, 320).forEach { kbps ->
                                    Button(
                                            onClick = {
                                                onBitrateSelected(kbps)
                                                onToggleQualityExpanded()
                                            },
                                            colors =
                                                    ButtonDefaults.buttonColors(
                                                            containerColor =
                                                                    if (selectedRadioBitrate == kbps
                                                                    )
                                                                            MaterialTheme
                                                                                    .colorScheme
                                                                                    .primary
                                                                    else
                                                                            MaterialTheme
                                                                                    .colorScheme
                                                                                    .surfaceVariant,
                                                            contentColor =
                                                                    if (selectedRadioBitrate == kbps
                                                                    )
                                                                            MaterialTheme
                                                                                    .colorScheme
                                                                                    .onPrimary
                                                                    else
                                                                            MaterialTheme
                                                                                    .colorScheme
                                                                                    .onSurfaceVariant
                                                    )
                                    ) { Text("$kbps") }
                                }
                            }
                        }
                    }
                    // Pays
                    item {
                        SidebarItem(
                                text =
                                        "Pays : ${radioCountries.find { it.iso_3166_1 == selectedRadioCountry }?.name ?: "Tous"}",
                                icon = Icons.Default.Public,
                                isSelected = selectedRadioCountry != null,
                                onClick = onToggleCountryExpanded
                        )
                    }
                    if (isCountryExpanded) {
                        item {
                            SidebarItem(
                                    text = "Tous les pays",
                                    isSelected = selectedRadioCountry == null,
                                    onClick = {
                                        onCountrySelected(null)
                                        onToggleCountryExpanded()
                                    }
                            )
                        }
                        items(
                                radioCountries
                                        .filter {
                                            it.name.contains(
                                                    sidebarCountrySearch,
                                                    ignoreCase = true
                                            )
                                        }
                                        .take(50)
                        ) { country ->
                            SidebarItem(
                                    text = country.name,
                                    isSelected = selectedRadioCountry == country.iso_3166_1,
                                    onClick = {
                                        onCountrySelected(country.iso_3166_1)
                                        onToggleCountryExpanded()
                                    }
                            )
                        }
                    }
                    // Genre
                    item {
                        SidebarItem(
                                text = "Genre : ${selectedRadioTag ?: "Tous"}",
                                icon = Icons.AutoMirrored.Filled.Label,
                                isSelected = selectedRadioTag != null,
                                onClick = onToggleGenreExpanded
                        )
                    }
                    if (isGenreExpanded) {
                        item {
                            SidebarItem(
                                    text = "Tous les genres",
                                    isSelected = selectedRadioTag == null,
                                    onClick = {
                                        onTagSelected(null)
                                        onToggleGenreExpanded()
                                    }
                            )
                        }
                        items(
                                radioTags
                                        .filter {
                                            it.name.contains(sidebarTagSearch, ignoreCase = true)
                                        }
                                        .take(50)
                        ) { tag ->
                            SidebarItem(
                                    text = tag.name,
                                    isSelected = selectedRadioTag == tag.name,
                                    onClick = {
                                        onTagSelected(tag.name)
                                        onToggleGenreExpanded()
                                    }
                            )
                        }
                    }

                    item { Spacer(Modifier.height(120.dp)) }
                }
            }

            // RIGHT LIST
            Column(modifier = Modifier.weight(0.7f).fillMaxHeight().padding(8.dp)) {
                Button(
                        onClick = onResetFilters,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        colors =
                                ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                )
                ) {
                    Icon(Icons.Default.Clear, null)
                    Spacer(Modifier.width(8.dp))
                    Text("RÉINITIALISER")
                }
                LazyColumn(
                        state = resultsListState,
                        modifier = Modifier.fillMaxSize().focusRequester(listFocusRequester),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(currentRadioList) { radio ->
                        MainItem(
                                title = radio.name,
                                subtitle = "${radio.country ?: ""} | ${radio.bitrate ?: "?"} kbps",
                                iconUrl = radio.favicon,
                                isPlaying = playingRadio?.stationuuid == radio.stationuuid,
                                onClick = { onRadioSelected(radio) },
                                onAddFavorite = { onAddFavorite(radio) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(120.dp)) }
                }
            }
        }
    }
}
