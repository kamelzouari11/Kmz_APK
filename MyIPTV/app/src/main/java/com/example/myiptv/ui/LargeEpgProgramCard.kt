package com.example.myiptv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.myiptv.data.EpgArtwork
import com.example.myiptv.data.EpgArtworkRepository
import com.example.myiptv.ui.theme.MyIptvPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException

/** Shared by full-size guide, search and programme-detail views only. */
@Composable
internal fun LargeEpgProgramCard(
    originalTitle: String,
    description: String,
    country: String,
    timeLabel: String,
    channelName: String = "",
    channelLogoUrl: String? = null,
    current: Boolean = false,
    translationButtonModifier: Modifier = Modifier,
    programImageUrl: String = "",
    movieOnly: Boolean = false,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    Surface(
        color = if (current) MyIptvPalette.ActiveSurface else MyIptvPalette.Card,
        border = BorderStroke(if (current) 2.dp else 1.dp, if (current) MyIptvPalette.Positive else MyIptvPalette.Border),
        shape = MaterialTheme.shapes.medium,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(16.dp)) {
            val wide = maxWidth >= 600.dp
            val artworkWidth = if (wide) minOf(maxWidth * 0.28f, 280.dp) else minOf(maxWidth, 220.dp)
            val details: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (channelName.isNotBlank()) {
                        Text(channelName, color = MyIptvPalette.TextSecondary, style = MaterialTheme.typography.labelLarge)
                    }
                    Text(
                        if (current) "EN COURS · $timeLabel" else timeLabel,
                        color = if (current) MyIptvPalette.Positive else MyIptvPalette.Primary,
                        fontWeight = FontWeight.Bold,
                    )
                    EpgTranslatedText(originalTitle, description, country, translationButtonModifier)
                    actions()
                }
            }
            if (wide) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    EpgArtworkPanel(originalTitle, country, channelLogoUrl, Modifier.width(artworkWidth), 390, programImageUrl, movieOnly)
                    Column(Modifier.weight(1f)) { details() }
                }
            } else {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    EpgArtworkPanel(originalTitle, country, channelLogoUrl, Modifier.width(artworkWidth).align(Alignment.CenterHorizontally), 160, programImageUrl, movieOnly)
                    details()
                }
            }
        }
    }
}

private data class ArtworkState(val loading: Boolean = true, val artwork: EpgArtwork? = null)

@Composable
private fun EpgArtworkPanel(
    originalTitle: String,
    country: String,
    channelLogoUrl: String?,
    modifier: Modifier,
    imageHeight: Int,
    programImageUrl: String,
    movieOnly: Boolean,
) {
    var rejectedProgramImage by remember(programImageUrl) { mutableStateOf(false) }
    val state by produceState(ArtworkState(), originalTitle, country, programImageUrl, movieOnly, rejectedProgramImage) {
        value = ArtworkState()
        value = try {
            ArtworkState(loading = false, artwork = when {
                programImageUrl.startsWith("https://") && !rejectedProgramImage -> EpgArtwork(listOf(
                    com.example.myiptv.data.EpgArtworkImage(programImageUrl, originalTitle, programImageUrl, "EPG"),
                ))
                movieOnly -> EpgArtworkRepository.findMovie(originalTitle)
                else -> EpgArtworkRepository.find(originalTitle, country)
            })
        } catch (_: TimeoutCancellationException) {
            ArtworkState(loading = false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ArtworkState(loading = false)
        }
    }
    val uriHandler = LocalUriHandler.current
    val artwork = state.artwork
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(color = MyIptvPalette.Surface, shape = MaterialTheme.shapes.medium) {
            Box(Modifier.fillMaxWidth().height((if (artwork?.clubs == true) minOf(imageHeight, 160) else imageHeight).dp), contentAlignment = Alignment.Center) {
                if (artwork == null && state.loading) {
                    Text(
                        "Recherche d’une illustration…",
                        modifier = Modifier.padding(12.dp), color = MyIptvPalette.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else if (artwork == null && channelLogoUrl?.startsWith("http") == true) {
                    AsyncImage(
                        model = channelLogoUrl,
                        contentDescription = "Logo de $originalTitle",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(18.dp),
                    )
                } else if (artwork == null) {
                    Text(
                        "Illustration indisponible",
                        modifier = Modifier.padding(12.dp), color = MyIptvPalette.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        artwork.images.forEach { picture ->
                            var failed by remember(picture.url) { mutableStateOf(false) }
                            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                if (failed) Text("Image indisponible", color = MyIptvPalette.TextSecondary)
                                else AsyncImage(
                                    model = picture.url,
                                    contentDescription = "Illustration : ${picture.title}",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize(),
                                    onError = {
                                        if (picture.url == programImageUrl) rejectedProgramImage = true
                                        else failed = true
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        if (artwork != null) {
            artwork.images.forEach { picture ->
                TextButton(
                    onClick = { runCatching { uriHandler.openUri(picture.sourceUrl) } },
                    modifier = Modifier.tvFocusBorder(),
                    contentPadding = PaddingValues(4.dp),
                ) {
                    Text("Source", color = MyIptvPalette.TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
