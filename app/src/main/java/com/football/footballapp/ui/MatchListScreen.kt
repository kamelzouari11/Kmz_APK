package com.football.footballapp.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.football.footballapp.data.FiltersStore
import com.football.footballapp.data.model.Match
import com.football.footballapp.data.model.MatchStatus
import com.football.footballapp.data.model.Score
import com.football.footballapp.repository.MatchRepository
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private sealed class Screen {
    object Main : Screen()
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
                showAll = state.showAll,
                onRefresh = { viewModel.load(refresh = true) },
                onToggleLive = { viewModel.toggleLive() },
                onToggleAll = { viewModel.toggleShowAll() },
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
                    liveOnly = state.liveOnly
                )
                else -> MatchList(
                    grouped = state.groupedByCompetition,
                    showHeader = !state.showAll,
                    onMatchClick = onMatchClick
                )
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        viewModel.changeDate(
                            java.time.Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault()).toLocalDate()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    isRefreshing: Boolean,
    liveOnly: Boolean,
    showAll: Boolean,
    onRefresh: () -> Unit,
    onToggleLive: () -> Unit,
    onToggleAll: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (isRefreshing) 360f else 0f,
        animationSpec = tween(800),
        label = "refresh-rotation"
    )
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "⚽ Football",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 17.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(Modifier.weight(1f))
            ToggleChip(
                label = "Live",
                selected = liveOnly,
                onClick = onToggleLive,
                accent = Color(0xFFE53935)
            )
            Spacer(Modifier.width(6.dp))
            ToggleChip(
                label = "All",
                selected = showAll,
                onClick = onToggleAll,
                accent = MaterialTheme.colorScheme.primary
            )
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Réglages",
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Actualiser",
                    modifier = Modifier.rotate(rotation).size(20.dp)
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
    accent: Color
) {
    val bg = if (selected) accent else Color.Transparent
    val fg = if (selected) Color.White else accent
    Surface(
        shape = RoundedCornerShape(50),
        color = bg,
        modifier = Modifier
            .border(width = 1.2.dp, color = accent, shape = RoundedCornerShape(50))
            .clip(RoundedCornerShape(50))
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (label == "Live" && selected) {
                Box(
                    Modifier.size(6.dp).clip(CircleShape).background(Color.White)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
private fun EmptyState(hasAnyMatch: Boolean, liveOnly: Boolean) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(if (liveOnly) "📺" else "🏟️", fontSize = 40.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            when {
                liveOnly -> "Aucun match en direct"
                hasAnyMatch -> "Aucun match dans les pays sélectionnés"
                else -> "Aucun match prévu ce jour-là"
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
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
    val enabled = countries.filter { it.name in enabledCountries }
        .ifEmpty { enabledCountries.map {
            com.football.footballapp.data.ApiFootballCountryDto(name = it, code = null, flag = null)
        } }

    val allOn = enabled.isNotEmpty() && enabled.all { it.name in activeFilters }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ALL toggle en premier
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

        enabled.forEach { country ->
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
    grouped: Map<String, List<Match>>,
    showHeader: Boolean,
    onMatchClick: (Match) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        grouped.forEach { (competition, matches) ->
            if (showHeader) {
                item(key = "header-$competition") {
                    CompetitionHeader(competition, matches.firstOrNull()?.competitionEmblem)
                }
            }
            items(matches, key = { it.id }) { match ->
                MatchCard(match, onMatchClick)
            }
        }
    }
}

@Composable
private fun CompetitionHeader(name: String, emblem: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 6.dp, bottom = 0.dp)
    ) {
        if (!emblem.isNullOrBlank()) {
            AsyncImage(
                model = emblem,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun MatchCard(match: Match, onMatchClick: (Match) -> Unit) {
    val isLive = match.status == MatchStatus.LIVE || match.status == MatchStatus.HALF_TIME
    val liveAccent = Color(0xFFE53935)
    // Blend surface ↔ accent rouge : 10 % en light, 22 % en dark — reste lisible avec onSurface
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val blend = if (isDark) 0.22f else 0.10f
    val cardColor = if (isLive)
        lerp(MaterialTheme.colorScheme.surface, liveAccent, blend)
    else MaterialTheme.colorScheme.surface
    val cardBorder = if (isLive) liveAccent.copy(alpha = 0.45f) else Color.Transparent
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
                StatusChip(match.status, match.statusLabel, match.minute)
                Spacer(Modifier.weight(1f))
                Text(
                    formatKickoff(match.utcDate, match.status),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TeamSide(
                    name = match.homeTeam.name,
                    logo = match.homeTeam.logoUrl,
                    modifier = Modifier.weight(3f)
                )
                ScoreBlock(match.score, match.status, modifier = Modifier.weight(2f))
                TeamSide(
                    name = match.awayTeam.name,
                    logo = match.awayTeam.logoUrl,
                    modifier = Modifier.weight(3f)
                )
            }
        }
    }
}

@Composable
private fun StatusChip(status: MatchStatus, label: String, minute: Int?) {
    val (bg, fg, text) = when (status) {
        MatchStatus.LIVE -> Triple(
            Color(0xFFE53935).copy(alpha = 0.15f),
            Color(0xFFE53935),
            if (minute != null) "LIVE · ${minute}'" else "LIVE"
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
private fun TeamSide(name: String, logo: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TeamLogo(name = name, url = logo)
        Spacer(Modifier.height(4.dp))
        Text(
            text = name,
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
    if (!url.isNullOrBlank()) {
        AsyncImage(
            model = url,
            contentDescription = "$name logo",
            modifier = Modifier.size(size),
            contentScale = ContentScale.Fit
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
            status == MatchStatus.FINISHED -> Text(
                // Match terminé mais score non encore disponible dans nos sources gratuites
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
