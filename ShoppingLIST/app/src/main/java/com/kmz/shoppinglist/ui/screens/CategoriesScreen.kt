package com.kmz.shoppinglist.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kmz.shoppinglist.data.*
import com.kmz.shoppinglist.ui.components.*
import com.kmz.shoppinglist.ui.theme.*

/** Écran niveau 1 : Liste des catégories */
@Composable
fun CategoriesScreen(
        dataManager: DataManager,
        onCategoryClick: (Category) -> Unit,
        onAllArticlesClick: () -> Unit,
        onIconManagerClick: () -> Unit,
        onMicClick: () -> Unit
) {
        var categories by remember { mutableStateOf(dataManager.getCategories()) }
        var showAddDialog by remember { mutableStateOf(false) }
        var categoryToEdit by remember { mutableStateOf<Category?>(null) }
        var showEditDialog by remember { mutableStateOf(false) }
        var filterPriority by remember { mutableStateOf(dataManager.getFilterPriority()) }

        val context = LocalContext.current

        val updateFilterPriority: (Priority) -> Unit = { priority ->
                filterPriority = priority
                dataManager.setFilterPriority(priority)
        }

        val totalUnbought =
                dataManager.getArticles().count {
                        !it.isBought && it.priority.displayOrder <= filterPriority.displayOrder
                }

        LaunchedEffect(Unit) { categories = dataManager.getCategories() }

        fun refreshCategories() {
                categories = dataManager.getCategories()
        }

        Box(modifier = Modifier.fillMaxSize().background(Black)) {
                Column(modifier = Modifier.fillMaxSize()) {
                        MainScreenHeader(
                                title = "🛒 Ma Liste",
                                onExportClick = {
                                        val result = dataManager.exportDatabase()
                                        Toast.makeText(
                                                        context,
                                                        if (result.first)
                                                                "Sauvegardé: ${result.second}"
                                                        else "Erreur: ${result.second}",
                                                        Toast.LENGTH_LONG
                                                )
                                                .show()
                                },
                                onImportClick = {
                                        val result = dataManager.importDatabase()
                                        if (result.first) refreshCategories()
                                        Toast.makeText(context, result.second, Toast.LENGTH_LONG)
                                                .show()
                                },
                                onIconManagerClick = onIconManagerClick
                        )

                        val margin = (LocalConfiguration.current.screenWidthDp * 0.02f).dp

                        LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentPadding =
                                        PaddingValues(
                                                start = margin,
                                                end = margin,
                                                top = 12.dp,
                                                bottom = 100.dp
                                        ),
                                horizontalArrangement = Arrangement.spacedBy(margin),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                                item {
                                        AllArticlesCard(
                                                unboughtCount = totalUnbought,
                                                onClick = onAllArticlesClick
                                        )
                                }

                                items(categories, key = { it.id }) { category ->
                                        val unboughtCount =
                                                dataManager.getArticles().count {
                                                        it.categoryId == category.id &&
                                                                !it.isBought &&
                                                                it.priority.displayOrder <=
                                                                        filterPriority.displayOrder
                                                }
                                        CategoryCard(
                                                category = category,
                                                unboughtCount = unboughtCount,
                                                onClick = { onCategoryClick(category) }
                                        )
                                }
                        }
                }

                BottomActionButtons(
                        onFilterClick = updateFilterPriority,
                        onMicClick = onMicClick,
                        onAddClick = {
                                categoryToEdit = null
                                showAddDialog = true
                        },
                        filterPriority = filterPriority,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        showFilter = false
                )
        }

        if (showAddDialog) {
                EditCategoryDialog(
                        category = null,
                        onSave = { name, frenchName, iconId ->
                                dataManager.addCategory(
                                        Category(
                                                name = name,
                                                frenchName = frenchName,
                                                iconId = iconId
                                        )
                                )
                                refreshCategories()
                                showAddDialog = false
                        },
                        onDelete = {},
                        onCreateNew = {},
                        onDismiss = { showAddDialog = false }
                )
        }

        if (showEditDialog && categoryToEdit != null) {
                EditCategoryDialog(
                        category = categoryToEdit,
                        onSave = { name, frenchName, iconId ->
                                categoryToEdit?.let { category ->
                                        dataManager.updateCategory(
                                                category.copy(
                                                        name = name,
                                                        frenchName = frenchName,
                                                        iconId = iconId
                                                )
                                        )
                                }
                                refreshCategories()
                                showEditDialog = false
                                categoryToEdit = null
                        },
                        onDelete = {
                                categoryToEdit?.let { category ->
                                        dataManager.deleteCategory(category.id)
                                }
                                refreshCategories()
                                showEditDialog = false
                                categoryToEdit = null
                        },
                        onCreateNew = {
                                showEditDialog = false
                                categoryToEdit = null
                                showAddDialog = true
                        },
                        onDismiss = {
                                showEditDialog = false
                                categoryToEdit = null
                        }
                )
        }
}
