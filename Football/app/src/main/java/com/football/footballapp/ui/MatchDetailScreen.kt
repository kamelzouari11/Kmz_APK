package com.football.footballapp.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
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
    val tabs = listOf("Chaînes TV", "Événements", "Compositions")

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

            PrimaryScrollableTabRow(
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
                        0 -> TvChannelsTab(
                            tvChannels = detail?.tvChannels.orEmpty(),
                            status = detail?.tvStatus ?: TvCoverageStatus.UNKNOWN,
                            source = detail?.tvSource,
                            verifiedAt = detail?.tvVerifiedAt
                        )
                        1 -> EventsTab(
                            events = detail?.events ?: emptyList(),
                            snapshot = detail?.eventsSnapshot,
                            homeTeamId = match.homeTeam.id,
                            awayTeamId = match.awayTeam.id
                        )
                        2 -> LineupsTab(lineups = detail?.lineups)
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
private fun TvChannelsTab(
    tvChannels: List<TvChannelGroup>,
    status: TvCoverageStatus,
    source: String?,
    verifiedAt: String?
) {
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
                    text = if (status == TvCoverageStatus.NOT_BROADCAST) {
                        "Aucune diffusion TV confirmée pour cette date."
                    } else {
                        "Informations TV momentanément indisponibles."
                    },
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
                TvVerificationLabel(source = source, verifiedAt = verifiedAt)
            }
        }
    } else {
        val visibleGroups = remember(tvChannels) {
            tvChannels.mapNotNull { group ->
                group.copy(
                    channels = group.channels
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                ).takeIf { it.channels.isNotEmpty() }
            }
        }
        val channelCount = remember(visibleGroups) {
            visibleGroups.flatMap { it.channels }.distinct().size
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "tv-summary") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "$channelCount chaîne${if (channelCount > 1) "s" else ""} disponible${if (channelCount > 1) "s" else ""}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Europe • MENA • Amérique du Nord",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TvVerificationLabel(
                        source = source,
                        verifiedAt = verifiedAt,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
            items(visibleGroups, key = { it.country }) { group ->
                val isMena = group.country == "MENA (Middle East)"
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isMena) {
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.58f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isMena) {
                                    "🌍  MENA • beIN SPORTS"
                                } else {
                                    "${tvCountryFlag(group.country)}  ${group.country}"
                                },
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (isMena) {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                            Text(
                                text = group.channels.size.toString(),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(7.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            group.channels.forEach { channel ->
                                TvChannelPill(channel = channel, highlighted = isMena)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvChannelPill(channel: String, highlighted: Boolean) {
    val contentColor = if (highlighted) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        contentColor = contentColor,
        border = BorderStroke(
            width = 0.5.dp,
            color = contentColor.copy(alpha = 0.18f)
        )
    ) {
        Text(
            text = channel,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun TvVerificationLabel(
    source: String?,
    verifiedAt: String?,
    modifier: Modifier = Modifier
) {
    val verifiedTime = remember(verifiedAt) {
        verifiedAt?.let {
            runCatching {
                OffsetDateTime.parse(it)
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("HH:mm"))
            }.getOrNull()
        }
    }
    val parts = listOfNotNull(
        source?.takeIf { it.isNotBlank() }?.let { "Source : $it" },
        verifiedTime?.let { "vérifié à $it" }
    )
    if (parts.isNotEmpty()) {
        Column(modifier = modifier.fillMaxWidth()) {
            Text(
                text = parts.joinToString(" • "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
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
    var imageFailed by remember(url) { mutableStateOf(false) }
    if (url.isNullOrBlank() || imageFailed) {
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
            contentScale = ContentScale.Fit,
            onError = { imageFailed = true }
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
        modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
    ) {
        Text(
            text = label.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
        )
    }
}



@Composable
private fun EventsTab(
    events: List<MatchEvent>,
    snapshot: String?,
    homeTeamId: Int,
    awayTeamId: Int
) {
    if (!snapshot.isNullOrBlank()) {
        val snapshotLines = remember(snapshot, events, homeTeamId, awayTeamId) {
            snapshotEventLines(snapshot, events, homeTeamId, awayTeamId)
        }
        LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(snapshotLines, key = { it.index }) { eventLine ->
                val isHome = eventLine.side == SnapshotSide.HOME
                val eventIcon = eventLine.icon
                val isScoreChange = eventIcon == "⚽"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isHome) {
                        Arrangement.Start
                    } else {
                        Arrangement.End
                    }
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(if (isScoreChange) 0.94f else 0.86f),
                        shape = RoundedCornerShape(9.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isHome) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
                            }
                        )
                    ) {
                        val displayedText = when {
                            eventIcon == null -> eventLine.text
                            isHome -> "$eventIcon  ${eventLine.text}"
                            else -> "${eventLine.text}  $eventIcon"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = if (isScoreChange) 14.dp else 10.dp,
                                    vertical = if (isScoreChange) 11.dp else 7.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            eventLine.minute?.let { minute ->
                                Text(
                                    text = minute,
                                    fontSize = if (isScoreChange) 16.sp else 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            Text(
                                text = displayedText,
                                modifier = Modifier.weight(1f),
                                fontSize = if (isScoreChange) 16.sp else 12.sp,
                                lineHeight = if (isScoreChange) 21.sp else 16.sp,
                                fontWeight = if (isScoreChange) FontWeight.Bold else FontWeight.Normal,
                                textAlign = if (isHome) TextAlign.Start else TextAlign.End,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    } else if (events.isEmpty()) {
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

private enum class SnapshotSide { HOME, AWAY }

private data class SnapshotEventLine(
    val index: Int,
    val text: String,
    val side: SnapshotSide,
    val minute: String?,
    val icon: String?
)

private val SNAPSHOT_MINUTE_AT_START = Regex("""^\d{1,3}(?:\+\d{1,2})?['’]""")
private val SNAPSHOT_MINUTE_AT_END = Regex("""\d{1,3}(?:\+\d{1,2})?['’]$""")
private val SNAPSHOT_SCORE = Regex("""\(\s*\d+\s*-\s*\d+\s*\)""")

private fun snapshotEventIcon(line: String): String? {
    val normalized = line.lowercase()
    return when {
        SNAPSHOT_SCORE.containsMatchIn(line) -> "⚽"
        line.startsWith("Assist:", ignoreCase = true) -> null
        " / " in line -> null
        SNAPSHOT_MINUTE_AT_START.containsMatchIn(line) ||
            SNAPSHOT_MINUTE_AT_END.containsMatchIn(line) -> {
            if (
                "red card" in normalized ||
                "second yellow" in normalized ||
                "sent off" in normalized ||
                "carton rouge" in normalized
            ) {
                "🟥"
            } else {
                "🟨"
            }
        }
        else -> null
    }
}

private fun snapshotEventLines(
    snapshot: String,
    events: List<MatchEvent>,
    homeTeamId: Int,
    awayTeamId: Int
): List<SnapshotEventLine> {
    val lines = snapshot.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toList()
    val minuteMatches = lines.map { line ->
        SNAPSHOT_MINUTE_AT_START.find(line) ?: SNAPSHOT_MINUTE_AT_END.find(line)
    }
    val textWithoutMinute = lines.mapIndexed { index, line ->
        minuteMatches[index]?.let { line.removeRange(it.range).trim() } ?: line
    }
    val explicitSides = lines.map { line ->
        when {
            SNAPSHOT_MINUTE_AT_START.containsMatchIn(line) -> SnapshotSide.AWAY
            SNAPSHOT_MINUTE_AT_END.containsMatchIn(line) -> SnapshotSide.HOME
            else -> null
        }
    }

    var previousSide: SnapshotSide? = null
    return lines.mapIndexed { index, line ->
        val nextExplicitSide = explicitSides.getOrNull(index + 1)
        val side = when {
            line.startsWith("Assist:", ignoreCase = true) ->
                explicitSides[index] ?: previousSide ?: nextExplicitSide ?: SnapshotSide.HOME
            explicitSides[index] != null -> explicitSides[index]!!
            nextExplicitSide != null -> nextExplicitSide
            else -> previousSide ?: SnapshotSide.HOME
        }
        previousSide = side
        val ownMinute = minuteMatches[index]?.value
        val minute = ownMinute ?: when {
            SNAPSHOT_SCORE.containsMatchIn(line) -> minuteMatches.getOrNull(index + 1)?.value
            line.startsWith("Assist:", ignoreCase = true) ->
                minuteMatches.getOrNull(index - 1)?.value
            else -> null
        }
        val eventIcon = snapshotEventIcon(line)
        var displayedText = textWithoutMinute[index]
        if (eventIcon == "🟨" || eventIcon == "🟥") {
            val minuteNumbers = minute
                ?.dropLast(1)
                ?.split('+', limit = 2)
            val elapsed = minuteNumbers?.getOrNull(0)?.toIntOrNull()
            val extra = minuteNumbers?.getOrNull(1)?.toIntOrNull()
            val expectedTeamId = if (side == SnapshotSide.HOME) homeTeamId else awayTeamId
            val cardPlayer = events.firstOrNull { event ->
                event.type.equals("card", ignoreCase = true) &&
                    event.teamId == expectedTeamId &&
                    event.time.elapsed == elapsed &&
                    event.time.extra == extra
            }?.player?.name?.takeIf { it.isNotBlank() }
            if (displayedText.isBlank()) {
                displayedText = cardPlayer ?: "Joueur non indiqué"
            } else if (cardPlayer != null && !displayedText.contains(cardPlayer, ignoreCase = true)) {
                displayedText = "$displayedText  $cardPlayer"
            }
        }
        SnapshotEventLine(
            index = index,
            text = displayedText,
            side = side,
            minute = minute,
            icon = eventIcon
        )
    }
}

@Composable
private fun EventItemContent(event: MatchEvent, isHome: Boolean) {
    val isSubstitution = event.type.equals("subst", ignoreCase = true)
    val cardColor = if (isHome) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
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
                val titleColor = when {
                    event.type.equals("goal", true) -> Color(0xFF1E88E5)
                    isSubstitution -> Color(0xFF00C853)
                    event.type.equals("card", true) && event.detail.lowercase().contains("red") -> Color(0xFFFF1744)
                    else -> MaterialTheme.colorScheme.onSurface
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isSubstitution) {
                            event.assist?.name ?: "Entrant"
                        } else {
                            event.player.name ?: "Joueur"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    EventTimeBadge(time = event.time)
                }

                if (isSubstitution) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = event.player.name ?: "Sortant",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFC62828),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!isSubstitution) {
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
            PrimaryTabRow(
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
                                PlayerRow(player = player, isSubstitute = false, isAwayTeam = isAwayLineup)
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
                        PlayerRow(player = player, isSubstitute = true, isAwayTeam = isAwayLineup)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerRow(player: LineupPlayer, isSubstitute: Boolean, isAwayTeam: Boolean) {
    val containerColor = if (isSubstitute) {
        if (isAwayTeam) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.52f)
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
        }
    } else {
        if (isAwayTeam) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.68f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
        }
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

private fun tvCountryFlag(country: String): String = when (country) {
    "Albania" -> "🇦🇱"
    "Andorra" -> "🇦🇩"
    "Armenia" -> "🇦🇲"
    "Austria" -> "🇦🇹"
    "Azerbaijan" -> "🇦🇿"
    "Belgium" -> "🇧🇪"
    "Bosnia and Herzegovina" -> "🇧🇦"
    "Bulgaria" -> "🇧🇬"
    "Croatia" -> "🇭🇷"
    "Cyprus" -> "🇨🇾"
    "Czech Republic", "Czechia" -> "🇨🇿"
    "Denmark" -> "🇩🇰"
    "Estonia" -> "🇪🇪"
    "Finland" -> "🇫🇮"
    "France" -> "🇫🇷"
    "Germany" -> "🇩🇪"
    "Greece" -> "🇬🇷"
    "Hungary" -> "🇭🇺"
    "Iceland" -> "🇮🇸"
    "Ireland" -> "🇮🇪"
    "Italy" -> "🇮🇹"
    "Kosovo" -> "🇽🇰"
    "Latvia" -> "🇱🇻"
    "Liechtenstein" -> "🇱🇮"
    "Lithuania" -> "🇱🇹"
    "Luxembourg" -> "🇱🇺"
    "Malta" -> "🇲🇹"
    "Moldova" -> "🇲🇩"
    "Monaco" -> "🇲🇨"
    "Montenegro" -> "🇲🇪"
    "Netherlands" -> "🇳🇱"
    "North Macedonia" -> "🇲🇰"
    "Norway" -> "🇳🇴"
    "Poland" -> "🇵🇱"
    "Portugal" -> "🇵🇹"
    "Romania" -> "🇷🇴"
    "San Marino" -> "🇸🇲"
    "Serbia" -> "🇷🇸"
    "Slovakia" -> "🇸🇰"
    "Slovenia" -> "🇸🇮"
    "Spain" -> "🇪🇸"
    "Sweden" -> "🇸🇪"
    "Switzerland" -> "🇨🇭"
    "Turkey", "Türkiye" -> "🇹🇷"
    "Ukraine" -> "🇺🇦"
    "United Kingdom", "England" -> "🇬🇧"
    "United Kingdom & Ireland" -> "🇬🇧 🇮🇪"
    "Vatican City" -> "🇻🇦"
    "USA" -> "🇺🇸"
    "Canada" -> "🇨🇦"
    "Mexico" -> "🇲🇽"
    "Algeria" -> "🇩🇿"
    "Bahrain" -> "🇧🇭"
    "Egypt" -> "🇪🇬"
    "Iran" -> "🇮🇷"
    "Iraq" -> "🇮🇶"
    "Jordan" -> "🇯🇴"
    "Kuwait" -> "🇰🇼"
    "Lebanon" -> "🇱🇧"
    "Libya" -> "🇱🇾"
    "Mauritania" -> "🇲🇷"
    "Morocco" -> "🇲🇦"
    "Oman" -> "🇴🇲"
    "Palestine", "Palestinian Territory" -> "🇵🇸"
    "Qatar" -> "🇶🇦"
    "Saudi Arabia" -> "🇸🇦"
    "Sudan" -> "🇸🇩"
    "Syria" -> "🇸🇾"
    "Tunisia" -> "🇹🇳"
    "United Arab Emirates" -> "🇦🇪"
    "Yemen" -> "🇾🇪"
    "Australia" -> "🇦🇺"
    "Japan" -> "🇯🇵"
    "India" -> "🇮🇳"
    "China" -> "🇨🇳"
    "Israel" -> "🇮🇱"
    "Tajikistan" -> "🇹🇯"
    "Balkans", "Scandinavia & Baltics", "Europe" -> "🇪🇺"
    "MENA (Middle East)", "Africa" -> "🌍"
    else -> "🌐"
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
