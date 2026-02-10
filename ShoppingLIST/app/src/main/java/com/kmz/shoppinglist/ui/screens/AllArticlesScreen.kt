package com.kmz.shoppinglist.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kmz.shoppinglist.data.*
import com.kmz.shoppinglist.ui.components.*
import com.kmz.shoppinglist.ui.theme.*

/**
 * Écran "Toutes" : affiche tous les articles à acheter groupés par catégorie Chaque groupe peut
 * être expand/collapse
 */
@Composable
fun AllArticlesScreen(dataManager: DataManager, onBackClick: () -> Unit, onMicClick: () -> Unit) {
    // État pour suivre quelles catégories sont développées
    val expandedCategories = remember { mutableStateMapOf<Long, Boolean>() }
    var allExpanded by remember { mutableStateOf(false) }

    var categories by remember { mutableStateOf(dataManager.getCategories()) }
    var allArticles by remember { mutableStateOf(dataManager.getArticles()) }
    var articleToEdit by remember { mutableStateOf<Article?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var isIconMode by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var filterPriority by remember { mutableStateOf(Priority.OPTIONAL) }

    var currentAddCategoryId by remember { mutableLongStateOf(categories.firstOrNull()?.id ?: 0L) }
    var addDialogKey by remember { mutableIntStateOf(0) }

    // Filtrer les articles par priorité sélectionnée
    val filteredArticles =
            allArticles.filter { it.priority.displayOrder <= filterPriority.displayOrder }

    // Séparer les articles achetés et non achetés
    val unboughtArticles = filteredArticles.filter { !it.isBought }
    val boughtArticles = allArticles.filter { it.isBought }

    // Grouper par catégorie
    val articlesByCategory = unboughtArticles.groupBy { it.categoryId }
    val boughtByCategory = boughtArticles.groupBy { it.categoryId }

    // Catégories qui ont des articles à acheter
    val categoriesWithArticles =
            categories.filter { category -> articlesByCategory[category.id]?.isNotEmpty() == true }

    // Catégories qui ont des articles achetés
    val categoriesWithBought =
            categories.filter { category -> boughtByCategory[category.id]?.isNotEmpty() == true }

    // Nombre total d'articles à acheter
    val totalUnbought = unboughtArticles.size

    fun refreshData() {
        categories = dataManager.getCategories()
        allArticles = dataManager.getArticles()
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
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = White)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Column {
                        Text(
                                text = if (!isIconMode) "Toutes" else "",
                                color = White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                        )
                        Text(
                                text =
                                        "$totalUnbought article${if (totalUnbought > 1) "s" else ""}",
                                color = TextGray,
                                fontSize = 11.sp
                        )
                    }

                    // Spacer pour pousser les boutons vers la droite
                    Spacer(modifier = Modifier.weight(1f))

                    // Bouton Expand/Collapse All (Fond blanc, sigle noir)
                    IconButton(
                            onClick = {
                                allExpanded = !allExpanded
                                categoriesWithArticles.forEach { category ->
                                    expandedCategories[category.id] = allExpanded
                                }
                            },
                            modifier = Modifier.size(36.dp).background(White, CircleShape)
                    ) {
                        Icon(
                                imageVector =
                                        if (allExpanded) Icons.Default.UnfoldLess
                                        else Icons.Default.UnfoldMore,
                                contentDescription =
                                        if (allExpanded) "Tout réduire" else "Tout développer",
                                tint = Black,
                                modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Bouton Toggle Large Bleu (Changement de vue)
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

            // Liste des catégories avec leurs articles
            LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp)
            ) {
                categoriesWithArticles.forEach { category ->
                    val isExpanded =
                            expandedCategories[category.id] ?: false // Par défaut collapsed
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

                    // Articles de cette catégorie (B1: Texte ou B2: Icones)
                    if (isExpanded) {
                        val chunkedArticles = categoryArticles.chunked(2)
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
                                                        refreshData()
                                                    },
                                                    onLongClick = {
                                                        articleToEdit = article
                                                        showEditDialog = true
                                                    },
                                                    onPriorityChange = { priority ->
                                                        dataManager.updateArticle(
                                                                article.copy(priority = priority)
                                                        )
                                                        refreshData()
                                                    }
                                            )
                                        } else {
                                            ArticleTextItem(
                                                    article = article,
                                                    onClick = {
                                                        dataManager.toggleArticleBought(article.id)
                                                        refreshData()
                                                    },
                                                    onLongClick = {
                                                        articleToEdit = article
                                                        showEditDialog = true
                                                    },
                                                    onPriorityChange = { priority ->
                                                        dataManager.updateArticle(
                                                                article.copy(priority = priority)
                                                        )
                                                        refreshData()
                                                    }
                                            )
                                        }
                                    }
                                }
                                if (rowArticles.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    // Espacement entre les catégories
                    item(key = "spacer_${category.id}") { Spacer(modifier = Modifier.height(8.dp)) }
                }

                // Section "Achetés" groupée par catégorie (Visible dans les deux modes, layout
                // texte)
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

                    categoriesWithBought.forEach { category ->
                        val catBoughtArticles = boughtByCategory[category.id] ?: emptyList()

                        // Petit titre de catégorie pour les achetés
                        item {
                            Text(
                                    text = category.name,
                                    color = TextGray,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                            )
                        }

                        val chunkedBought = catBoughtArticles.chunked(2)
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
                                                    refreshData()
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
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }
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

        // Barres de boutons d'action (Filtre, Micro, Ajout)
        BottomActionButtons(
                onFilterClick = { priority -> filterPriority = priority },
                onMicClick = onMicClick,
                onAddClick = {
                    articleToEdit = null
                    currentAddCategoryId = categories.firstOrNull()?.id ?: 0L
                    showAddDialog = true
                },
                filterPriority = filterPriority,
                modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // Dialog d'ajout d'article
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
                        refreshData()
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
            // Nom de la catégorie (réduit pour tenir sur une ligne)
            Text(
                    text = category.name,
                    color = White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
            )

            // Badge nombre d'articles
            Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(TextDarkGray),
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
