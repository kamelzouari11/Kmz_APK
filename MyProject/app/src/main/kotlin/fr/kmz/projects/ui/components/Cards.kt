package fr.kmz.projects.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LotCard(
        lotName: String,
        total: String,
        isValidated: Boolean,
        onEdit: () -> Unit,
        onNavigate: () -> Unit,
        modifier: Modifier = Modifier
) {
        Card(
                modifier =
                        modifier.fillMaxWidth()
                                .padding(8.dp)
                                .combinedClickable(onClick = onNavigate, onLongClick = onEdit),
                colors =
                        CardDefaults.cardColors(
                                containerColor =
                                        if (isValidated) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surface
                        )
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Text(
                                text = lotName,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                        )
                        Text(
                                text = total,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.End
                        )
                }

                // Card is clickable to navigate — buttons "Valider" and "Détails" removed
        }
}

@Composable
fun RecapCard(title: String, total: String, modifier: Modifier = Modifier) {
        Card(
                modifier = modifier.fillMaxWidth().padding(8.dp),
                colors =
                        CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                        )
                        Text(
                                text = total,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.End
                        )
                }
        }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SousLotCard(
        sousLotName: String,
        total: String,
        isValidated: Boolean,
        nbArticles: Int,
        onEdit: () -> Unit,
        onNavigate: () -> Unit,
        modifier: Modifier = Modifier
) {
        Card(
                modifier =
                        modifier.fillMaxWidth()
                                .padding(8.dp)
                                .combinedClickable(onClick = onNavigate, onLongClick = onEdit),
                colors =
                        CardDefaults.cardColors(
                                containerColor =
                                        if (isValidated) MaterialTheme.colorScheme.tertiaryContainer
                                        else MaterialTheme.colorScheme.surface
                        )
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        text = sousLotName,
                                        style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                        text = "$nbArticles Articles",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                )
                        }

                        Text(
                                text = total,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.End
                        )
                }

                // Card is clickable to navigate — buttons "Valider" and "Articles" removed
        }
}
