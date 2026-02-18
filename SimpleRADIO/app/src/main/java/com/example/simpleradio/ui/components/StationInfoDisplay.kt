package com.example.simpleradio.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.simpleradio.data.local.entities.RadioStationEntity

/** Station name + country/bitrate info. Used in both Portrait and Landscape player layouts. */
@Composable
fun StationInfoDisplay(
        currentStation: RadioStationEntity?,
        modifier: Modifier = Modifier,
        textAlign: TextAlign = TextAlign.Center,
        nameMaxLines: Int = 1,
        nameStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineSmall
) {
        Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
        ) {
                Text(
                        text = currentStation?.name ?: "Station inconnue",
                        style = nameStyle,
                        color = Color.White,
                        maxLines = nameMaxLines,
                        textAlign = textAlign
                )
                Text(
                        text =
                                "${currentStation?.country ?: "Monde"} | ${currentStation?.bitrate ?: "?"} kbps",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        maxLines = 1
                )
                // DEBUG: Affichage de l'URL complète en petit
                currentStation?.url?.let { url ->
                        Text(
                                text = url,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray.copy(alpha = 0.5f),
                                maxLines = 1,
                                modifier = Modifier.padding(top = 4.dp).padding(horizontal = 16.dp),
                                textAlign = textAlign
                        )
                }
        }
}

/** Artist / Title metadata display. Used in both Portrait and Landscape player layouts. */
@Composable
fun MetadataInfoDisplay(
        artist: String?,
        title: String?,
        currentStation: RadioStationEntity?,
        modifier: Modifier = Modifier,
        textAlign: TextAlign = TextAlign.Center,
        artistMaxLines: Int = 1,
        titleMaxLines: Int = 1,
        artistStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleLarge,
        titleStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge
) {
        Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
        ) {
                // Uniquement si on a de VRAIES métadonnées de chanson
                if (!artist.isNullOrBlank() || !title.isNullOrBlank()) {
                        Text(
                                text = artist ?: "",
                                style = artistStyle,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = artistMaxLines,
                                textAlign = textAlign
                        )
                        if (!title.isNullOrBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                        text = title,
                                        style = titleStyle,
                                        color = Color.White,
                                        maxLines = titleMaxLines,
                                        textAlign = textAlign
                                )
                        }
                } else {
                        // Sinon on affiche juste un rappel discret de la station
                        Text(
                                text = currentStation?.name ?: "",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.Gray
                        )
                }
        }
}
