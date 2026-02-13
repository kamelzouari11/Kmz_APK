package com.example.simpleradio.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.simpleradio.data.model.RadioCountry
import com.example.simpleradio.data.model.RadioTag

/**
 * Reusable filter sidebar content used in both portrait (full screen) and landscape (left panel).
 * Uses LazyListScope extension to inject items into an existing LazyColumn.
 */
fun LazyListScope.filterSidebarItems(
        radioCountries: List<RadioCountry>,
        radioTags: List<RadioTag>,
        selectedRadioCountry: String?,
        selectedRadioTag: String?,
        selectedRadioBitrate: Int?,
        isQualityExpanded: Boolean,
        isCountryExpanded: Boolean,
        isGenreExpanded: Boolean,
        onCountrySelected: (String?) -> Unit,
        onTagSelected: (String?) -> Unit,
        onBitrateSelected: (Int?) -> Unit,
        onToggleQualityExpanded: () -> Unit,
        onToggleCountryExpanded: () -> Unit,
        onToggleGenreExpanded: () -> Unit,
        onSearchClick: () -> Unit,
        onApplyFilters: () -> Unit,
        onResetFilters: () -> Unit
) {
    // 1. Recherche
    item {
        SidebarItem(
                text = "Recherche",
                icon = Icons.Default.Search,
                isSelected = false,
                onClick = onSearchClick
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(64, 128, 192, 320).forEach { kbps ->
                    var isFocused by remember { mutableStateOf(false) }
                    Button(
                            onClick = {
                                onBitrateSelected(kbps)
                                onToggleQualityExpanded()
                            },
                            modifier =
                                    Modifier.weight(1f).padding(horizontal = 2.dp).onFocusChanged {
                                        isFocused = it.isFocused
                                    },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                            colors =
                                    ButtonDefaults.buttonColors(
                                            containerColor =
                                                    if (isFocused) Color.White
                                                    else if (selectedRadioBitrate == kbps)
                                                            MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor =
                                                    if (isFocused) Color.Black
                                                    else if (selectedRadioBitrate == kbps)
                                                            MaterialTheme.colorScheme.onPrimary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                    ) { Text("$kbps", maxLines = 1) }
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
        items(radioCountries.take(50)) { country ->
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
        items(radioTags.take(50)) { tag ->
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

    // Bouton Filtrer liste (VERT)
    item {
        var isFocused by remember { mutableStateOf(false) }
        Card(
                modifier =
                        Modifier.fillMaxWidth()
                                .onFocusChanged { isFocused = it.isFocused }
                                .clickable { onApplyFilters() },
                colors =
                        CardDefaults.cardColors(
                                containerColor =
                                        if (isFocused) Color.White else Color(0xFF4CAF50) // Vert
                        )
        ) {
            Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                        Icons.Default.FilterList,
                        null,
                        tint = if (isFocused) Color.Black else Color.White
                )
                Spacer(Modifier.width(12.dp))
                Text(
                        "Filtrer liste",
                        color = if (isFocused) Color.Black else Color.White,
                        maxLines = 1
                )
            }
        }
    }

    // Bouton Réinitialiser
    item {
        SidebarItem(
                text = "Réinitialiser",
                icon = Icons.Default.Clear,
                isSelected = false,
                onClick = onResetFilters
        )
    }

    // Spacer
    item { Spacer(modifier = Modifier.height(120.dp)) }
}
