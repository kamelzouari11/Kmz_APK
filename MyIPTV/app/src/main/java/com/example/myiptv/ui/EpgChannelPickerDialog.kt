package com.example.myiptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.myiptv.data.EpgCountrySelection
import com.example.myiptv.data.SavedChannel
import com.example.myiptv.ui.theme.MyIptvPalette

private enum class EpgPickerPage { COUNTRIES, CATEGORIES, CHANNELS }

@Composable
fun EpgChannelPickerDialog(
    selection: EpgCountrySelection,
    loading: Boolean,
    status: String,
    onRefresh: () -> Unit,
    onCancelLoading: () -> Unit,
    onSelect: (SavedChannel) -> Unit,
    onDismiss: () -> Unit,
) {
    var pageName by rememberSaveable { mutableStateOf(EpgPickerPage.COUNTRIES.name) }
    var countryCode by rememberSaveable { mutableStateOf<String?>(null) }
    var categoryKey by rememberSaveable { mutableStateOf<String?>(null) }
    var channelQuery by rememberSaveable { mutableStateOf("") }
    var expandedEpgId by rememberSaveable { mutableStateOf<String?>(null) }
    val page = EpgPickerPage.valueOf(pageName)
    val country = selection.orderedOptions.firstOrNull { it.code == countryCode }
    val category = country?.categories?.firstOrNull { it.key == categoryKey }

    LaunchedEffect(loading, selection) {
        if (!loading) {
            if (country == null && page != EpgPickerPage.CHANNELS) pageName = EpgPickerPage.COUNTRIES.name
        }
    }

    val goBack = {
        when (page) {
            EpgPickerPage.CHANNELS -> pageName = if (category == null) {
                EpgPickerPage.COUNTRIES.name
            } else {
                EpgPickerPage.CATEGORIES.name
            }
            EpgPickerPage.CATEGORIES -> pageName = EpgPickerPage.COUNTRIES.name
            EpgPickerPage.COUNTRIES -> onDismiss()
        }
    }
    BackHandler(onBack = goBack)

    Dialog(
        onDismissRequest = goBack,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(color = MyIptvPalette.Background, modifier = Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MenuButton(text = "Retour", onClick = goBack)
                    Column(Modifier.weight(1f)) {
                        Text("Choisir une chaîne EPG", style = MaterialTheme.typography.titleLarge)
                        Text(
                            when (page) {
                                EpgPickerPage.COUNTRIES -> "Pays activés"
                                EpgPickerPage.CATEGORIES -> country?.let { countryLabel(it.code) }.orEmpty()
                                EpgPickerPage.CHANNELS -> category?.name ?: "Toutes les chaînes EPG"
                            },
                            color = MyIptvPalette.TextSecondary,
                        )
                    }
                }
                if (loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(status, color = MyIptvPalette.TextSecondary)
                    MenuButton(text = "Annuler le chargement", onClick = onCancelLoading)
                } else {
                    MenuButton(text = "Actualiser les chaînes avec programmes", onClick = onRefresh)
                    if (status.isNotBlank()) Text(status, color = MyIptvPalette.Warning)
                    if (!selection.hasEpgChannels) {
                        Text(
                            "Aucun programme actuel ou à venir dans les pays et catégories activés. Actualisez l’EPG pour réessayer.",
                            color = MyIptvPalette.TextSecondary,
                        )
                    }
                }
                if (!loading && page == EpgPickerPage.CHANNELS) {
                    TvTextField(
                        value = channelQuery,
                        onValueChange = { channelQuery = it },
                        modifier = Modifier.fillMaxWidth().tvFocusBorder(),
                        label = { Text("Filtrer les chaînes") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MyIptvPalette.White,
                            unfocusedTextColor = MyIptvPalette.White,
                            focusedContainerColor = MyIptvPalette.ActiveSurface,
                            unfocusedContainerColor = MyIptvPalette.CardHover,
                            cursorColor = MyIptvPalette.Primary,
                            focusedBorderColor = if (isTvLandscape()) {
                                MyIptvPalette.Negative
                            } else {
                                MyIptvPalette.Primary
                            },
                            unfocusedBorderColor = MyIptvPalette.Info,
                            focusedLabelColor = if (isTvLandscape()) {
                                MyIptvPalette.Negative
                            } else {
                                MyIptvPalette.Primary
                            },
                            unfocusedLabelColor = MyIptvPalette.Info,
                        ),
                    )
                }
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    if (!loading) when (page) {
                        EpgPickerPage.COUNTRIES -> {
                            item {
                                PickerRow(
                                    title = "Toutes les chaînes EPG",
                                    subtitle = "${selection.activeOptions.sumOf { it.epgChannels }} chaînes EPG",
                                    subtitleColor = MyIptvPalette.Positive,
                                    onClick = {
                                        countryCode = null
                                        categoryKey = null
                                        channelQuery = ""
                                        expandedEpgId = null
                                        pageName = EpgPickerPage.CHANNELS.name
                                    },
                                )
                            }
                            items(selection.activeOptions, key = { it.code }) { option ->
                                PickerRow(
                                    title = countryLabel(option.code),
                                    subtitle = "${option.epgChannels} chaînes EPG",
                                    subtitleColor = MyIptvPalette.Positive,
                                    onClick = {
                                        countryCode = option.code
                                        pageName = EpgPickerPage.CATEGORIES.name
                                    },
                                )
                            }
                        }
                        EpgPickerPage.CATEGORIES -> {
                            val categories = country?.let(selection::activeCategories).orEmpty()
                            items(categories, key = { it.key }) { option ->
                                PickerRow(
                                    title = option.name,
                                    subtitle = "${option.epgChannels} chaînes EPG",
                                    subtitleColor = MyIptvPalette.Positive,
                                    onClick = {
                                        categoryKey = option.key
                                        channelQuery = ""
                                        expandedEpgId = null
                                        pageName = EpgPickerPage.CHANNELS.name
                                    },
                                )
                            }
                        }
                        EpgPickerPage.CHANNELS -> {
                            val terms = channelQuery.trim().split(Regex("\\s+"))
                                .filter(String::isNotBlank)
                            val availableChannels = category?.channels ?: selection.activeOptions
                                .flatMap { countryOption -> countryOption.categories.flatMap { it.channels } }
                            val channelGroups = availableChannels
                                .filter { channel ->
                                    terms.all { channel.name.contains(it, ignoreCase = true) }
                                }
                                .groupBy { it.epgChannelId?.trim() }
                                .values
                                .filter { it.firstOrNull()?.epgChannelId != null }
                            channelGroups.forEach { variants ->
                                val epgId = variants.first().epgChannelId!!.trim()
                                item(key = "channel:$epgId") {
                                    PickerRow(
                                        title = variants.first().name,
                                        subtitle = if (variants.size == 1) "1 flux" else {
                                            if (expandedEpgId == epgId) "Masquer les variantes" else
                                                "Afficher les ${variants.size} variantes"
                                        },
                                        onClick = {
                                            if (variants.size == 1) onSelect(variants.first())
                                            else expandedEpgId = if (expandedEpgId == epgId) null else epgId
                                        },
                                    )
                                }
                                if (variants.size > 1 && expandedEpgId == epgId) {
                                    items(variants, key = { "variant:${it.profileId}:${it.streamId}" }) { variant ->
                                        Column(Modifier.padding(start = 20.dp)) {
                                            PickerRow(
                                                title = variant.name,
                                                subtitle = "Choisir ce flux",
                                                onClick = { onSelect(variant) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    title: String,
    subtitle: String,
    subtitleColor: Color = MyIptvPalette.TextSecondary,
    onClick: () -> Unit,
) {
    TvListItem(selected = false, onClick = onClick) { _, color ->
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp)) {
            Text(title, color = color)
            Text(
                subtitle,
                color = subtitleColor,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
