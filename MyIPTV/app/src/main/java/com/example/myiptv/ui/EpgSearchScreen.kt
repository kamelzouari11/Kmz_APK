package com.example.myiptv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.myiptv.data.EpgGuidePage
import com.example.myiptv.data.EpgSearch
import com.example.myiptv.data.EpgSearchResult
import com.example.myiptv.data.SavedChannel
import com.example.myiptv.ui.theme.MyIptvPalette
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class EpgPage { GUIDE, SEARCH }

@Composable
fun EpgSearchScreen(
    onLoadCountries: () -> Unit,
    onLoadGuideChannels: (Boolean) -> Unit,
    onApplyFilters: (Set<String>, Set<String>) -> Unit,
    listState: LazyListState,
    query: String,
    searchState: EpgSearchUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onRefreshSearch: () -> Unit,
    onExportDatabase: () -> Unit,
    onImportDatabase: () -> Unit,
    onUseSearchChannels: () -> Unit,
    onLoadGuide: (SavedChannel, Boolean) -> Unit,
    onCancelLoading: () -> Unit,
    onPlay: (SavedChannel) -> Unit,
    playingChannel: SavedChannel?,
    onDismiss: () -> Unit,
) {
    var pageName by rememberSaveable {
        mutableStateOf(
            if (searchState.page != null) EpgPage.SEARCH.name else EpgPage.GUIDE.name,
        )
    }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showChannelPicker by rememberSaveable { mutableStateOf(false) }
    val page = EpgPage.valueOf(pageName)
    val keyboard = LocalSoftwareKeyboardController.current
    val selection = searchState.countries
    val canUseGuide = !searchState.loading && !searchState.countriesLoading &&
        selection?.hasEpgChannels == true
    val searchMatches = query == searchState.query
    val searchResults = searchState.page.takeIf { searchMatches }
    val guide = searchState.guide
    val playingResultId = searchResults?.results?.firstOrNull { result ->
        result.channels.any { it.sameStreamAs(playingChannel) }
    }?.id
    val currentGuideProgramId = guide?.programs?.firstOrNull { program ->
        val now = Instant.now().epochSecond
        now >= program.start && now < program.stop
    }?.id ?: guide?.programs?.firstOrNull()?.id

    LaunchedEffect(Unit) { onLoadCountries() }
    LaunchedEffect(page, searchResults, guide, playingResultId, currentGuideProgramId) {
        val targetIndex = when (page) {
            EpgPage.SEARCH -> if (searchResults?.results?.isNotEmpty() == true) 1 else 0
            EpgPage.GUIDE -> if (guide?.programs?.isNotEmpty() == true) 2 else 0
        }
        listState.scrollToItem(targetIndex)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MyIptvPalette.Background) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            MenuButton(text = "Retour", onClick = onDismiss)
                            Column(Modifier.weight(1f)) {
                                Text("EPG", style = MaterialTheme.typography.headlineSmall)
                                Text("STRONG IPTV · Heure de Tunis", color = MyIptvPalette.Primary)
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            MenuButton(
                                text = "Guide d’une chaîne",
                                active = page == EpgPage.GUIDE,
                                onClick = { pageName = EpgPage.GUIDE.name },
                                modifier = Modifier.weight(1f),
                            )
                            MenuButton(
                                text = "Recherche",
                                active = page == EpgPage.SEARCH,
                                onClick = { pageName = EpgPage.SEARCH.name },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        MenuButton(
                            text = selection?.let {
                                "Filtres · ${it.selectedCount} pays · " +
                                    "${it.selectedCategoryCount} catégories"
                            } ?: "Charger les pays et catégories",
                            enabled = !searchState.loading && !searchState.countriesLoading,
                            active = selection?.hasEpgChannels == true,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                keyboard?.hide()
                                if (selection == null) onLoadCountries() else showFilters = true
                            },
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            EpgActionButton(
                                text = "Exporter / GitHub",
                                color = MyIptvPalette.Positive,
                                enabled = !searchState.loading && !searchState.countriesLoading,
                                onClick = onExportDatabase,
                                modifier = Modifier.weight(1f),
                            )
                            EpgActionButton(
                                text = "Importer / GitHub",
                                color = MyIptvPalette.Info,
                                enabled = !searchState.loading && !searchState.countriesLoading,
                                onClick = onImportDatabase,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (searchState.countriesLoading) {
                            Text("Chargement des pays et catégories…", color = MyIptvPalette.TextSecondary)
                        } else if (selection?.hasEpgChannels == false) {
                            Text(
                                "Activez au moins un pays et une catégorie contenant des chaînes EPG.",
                                color = MyIptvPalette.Warning,
                            )
                        }
                        if (searchState.loading) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(searchState.status, color = MyIptvPalette.TextSecondary)
                            EpgActionButton(
                                text = "Annuler le chargement",
                                color = MyIptvPalette.Negative,
                                onClick = onCancelLoading,
                            )
                        } else if (searchState.error != null) {
                            Text(searchState.error, color = MyIptvPalette.Warning)
                        } else if (searchState.status.isNotBlank()) {
                            Surface(
                                color = MyIptvPalette.Positive.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, MyIptvPalette.Positive),
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Text(
                                    text = searchState.status,
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    color = MyIptvPalette.Positive,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        if (page == EpgPage.GUIDE) {
                            GuideControls(
                                enabled = canUseGuide,
                                guide = searchState.guide,
                                onChooseChannel = {
                                    showChannelPicker = true
                                    onLoadGuideChannels(false)
                                },
                                onLoad = { channel, refresh -> onLoadGuide(channel, refresh) },
                                onPlay = onPlay,
                            )
                        } else {
                            SearchControls(
                                query = query,
                                enabled = canUseGuide,
                                onQueryChange = onQueryChange,
                                onSearch = {
                                    keyboard?.hide()
                                    onSearch()
                                },
                            )
                            EpgActionButton(
                                text = "Chaînes EPG disponibles",
                                color = MyIptvPalette.AccentSecondary,
                                enabled = canUseGuide,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    showChannelPicker = true
                                    onLoadGuideChannels(false)
                                },
                            )
                            searchResults?.let { resultPage ->
                                CacheStatus(
                                    resultPage.syncedAt,
                                    resultPage.coverage,
                                    resultPage.cacheBytes,
                                )
                                Text(
                                    "${resultPage.results.size} programme(s) · " +
                                        "${resultPage.results.flatMap { it.channels }.distinctBy { it.streamId }.size} chaîne(s)",
                                )
                                EpgActionButton(
                                    text = "Ouvrir la liste des chaînes trouvées",
                                    color = MyIptvPalette.Positive,
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = onUseSearchChannels,
                                )
                                MenuButton(
                                    text = "Actualiser le guide et rechercher",
                                    enabled = canUseGuide && EpgSearch.words(query).isNotEmpty(),
                                    active = true,
                                    onClick = onRefreshSearch,
                                )
                                if (resultPage.results.isEmpty()) {
                                    Text(
                                        "Aucun des quatre programmes disponibles ne contient tous ces mots.",
                                        color = MyIptvPalette.TextSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
                if (page == EpgPage.GUIDE && guide != null) {
                    item {
                        Surface(
                            color = MyIptvPalette.Warning.copy(alpha = 0.12f),
                            border = BorderStroke(2.dp, MyIptvPalette.Warning),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                Text(
                                    "PROGRAMME ACTUEL ET 3 SUIVANTS",
                                    color = MyIptvPalette.Warning,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    guide.channel.name,
                                    color = MyIptvPalette.White,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                CacheStatus(guide.syncedAt, guide.coverage, guide.cacheBytes)
                                if (guide.programs.isEmpty()) {
                                    Text(
                                        "Aucun programme EPG disponible pour cette chaîne.",
                                        color = MyIptvPalette.TextSecondary,
                                    )
                                } else {
                                    Text("${guide.programs.size} programme(s) disponible(s)")
                                }
                            }
                        }
                    }
                    if (guide.programs.isNotEmpty()) item(key = "guide-pages") {
                        EpgProgramPager(
                            pageKey = "${guide.channel.profileId}:${guide.channel.streamId}:${guide.syncedAt}",
                            count = guide.programs.size,
                            initialPage = guide.programs.indexOfFirst { it.id == currentGuideProgramId }.coerceAtLeast(0),
                            modifier = Modifier.fillParentMaxHeight().fillMaxWidth(),
                        ) { index ->
                            val program = guide.programs[index]
                            GuideProgramCard(
                                program, guide, onPlay,
                                requestFocus = !showChannelPicker && !showFilters && !searchState.loading &&
                                    program.id == currentGuideProgramId,
                            )
                        }
                    }
                }
                if (page == EpgPage.SEARCH && searchResults?.results?.isNotEmpty() == true) {
                    item(key = "search-pages") {
                        EpgProgramPager(
                            pageKey = "${searchState.query}:${searchResults.syncedAt}",
                            count = searchResults.results.size,
                            initialPage = searchResults.results.indexOfFirst { it.id == playingResultId }.coerceAtLeast(0),
                            modifier = Modifier.fillParentMaxHeight().fillMaxWidth(),
                        ) { index ->
                            val result = searchResults.results[index]
                            EpgResultCard(
                                result = result,
                                playingChannel = playingChannel,
                                requestFocus = result.id == playingResultId,
                                onPlay = onPlay,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFilters && selection != null) {
        EpgCountriesDialog(
            selection = selection,
            onApply = { countries, categories ->
                onApplyFilters(countries, categories)
                showFilters = false
            },
            onDismiss = { showFilters = false },
        )
    }
    if (showChannelPicker && selection != null) {
        EpgChannelPickerDialog(
            selection = searchState.guideCountries ?: selection.copy(options = emptyList()),
            loading = searchState.loading,
            status = searchState.error ?: searchState.status,
            onRefresh = { onLoadGuideChannels(true) },
            onCancelLoading = onCancelLoading,
            onSelect = { channel ->
                showChannelPicker = false
                pageName = EpgPage.GUIDE.name
                onLoadGuide(channel, false)
            },
            onDismiss = {
                if (searchState.loading) onCancelLoading()
                showChannelPicker = false
            },
        )
    }
}

@Composable
private fun GuideControls(
    enabled: Boolean,
    guide: EpgGuidePage?,
    onChooseChannel: () -> Unit,
    onLoad: (SavedChannel, Boolean) -> Unit,
    onPlay: (SavedChannel) -> Unit,
) {
    Surface(
        color = MyIptvPalette.Surface,
        border = BorderStroke(1.dp, MyIptvPalette.Border),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Guide complet d’une chaîne",
                color = MyIptvPalette.White,
                style = MaterialTheme.typography.titleMedium,
            )
            EpgActionButton(
                text = guide?.let { "Chaîne · ${it.channel.name}" } ?: "Choisir une chaîne",
                color = MyIptvPalette.AccentSecondary,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                onClick = onChooseChannel,
            )
            if (guide != null) {
                EpgActionButton(
                    text = "Lire la chaîne maintenant",
                    color = MyIptvPalette.Positive,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onPlay(guide.channel) },
                )
                EpgActionButton(
                    text = "Recharger les 4 programmes",
                    color = MyIptvPalette.Primary,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onLoad(guide.channel, false) },
                )
                EpgActionButton(
                    text = "Actualiser l’EPG puis recharger",
                    color = MyIptvPalette.Info,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onLoad(guide.channel, true) },
                )
            }
        }
    }
}

@Composable
private fun SearchControls(
    query: String,
    enabled: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    val canSearch = enabled && EpgSearch.words(query).isNotEmpty()
    Surface(
        color = MyIptvPalette.Surface,
        border = BorderStroke(1.dp, MyIptvPalette.Border),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Rechercher dans les titres et descriptions",
                color = MyIptvPalette.White,
                style = MaterialTheme.typography.titleMedium,
            )
            TvTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().tvFocusBorder(enabled = enabled),
                enabled = enabled,
                label = { Text("Équipes ou programme") },
                placeholder = { Text("Ex. : real madrid inter") },
                supportingText = { Text("Tous les mots, dans n’importe quel ordre") },
                singleLine = true,
                colors = epgTextFieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (canSearch) onSearch() }),
            )
            Text(
                "Recherche dans le programme actuel et les trois suivants de chaque chaîne.",
                color = MyIptvPalette.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            EpgActionButton(
                text = "Rechercher dans l’EPG",
                color = MyIptvPalette.Primary,
                enabled = canSearch,
                modifier = Modifier.fillMaxWidth(),
                onClick = onSearch,
            )
        }
    }
}

@Composable
private fun CacheStatus(syncedAt: Long, coverage: String, cacheBytes: Long) {
    val updated = remember(syncedAt) {
        DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(EpgSearch.tunis)
            .format(Instant.ofEpochMilli(syncedAt))
    }
    Text(
        "Actualisé le $updated · $coverage · Cache ${formatCacheSize(cacheBytes)}",
        color = MyIptvPalette.TextSecondary,
        style = MaterialTheme.typography.bodySmall,
    )
}

private fun formatCacheSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.FRENCH, "%.1f Mo", bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> "${bytes / 1024} Ko"
    else -> "$bytes octets"
}

@Composable
private fun GuideProgramCard(
    program: EpgSearchResult,
    guide: EpgGuidePage,
    onPlay: (SavedChannel) -> Unit,
    requestFocus: Boolean,
) {
    val focusRequester = remember(program.id) { FocusRequester() }
    val tvLandscape = isTvLandscape()
    LaunchedEffect(requestFocus, tvLandscape) {
        if (requestFocus && tvLandscape) {
            repeat(4) {
                withFrameNanos { }
                if (runCatching { focusRequester.requestFocus() }.isSuccess) return@LaunchedEffect
            }
        }
    }
    val now = Instant.now().epochSecond
    val current = now >= program.start && now < program.stop
    LargeEpgProgramCard(
        originalTitle = program.title,
        description = program.description,
        country = guide.channel.countryCode,
        timeLabel = program.timeRange,
        channelName = guide.channel.name,
        channelLogoUrl = guide.channel.iconUrl,
        current = current,
        translationButtonModifier = if (tvLandscape) Modifier.focusRequester(focusRequester) else Modifier,
    ) {
        if (current) {
            EpgActionButton(
                text = "Lire · ${guide.channel.name}",
                color = MyIptvPalette.Positive,
                onClick = { onPlay(guide.channel) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EpgResultCard(
    result: EpgSearchResult,
    playingChannel: SavedChannel?,
    requestFocus: Boolean,
    onPlay: (SavedChannel) -> Unit,
) {
    var expanded by rememberSaveable(result.id) { mutableStateOf(false) }
    val activeChannel = result.channels.firstOrNull { it.sameStreamAs(playingChannel) }
    val focusRequester = remember(result.id) { FocusRequester() }
    LaunchedEffect(requestFocus, activeChannel?.streamId) {
        if (requestFocus && activeChannel != null) {
            repeat(4) {
                withFrameNanos { }
                if (runCatching { focusRequester.requestFocus() }.isSuccess) {
                    return@LaunchedEffect
                }
            }
        }
    }
    val channel = activeChannel ?: result.channels.firstOrNull()
    val now = Instant.now().epochSecond
    LargeEpgProgramCard(
        originalTitle = result.title,
        description = result.description,
        country = channel?.countryCode.orEmpty(),
        timeLabel = "${result.timeRange} · Tunis",
        channelName = if (activeChannel != null) "EN LECTURE · ${activeChannel.name}" else channel?.name.orEmpty(),
        channelLogoUrl = channel?.iconUrl,
        current = now >= result.start && now < result.stop,
    ) {
        val visibleChannels = when {
            expanded -> result.channels
            activeChannel != null -> listOf(activeChannel)
            else -> result.channels.take(1)
        }
        visibleChannels.forEach { channel ->
            val isPlaying = channel.sameStreamAs(playingChannel)
            EpgActionButton(
                text = if (isPlaying) {
                    "En lecture · ${channel.name}"
                } else {
                    "Lire · ${channel.name}"
                },
                color = if (isPlaying) {
                    MyIptvPalette.Positive
                } else {
                    MyIptvPalette.AccentSecondary
                },
                onClick = { onPlay(channel) },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isPlaying && requestFocus) {
                        Modifier.focusRequester(focusRequester)
                    } else {
                        Modifier
                    }),
            )
        }
        if (result.channels.size > 1) {
            MenuButton(
                text = if (expanded) {
                    "Masquer les variantes"
                } else {
                    "Afficher ${result.channels.size} variantes"
                },
                onClick = { expanded = !expanded },
            )
        }
    }
}

@Composable
private fun EpgActionButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tvLandscape = isTvLandscape()
    val containerColor = if (tvLandscape && color == MyIptvPalette.Negative) {
        MyIptvPalette.CardHover
    } else {
        color
    }
    val contentColor = if (tvLandscape && color == MyIptvPalette.Negative) {
        MyIptvPalette.Negative
    } else {
        MyIptvPalette.Background
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.tvFocusBorder(MaterialTheme.shapes.medium, enabled),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = MyIptvPalette.CardHover,
            disabledContentColor = MyIptvPalette.Disabled,
        ),
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun epgTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MyIptvPalette.White,
    unfocusedTextColor = MyIptvPalette.White,
    focusedContainerColor = MyIptvPalette.ActiveSurface,
    unfocusedContainerColor = MyIptvPalette.CardHover,
    cursorColor = MyIptvPalette.Primary,
    focusedBorderColor = if (isTvLandscape()) MyIptvPalette.Negative else MyIptvPalette.Primary,
    unfocusedBorderColor = MyIptvPalette.Info,
    focusedLabelColor = if (isTvLandscape()) MyIptvPalette.Negative else MyIptvPalette.Primary,
    unfocusedLabelColor = MyIptvPalette.Info,
)

private fun SavedChannel.sameStreamAs(other: SavedChannel?): Boolean =
    other != null && profileId == other.profileId && streamId == other.streamId
