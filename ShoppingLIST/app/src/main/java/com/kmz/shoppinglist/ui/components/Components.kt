package com.kmz.shoppinglist.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.kmz.shoppinglist.R
import com.kmz.shoppinglist.data.Article
import com.kmz.shoppinglist.data.Category
import com.kmz.shoppinglist.data.LocalIconProvider
import com.kmz.shoppinglist.data.Priority
import com.kmz.shoppinglist.ui.theme.*

/** Carte de catégorie pour l'écran niveau 1 (Grille 2x2) */
@Composable
fun CategoryCard(category: Category, unboughtCount: Int, onClick: () -> Unit) {
        Column(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
                // Icône de catégorie locale
                val context = LocalContext.current
                val iconProvider = remember { LocalIconProvider(context) }
                val iconUrl = iconProvider.getIconPath(category.getIconIdSafe())

                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.Transparent),
                        contentAlignment = Alignment.Center
                ) {
                        AsyncImage(
                                model = iconUrl ?: "",
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(0.85f)
                        )

                        // Badge nombre d'articles non achetés (en haut à droite)
                        if (unboughtCount > 0) {
                                Box(
                                        modifier =
                                                Modifier.align(Alignment.TopEnd)
                                                        .padding(6.dp)
                                                        .size(24.dp)
                                                        .clip(CircleShape)
                                                        .background(TextGray),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Text(
                                                text = unboughtCount.toString(),
                                                color = White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                        )
                                }
                        }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Nom de la catégorie (petite police, blanc, sans fond)
                Text(
                        text = category.name,
                        color = White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        modifier = Modifier.padding(bottom = 8.dp)
                )
        }
}

/** Carte spéciale "Toutes" (Grille 2x2) */
@Composable
fun AllArticlesCard(unboughtCount: Int, onClick: () -> Unit) {
        Column(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.Transparent),
                        contentAlignment = Alignment.Center
                ) {
                        // Tente de charger l'image png personnalisée via R.drawable
                        AsyncImage(
                                model = R.drawable.liste_courses,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(0.9f),
                                onError = {
                                        // Optionnel : Log ou état si besoin
                                }
                        )

                        // Icone de secours si le png n'est pas trouvé ou invalide
                        // (On la met par-dessus ou on l'affiche si le png vide)
                        // Note: Coil ne gère pas nativement un 'placeholder' qui survit à l'erreur
                        // sans condition,
                        // mais on peut utiliser un état ou simplement mettre l'icône derrière si le
                        // png est transparent.
                        Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = null,
                                tint = AccentBlue.copy(alpha = 0.3f),
                                modifier = Modifier.size(48.dp)
                        )

                        // Badge nombre d'articles non achetés
                        if (unboughtCount > 0) {
                                Box(
                                        modifier =
                                                Modifier.align(Alignment.TopEnd)
                                                        .padding(6.dp)
                                                        .size(24.dp)
                                                        .clip(CircleShape)
                                                        .background(TextGray),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Text(
                                                text = unboughtCount.toString(),
                                                color = White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                        )
                                }
                        }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                        text = "📋 Toutes",
                        color = White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        modifier = Modifier.padding(bottom = 8.dp)
                )
        }
}

/** Carte d'article pour l'écran niveau 2 */
@Composable
fun ArticleCard(
        article: Article,
        onToggleBought: () -> Unit,
        onPriorityChange: (Priority) -> Unit
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
                                                .background(Color.Transparent),
                                contentAlignment = Alignment.Center
                        ) {
                                AsyncImage(
                                        model = iconUrl ?: "",
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
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
                        Priority.URGENT -> "Très important"
                        Priority.IMPORTANT -> "Important"
                        Priority.NORMAL -> "Normal"
                        Priority.OPTIONAL -> "Optionnel"
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

/** B1 : Version texte condensée (2 colonnes) */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArticleTextItem(
        article: Article,
        onClick: () -> Unit,
        onLongClick: () -> Unit = {},
        onPriorityChange: (Priority) -> Unit = {}
) {
        var showPriorityDialog by remember { mutableStateOf(false) }
        val priorityColor =
                when (article.priority) {
                        Priority.URGENT -> PriorityUrgent
                        Priority.IMPORTANT -> PriorityImportant
                        Priority.NORMAL -> PriorityNormal
                        Priority.OPTIONAL -> PriorityOptional
                }

        Surface(
                modifier =
                        Modifier.fillMaxWidth()
                                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
                color = Color.Transparent,
                shape = RoundedCornerShape(8.dp)
        ) {
                Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                        Text(
                                text = article.name,
                                color = White,
                                fontSize = 15.sp,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                        )

                        Box(
                                modifier =
                                        Modifier.size(14.dp)
                                                .clip(CircleShape)
                                                .background(priorityColor)
                                                .border(
                                                        1.dp,
                                                        Color.White.copy(alpha = 0.2f),
                                                        CircleShape
                                                )
                                                .clickable { showPriorityDialog = true }
                        )
                }

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
}

/** B1 : Version texte pour articles achetés */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BoughtArticleTextItem(
        article: Article,
        onClick: () -> Unit = {},
        onLongClick: () -> Unit = {}
) {
        Surface(
                modifier =
                        Modifier.fillMaxWidth()
                                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
                color = Color.Transparent
        ) {
                Text(
                        text = article.name,
                        color = TextGray.copy(alpha = 0.8f), // Gris plus clair / lisible
                        fontSize = 15.sp,
                        textDecoration = TextDecoration.LineThrough,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
        }
}

/** B2 : Version icônes 2 par 2 (remplit l'espace) */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArticleIconItem(
        article: Article,
        onClick: () -> Unit,
        onLongClick: () -> Unit = {},
        onPriorityChange: (Priority) -> Unit = {}
) {
        var showPriorityDialog by remember { mutableStateOf(false) }
        val context = LocalContext.current
        val iconProvider = remember { LocalIconProvider(context) }
        val iconUrl = iconProvider.getIconPath(article.getIconIdSafe())
        val priorityColor = getPriorityColor(article.priority)

        Column(
                modifier =
                        Modifier.fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Transparent),
                        contentAlignment = Alignment.Center
                ) {
                        AsyncImage(
                                model = iconUrl ?: "",
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                        )

                        // Indicateur de priorité
                        Box(
                                modifier =
                                        Modifier.align(Alignment.TopEnd)
                                                .padding(6.dp)
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(priorityColor)
                                                .border(
                                                        1.dp,
                                                        Color.White.copy(alpha = 0.4f),
                                                        CircleShape
                                                )
                                                .clickable { showPriorityDialog = true }
                        )
                }

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

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                        text = article.name,
                        color = White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.padding(bottom = 6.dp)
                )
        }
}

/**
 * Barre de boutons d'action commune en bas des écrans Comprend : Filtre de priorité (gauche), Micro
 * (milieu), Ajout (droite)
 */
@Composable
fun BottomActionButtons(
        onFilterClick: (Priority) -> Unit,
        onMicClick: () -> Unit,
        onAddClick: () -> Unit,
        filterPriority: Priority,
        modifier: Modifier = Modifier
) {
        var showPriorityList by remember { mutableStateOf(false) }

        Box(modifier = modifier.fillMaxWidth()) {
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        // Bouton Filtre (gauche)
                        FloatingActionButton(
                                onClick = { showPriorityList = !showPriorityList },
                                containerColor = getPriorityColor(filterPriority),
                                contentColor =
                                        if (filterPriority == Priority.NORMAL) Black else White,
                                shape = CircleShape,
                                modifier = Modifier.size(56.dp)
                        ) {
                                Icon(
                                        imageVector = Icons.Default.FilterList,
                                        contentDescription = "Filtrer par priorité",
                                        modifier = Modifier.size(28.dp)
                                )
                        }

                        // Bouton Microphone Vert (milieu)
                        FloatingActionButton(
                                onClick = onMicClick,
                                containerColor = AccentGreen,
                                contentColor = White,
                                shape = CircleShape,
                                modifier = Modifier.size(56.dp)
                        ) {
                                Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Saisie vocale",
                                        modifier = Modifier.size(28.dp)
                                )
                        }

                        // Bouton Ajouter Bleu (droite)
                        FloatingActionButton(
                                onClick = onAddClick,
                                containerColor = AccentBlue,
                                contentColor = White,
                                shape = CircleShape,
                                modifier = Modifier.size(56.dp)
                        ) {
                                Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Ajouter",
                                        modifier = Modifier.size(28.dp)
                                )
                        }
                }

                // Liste de sélection de priorité (Flottante au-dessus du bouton filtre)
                // Placée APRES le Row pour être dessinée par-dessus (Z-order)
                if (showPriorityList) {
                        Column(
                                modifier =
                                        Modifier.align(Alignment.BottomStart)
                                                .padding(start = 24.dp, bottom = 90.dp)
                                                .background(MediumGray, RoundedCornerShape(16.dp))
                                                .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                                Priority.values().forEach { priority ->
                                        Box(
                                                modifier =
                                                        Modifier.size(36.dp)
                                                                .clip(CircleShape)
                                                                .background(
                                                                        getPriorityColor(priority)
                                                                )
                                                                .border(
                                                                        if (priority ==
                                                                                        filterPriority
                                                                        )
                                                                                2.dp
                                                                        else 1.dp,
                                                                        if (priority ==
                                                                                        filterPriority
                                                                        )
                                                                                (if (priority ==
                                                                                                Priority.NORMAL
                                                                                )
                                                                                        Black
                                                                                else White)
                                                                        else
                                                                                (if (priority ==
                                                                                                Priority.NORMAL
                                                                                )
                                                                                        Black.copy(
                                                                                                alpha =
                                                                                                        0.2f
                                                                                        )
                                                                                else
                                                                                        White.copy(
                                                                                                alpha =
                                                                                                        0.2f
                                                                                        )),
                                                                        CircleShape
                                                                )
                                                                .clickable {
                                                                        onFilterClick(priority)
                                                                        showPriorityList = false
                                                                }
                                        )
                                }
                        }
                }
        }
}
