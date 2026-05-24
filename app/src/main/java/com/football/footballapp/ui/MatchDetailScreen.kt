package com.football.footballapp.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.football.footballapp.data.model.*
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchDetailScreen(
    viewModel: MatchDetailViewModel
) {
    val match = viewModel.match
    val state by viewModel.state.collectAsState()
    var showRefreshConfirmation by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Événements", "Compositions")

    LaunchedEffect(match.id) {
        viewModel.loadMatchDetail()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            MatchHeaderCard(
                match = match,
                onRefresh = {
                    if (state.matchDetail != null) {
                        showRefreshConfirmation = true
                    } else {
                        viewModel.loadMatchDetail(forceRefresh = true)
                    }
                }
            )

            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (state.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (state.error != null) {
                    ErrorLayout(
                        error = state.error ?: "Erreur",
                        onRetry = {
                            viewModel.loadMatchDetail(forceRefresh = true)
                        }
                    )
                } else {
                    val detail = state.matchDetail
                    when (selectedTab) {
                        0 -> EventsTab(
                            events = detail?.events ?: emptyList(),
                            homeTeamId = match.homeTeam.id,
                            awayTeamId = match.awayTeam.id
                        )
                        1 -> LineupsTab(lineups = detail?.lineups)
                    }
                }
            }

            if (showRefreshConfirmation) {
                AlertDialog(
                    onDismissRequest = { showRefreshConfirmation = false },
                    confirmButton = {
                                TextButton(onClick = {
                                    showRefreshConfirmation = false
                                    viewModel.loadMatchDetail(forceRefresh = true)
                                }) {
                                    Text("Confirmer")
                                }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRefreshConfirmation = false }) {
                            Text("Annuler")
                        }
                    },

                    title = { Text("Actualisation du match") },
                    text = {
                        Text("Cela consommera une requête réseau. Voulez-vous actualiser les détails du match ?")
                    }
                )
            }
        }
    }
}

@Composable
private fun MatchHeaderCard(match: Match, onRefresh: () -> Unit) {
    val gradientColors = listOf(
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(gradientColors))
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!match.competitionFlag.isNullOrBlank()) {
                        AsyncImage(
                            model = match.competitionFlag,
                            contentDescription = match.competitionCountry,
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .border(0.5.dp, Color.LightGray, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = "${match.competitionCountry ?: ""} • ${match.competitionName}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Synchroniser",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TeamLogoLarge(url = match.homeTeam.logoUrl, name = match.homeTeam.name, size = 46.dp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = match.homeTeam.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(
                    modifier = Modifier.width(84.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val hasScore = match.score.home != null && match.score.away != null
                    if (hasScore) {
                        Text(
                            text = "${match.score.home} - ${match.score.away}",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Text(
                            text = "VS",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    StatusBadge(status = match.status, statusLabel = match.statusLabel, elapsed = match.minute)
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TeamLogoLarge(url = match.awayTeam.logoUrl, name = match.awayTeam.name, size = 46.dp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = match.awayTeam.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}


@Composable
private fun TeamLogoLarge(url: String?, name: String, size: Dp = 60.dp) {
    if (url.isNullOrBlank()) {
        Box(
            modifier = Modifier
                .size(size)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.take(2).uppercase(),
                fontWeight = FontWeight.Bold,
                fontSize = (size.value / 3).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        AsyncImage(
            model = url,
            contentDescription = name,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color.White)
                .padding(4.dp),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun StatusBadge(status: MatchStatus, statusLabel: String, elapsed: Int?) {
    val containerColor = when (status) {
        MatchStatus.LIVE, MatchStatus.HALF_TIME -> Color(0xFFE53935)
        MatchStatus.FINISHED -> Color(0xFF43A047)
        MatchStatus.SCHEDULED -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when (status) {
        MatchStatus.LIVE, MatchStatus.HALF_TIME, MatchStatus.FINISHED -> Color.White
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val label = when {
        status == MatchStatus.LIVE && elapsed != null -> "${elapsed}'"
        else -> statusLabel
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = label.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}



@Composable
private fun EventsTab(events: List<MatchEvent>, homeTeamId: Int, awayTeamId: Int) {
    if (events.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Aucun événement répertorié.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(events.sortedBy { it.time.elapsed }) { event ->
                val isHome = event.teamId == homeTeamId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = if (isHome) Arrangement.Start else Arrangement.End
                ) {
                    EventItemContent(event = event, isHome = isHome)
                }
            }
        }
    }
}

@Composable
private fun EventItemContent(event: MatchEvent, isHome: Boolean) {
    val cardColor = if (isHome) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        ),
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isHome) {
                EventEmoji(event = event)
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = event.player.name ?: "Joueur",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    EventTimeBadge(time = event.time)
                }

                val description = event.detail.takeIf { it.isNotBlank() }
                    ?: event.type.replaceFirstChar { it.uppercase() }

                Text(
                    text = description,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!isHome) {
                Spacer(modifier = Modifier.width(8.dp))
                EventEmoji(event = event)
            }
        }
    }
}

@Composable
private fun EventEmoji(event: MatchEvent) {
    val emoji = when (event.type.lowercase()) {
        "goal" -> "⚽"
        "card" -> if (event.detail.lowercase().contains("red")) "🟥" else "🟨"
        "subst" -> "🔄"
        else -> "ℹ️"
    }
    val color = when (event.type.lowercase()) {
        "goal" -> MaterialTheme.colorScheme.primary
        "card" -> if (event.detail.lowercase().contains("red")) Color(0xFFB71C1C) else Color(0xFFF9A825)
        "subst" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Text(emoji, fontSize = 18.sp, color = color)
}

@Composable
private fun EventTimeBadge(time: EventTime) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val label = if (time.extra != null) "${time.elapsed}+${time.extra}" else "${time.elapsed}"
        Text(
            text = "$label'",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}


@Composable
private fun LineupsTab(lineups: MatchLineups?) {
    if (lineups == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Compositions non disponibles.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        var selectedTeamTab by remember { mutableIntStateOf(0) } // 0 = Home, 1 = Away
        Column(modifier = Modifier.fillMaxSize()) {
            TabRow(
                selectedTabIndex = selectedTeamTab,
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            ) {
                Tab(
                    selected = selectedTeamTab == 0,
                    onClick = { selectedTeamTab = 0 },
                    text = { Text(lineups.home.teamName, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
                Tab(
                    selected = selectedTeamTab == 1,
                    onClick = { selectedTeamTab = 1 },
                    text = { Text(lineups.away.teamName, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }

            val isAwayLineup = selectedTeamTab == 1
            val lineup = if (isAwayLineup) lineups.away else lineups.home

            LazyColumn(
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Formation: ${lineup.formation ?: "Inconnue"}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        lineup.coach?.name?.takeIf { it.isNotBlank() }?.let { coachName ->
                            Text(
                                text = "Entraîneur: $coachName",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "Titulaires",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                items(lineup.startXI) { player ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isAwayLineup) Arrangement.End else Arrangement.Start
                    ) {
                        PlayerRow(player = player, isSubstitute = false)
                    }
                }

                item {
                    Text(
                        text = "Remplaçants",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                items(lineup.substitutes) { player ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isAwayLineup) Arrangement.End else Arrangement.Start
                    ) {
                        PlayerRow(player = player, isSubstitute = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerRow(player: LineupPlayer, isSubstitute: Boolean) {
    val containerColor = if (isSubstitute) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
    }

    val contentColor = if (isSubstitute) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.95f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth(0.82f)
            .padding(vertical = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        )
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = player.number?.toString() ?: "-",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = player.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = contentColor,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            normalizePosition(player.position)?.let { posAbbrev ->
                Text(
                    text = posAbbrev,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .widthIn(min = 30.dp)
                )
            }
        }
    }
}

@Composable
private fun StatsTab(stats: List<MatchTeamStats>, homeTeamId: Int, awayTeamId: Int) {
    if (stats.size < 2) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Statistiques non disponibles.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        val homeStats = stats[0]
        val awayStats = stats[1]

        val keys = homeStats.stats.map { it.type }.intersect(awayStats.stats.map { it.type }.toSet()).toList()

        if (keys.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Aucune statistique comparable.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(keys) { key ->
                    val homeValStr = homeStats.stats.first { it.type == key }.value
                    val awayValStr = awayStats.stats.first { it.type == key }.value

                    val homeVal = parseStatValue(homeValStr)
                    val awayVal = parseStatValue(awayValStr)

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(homeValStr, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(key, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(awayValStr, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Double progress bar comparison
                        val total = homeVal + awayVal
                        val homePercent = if (total > 0) homeVal / total else 0.5f

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(homePercent.coerceAtLeast(0.01f))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight((1f - homePercent).coerceAtLeast(0.01f))
                                    .background(MaterialTheme.colorScheme.secondary)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun parseStatValue(valStr: String): Float = try {
    valStr.replace("%", "").trim().toFloat()
} catch (e: Exception) {
    0f
}

private fun formatMatchTime(utcDate: String): String = try {
    val odt = OffsetDateTime.parse(utcDate)
    val local = odt.atZoneSameInstant(ZoneId.systemDefault())
    val formatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy • HH:mm")
    local.format(formatter)
} catch (e: Exception) {
    utcDate
}


private fun normalizePosition(pos: String?): String? {
    if (pos.isNullOrBlank()) return null
    val p = pos.trim().lowercase()
    return when {
        p.startsWith("g") -> "GK"
        p.startsWith("d") -> "DF"
        p.startsWith("m") -> "MF"
        p.startsWith("f") -> "FW"
        else -> p.uppercase().take(2)
    }
}
@Composable
private fun ErrorLayout(error: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(error, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) { Text("Réessayer") }
        }
    }
}

// FlowRow is not available in basic foundation in standard compose versions.
// Let's implement a simple custom FlowRow or use a normal Row with scrolling, or a grid.
// Let's implement a simple custom FlowRow so it doesn't crash if FlowRow is not found!
@Composable
private fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    // Basic Row with horizontal scrolling fallback as a very simple and robust solution.
    // That way we avoid any complex custom layout that could have compilation issues on different Compose versions.
    // Or we can write a simple custom layout. Let's do a Row with scroll or a Grid or a simple custom layout.
    // Let's implement a simple Row that wraps or a simple scrolling Row.
    // Let's do a scrolling Row which is standard and doesn't require complex layout code.
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = horizontalArrangement,
        modifier = modifier.fillMaxWidth()
    ) {
        item {
            Row(
                horizontalArrangement = horizontalArrangement,
                verticalAlignment = Alignment.CenterVertically
            ) {
                content()
            }
        }
    }
}
