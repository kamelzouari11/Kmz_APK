package com.kmz.shoppinglist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.kmz.shoppinglist.data.Article
import com.kmz.shoppinglist.data.Category
import com.kmz.shoppinglist.data.LocalIconProvider
import com.kmz.shoppinglist.data.Priority
import com.kmz.shoppinglist.ui.theme.*

/** Carte de catégorie pour l'écran niveau 1 */
@Composable
fun CategoryCard(
        category: Category,
        unboughtCount: Int,
        onClick: () -> Unit,
        onEditClick: () -> Unit
) {
        Card(
                modifier =
                        Modifier.fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clickable(onClick = onClick),
                colors = CardDefaults.cardColors(containerColor = DarkGray),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        // Icône de catégorie locale
                        val context = LocalContext.current
                        val iconProvider = remember { LocalIconProvider(context) }
                        val iconUrl = iconProvider.getIconPath(category.getIconIdSafe())

                        Box(
                                modifier =
                                        Modifier.size(72.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MediumGray),
                                contentAlignment = Alignment.Center
                        ) {
                                AsyncImage(
                                        model = iconUrl ?: "",
                                        contentDescription = null,
                                        modifier = Modifier.size(68.dp)
                                )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Nom de la catégorie
                        Text(
                                text = category.name,
                                color = White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        // Column à droite pour Badge (haut) et Stylo (bas)
                        Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                        ) {
                                // Badge nombre d'articles non achetés
                                if (unboughtCount > 0) {
                                        Box(
                                                modifier =
                                                        Modifier.size(28.dp)
                                                                .clip(CircleShape)
                                                                .background(AccentBlue),
                                                contentAlignment = Alignment.Center
                                        ) {
                                                Text(
                                                        text = unboughtCount.toString(),
                                                        color = White,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                )
                                        }
                                } else {
                                        // Spacer pour maintenir l'alignement si pas de badge
                                        Spacer(modifier = Modifier.height(28.dp))
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Bouton édition (stylo)
                                IconButton(onClick = onEditClick, modifier = Modifier.size(40.dp)) {
                                        Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Modifier",
                                                tint = AccentBlue,
                                                modifier = Modifier.size(24.dp)
                                        )
                                }
                        }

                        Spacer(modifier = Modifier.width(4.dp))
                }
        }
}

/** Carte d'article pour l'écran niveau 2 */
@Composable
fun ArticleCard(
        article: Article,
        onToggleBought: () -> Unit,
        onPriorityChange: (Priority) -> Unit,
        onEditClick: () -> Unit
) {
        var showPriorityDialog by remember { mutableStateOf(false) }

        val textColor = if (article.isBought) TextGray else White
        val backgroundColor = if (article.isBought) Color(0xFF0D0D0D) else DarkGray

        Card(
                modifier =
                        Modifier.fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clickable(onClick = onToggleBought),
                colors = CardDefaults.cardColors(containerColor = backgroundColor),
                shape = RoundedCornerShape(12.dp)
        ) {
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        // Bouton de priorité (petit rond coloré - taille réduite)
                        Box(
                                modifier =
                                        Modifier.size(14.dp)
                                                .clip(CircleShape)
                                                .background(getPriorityColor(article.priority))
                                                .border(
                                                        1.dp,
                                                        Color.White.copy(alpha = 0.2f),
                                                        CircleShape
                                                )
                                                .clickable { showPriorityDialog = true }
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        // Icône d'article locale
                        val context = LocalContext.current
                        val iconProvider = remember { LocalIconProvider(context) }
                        val iconUrl = iconProvider.getIconPath(article.getIconIdSafe())

                        Box(
                                modifier =
                                        Modifier.size(54.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                        if (article.isBought) Color(0xFF151515)
                                                        else MediumGray
                                                ),
                                contentAlignment = Alignment.Center
                        ) {
                                AsyncImage(
                                        model = iconUrl ?: "",
                                        contentDescription = null,
                                        modifier = Modifier.size(50.dp)
                                )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Nom de l'article
                        Text(
                                text = article.name,
                                color = textColor,
                                fontSize = 20.sp,
                                fontWeight =
                                        if (article.isBought) FontWeight.Normal
                                        else FontWeight.Medium,
                                textDecoration =
                                        if (article.isBought) TextDecoration.LineThrough
                                        else TextDecoration.None,
                                modifier = Modifier.weight(1f)
                        )

                        // Bouton édition (stylo)
                        IconButton(onClick = onEditClick, modifier = Modifier.size(32.dp)) {
                                Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Modifier",
                                        tint = AccentBlue,
                                        modifier = Modifier.size(20.dp)
                                )
                        }
                }
        }

        // Dialog de sélection de priorité
        if (showPriorityDialog) {
                PriorityDialog(
                        currentPriority = article.priority,
                        onPrioritySelected = { priority ->
                                onPriorityChange(priority)
                                showPriorityDialog = false
                        },
                        onDismiss = { showPriorityDialog = false }
                )
        }
}

/** Dialog de sélection de priorité */
@Composable
fun PriorityDialog(
        currentPriority: Priority,
        onPrioritySelected: (Priority) -> Unit,
        onDismiss: () -> Unit
) {
        Dialog(onDismissRequest = onDismiss) {
                Card(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkGray),
                        shape = RoundedCornerShape(20.dp)
                ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                        text = "Choisir la priorité",
                                        color = White,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                Priority.values().forEach { priority ->
                                        PriorityOption(
                                                priority = priority,
                                                isSelected = priority == currentPriority,
                                                onClick = { onPrioritySelected(priority) }
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                }
                        }
                }
        }
}

@Composable
fun PriorityOption(priority: Priority, isSelected: Boolean, onClick: () -> Unit) {
        val priorityName =
                when (priority) {
                        Priority.URGENT -> "🔴 Très important"
                        Priority.IMPORTANT -> "🟠 Important"
                        Priority.NORMAL -> "🔘 Normal"
                        Priority.OPTIONAL -> "⚪ Optionnel"
                }

        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) MediumGray else Color.Transparent)
                                .clickable(onClick = onClick)
                                .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
                Box(
                        modifier =
                                Modifier.size(24.dp)
                                        .clip(CircleShape)
                                        .background(getPriorityColor(priority))
                                        .border(
                                                2.dp,
                                                if (isSelected) White else Color.Transparent,
                                                CircleShape
                                        )
                )

                Spacer(modifier = Modifier.width(16.dp))

                Text(text = priorityName, color = White, fontSize = 16.sp)
        }
}

fun getPriorityColor(priority: Priority): Color {
        return when (priority) {
                Priority.URGENT -> PriorityUrgent
                Priority.IMPORTANT -> PriorityImportant
                Priority.NORMAL -> PriorityNormal
                Priority.OPTIONAL -> PriorityOptional
        }
}

/** Dialog de confirmation de suppression */
@Composable
fun ConfirmDeleteDialog(
        title: String,
        message: String,
        onConfirm: () -> Unit,
        onDismiss: () -> Unit
) {
        AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = DarkGray,
                title = { Text(text = title, color = White, fontWeight = FontWeight.Bold) },
                text = { Text(text = message, color = TextGray) },
                confirmButton = {
                        TextButton(onClick = onConfirm) {
                                Text(
                                        text = "Supprimer",
                                        color = AccentRed,
                                        fontWeight = FontWeight.Bold
                                )
                        }
                },
                dismissButton = {
                        TextButton(onClick = onDismiss) { Text(text = "Annuler", color = TextGray) }
                }
        )
}

/** Dialog pour ajouter une catégorie ou un article */
@Composable
fun AddItemDialog(
        title: String,
        placeholder: String,
        onConfirm: (String) -> Unit,
        onDismiss: () -> Unit
) {
        var text by remember { mutableStateOf("") }

        AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = DarkGray,
                title = { Text(text = title, color = White, fontWeight = FontWeight.Bold) },
                text = {
                        OutlinedTextField(
                                value = text,
                                onValueChange = { text = it },
                                placeholder = { Text(placeholder, color = TextGray) },
                                colors =
                                        OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = White,
                                                unfocusedTextColor = White,
                                                focusedBorderColor = AccentBlue,
                                                unfocusedBorderColor = MediumGray,
                                                cursorColor = AccentBlue
                                        ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                        )
                },
                confirmButton = {
                        TextButton(
                                onClick = {
                                        if (text.isNotBlank()) {
                                                onConfirm(text.trim())
                                        }
                                }
                        ) {
                                Text(
                                        text = "Ajouter",
                                        color = AccentBlue,
                                        fontWeight = FontWeight.Bold
                                )
                        }
                },
                dismissButton = {
                        TextButton(onClick = onDismiss) { Text(text = "Annuler", color = TextGray) }
                }
        )
}
