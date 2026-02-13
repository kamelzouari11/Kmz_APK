package com.kmz.shoppinglist.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kmz.shoppinglist.data.Article
import com.kmz.shoppinglist.data.LocalIconProvider
import com.kmz.shoppinglist.data.Priority
import com.kmz.shoppinglist.ui.theme.*

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
        val context = LocalContext.current
        val iconProvider = remember { LocalIconProvider(context) }
        val iconUrl = iconProvider.getIconPath(article.getIconIdSafe())
        val priorityColor = getPriorityColor(article.priority)

        Surface(
                modifier =
                        Modifier.fillMaxWidth()
                                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
                color = Color.Transparent
        ) {
                Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Box(
                                modifier =
                                        Modifier.size(32.dp).clip(CircleShape).background(DarkGray),
                                contentAlignment = Alignment.Center
                        ) {
                                AsyncImage(
                                        model = iconUrl ?: "",
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.size(24.dp)
                                )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                                text = article.name,
                                color = White,
                                fontSize = 15.sp,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                        )
                        Box(
                                modifier =
                                        Modifier.size(12.dp)
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
        }

        if (showPriorityDialog) {
                PriorityDialog(
                        currentPriority = article.priority,
                        onPrioritySelected = {
                                onPriorityChange(it)
                                showPriorityDialog = false
                        },
                        onDismiss = { showPriorityDialog = false }
                )
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
        val context = LocalContext.current
        val iconProvider = remember { LocalIconProvider(context) }
        val iconUrl = iconProvider.getIconPath(article.getIconIdSafe())

        Surface(
                modifier =
                        Modifier.fillMaxWidth()
                                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
                color = Color.Transparent
        ) {
                Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        AsyncImage(
                                model = iconUrl ?: "",
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                colorFilter =
                                        ColorFilter.colorMatrix(
                                                ColorMatrix().apply { setToSaturation(0f) }
                                        ),
                                modifier = Modifier.size(30.dp).alpha(0.8f)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                                text = article.name,
                                color = White.copy(alpha = 0.8f),
                                fontSize = 15.sp,
                                textDecoration = TextDecoration.LineThrough,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                        )
                }
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
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Transparent),
                        contentAlignment = Alignment.Center
                ) {
                        AsyncImage(
                                model = iconUrl ?: "",
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                colorFilter =
                                        if (article.isBought)
                                                ColorFilter.colorMatrix(
                                                        ColorMatrix().apply { setToSaturation(0f) }
                                                )
                                        else null,
                                modifier =
                                        Modifier.fillMaxSize()
                                                .alpha(if (article.isBought) 0.8f else 1f)
                        )
                        if (!article.isBought) {
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
                }
                if (showPriorityDialog) {
                        PriorityDialog(
                                currentPriority = article.priority,
                                onPrioritySelected = {
                                        onPriorityChange(it)
                                        showPriorityDialog = false
                                },
                                onDismiss = { showPriorityDialog = false }
                        )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                        text = article.name,
                        color = if (article.isBought) White.copy(alpha = 0.8f) else White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        textDecoration =
                                if (article.isBought) TextDecoration.LineThrough
                                else TextDecoration.None,
                        maxLines = 1,
                        modifier = Modifier.padding(bottom = 6.dp)
                )
        }
}

/** Carte d'article pour l'écran niveau 2 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArticleCard(
        article: Article,
        onToggleBought: () -> Unit,
        onPriorityChange: (Priority) -> Unit
) {
        var showPriorityDialog by remember { mutableStateOf(false) }
        val context = LocalContext.current
        val iconProvider = remember { LocalIconProvider(context) }
        val iconUrl = iconProvider.getIconPath(article.getIconIdSafe())
        val priorityColor = getPriorityColor(article.priority)

        Card(
                modifier =
                        Modifier.fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .combinedClickable(
                                        onClick = onToggleBought,
                                        onLongClick = { /* Edit already handled by parent */}
                                ),
                shape = RoundedCornerShape(16.dp),
                colors =
                        CardDefaults.cardColors(
                                containerColor =
                                        if (article.isBought) DarkGray.copy(alpha = 0.5f)
                                        else MediumGray
                        )
        ) {
                Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Box(
                                modifier =
                                        Modifier.size(50.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                        if (article.isBought) Color.Transparent
                                                        else DarkGray
                                                ),
                                contentAlignment = Alignment.Center
                        ) {
                                AsyncImage(
                                        model = iconUrl ?: "",
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        colorFilter =
                                                if (article.isBought)
                                                        ColorFilter.colorMatrix(
                                                                ColorMatrix().apply {
                                                                        setToSaturation(0f)
                                                                }
                                                        )
                                                else null,
                                        modifier =
                                                Modifier.size(36.dp)
                                                        .alpha(if (article.isBought) 0.8f else 1f)
                                )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        text = article.name,
                                        color =
                                                if (article.isBought) White.copy(alpha = 0.8f)
                                                else White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        textDecoration =
                                                if (article.isBought) TextDecoration.LineThrough
                                                else TextDecoration.None
                                )
                                val frenchName = article.frenchName
                                if (frenchName != null &&
                                                frenchName.isNotBlank() &&
                                                frenchName != article.name
                                ) {
                                        Text(text = frenchName, color = TextGray, fontSize = 14.sp)
                                }
                        }
                        Box(
                                modifier =
                                        Modifier.size(20.dp)
                                                .clip(CircleShape)
                                                .background(priorityColor)
                                                .border(
                                                        1.5.dp,
                                                        Color.White.copy(alpha = 0.5f),
                                                        CircleShape
                                                )
                                                .clickable { showPriorityDialog = true }
                        )
                }
        }

        if (showPriorityDialog) {
                PriorityDialog(
                        currentPriority = article.priority,
                        onPrioritySelected = {
                                onPriorityChange(it)
                                showPriorityDialog = false
                        },
                        onDismiss = { showPriorityDialog = false }
                )
        }
}

/** Ligne de grille d'articles (Text ou Icones) factorisée */
@Composable
fun ArticleGridRow(
        articles: List<Article>,
        columnCount: Int,
        isIconMode: Boolean,
        onToggleBought: (Article) -> Unit,
        onEdit: (Article) -> Unit,
        onPriorityChange: (Article, Priority) -> Unit
) {
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .padding(
                                        horizontal = if (isIconMode) 12.dp else 16.dp,
                                        vertical = 4.dp
                                ),
                horizontalArrangement = Arrangement.spacedBy(if (isIconMode) 4.dp else 8.dp)
        ) {
                articles.forEach { article ->
                        Box(modifier = Modifier.weight(1f)) {
                                if (isIconMode) {
                                        ArticleIconItem(
                                                article = article,
                                                onClick = { onToggleBought(article) },
                                                onLongClick = { onEdit(article) },
                                                onPriorityChange = { onPriorityChange(article, it) }
                                        )
                                } else if (article.isBought) {
                                        BoughtArticleTextItem(
                                                article = article,
                                                onClick = { onToggleBought(article) },
                                                onLongClick = { onEdit(article) }
                                        )
                                } else {
                                        ArticleTextItem(
                                                article = article,
                                                onClick = { onToggleBought(article) },
                                                onLongClick = { onEdit(article) },
                                                onPriorityChange = { onPriorityChange(article, it) }
                                        )
                                }
                        }
                }
                repeat(columnCount - articles.size) { Spacer(modifier = Modifier.weight(1f)) }
        }
}
