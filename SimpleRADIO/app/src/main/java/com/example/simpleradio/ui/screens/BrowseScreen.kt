package com.example.simpleradio.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.example.simpleradio.data.local.entities.RadioStationEntity
import com.example.simpleradio.data.model.RadioCountry
import com.example.simpleradio.data.model.RadioTag
import com.example.simpleradio.ui.components.RadioStationList
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
        onCountrySelected: (String?) -> Unit,
        onTagSelected: (String?) -> Unit,
        onBitrateSelected: (Int?) -> Unit,
        onToggleQualityExpanded: () -> Unit,
        onToggleCountryExpanded: () -> Unit,
        onToggleGenreExpanded: () -> Unit,
        onToggleViewingResults: (Boolean) -> Unit,
        onRadioSelected: (RadioStationEntity) -> Unit,
        onAddFavorite: (RadioStationEntity) -> Unit,
        onResetFilters: () -> Unit,
        onSearchClick: () -> Unit,
        onApplyFilters: () -> Unit
) {
    if (isPortrait) {
        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            if (!isViewingRadioResults && radioSearchQuery.isBlank()) {
                // CATEGORY LIST (PORTRAIT)
                LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filterSidebarItems(
                            radioCountries = radioCountries,
                            radioTags = radioTags,
                            selectedRadioCountry = selectedRadioCountry,
                            selectedRadioTag = selectedRadioTag,
                            selectedRadioBitrate = selectedRadioBitrate,
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
                // RADIO LIST (PORTRAIT)
                Column(Modifier.fillMaxSize()) {
                    Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                                onClick = { onToggleViewingResults(false) },
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
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    filterSidebarItems(
                            radioCountries = radioCountries,
                            radioTags = radioTags,
                            selectedRadioCountry = selectedRadioCountry,
                            selectedRadioTag = selectedRadioTag,
                            selectedRadioBitrate = selectedRadioBitrate,
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
