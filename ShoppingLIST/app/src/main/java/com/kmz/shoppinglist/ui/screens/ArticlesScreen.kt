package com.kmz.shoppinglist.ui.screens

import androidx.compose.foundation.background
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

/** Écran niveau 2 : Liste des articles d'une catégorie. Strictly identical to AllArticlesScreen. */
@Composable
fun ArticlesScreen(
        category: Category,
        dataManager: DataManager,
        onBackClick: () -> Unit,
        onMicClick: () -> Unit
) {
    var articles by remember { mutableStateOf(dataManager.getArticlesByCategory(category.id)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var articleToEdit by remember { mutableStateOf<Article?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var isIconMode by remember { mutableStateOf(false) }
    var filterPriority by remember { mutableStateOf(Priority.OPTIONAL) }

    val categories = remember { dataManager.getCategories() }

    // Filtrer les articles par priorité sélectionnée
    val filteredArticles =
            articles.filter { it.priority.displayOrder <= filterPriority.displayOrder }

    // Séparer les articles achetés et non achetés (unbought filtrés, bought non filtrés car souvent
    // non priorisés)
    val unboughtArticles = filteredArticles.filter { !it.isBought }
    val boughtArticles = articles.filter { it.isBought }

    var currentAddCategoryId by remember(category) { mutableLongStateOf(category.id) }
    var addDialogKey by remember { mutableIntStateOf(0) }

    fun refreshArticles() {
        articles = dataManager.getArticlesByCategory(category.id)
    }

    Box(modifier = Modifier.fillMaxSize().background(Black)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .background(DarkGray)
                                    .padding(vertical = 8.dp, horizontal = 16.dp)
            ) {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                    Icons.Default.ArrowBack,
                                    contentDescription = "Retour",
                                    tint = White
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Column {
                            Text(
                                    text = category.name,
                                    color = White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                            )
                            Text(
                                    text = "${unboughtArticles.size} à acheter",
                                    color = TextGray,
                                    fontSize = 11.sp
                            )
                        }
                    }

                    // Bouton Toggle Large Bleu au maximum
                    IconButton(
                            onClick = { isIconMode = !isIconMode },
                            modifier = Modifier.size(56.dp).background(AccentBlue, CircleShape)
                    ) {
                        Icon(
                                imageVector =
                                        if (isIconMode) Icons.Default.List
                                        else Icons.Default.ShoppingCart,
                                contentDescription = "Changer de vue",
                                tint = White,
                                modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            // Liste des articles
            LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp)
            ) {
                // Articles non achetés (Grille 2 colonnes comme AllArticlesScreen)
                val chunkedArticles = unboughtArticles.chunked(2)
                items(chunkedArticles) { rowArticles ->
                    Row(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowArticles.forEach { article ->
                            Box(modifier = Modifier.weight(1f)) {
                                if (isIconMode) {
                                    ArticleIconItem(
                                            article = article,
                                            onClick = {
                                                dataManager.toggleArticleBought(article.id)
                                                refreshArticles()
                                            },
                                            onLongClick = {
                                                articleToEdit = article
                                                showEditDialog = true
                                            },
                                            onPriorityChange = { priority ->
                                                dataManager.updateArticle(
                                                        article.copy(priority = priority)
                                                )
                                                refreshArticles()
                                            }
                                    )
                                } else {
                                    ArticleTextItem(
                                            article = article,
                                            onClick = {
                                                dataManager.toggleArticleBought(article.id)
                                                refreshArticles()
                                            },
                                            onLongClick = {
                                                articleToEdit = article
                                                showEditDialog = true
                                            },
                                            onPriorityChange = { priority ->
                                                dataManager.updateArticle(
                                                        article.copy(priority = priority)
                                                )
                                                refreshArticles()
                                            }
                                    )
                                }
                            }
                        }
                        if (rowArticles.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }

                // Section "Achetés"
                if (boughtArticles.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                        Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                    modifier =
                                            Modifier.weight(1f)
                                                    .height(1.dp)
                                                    .background(TextDarkGray)
                            )
                            Text(
                                    text = " ARTICLES ACHETÉS ",
                                    color = TextGray,
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

                    val chunkedBought = boughtArticles.chunked(2)
                    items(chunkedBought) { rowArticles ->
                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowArticles.forEach { article ->
                                Box(modifier = Modifier.weight(1f)) {
                                    BoughtArticleTextItem(
                                            article = article,
                                            onClick = {
                                                dataManager.toggleArticleBought(article.id)
                                                refreshArticles()
                                            },
                                            onLongClick = {
                                                articleToEdit = article
                                                showEditDialog = true
                                            }
                                    )
                                }
                            }
                            if (rowArticles.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                // Message vide
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

        // Barres de boutons d'action (Filtre, Micro, Ajout)
        BottomActionButtons(
                onFilterClick = { priority -> filterPriority = priority },
                onMicClick = onMicClick,
                onAddClick = {
                    articleToEdit = null
                    currentAddCategoryId = category.id
                    showAddDialog = true
                },
                filterPriority = filterPriority,
                modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // Dialog d'ajout
    if (showAddDialog) {
        key(addDialogKey) {
            EditArticleDialog(
                    article = null,
                    categories = categories,
                    currentCategoryId = currentAddCategoryId,
                    onSave = { name, frenchName, iconId, categoryId ->
                        dataManager.addArticle(
                                Article(
                                        name = name,
                                        frenchName = frenchName,
                                        iconId = iconId,
                                        categoryId = categoryId
                                )
                        )
                        refreshArticles()
                        showAddDialog = false
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

    // Dialog d'édition
    if (showEditDialog && articleToEdit != null) {
        EditArticleDialog(
                article = articleToEdit,
                categories = categories,
                currentCategoryId = articleToEdit?.categoryId ?: category.id,
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
