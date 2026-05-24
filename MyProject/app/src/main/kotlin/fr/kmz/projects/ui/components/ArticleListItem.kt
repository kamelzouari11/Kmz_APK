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
fun ArticleCard(
        nom: String,
        quantite: Int,
        unite: String,
        prixUnitaire: String,
        total: String,
        onEdit: () -> Unit,
        isTabletMode: Boolean = false,
        modifier: Modifier = Modifier
) {
        val paddingAmount = if (isTabletMode) 4.dp else 8.dp
        val elevation = if (isTabletMode) 1.dp else 4.dp // less elevation for row feel

        Card(
                modifier =
                        modifier.fillMaxWidth()
                                .padding(horizontal = paddingAmount, vertical = paddingAmount / 2)
                                .combinedClickable(
                                        onClick = onEdit, /* easy click in tableur mode */
                                        onLongClick = onEdit
                                ),
                elevation = CardDefaults.cardElevation(defaultElevation = elevation),
                shape =
                        if (isTabletMode) androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                        else CardDefaults.shape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
                Row(
                        modifier =
                                Modifier.fillMaxWidth().padding(if (isTabletMode) 8.dp else 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        if (isTabletMode) {
                                // Table row mode: Nom | Qté | Prix U. | Total
                                Text(
                                        text = nom,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(2f)
                                )
                                Text(
                                        text = "$quantite $unite",
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.weight(1f)
                                )
                                Text(
                                        text = prixUnitaire,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(1f)
                                )
                                Text(
                                        text = total,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color.White,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.End
                                )
                        } else {
                                // Original Card Mode
                                Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                                text = nom,
                                                style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                                text = "$quantite $unite × $prixUnitaire",
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
                }
        }
}
