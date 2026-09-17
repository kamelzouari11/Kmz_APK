package com.example.myiptv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.myiptv.data.EpgProgram
import com.example.myiptv.data.SavedChannel
import com.example.myiptv.ui.theme.MyIptvPalette
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PortraitFullEpgOverlay(
    channel: SavedChannel,
    programs: List<EpgProgram>,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
) {
    val now = Instant.now().epochSecond
    val currentIndex = remember(programs, now) {
        programs.indexOfFirst { program ->
            val start = program.startEpochSeconds
            val stop = program.stopEpochSeconds
            start != null && stop != null && now >= start && now < stop
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MyIptvPalette.Background.copy(alpha = 0.72f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
                    .safeDrawingPadding(),
                color = MyIptvPalette.Surface,
                border = BorderStroke(2.dp, MyIptvPalette.Primary),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(
                    Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "EPG · ACTUEL ET SUIVANTS",
                                color = MyIptvPalette.Primary,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                channel.name,
                                color = MyIptvPalette.White,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        MenuButton(text = "Fermer", onClick = onDismiss)
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MenuButton(
                            text = "Lire la chaîne",
                            active = true,
                            onClick = onPlay,
                            modifier = Modifier.weight(1f),
                        )
                        MenuButton(
                            text = "Actualiser",
                            enabled = !loading,
                            onClick = onRetry,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        "Programme actuel et trois suivants · indépendant des filtres du menu EPG",
                        color = MyIptvPalette.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    when {
                        loading -> Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = MyIptvPalette.Primary)
                        }
                        error != null -> Column(
                            Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(error, color = MyIptvPalette.Warning)
                            MenuButton(text = "Réessayer", active = true, onClick = onRetry)
                        }
                        else -> EpgProgramPager(
                            pageKey = "${channel.profileId}:${channel.streamId}",
                            count = programs.size,
                            initialPage = currentIndex.coerceAtLeast(0),
                            modifier = Modifier.fillMaxSize(),
                        ) { index ->
                            val program = programs[index]
                            LargeEpgProgramCard(
                                originalTitle = program.title,
                                description = program.description,
                                country = channel.countryCode,
                                timeLabel = listOfNotNull(program.epgDateLabel(), program.timeRange).joinToString(" · "),
                                channelName = channel.name,
                                channelLogoUrl = channel.iconUrl,
                                current = program.startEpochSeconds?.let { start ->
                                    program.stopEpochSeconds?.let { stop -> now >= start && now < stop }
                                } == true,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun EpgProgram.epgDateLabel(): String? = startEpochSeconds?.let { seconds ->
    DATE_FORMATTER.format(Instant.ofEpochSecond(seconds))
}

private val DATE_FORMATTER = DateTimeFormatter
    .ofPattern("EEEE d MMMM yyyy", Locale.FRENCH)
    .withZone(ZoneId.of("Africa/Tunis"))
