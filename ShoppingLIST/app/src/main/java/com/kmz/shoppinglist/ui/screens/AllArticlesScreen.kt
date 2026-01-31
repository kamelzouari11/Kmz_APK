package com.kmz.shoppinglist.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kmz.shoppinglist.data.Article
import com.kmz.shoppinglist.data.Category
import com.kmz.shoppinglist.data.DataManager
import com.kmz.shoppinglist.ui.components.ArticleCard
import com.kmz.shoppinglist.ui.components.EditArticleDialog
import com.kmz.shoppinglist.ui.theme.*

/**
 * Écran "Toutes" : affiche tous les articles à acheter groupés par catégorie Chaque groupe peut
 * être expand/collapse
 */
@Composable
fun AllArticlesScreen(dataManager: DataManager, onBackClick: () -> Unit) {
    // État pour suivre quelles catégories sont développées
    val expandedCategories = remember { mutableStateMapOf<Long, Boolean>() }

    var categories by remember { mutableStateOf(dataManager.getCategories()) }
    var allArticles by remember { mutableStateOf(dataManager.getArticles()) }
    var articleToEdit by remember { mutableStateOf<Article?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }

    // Filtrer uniquement les articles non achetés
    val unboughtArticles = allArticles.filter { !it.isBought }

    // Grouper par catégorie
    val articlesByCategory = unboughtArticles.groupBy { it.categoryId }

    // Catégories qui ont des articles à acheter
    val categoriesWithArticles =
            categories.filter { category -> articlesByCategory[category.id]?.isNotEmpty() == true }

    // Nombre total d'articles à acheter
    val totalUnbought = unboughtArticles.size

    fun refreshData() {
        categories = dataManager.getCategories()
        allArticles = dataManager.getArticles()
    }

    Box(modifier = Modifier.fillMaxSize().background(Black)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // En-tête avec bouton retour
            Box(modifier = Modifier.fillMaxWidth().background(DarkGray).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Retour",
                                tint = White
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Text(
                                text = "📋 Toutes",
                                color = White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                        )
                        Text(
                                text =
                                        "$totalUnbought article${if (totalUnbought > 1) "s" else ""} à acheter",
                                color = TextGray,
                                fontSize = 13.sp
                        )
                    }
                }
            }

            // Liste des catégories avec leurs articles
            LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                categoriesWithArticles.forEach { category ->
                    val isExpanded = expandedCategories[category.id] ?: true // Par défaut expanded
                    val categoryArticles =
                            articlesByCategory[category.id]?.sortedWith(
                                    compareBy { it.priority.displayOrder }
                            )
                                    ?: emptyList()

                    // En-tête de catégorie (cliquable pour expand/collapse)
                    item(key = "header_${category.id}") {
                        CategoryGroupHeader(
                                category = category,
                                articleCount = categoryArticles.size,
                                isExpanded = isExpanded,
                                onClick = { expandedCategories[category.id] = !isExpanded }
                        )
                    }

                    // Articles de cette catégorie (avec animation)
                    if (isExpanded) {
                        items(categoryArticles, key = { "article_${it.id}" }) { article ->
                            ArticleCard(
                                    article = article,
                                    onToggleBought = {
                                        dataManager.toggleArticleBought(article.id)
                                        refreshData()
                                    },
                                    onPriorityChange = { priority ->
                                        dataManager.updateArticlePriority(article.id, priority)
                                        refreshData()
                                    },
                                    onEditClick = {
                                        articleToEdit = article
                                        showEditDialog = true
                                    }
                            )
                        }
                    }

                    // Espacement entre les catégories
                    item(key = "spacer_${category.id}") { Spacer(modifier = Modifier.height(8.dp)) }
                }

                // Message si aucun article
                if (unboughtArticles.isEmpty()) {
                    item {
                        Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "✅", fontSize = 48.sp)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(text = "Tout est acheté !", color = TextGray, fontSize = 18.sp)
                                Text(
                                        text = "Votre liste de courses est vide",
                                        color = TextDarkGray,
                                        fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog d'édition d'article
    if (showEditDialog && articleToEdit != null) {
        EditArticleDialog(
                article = articleToEdit,
                categories = categories,
                currentCategoryId = articleToEdit?.categoryId ?: 0L,
                onSave = { name, frenchName, iconId, categoryId ->
                    articleToEdit?.let { article ->
                        dataManager.updateArticle(
                                article.copy(
                                        name = name,
                                        frenchName = frenchName,
                                        iconId = iconId,
                                        categoryId = categoryId
                                )
                        )
                    }
                    refreshData()
                    showEditDialog = false
                    articleToEdit = null
                },
                onDelete = {
                    articleToEdit?.let { article -> dataManager.deleteArticle(article.id) }
                    refreshData()
                    showEditDialog = false
                    articleToEdit = null
                },
                onCreateNew = {
                    // Dans cet écran, on ne crée pas de nouvel article
                    showEditDialog = false
                    articleToEdit = null
                },
                onDismiss = {
                    showEditDialog = false
                    articleToEdit = null
                }
        )
    }
}

/** En-tête de groupe de catégorie avec expand/collapse */
@Composable
fun CategoryGroupHeader(
        category: Category,
        articleCount: Int,
        isExpanded: Boolean,
        onClick: () -> Unit
) {
    Card(
            modifier =
                    Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable(onClick = onClick),
            colors = CardDefaults.cardColors(containerColor = MediumGray),
            shape = RoundedCornerShape(12.dp)
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            // Nom de la catégorie
            Text(
                    text = category.name,
                    color = White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
            )

            // Badge nombre d'articles
            Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(AccentBlue),
                    contentAlignment = Alignment.Center
            ) {
                Text(
                        text = articleCount.toString(),
                        color = White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Icône expand/collapse
            Icon(
                    imageVector =
                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Réduire" else "Développer",
                    tint = TextGray,
                    modifier = Modifier.size(24.dp)
            )
        }
    }
}
