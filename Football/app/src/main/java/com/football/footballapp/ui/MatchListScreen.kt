package com.football.footballapp.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.football.footballapp.MainActivity
import com.football.footballapp.data.FiltersStore
import com.football.footballapp.data.model.Match
import com.football.footballapp.data.model.MatchStatus
import com.football.footballapp.data.model.Score
import com.football.footballapp.data.model.Team
import com.football.footballapp.data.model.localCalendarDate
import com.football.footballapp.data.model.matchesFavorite
import com.football.footballapp.notifications.MatchReminderManager
import com.football.footballapp.repository.MatchRepository
import kotlinx.coroutines.delay
import java.text.Normalizer
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private sealed class Screen {
    object Main : Screen()
    object Search : Screen()
    object SettingsCountries : Screen()
    data class SettingsCompetitions(val country: String) : Screen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchListScreen(
    repository: MatchRepository,
    filtersStore: FiltersStore,
    onMatchClick: (Match) -> Unit
) {
    val viewModel: MatchViewModel = viewModel(
        factory = MatchViewModel.Factory(repository, filtersStore)
    )
    val state by viewModel.state.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var screen: Screen by remember { mutableStateOf(Screen.Main) }

    // si l'utilisateur a déjà des pays activés en settings, on charge la liste pour
    // pouvoir afficher leurs drapeaux dans la petite box
    LaunchedEffect(state.settingsCountries) {
        if (state.settingsCountries.isNotEmpty()) viewModel.loadSettingsCountries()
    }

    when (val s = screen) {
        Screen.Search -> {
            CachedTeamSearchScreen(
                matches = state.cachedSearchMatches,
                isLoading = state.isSearchLoading,
                favoriteTeamKeys = state.favoriteTeamKeys,
                onBack = { screen = Screen.Main },
                onQueryReady = viewModel::refreshMissingSearchScores,
                onToggleFavorite = viewModel::toggleFavoriteTeam,
                onMatchClick = onMatchClick
            )
            return
        }
        Screen.SettingsCountries -> {
            SettingsCountriesScreen(
                viewModel = viewModel,
                onBack = { screen = Screen.Main },
                onOpenCountry = { screen = Screen.SettingsCompetitions(it) }
            )
            return
        }
        is Screen.SettingsCompetitions -> {
            SettingsCompetitionsScreen(
                viewModel = viewModel,
                country = s.country,
                onBack = { screen = Screen.SettingsCountries }
            )
            return
        }
        Screen.Main -> Unit // fall-through au reste de la fonction
    }

    Scaffold(
        topBar = {
            AppTopBar(
                isRefreshing = state.isRefreshing,
                liveOnly = state.liveOnly,
                favoritesOnly = state.favoritesOnly,
                groupByCompetition = state.groupByCompetition,
                onRefresh = { viewModel.load(refresh = true) },
                onToggleLive = { viewModel.toggleLive() },
                onToggleFavorites = { viewModel.toggleFavoritesOnly() },
                onToggleGrouping = { viewModel.toggleGroupByCompetition() },
                onOpenSearch = {
                    viewModel.loadCachedMatchesForSearch()
                    screen = Screen.Search
                },
                onOpenSettings = { screen = Screen.SettingsCountries }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            DateNavigator(
                date = state.date,
                onPrev = { viewModel.shiftDate(-1) },
                onNext = { viewModel.shiftDate(1) },
                onPickDate = { showDatePicker = true }
            )

            val availableCountries by viewModel.availableCountries.collectAsState()
            FlagFilterRow(
                enabledCountries = state.settingsCountries,
                activeFilters = state.flagFilters,
                countries = availableCountries,
                onToggleAll = { allOn -> viewModel.setAllFlags(allOn) },
                onToggleFlag = { viewModel.toggleFlag(it) },
                onLongPressFlag = { screen = Screen.SettingsCompetitions(it) }
            )

            when {
                state.isLoading -> LoadingState()
                state.error != null -> ErrorState(state.error!!, onRetry = { viewModel.load() })
                state.filteredMatches.isEmpty() -> EmptyState(
                    hasAnyMatch = state.matches.isNotEmpty(),
                    liveOnly = state.liveOnly,
                    favoritesOnly = state.favoritesOnly,
                    hasFavoriteTeams = state.favoriteTeamKeys.isNotEmpty(),
                    selectionConfigured = state.countrySelectionConfigured,
                    hasActiveCompetition = state.flagFilters.any { country ->
                        state.settingsLeaguesByCountry[country].orEmpty().isNotEmpty()
                    },
                    onOpenSettings = { screen = Screen.SettingsCountries }
                )
                else -> MatchList(
                    matches = state.filteredMatches,
                    groupByCompetition = state.groupByCompetition,
                    favoriteTeamKeys = state.favoriteTeamKeys,
                    onToggleFavorite = viewModel::toggleFavoriteTeam,
                    onMatchClick = onMatchClick
                )
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        viewModel.changeDate(
                            java.time.Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC).toLocalDate()
                        )
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Annuler") }
            }
        ) { DatePicker(state = pickerState) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun CachedTeamSearchScreen(
    matches: List<Match>,
    isLoading: Boolean,
    favoriteTeamKeys: Set<String>,
    onBack: () -> Unit,
    onQueryReady: (String) -> Unit,
    onToggleFavorite: (Match, Team) -> Unit,
    onMatchClick: (Match) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val normalizedQuery = remember(query) { normalizeSearchText(query) }
    val results = remember(matches, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            emptyList()
        } else {
            matches.filter { match ->
                listOfNotNull(
                    match.homeTeam.name,
                    match.homeTeam.shortName,
                    match.awayTeam.name,
                    match.awayTeam.shortName
                ).any { teamName ->
                    normalizeSearchText(teamName).contains(normalizedQuery)
                }
            }.sortedByDescending { it.utcDate }
        }
    }
    val matchesByDate = remember(results) {
        results.groupBy { it.localCalendarDate()?.toString().orEmpty() }
    }
    LaunchedEffect(normalizedQuery, isLoading) {
        if (!isLoading && normalizedQuery.length >= 2) {
            delay(350)
            onQueryReady(query)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recherche par équipe") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder = { Text("Ex. Liverpool") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Effacer")
                        }
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            when {
                isLoading -> LoadingState()
                normalizedQuery.isBlank() -> SearchMessage(
                    "Recherche dans toutes les journées en cache et la journée affichée"
                )
                results.isEmpty() -> SearchMessage("Aucun match trouvé pour « $query »")
                else -> {
                    Text(
                        text = "${results.size} match${if (results.size > 1) "s" else ""}",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        matchesByDate.forEach { (date, dayMatches) ->
                            stickyHeader(key = "search-date-$date") {
                                Surface(
                                    color = MaterialTheme.colorScheme.background,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = formatSearchDate(date),
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            items(
                                items = dayMatches,
                                key = { match ->
                                    "search-$date-${match.id}-${match.source}"
                                }
                            ) { match ->
                                MatchCard(
                                    match = match,
                                    favoriteTeamKeys = favoriteTeamKeys,
                                    onToggleFavorite = onToggleFavorite,
                                    onMatchClick = onMatchClick
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private fun normalizeSearchText(value: String): String = Normalizer
    .normalize(value.trim(), Normalizer.Form.NFD)
    .replace(SEARCH_DIACRITICS, "")
    .lowercase(Locale.ROOT)

private fun formatSearchDate(rawDate: String): String = runCatching {
    val date = LocalDate.parse(rawDate)
    val relative = when (date) {
        LocalDate.now() -> "Aujourd'hui"
        LocalDate.now().plusDays(1) -> "Demain"
        LocalDate.now().minusDays(1) -> "Hier"
        else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.FRENCH)
            .replaceFirstChar { it.uppercase() }
    }
    val formatted = date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH))
    "$relative · $formatted"
}.getOrDefault(rawDate)

private val SEARCH_DIACRITICS = Regex("\\p{Mn}+")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    isRefreshing: Boolean,
    liveOnly: Boolean,
    favoritesOnly: Boolean,
    groupByCompetition: Boolean,
    onRefresh: () -> Unit,
    onToggleLive: () -> Unit,
    onToggleFavorites: () -> Unit,
    onToggleGrouping: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "refresh")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(700, easing = LinearEasing)),
        label = "refresh-spin"
    )
    val rotation = if (isRefreshing) spinAngle else 0f
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "⚽ Football",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onOpenSearch, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Rechercher une équipe",
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Réglages",
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Actualiser",
                        modifier = Modifier.rotate(rotation).size(22.dp)
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ToggleChip(
                    label = "Live",
                    selected = liveOnly,
                    onClick = onToggleLive,
                    accent = Color(0xFFE53935),
                    modifier = Modifier.weight(0.78f)
                )
                ToggleChip(
                    label = "★ Favoris",
                    selected = favoritesOnly,
                    onClick = onToggleFavorites,
                    accent = Color(0xFFFFB300),
                    modifier = Modifier.weight(1f)
                )
                ToggleChip(
                    label = "▦ Compétitions",
                    selected = groupByCompetition,
                    onClick = onToggleGrouping,
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1.42f)
                )
            }
        }
    }
}

@Composable
private fun ToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    val bg = if (selected) accent else Color.Transparent
    val fg = if (selected) {
        if (accent.luminance() > 0.5f) Color.Black else Color.White
    } else {
        accent
    }
    Surface(
        shape = shape,
        color = bg,
        modifier = modifier
            .border(width = 1.2.dp, color = accent, shape = shape)
            .clip(shape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 40.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (label == "Live" && selected) {
                Box(
                    Modifier.size(6.dp).clip(CircleShape).background(Color.White)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = label,
                color = fg,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DateNavigator(
    date: LocalDate,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPickDate: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPrev, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.ChevronLeft,
                    contentDescription = "Jour précédent",
                    modifier = Modifier.size(20.dp)
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(
                    onClick = onPickDate,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = formatDateHeader(date),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
            IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Jour suivant",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun formatDateHeader(date: LocalDate): String {
    val today = LocalDate.now()
    val label = when (date) {
        today -> "Aujourd'hui"
        today.plusDays(1) -> "Demain"
        today.minusDays(1) -> "Hier"
        else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.FRENCH)
            .replaceFirstChar { it.uppercase() }
    }
    val dm = date.format(DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH))
    return "$label · $dm"
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 3.dp)
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("⚠️", fontSize = 40.sp)
        Spacer(Modifier.height(10.dp))
        Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text("Réessayer") }
    }
}

@Composable
private fun EmptyState(
    hasAnyMatch: Boolean,
    liveOnly: Boolean,
    favoritesOnly: Boolean,
    hasFavoriteTeams: Boolean,
    selectionConfigured: Boolean,
    hasActiveCompetition: Boolean,
    onOpenSettings: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(if (favoritesOnly) "☆" else if (liveOnly) "📺" else "🏟️", fontSize = 40.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            when {
                favoritesOnly && !hasFavoriteTeams ->
                    "Aucune équipe favorite. Touchez l’étoile près d’une équipe."
                favoritesOnly -> "Aucun match de vos équipes favorites ce jour-là"
                liveOnly -> "Aucun match en direct"
                !selectionConfigured -> "Sélectionnez vos pays et compétitions"
                !hasActiveCompetition -> "Aucune compétition active dans les pays sélectionnés"
                hasAnyMatch -> "Aucun match dans les pays sélectionnés"
                else -> "Aucun match prévu ce jour-là"
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (!selectionConfigured || !hasActiveCompetition) {
            Spacer(Modifier.height(14.dp))
            Button(onClick = onOpenSettings) {
                Text("Ouvrir les réglages")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FlagFilterRow(
    enabledCountries: Set<String>,
    activeFilters: Set<String>,
    countries: List<com.football.footballapp.data.ApiFootballCountryDto>,
    onToggleAll: (Boolean) -> Unit,
    onToggleFlag: (String) -> Unit,
    onLongPressFlag: (String) -> Unit
) {
    if (enabledCountries.isEmpty()) return
    val accent = MaterialTheme.colorScheme.primary
    val countriesByName = countries.associateBy { it.name }
    val enabled = enabledCountries.map { countryName ->
        countriesByName[countryName] ?: fallbackCountry(countryName)
    }

    val allOn = enabled.isNotEmpty() && enabled.all { it.name in activeFilters }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item(key = "all-toggle") {
            Surface(
                shape = RoundedCornerShape(50),
                color = if (allOn) accent else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onToggleAll(!allOn) }
                    .border(
                        width = if (allOn) 0.dp else 1.dp,
                        color = if (allOn) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(50)
                    )
            ) {
                Text(
                    "ALL",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = if (allOn) Color.White else MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        items(enabled, key = { it.name }) { country ->
            val isOn = country.name in activeFilters
            FlagChip(
                country = country,
                isActive = isOn,
                accent = accent,
                onClick = { onToggleFlag(country.name) },
                onLongClick = { onLongPressFlag(country.name) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FlagChip(
    country: com.football.footballapp.data.ApiFootballCountryDto,
    isActive: Boolean,
    accent: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val shape = RoundedCornerShape(6.dp)
    val ringColor = if (isActive) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val bg = if (isActive) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    Surface(
        shape = shape,
        color = bg,
        modifier = Modifier
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .border(width = if (isActive) 2.dp else 1.dp, color = ringColor, shape = shape)
    ) {
        Box(
            modifier = Modifier.padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            FlagImage(url = country.flag, fallback = country.code ?: country.name)
        }
    }
}

@Composable
private fun MatchList(
    matches: List<Match>,
    groupByCompetition: Boolean,
    favoriteTeamKeys: Set<String>,
    onToggleFavorite: (Match, Team) -> Unit,
    onMatchClick: (Match) -> Unit
) {
    val competitionGroups = remember(matches, groupByCompetition) {
        if (!groupByCompetition) {
            emptyMap()
        } else {
            matches.groupBy { match ->
                val country = normalizeSearchText(match.competitionCountry.orEmpty())
                val competition = normalizeSearchText(match.competitionName)
                "$country|$competition"
            }
        }
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (groupByCompetition) {
            competitionGroups.forEach { (groupKey, competitionMatches) ->
                item(key = "competition-header-$groupKey") {
                    val firstMatch = competitionMatches.first()
                    val headerEmblem = competitionMatches.firstNotNullOfOrNull { match ->
                        match.competitionEmblem?.takeIf { it.isNotBlank() }
                    }
                    CompetitionGroupHeader(
                        match = firstMatch,
                        emblem = headerEmblem,
                        matchCount = competitionMatches.size
                    )
                }
                items(
                    items = competitionMatches,
                    key = { match -> "grouped-$groupKey-${match.id}|${match.source}" }
                ) { match ->
                    MatchCard(
                        match = match,
                        favoriteTeamKeys = favoriteTeamKeys,
                        onToggleFavorite = onToggleFavorite,
                        onMatchClick = onMatchClick,
                        showCompetition = false
                    )
                }
            }
        } else {
            items(
                items = matches,
                key = { match -> "${match.id}|${match.source}" }
            ) { match ->
                MatchCard(
                    match = match,
                    favoriteTeamKeys = favoriteTeamKeys,
                    onToggleFavorite = onToggleFavorite,
                    onMatchClick = onMatchClick
                )
            }
        }
    }
}

@Composable
private fun CompetitionGroupHeader(match: Match, emblem: String?, matchCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        emblem?.takeIf { it.isNotBlank() }?.let { emblemUrl ->
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.92f))
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = emblemUrl,
                    contentDescription = "Emblème ${match.competitionName}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = match.competitionName,
                fontSize = 16.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            match.competitionCountry?.takeIf { it.isNotBlank() }?.let { country ->
                Text(
                    text = country,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$matchCount match${if (matchCount > 1) "s" else ""}",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MatchCard(
    match: Match,
    favoriteTeamKeys: Set<String>,
    onToggleFavorite: (Match, Team) -> Unit,
    onMatchClick: (Match) -> Unit,
    showCompetition: Boolean = true
) {
    val context = LocalContext.current
    val reminderManager = remember(context) { MatchReminderManager(context) }
    var reminderEnabled by remember(match.id, match.utcDate) {
        mutableStateOf(reminderManager.isEnabled(match))
    }
    val isLive = match.status == MatchStatus.LIVE || match.status == MatchStatus.HALF_TIME
    val scoreUnavailable = match.status == MatchStatus.FINISHED &&
        (match.score.home == null || match.score.away == null)
    val displayedStatus = if (scoreUnavailable) MatchStatus.UNKNOWN else match.status
    val homeIsFavorite = match.homeTeam.matchesFavorite(favoriteTeamKeys, match.source)
    val awayIsFavorite = match.awayTeam.matchesFavorite(favoriteTeamKeys, match.source)
    val matchDate = remember(match.utcDate) { match.localCalendarDate() }
    val today = LocalDate.now()
    val usesPastStyle = matchDate?.isBefore(today) == true ||
        (matchDate == today && match.status == MatchStatus.FINISHED)
    val cardAccent = when {
        isLive -> Color(0xFFE53935)
        matchDate == today && match.status == MatchStatus.FINISHED -> Color(0xFF9E9E9E)
        matchDate == today && match.status == MatchStatus.SCHEDULED -> Color(0xFF43A047)
        matchDate?.isBefore(today) == true -> Color(0xFF9E9E9E)
        matchDate?.isAfter(today) == true -> Color(0xFF43A047)
        matchDate == today -> Color(0xFFEC407A)
        else -> null
    }
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val blend = when {
        isLive && isDark -> 0.22f
        isLive -> 0.10f
        isDark -> 0.20f
        usesPastStyle -> 0.18f
        else -> 0.12f
    }
    val cardColor = cardAccent?.let {
        lerp(MaterialTheme.colorScheme.surface, it, blend)
    } ?: MaterialTheme.colorScheme.surface
    val cardBorder = if (isLive) {
        Color(0xFFE53935).copy(alpha = 0.45f)
    } else {
        Color.Transparent
    }
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onMatchClick(match) }
            .then(
                if (isLive) Modifier.border(1.2.dp, cardBorder, RoundedCornerShape(14.dp))
                else Modifier
            ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (isLive) 3.dp else 1.dp
        ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = cardColor,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusChip(
                    status = displayedStatus,
                    label = if (scoreUnavailable) "Score indisponible" else match.statusLabel,
                    minute = match.minute
                )
                Spacer(Modifier.weight(1f))
                if (match.status == MatchStatus.SCHEDULED) {
                    IconButton(
                        onClick = {
                            if (reminderEnabled) {
                                reminderManager.cancel(match.id)
                                reminderEnabled = false
                            } else {
                                val enableReminder = {
                                    when (reminderManager.schedule(match)) {
                                        MatchReminderManager.ScheduleResult.EXACT,
                                        MatchReminderManager.ScheduleResult.APPROXIMATE -> {
                                            reminderEnabled = true
                                        }
                                        MatchReminderManager.ScheduleResult.TOO_LATE -> {
                                            Toast.makeText(
                                                context,
                                                "Rappel impossible à moins de 15 minutes du match",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                                val activity = context.findActivity()
                                if (activity is MainActivity) {
                                    activity.runWithReminderPermissions(enableReminder)
                                } else {
                                    enableReminder()
                                }
                            }
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = if (reminderEnabled) {
                                Icons.Default.NotificationsActive
                            } else {
                                Icons.Default.NotificationsNone
                            },
                            contentDescription = if (reminderEnabled) {
                                "Désactiver le rappel"
                            } else {
                                "Activer le rappel 15 minutes avant"
                            },
                            tint = if (reminderEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(Modifier.width(2.dp))
                }
                Text(
                    formatKickoff(match.utcDate, displayedStatus),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (showCompetition) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val competitionImage = match.competitionEmblem ?: match.competitionFlag
                    if (!competitionImage.isNullOrBlank()) {
                        AsyncImage(
                            model = competitionImage,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(
                        text = listOfNotNull(
                            match.competitionCountry?.takeIf { it.isNotBlank() },
                            match.competitionName.takeIf { it.isNotBlank() }
                        ).joinToString(" · "),
                        modifier = Modifier.weight(1f),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TeamSide(
                    team = match.homeTeam,
                    isFavorite = homeIsFavorite,
                    onToggleFavorite = { onToggleFavorite(match, match.homeTeam) },
                    modifier = Modifier.weight(3f)
                )
                ScoreBlock(match.score, displayedStatus, modifier = Modifier.weight(2f))
                TeamSide(
                    team = match.awayTeam,
                    isFavorite = awayIsFavorite,
                    onToggleFavorite = { onToggleFavorite(match, match.awayTeam) },
                    modifier = Modifier.weight(3f)
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun StatusChip(status: MatchStatus, label: String, minute: Int?) {
    val (bg, fg, text) = when (status) {
        MatchStatus.LIVE -> Triple(
            Color(0xFFE53935).copy(alpha = 0.15f),
            Color(0xFFE53935),
            if (minute != null) "${minute}'" else "LIVE"
        )
        MatchStatus.HALF_TIME -> Triple(
            Color(0xFFFFA000).copy(alpha = 0.15f),
            Color(0xFFFFA000),
            "Mi-temps"
        )
        MatchStatus.FINISHED -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Terminé"
        )
        MatchStatus.SCHEDULED -> Triple(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.primary,
            "À venir"
        )
        MatchStatus.POSTPONED -> Triple(
            Color(0xFF757575).copy(alpha = 0.15f),
            Color(0xFF424242),
            "Reporté"
        )
        MatchStatus.CANCELLED -> Triple(
            Color(0xFF757575).copy(alpha = 0.15f),
            Color(0xFF424242),
            "Annulé"
        )
        MatchStatus.UNKNOWN -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            label
        )
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (status == MatchStatus.LIVE) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(fg)
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
    }
}

@Composable
private fun TeamSide(
    team: Team,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamLogo(name = team.name, url = team.logoUrl)
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Star else Icons.Outlined.Star,
                    contentDescription = if (isFavorite) {
                        "Retirer ${team.name} des favoris"
                    } else {
                        "Ajouter ${team.name} aux favoris"
                    },
                    tint = if (isFavorite) {
                        Color(0xFFFFB300)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(19.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = team.name,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 15.sp
        )
    }
}

@Composable
private fun TeamLogo(name: String, url: String?) {
    val size = 34.dp
    var imageFailed by remember(url) { mutableStateOf(false) }
    if (!url.isNullOrBlank() && !imageFailed) {
        AsyncImage(
            model = url,
            contentDescription = "$name logo",
            modifier = Modifier.size(size),
            contentScale = ContentScale.Fit,
            onError = { imageFailed = true }
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.initials(),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

private fun String.initials(): String {
    val parts = trim().split(" ", "-").filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts[0].first().toString() + parts[1].first()).uppercase()
    }
}

@Composable
private fun ScoreBlock(score: Score, status: MatchStatus, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        val hasScore = score.home != null && score.away != null
        when {
            hasScore -> Text(
                text = "${score.home}  -  ${score.away}",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )
            status == MatchStatus.FINISHED || status == MatchStatus.UNKNOWN -> Text(
                // Journée passée mais score non encore disponible dans nos sources gratuites
                text = "— · —",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> Text(
                text = "VS",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatKickoff(utcDate: String, status: MatchStatus): String = try {
    val odt = OffsetDateTime.parse(utcDate)
    val local = odt.atZoneSameInstant(ZoneId.systemDefault())
    val time = local.format(DateTimeFormatter.ofPattern("HH:mm"))
    when (status) {
        MatchStatus.FINISHED -> "FT · $time"
        MatchStatus.SCHEDULED -> "⏱ $time"
        else -> time
    }
} catch (e: Exception) {
    utcDate
}
