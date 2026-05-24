package com.kmz.shoppinglist.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kmz.shoppinglist.data.*
import com.kmz.shoppinglist.ui.components.*
import com.kmz.shoppinglist.ui.theme.*

/**
 * Écran niveau 2 : Liste des articles d'une catégorie. Strictly identical to AllArticlesScreen
 * logic but for 1 category.
 */
@Composable
fun ArticlesScreen(
        category: Category,
        dataManager: DataManager,
        onBackClick: () -> Unit,
        onMicClick: () -> Unit
) {
    var articles by remember { mutableStateOf(dataManager.getArticlesByCategory(category.id)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showQuickAddDialog by remember { mutableStateOf(false) }
    var articleToEdit by remember { mutableStateOf<Article?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var isIconMode by remember { mutableStateOf(dataManager.getIconMode()) }
    var filterPriority by remember { mutableStateOf(dataManager.getFilterPriority()) }
    var isBoughtExpanded by remember { mutableStateOf(dataManager.getBoughtExpanded()) }

    fun updateIconMode(enabled: Boolean) {
        isIconMode = enabled
        dataManager.setIconMode(enabled)
    }

    fun updateFilterPriority(priority: Priority) {
        filterPriority = priority
        dataManager.setFilterPriority(priority)
    }

    fun updateBoughtExpanded(expanded: Boolean) {
        isBoughtExpanded = expanded
        dataManager.setBoughtExpanded(expanded)
    }

    val categories = remember { dataManager.getCategories() }

    // Filtrer les articles par priorité sélectionnée
    val filteredArticles =
            articles.filter { it.priority.displayOrder <= filterPriority.displayOrder }

    // Séparer les articles achetés et non achetés
    val unboughtArticles = filteredArticles.filter { !it.isBought }
    val boughtArticles = articles.filter { it.isBought }

    var currentAddCategoryId by remember(category) { mutableLongStateOf(category.id) }
    var addDialogKey by remember { mutableIntStateOf(0) }

    fun refreshArticles() {
        articles = dataManager.getArticlesByCategory(category.id)
    }

    Box(modifier = Modifier.fillMaxSize().background(Black)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                    title = category.name,
                    subtitle = "${unboughtArticles.size} à acheter",
                    onBackClick = onBackClick,
                    isIconMode = isIconMode,
                    onIconModeChange = { updateIconMode(it) },
                    filterPriority = filterPriority,
                    onFilterPriorityChange = { updateFilterPriority(it) }
            )

            LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp)
            ) {
                val columnCount = if (isIconMode) 3 else 2

                // Articles non achetés
                val chunkedArticles = unboughtArticles.chunked(columnCount)
                items(chunkedArticles) { rowArticles ->
                    ArticleGridRow(
                            articles = rowArticles,
                            columnCount = columnCount,
                            isIconMode = isIconMode,
                            onToggleBought = { article ->
                                dataManager.toggleArticleBought(article.id)
                                refreshArticles()
                            },
                            onEdit = { article ->
                                articleToEdit = article
                                showEditDialog = true
                            },
                            onPriorityChange = { article, priority ->
                                dataManager.updateArticle(article.copy(priority = priority))
                                refreshArticles()
                            }
                    )
                }

                // Section "Achetés"
                if (boughtArticles.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .padding(horizontal = 16.dp)
                                                .clickable {
                                                    updateBoughtExpanded(!isBoughtExpanded)
                                                },
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                    modifier =
                                            Modifier.weight(1f)
                                                    .height(1.dp)
                                                    .background(TextDarkGray)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                    imageVector =
                                            if (isBoughtExpanded) Icons.Default.ExpandLess
                                            else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = White,
                                    modifier = Modifier.size(20.dp)
                            )
                            Text(
                                    text = " ARTICLES ACHETÉS ",
                                    color = White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                            )
                            Box(
                                    modifier =
                                            Modifier.weight(1f)
                                                    .height(1.dp)
                                                    .background(TextDarkGray)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (isBoughtExpanded) {
                        val chunkedBought = boughtArticles.chunked(columnCount)
                        items(chunkedBought) { rowArticles ->
                            ArticleGridRow(
                                    articles = rowArticles,
                                    columnCount = columnCount,
                                    isIconMode = isIconMode,
                                    onToggleBought = { article ->
                                        dataManager.toggleArticleBought(article.id)
                                        refreshArticles()
                                    },
                                    onEdit = { article ->
                                        articleToEdit = article
                                        showEditDialog = true
                                    },
                                    onPriorityChange = { article, priority ->
                                        dataManager.updateArticle(article.copy(priority = priority))
                                        refreshArticles()
                                    }
                            )
                        }
                    }
                }

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
                            }
                        }
                    }
                }
            }
        }

        // ── 3 boutons en bas ─────────────────────────────────────────────────
        Row(
                modifier =
                        Modifier.align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
        ) {
            // 🎤 Micro (vert)
            FloatingActionButton(
                    onClick = onMicClick,
                    containerColor = AccentGreen,
                    contentColor = White,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp)
            ) {
                Icon(
                        Icons.Default.Mic,
                        contentDescription = "Micro",
                        modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(20.dp))

            // 🔍 Ajout rapide depuis articles existants (violet)
            FloatingActionButton(
                    onClick = { showQuickAddDialog = true },
                    containerColor = AccentViolet,
                    contentColor = White,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp)
            ) {
                Icon(
                        Icons.Default.Search,
                        contentDescription = "Recherche rapide",
                        modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(20.dp))

            // ➕ Créer nouvel article (bleu)
            FloatingActionButton(
                    onClick = {
                        articleToEdit = null
                        currentAddCategoryId = category.id
                        showAddDialog = true
                    },
                    containerColor = AccentBlue,
                    contentColor = White,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp)
            ) {
                Icon(
                        Icons.Default.Add,
                        contentDescription = "Ajouter",
                        modifier = Modifier.size(28.dp)
                )
            }
        }
    }

    // ── Dialog recherche rapide ──────────────────────────────────────────────
    if (showQuickAddDialog) {
        val allArticles = remember { dataManager.getArticles() }
        QuickAddDialog(
                allArticles = allArticles,
                currentCategoryId = category.id,
                onAddArticle = { article ->
                    dataManager.addArticle(article)
                    refreshArticles()
                },
                onDismiss = { showQuickAddDialog = false }
        )
    }

    if (showAddDialog) {
        key(addDialogKey) {
            EditArticleDialog(
                    article = null,
                    categories = categories,
                    currentCategoryId = currentAddCategoryId,
                    onSave =
                            fun(
                                    name: String,
                                    frenchName: String,
                                    iconId: String,
                                    categoryId: Long
                            ): String? {
                                val error =
                                        dataManager.addArticle(
                                                Article(
                                                        name = name,
                                                        frenchName = frenchName,
                                                        iconId = iconId,
                                                        categoryId = categoryId
                                                )
                                        )
                                if (error == null) {
                                    refreshArticles()
                                    showAddDialog = false
                                }
                                return error
                            },
                    onDelete = {},
                    onCreateNew = { categoryId ->
                        currentAddCategoryId = categoryId
                        addDialogKey++
                    },
                    onDismiss = { showAddDialog = false }
            )
        }
    }

    if (showEditDialog && articleToEdit != null) {
        EditArticleDialog(
                article = articleToEdit,
                categories = categories,
                currentCategoryId = articleToEdit?.categoryId ?: category.id,
                onSave =
                        fun(
                                name: String,
                                frenchName: String,
                                iconId: String,
                                categoryId: Long
                        ): String? {
                            val currentArticle = articleToEdit ?: return null
                            val error =
                                    dataManager.updateArticle(
                                            currentArticle.copy(
                                                    name = name,
                                                    frenchName = frenchName,
                                                    iconId = iconId,
                                                    categoryId = categoryId
                                            )
                                    )
                            if (error == null) {
                                refreshArticles()
                                showEditDialog = false
                                articleToEdit = null
                            }
                            return error
                        },
                onDelete = {
                    articleToEdit?.let { article -> dataManager.deleteArticle(article.id) }
                    refreshArticles()
                    showEditDialog = false
                    articleToEdit = null
                },
                onCreateNew = { categoryId ->
                    showEditDialog = false
                    articleToEdit = null
                    currentAddCategoryId = categoryId
                    showAddDialog = true
                },
                onDismiss = {
                    showEditDialog = false
                    articleToEdit = null
                }
        )
    }
}
