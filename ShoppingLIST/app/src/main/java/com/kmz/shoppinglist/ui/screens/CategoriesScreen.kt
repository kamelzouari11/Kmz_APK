package com.kmz.shoppinglist.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        var filterPriority by remember { mutableStateOf(Priority.OPTIONAL) }

        // Nombre total d'articles non achetés filtrés
        val totalUnbought =
                dataManager.getArticles().count {
                        !it.isBought && it.priority.displayOrder <= filterPriority.displayOrder
                }

        // Rafraîchir les données au retour sur cet écran
        LaunchedEffect(Unit) { categories = dataManager.getCategories() }

        fun refreshCategories() {
                categories = dataManager.getCategories()
        }

        Box(modifier = Modifier.fillMaxSize().background(Black)) {
                Column(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.fillMaxWidth().background(DarkGray).padding(8.dp)) {
                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Text(
                                                text = "🛒 Ma Liste",
                                                color = White,
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(start = 8.dp)
                                        )

                                        // Boutons à droite
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                val context = LocalContext.current

                                                // Bouton Exporter (flèche haut)
                                                IconButton(
                                                        onClick = {
                                                                val result =
                                                                        dataManager.exportDatabase()
                                                                Toast.makeText(
                                                                                context,
                                                                                if (result.first)
                                                                                        "Sauvegardé: ${result.second}"
                                                                                else
                                                                                        "Erreur: ${result.second}",
                                                                                Toast.LENGTH_LONG
                                                                        )
                                                                        .show()
                                                        }
                                                ) {
                                                        Icon(
                                                                Icons.Default.Upload,
                                                                "Exporter",
                                                                tint = White
                                                        )
                                                }

                                                // Bouton Importer (flèche bas)
                                                IconButton(
                                                        onClick = {
                                                                val result =
                                                                        dataManager.importDatabase()
                                                                if (result.first)
                                                                        refreshCategories()
                                                                Toast.makeText(
                                                                                context,
                                                                                result.second,
                                                                                Toast.LENGTH_LONG
                                                                        )
                                                                        .show()
                                                        }
                                                ) {
                                                        Icon(
                                                                Icons.Default.Download,
                                                                "Importer",
                                                                tint = White
                                                        )
                                                }

                                                // Bouton gestionnaire d'icônes (Stylo blanc sur
                                                // cercle bleu)
                                                IconButton(
                                                        onClick = onIconManagerClick,
                                                        modifier =
                                                                Modifier.size(40.dp)
                                                                        .background(
                                                                                AccentViolet,
                                                                                CircleShape
                                                                        )
                                                ) {
                                                        Icon(
                                                                imageVector = Icons.Default.Edit,
                                                                contentDescription =
                                                                        "Gérer les icônes",
                                                                tint = White,
                                                                modifier = Modifier.size(22.dp)
                                                        )
                                                }
                                        }
                                }
                        }

                        // Liste des catégories en grille 2x2
                        val margin = (LocalConfiguration.current.screenWidthDp * 0.02f).dp

                        LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentPadding =
                                        PaddingValues(
                                                start = margin,
                                                end = margin,
                                                top = 12.dp,
                                                bottom = 80.dp
                                        ),
                                horizontalArrangement = Arrangement.spacedBy(margin),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                                // Carte "Toutes" en première position
                                item {
                                        AllArticlesCard(
                                                unboughtCount = totalUnbought,
                                                onClick = onAllArticlesClick
                                        )
                                }

                                // Autres catégories
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

                // Barres de boutons d'action (Filtre, Micro, Ajout)
                com.kmz.shoppinglist.ui.components.BottomActionButtons(
                        onFilterClick = { priority -> filterPriority = priority },
                        onMicClick = onMicClick,
                        onAddClick = {
                                categoryToEdit = null
                                showAddDialog = true
                        },
                        filterPriority = filterPriority,
                        modifier = Modifier.align(Alignment.BottomCenter)
                )
        }

        // Dialog d'ajout de catégorie (nouveau)
        if (showAddDialog) {
                EditCategoryDialog(
                        category = null,
                        onSave = { name, frenchName, iconId ->
                                val newCategory =
                                        Category(
                                                name = name,
                                                frenchName = frenchName,
                                                iconId = iconId
                                        )
                                dataManager.addCategory(newCategory)
                                refreshCategories()
                                showAddDialog = false
                        },
                        onDelete = {},
                        onCreateNew = {},
                        onDismiss = { showAddDialog = false }
                )
        }

        // Dialog d'édition de catégorie
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
