package com.kmz.shoppinglist.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kmz.shoppinglist.data.Article
import com.kmz.shoppinglist.data.Category
import com.kmz.shoppinglist.data.DataManager
import com.kmz.shoppinglist.data.LocalIconProvider
import com.kmz.shoppinglist.data.Priority
import com.kmz.shoppinglist.ui.components.ArticleCard
import com.kmz.shoppinglist.ui.components.EditArticleDialog
import com.kmz.shoppinglist.ui.theme.*

/** Écran niveau 2 : Liste des articles d'une catégorie */
@Composable
fun ArticlesScreen(category: Category, dataManager: DataManager, onBackClick: () -> Unit) {
    var articles by remember { mutableStateOf(dataManager.getArticlesByCategory(category.id)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var articleToEdit by remember { mutableStateOf<Article?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }

    val categories = remember { dataManager.getCategories() }

    // Séparer les articles achetés et non achetés
    val toBuyArticles = articles.filter { !it.isBought }
    val boughtArticles = articles.filter { it.isBought }

    fun refreshArticles() {
        articles = dataManager.getArticlesByCategory(category.id)
    }

    Box(modifier = Modifier.fillMaxSize().background(Black)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // En-tête avec bouton retour et icône de catégorie
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

                    // Icône de catégorie
                    val context = LocalContext.current
                    val iconProvider = remember { LocalIconProvider(context) }
                    val iconUrl = iconProvider.getIconPath(category.getIconIdSafe())

                    Box(
                            modifier =
                                    Modifier.size(64.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(MediumGray),
                            contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                                model = iconUrl ?: "",
                                contentDescription = null,
                                modifier = Modifier.size(60.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                                text = category.name,
                                color = White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                        )
                        Text(
                                text =
                                        "${toBuyArticles.size} à acheter • ${boughtArticles.size} acheté${if (boughtArticles.size > 1) "s" else ""}",
                                color = TextGray,
                                fontSize = 13.sp
                        )
                    }
                }
            }

            // Liste des articles
            LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                // Section "À acheter"
                if (toBuyArticles.isNotEmpty()) {
                    item { SectionHeader(title = "À acheter", count = toBuyArticles.size) }
                    items(toBuyArticles, key = { it.id }) { article ->
                        ArticleCard(
                                article = article,
                                onToggleBought = {
                                    dataManager.toggleArticleBought(article.id)
                                    refreshArticles()
                                },
                                onPriorityChange = { priority ->
                                    dataManager.updateArticlePriority(article.id, priority)
                                    refreshArticles()
                                },
                                onEditClick = {
                                    articleToEdit = article
                                    showEditDialog = true
                                }
                        )
                    }
                }

                // Section "Achetés"
                if (boughtArticles.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        SectionHeader(
                                title = "Achetés",
                                count = boughtArticles.size,
                                isBought = true
                        )
                    }
                    items(boughtArticles, key = { it.id }) { article ->
                        ArticleCard(
                                article = article,
                                onToggleBought = {
                                    dataManager.toggleArticleBought(article.id)
                                    refreshArticles()
                                },
                                onPriorityChange = { priority ->
                                    dataManager.updateArticlePriority(article.id, priority)
                                    refreshArticles()
                                },
                                onEditClick = {
                                    articleToEdit = article
                                    showEditDialog = true
                                }
                        )
                    }
                }

                // Message si la liste est vide
                if (articles.isEmpty()) {
                    item {
                        Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "📝", fontSize = 48.sp)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(text = "Aucun article", color = TextGray, fontSize = 18.sp)
                                Text(
                                        text = "Appuyez sur + pour ajouter un article",
                                        color = TextDarkGray,
                                        fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bouton flottant d'ajout
        FloatingActionButton(
                onClick = {
                    articleToEdit = null
                    showAddDialog = true
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                containerColor = AccentBlue,
                contentColor = White,
                shape = CircleShape
        ) {
            Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Ajouter un article",
                    modifier = Modifier.size(28.dp)
            )
        }
    }

    // Dialog d'ajout d'article (nouveau)
    if (showAddDialog) {
        EditArticleDialog(
                article = null,
                categories = categories,
                currentCategoryId = category.id,
                onSave = { name, frenchName, iconId, categoryId ->
                    val newArticle =
                            Article(
                                    name = name,
                                    frenchName = frenchName,
                                    iconId = iconId,
                                    categoryId = categoryId,
                                    priority = Priority.NORMAL
                            )
                    dataManager.addArticle(newArticle)
                    refreshArticles()
                    showAddDialog = false
                },
                onDelete = {},
                onCreateNew = {},
                onDismiss = { showAddDialog = false }
        )
    }

    // Dialog d'édition d'article
    if (showEditDialog && articleToEdit != null) {
        EditArticleDialog(
                article = articleToEdit,
                categories = categories,
                currentCategoryId = category.id,
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
                    refreshArticles()
                    showEditDialog = false
                    articleToEdit = null
                },
                onDelete = {
                    articleToEdit?.let { article -> dataManager.deleteArticle(article.id) }
                    refreshArticles()
                    showEditDialog = false
                    articleToEdit = null
                },
                onCreateNew = {
                    showEditDialog = false
                    articleToEdit = null
                    showAddDialog = true
                },
                onDismiss = {
                    showEditDialog = false
                    articleToEdit = null
                }
        )
    }
}

@Composable
fun SectionHeader(title: String, count: Int, isBought: Boolean = false) {
    Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
                text = title,
                color = if (isBought) TextGray else White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "($count)", color = TextDarkGray, fontSize = 14.sp)
    }
}
