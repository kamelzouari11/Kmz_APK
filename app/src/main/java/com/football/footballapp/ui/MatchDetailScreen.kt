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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
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
    viewModel: MatchDetailViewModel,
    onBack: () -> Unit
) {
    val match = viewModel.match
    val state by viewModel.state.collectAsState()

    LaunchedEffect(match.id) {
        viewModel.loadMatchDetail()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Détails du match", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.loadMatchDetail(forceRefresh = true)
                    }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Actualiser")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            MatchHeaderCard(match = match)

            var selectedTab by remember { mutableIntStateOf(0) }
            val tabs = listOf("Chaînes TV", "Événements", "Compositions", "Stats")

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
                        0 -> TvChannelsTab(tvChannels = detail?.tvChannels ?: emptyList())
                        1 -> EventsTab(
                            events = detail?.events ?: emptyList(),
                            homeTeamId = match.homeTeam.id,
                            awayTeamId = match.awayTeam.id
                        )
                        2 -> LineupsTab(lineups = detail?.lineups)
                        3 -> StatsTab(
                            stats = detail?.stats ?: emptyList(),
                            homeTeamId = match.homeTeam.id,
                            awayTeamId = match.awayTeam.id
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchHeaderCard(match: Match) {
    val gradientColors = listOf(
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(gradientColors))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Competition Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!match.competitionFlag.isNullOrBlank()) {
                    AsyncImage(
                        model = match.competitionFlag,
                        contentDescription = match.competitionCountry,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .border(0.5.dp, Color.LightGray, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = "${match.competitionCountry ?: ""} • ${match.competitionName}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Score and Teams Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Home Team
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TeamLogoLarge(url = match.homeTeam.logoUrl, name = match.homeTeam.name)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = match.homeTeam.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Score Block
                Column(
                    modifier = Modifier.width(100.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val hasScore = match.score.home != null && match.score.away != null
                    if (hasScore) {
                        Text(
                            text = "${match.score.home} - ${match.score.away}",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Text(
                            text = "VS",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Match Status Badge
                    StatusBadge(status = match.status, statusLabel = match.statusLabel, elapsed = match.minute)
                }

                // Away Team
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TeamLogoLarge(url = match.awayTeam.logoUrl, name = match.awayTeam.name)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = match.awayTeam.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Date / Time Info
            Text(
                text = formatMatchTime(match.utcDate),
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TeamLogoLarge(url: String?, name: String) {
    if (url.isNullOrBlank()) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.take(2).uppercase(),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        AsyncImage(
            model = url,
            contentDescription = name,
            modifier = Modifier
                .size(60.dp)
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
private fun TvChannelsTab(tvChannels: List<TvChannelGroup>) {
    if (tvChannels.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Aucune chaîne de télévision disponible.\nCes données sont collectées à l'approche du match.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(tvChannels) { group ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = group.country,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            group.channels.forEach { channel ->
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(channel, fontWeight = FontWeight.Medium) }
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
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(events.sortedBy { it.time.elapsed }) { event ->
                val isHome = event.teamId == homeTeamId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = if (isHome) Arrangement.Start else Arrangement.End
                ) {
                    if (isHome) {
                        EventTimeBadge(time = event.time)
                        Spacer(modifier = Modifier.width(8.dp))
                        EventItemContent(event = event, isHome = true)
                    } else {
                        EventItemContent(event = event, isHome = false)
                        Spacer(modifier = Modifier.width(8.dp))
                        EventTimeBadge(time = event.time)
                    }
                }
            }
        }
    }
}

@Composable
private fun EventTimeBadge(time: EventTime) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val label = if (time.extra != null) "${time.elapsed}+${time.extra}" else "${time.elapsed}"
        Text(
            text = "$label'",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun EventItemContent(event: MatchEvent, isHome: Boolean) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        modifier = Modifier.widthIn(max = 260.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val emoji = when (event.type.lowercase()) {
                "goal" -> "⚽"
                "card" -> if (event.detail.lowercase().contains("red")) "🟥" else "🟨"
                "subst" -> "🔄"
                else -> "ℹ️"
            }

            if (isHome) {
                Text(emoji, fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column {
                Text(
                    text = event.player.name ?: "Joueur",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                if (!event.assist?.name.isNullOrBlank()) {
                    Text(
                        text = "Passe: ${event.assist?.name}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (!event.detail.isNullOrBlank() && event.type.lowercase() != "card") {
                    Text(
                        text = event.detail,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!isHome) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(emoji, fontSize = 20.sp)
            }
        }
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

            val lineup = if (selectedTeamTab == 0) lineups.home else lineups.away

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Tactical formation and Coach
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Formation: ${lineup.formation ?: "Inconnue"}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        if (lineup.coach?.name != null) {
                            Text(
                                text = "Entraîneur: ${lineup.coach.name}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Titulaires Header
                item {
                    Text(
                        text = "Titulaires",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(lineup.startXI) { player ->
                    PlayerRow(player = player)
                }

                // Remplaçants Header
                item {
                    Text(
                        text = "Remplaçants",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                items(lineup.substitutes) { player ->
                    PlayerRow(player = player)
                }
            }
        }
    }
}

@Composable
private fun PlayerRow(player: LineupPlayer) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = player.number?.toString() ?: "-",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = player.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            player.position?.let {
                Text(
                    text = it.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
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
