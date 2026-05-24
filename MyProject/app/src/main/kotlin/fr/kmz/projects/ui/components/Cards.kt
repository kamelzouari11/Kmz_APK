package fr.kmz.projects.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
        isTabletMode: Boolean = false,
        modifier: Modifier = Modifier
) {
        val paddingAmount = if (isTabletMode) 4.dp else 8.dp
        val elevation = if (isTabletMode) 1.dp else 4.dp
        Card(
                modifier =
                        modifier.fillMaxWidth()
                                .padding(horizontal = paddingAmount, vertical = paddingAmount / 2)
                                .combinedClickable(onClick = onNavigate, onLongClick = onEdit),
                elevation = CardDefaults.cardElevation(defaultElevation = elevation),
                shape =
                        if (isTabletMode) androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                        else CardDefaults.shape,
                colors =
                        CardDefaults.cardColors(
                                containerColor =
                                        if (isValidated) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surface
                        )
        ) {
                Row(
                        modifier =
                                Modifier.fillMaxWidth().padding(if (isTabletMode) 8.dp else 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Text(
                                text = lotName,
                                style =
                                        if (isTabletMode) MaterialTheme.typography.bodyMedium
                                        else MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                        )
                        Text(
                                text = total,
                                style =
                                        if (isTabletMode) MaterialTheme.typography.bodyMedium
                                        else MaterialTheme.typography.titleMedium,
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
        isTabletMode: Boolean = false,
        modifier: Modifier = Modifier
) {
        val paddingAmount = if (isTabletMode) 4.dp else 8.dp
        val elevation = if (isTabletMode) 1.dp else 4.dp

        Card(
                modifier =
                        modifier.fillMaxWidth()
                                .padding(horizontal = paddingAmount, vertical = paddingAmount / 2)
                                .combinedClickable(onClick = onNavigate, onLongClick = onEdit),
                elevation = CardDefaults.cardElevation(defaultElevation = elevation),
                shape =
                        if (isTabletMode) androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                        else CardDefaults.shape,
                colors =
                        CardDefaults.cardColors(
                                containerColor =
                                        if (isValidated) MaterialTheme.colorScheme.tertiaryContainer
                                        else MaterialTheme.colorScheme.surface
                        )
        ) {
                Row(
                        modifier =
                                Modifier.fillMaxWidth().padding(if (isTabletMode) 8.dp else 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        text = sousLotName,
                                        style =
                                                if (isTabletMode)
                                                        MaterialTheme.typography.bodyMedium
                                                else MaterialTheme.typography.titleMedium
                                )
                                if (!isTabletMode) {
                                        Text(
                                                text = "$nbArticles Articles",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                        )
                                }
                        }

                        Text(
                                text = total,
                                style =
                                        if (isTabletMode) MaterialTheme.typography.bodyMedium
                                        else MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.End
                        )
                }

                // Card is clickable to navigate — buttons "Valider" and "Articles" removed
        }
}
