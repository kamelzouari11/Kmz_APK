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
 * Écran "Toutes" : affiche tous les articles à acheter groupés par catégorie Chaque groupe peut
 * être expand/collapse
 */
@Composable
fun AllArticlesScreen(dataManager: DataManager, onBackClick: () -> Unit, onMicClick: () -> Unit) {
        // État pour suivre quelles catégories sont développées
        val expandedCategories = remember {
                val initial = dataManager.getExpandedCategoryIds()
                mutableStateMapOf<Long, Boolean>().apply { initial.forEach { put(it, true) } }
        }
        var allExpanded by remember { mutableStateOf(dataManager.getAllExpanded()) }

        var categories by remember { mutableStateOf(dataManager.getCategories()) }
        var allArticles by remember { mutableStateOf(dataManager.getArticles()) }
        var articleToEdit by remember { mutableStateOf<Article?>(null) }
        var showEditDialog by remember { mutableStateOf(false) }
        var isIconMode by remember { mutableStateOf(dataManager.getIconMode()) }
        var showAddDialog by remember { mutableStateOf(false) }
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

        fun updateAllExpanded(expanded: Boolean) {
                allExpanded = expanded
                dataManager.setAllExpanded(expanded)
                if (expanded) {
                        val allIds = categories.map { it.id }.toSet()
                        dataManager.setExpandedCategoryIds(allIds)
                } else {
                        dataManager.setExpandedCategoryIds(emptySet())
                }
        }

        fun updateBoughtExpanded(expanded: Boolean) {
                isBoughtExpanded = expanded
                dataManager.setBoughtExpanded(expanded)
        }

        var currentAddCategoryId by remember {
                mutableLongStateOf(categories.firstOrNull()?.id ?: 0L)
        }
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
                categories.filter { category ->
                        articlesByCategory[category.id]?.isNotEmpty() == true
                }

        // Catégories qui ont des articles achetés
        val categoriesWithBought =
                categories.filter { category ->
                        boughtByCategory[category.id]?.isNotEmpty() == true
                }

        // Nombre total d'articles à acheter
        val totalUnbought = unboughtArticles.size

        // Initialiser l'état d'expansion des catégories si elles ne sont pas encore définies
        LaunchedEffect(categoriesWithArticles) {
                val persistentIds = dataManager.getExpandedCategoryIds()
                if (persistentIds.isEmpty() && allExpanded) {
                        categoriesWithArticles.forEach { category ->
                                expandedCategories[category.id] = true
                        }
                        dataManager.setExpandedCategoryIds(
                                categoriesWithArticles.map { it.id }.toSet()
                        )
                } else {
                        categoriesWithArticles.forEach { category ->
                                expandedCategories[category.id] =
                                        persistentIds.contains(category.id)
                        }
                }
        }

        fun refreshData() {
                categories = dataManager.getCategories()
                allArticles = dataManager.getArticles()
        }

        Box(modifier = Modifier.fillMaxSize().background(Black)) {
                Column(modifier = Modifier.fillMaxSize()) {
                        ScreenHeader(
                                title = if (!isIconMode) "Toutes" else "",
                                subtitle =
                                        "$totalUnbought article${if (totalUnbought > 1) "s" else ""}",
                                onBackClick = onBackClick,
                                isIconMode = isIconMode,
                                onIconModeChange = { updateIconMode(it) },
                                filterPriority = filterPriority,
                                onFilterPriorityChange = { updateFilterPriority(it) },
                                extraButtons = {
                                        Spacer(modifier = Modifier.width(10.dp))
                                        IconButton(
                                                onClick = {
                                                        val newState = !allExpanded
                                                        updateAllExpanded(newState)
                                                        categoriesWithArticles.forEach { category ->
                                                                expandedCategories[category.id] =
                                                                        newState
                                                        }
                                                },
                                                modifier =
                                                        Modifier.size(56.dp)
                                                                .background(White, CircleShape)
                                        ) {
                                                Icon(
                                                        imageVector =
                                                                if (allExpanded)
                                                                        Icons.Default.UnfoldLess
                                                                else Icons.Default.UnfoldMore,
                                                        contentDescription =
                                                                if (allExpanded) "Tout réduire"
                                                                else "Tout développer",
                                                        tint = Black,
                                                        modifier = Modifier.size(36.dp)
                                                )
                                        }
                                }
                        )

                        LazyColumn(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp)
                        ) {
                                val columnCount = if (isIconMode) 3 else 2

                                categoriesWithArticles.forEach { category ->
                                        val isExpanded = expandedCategories[category.id] ?: false
                                        val categoryArticles =
                                                articlesByCategory[category.id]?.sortedWith(
                                                        compareBy { it.priority.displayOrder }
                                                )
                                                        ?: emptyList()

                                        item(key = "header_${category.id}") {
                                                CategoryGroupHeader(
                                                        category = category,
                                                        articleCount = categoryArticles.size,
                                                        isExpanded = isExpanded,
                                                        onClick = {
                                                                val newState = !isExpanded
                                                                expandedCategories[category.id] =
                                                                        newState
                                                                val currentExpanded =
                                                                        expandedCategories
                                                                                .filter { it.value }
                                                                                .keys
                                                                dataManager.setExpandedCategoryIds(
                                                                        currentExpanded.toSet()
                                                                )
                                                                if (currentExpanded.isEmpty())
                                                                        updateAllExpanded(false)
                                                                else if (currentExpanded.size ==
                                                                                categoriesWithArticles
                                                                                        .size
                                                                )
                                                                        updateAllExpanded(true)
                                                        }
                                                )
                                        }

                                        if (isExpanded) {
                                                val chunkedArticles =
                                                        categoryArticles.chunked(columnCount)
                                                items(chunkedArticles) { rowArticles ->
                                                        ArticleGridRow(
                                                                articles = rowArticles,
                                                                columnCount = columnCount,
                                                                isIconMode = isIconMode,
                                                                onToggleBought = { article ->
                                                                        dataManager
                                                                                .toggleArticleBought(
                                                                                        article.id
                                                                                )
                                                                        refreshData()
                                                                },
                                                                onEdit = { article ->
                                                                        articleToEdit = article
                                                                        showEditDialog = true
                                                                },
                                                                onPriorityChange = {
                                                                        article,
                                                                        priority ->
                                                                        dataManager.updateArticle(
                                                                                article.copy(
                                                                                        priority =
                                                                                                priority
                                                                                )
                                                                        )
                                                                        refreshData()
                                                                }
                                                        )
                                                }
                                        }

                                        item(key = "spacer_${category.id}") {
                                                Spacer(modifier = Modifier.height(8.dp))
                                        }
                                }

                                if (boughtArticles.isNotEmpty()) {
                                        item {
                                                Spacer(modifier = Modifier.height(32.dp))
                                                Row(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(horizontal = 16.dp)
                                                                        .clickable {
                                                                                updateBoughtExpanded(
                                                                                        !isBoughtExpanded
                                                                                )
                                                                        },
                                                        verticalAlignment =
                                                                Alignment.CenterVertically
                                                ) {
                                                        Box(
                                                                modifier =
                                                                        Modifier.weight(1f)
                                                                                .height(1.dp)
                                                                                .background(
                                                                                        TextDarkGray
                                                                                )
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Icon(
                                                                imageVector =
                                                                        if (isBoughtExpanded)
                                                                                Icons.Default
                                                                                        .ExpandLess
                                                                        else
                                                                                Icons.Default
                                                                                        .ExpandMore,
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
                                                                                .background(
                                                                                        TextDarkGray
                                                                                )
                                                        )
                                                }
                                                Spacer(modifier = Modifier.height(16.dp))
                                        }

                                        if (isBoughtExpanded) {
                                                categoriesWithBought.forEach { category ->
                                                        val catBoughtArticles =
                                                                boughtByCategory[category.id]
                                                                        ?: emptyList()
                                                        item {
                                                                Text(
                                                                        text = category.name,
                                                                        color = White,
                                                                        fontSize = 14.sp,
                                                                        fontWeight =
                                                                                FontWeight.Medium,
                                                                        modifier =
                                                                                Modifier.padding(
                                                                                        horizontal =
                                                                                                24.dp,
                                                                                        vertical =
                                                                                                4.dp
                                                                                )
                                                                )
                                                        }
                                                        val chunkedBought =
                                                                catBoughtArticles.chunked(
                                                                        columnCount
                                                                )
                                                        items(chunkedBought) { rowArticles ->
                                                                ArticleGridRow(
                                                                        articles = rowArticles,
                                                                        columnCount = columnCount,
                                                                        isIconMode = isIconMode,
                                                                        onToggleBought = { article
                                                                                ->
                                                                                dataManager
                                                                                        .toggleArticleBought(
                                                                                                article.id
                                                                                        )
                                                                                refreshData()
                                                                        },
                                                                        onEdit = { article ->
                                                                                articleToEdit =
                                                                                        article
                                                                                showEditDialog =
                                                                                        true
                                                                        },
                                                                        onPriorityChange = {
                                                                                article,
                                                                                priority ->
                                                                                dataManager
                                                                                        .updateArticle(
                                                                                                article.copy(
                                                                                                        priority =
                                                                                                                priority
                                                                                                )
                                                                                        )
                                                                                refreshData()
                                                                        }
                                                                )
                                                        }
                                                        item {
                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.height(
                                                                                        8.dp
                                                                                )
                                                                )
                                                        }
                                                }
                                        }
                                }

                                if (unboughtArticles.isEmpty()) {
                                        item {
                                                Box(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(32.dp),
                                                        contentAlignment = Alignment.Center
                                                ) {
                                                        Column(
                                                                horizontalAlignment =
                                                                        Alignment.CenterHorizontally
                                                        ) {
                                                                Text(text = "✅", fontSize = 48.sp)
                                                                Spacer(
                                                                        modifier =
                                                                                Modifier.height(
                                                                                        16.dp
                                                                                )
                                                                )
                                                                Text(
                                                                        text = "Tout est acheté !",
                                                                        color = TextGray,
                                                                        fontSize = 18.sp
                                                                )
                                                                Text(
                                                                        text =
                                                                                "Votre liste de courses est vide",
                                                                        color = TextDarkGray,
                                                                        fontSize = 14.sp
                                                                )
                                                        }
                                                }
                                        }
                                }
                        }
                }

                BottomActionButtons(
                        onFilterClick = { updateFilterPriority(it) },
                        onMicClick = onMicClick,
                        onAddClick = {
                                articleToEdit = null
                                currentAddCategoryId = categories.firstOrNull()?.id ?: 0L
                                showAddDialog = true
                        },
                        filterPriority = filterPriority,
                        showFilter = false,
                        modifier = Modifier.align(Alignment.BottomCenter)
                )
        }

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
                                articleToEdit?.let { article ->
                                        dataManager.deleteArticle(article.id)
                                }
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
