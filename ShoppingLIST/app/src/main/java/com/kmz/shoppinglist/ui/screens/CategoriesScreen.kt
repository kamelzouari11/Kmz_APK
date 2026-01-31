package com.kmz.shoppinglist.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kmz.shoppinglist.data.Category
import com.kmz.shoppinglist.data.DataManager
import com.kmz.shoppinglist.ui.components.CategoryCard
import com.kmz.shoppinglist.ui.components.EditCategoryDialog
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

        // Nombre total d'articles non achetés
        val totalUnbought = dataManager.getArticles().count { !it.isBought }

        // Rafraîchir les données au retour sur cet écran
        LaunchedEffect(Unit) { categories = dataManager.getCategories() }

        fun refreshCategories() {
                categories = dataManager.getCategories()
        }

        Box(modifier = Modifier.fillMaxSize().background(Black)) {
                Column(modifier = Modifier.fillMaxSize()) {
                        // En-tête avec boutons gestionnaire d'icônes et microphone
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .background(DarkGray)
                                                .padding(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Column {
                                                Text(
                                                        text = "🛒 Ma Liste",
                                                        color = White,
                                                        fontSize = 28.sp,
                                                        fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                        text =
                                                                "${categories.size} catégorie${if (categories.size > 1) "s" else ""}",
                                                        color = TextGray,
                                                        fontSize = 14.sp
                                                )
                                        }

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

                                                Spacer(modifier = Modifier.width(8.dp))

                                                // Bouton gestionnaire d'icônes (roue blanche)
                                                IconButton(onClick = onIconManagerClick) {
                                                        Icon(
                                                                imageVector =
                                                                        Icons.Default.Settings,
                                                                contentDescription =
                                                                        "Gérer les icônes",
                                                                tint = White,
                                                                modifier = Modifier.size(28.dp)
                                                        )
                                                }

                                                // Bouton microphone (saisie vocale)
                                                IconButton(onClick = onMicClick) {
                                                        Icon(
                                                                imageVector = Icons.Default.Mic,
                                                                contentDescription =
                                                                        "Saisie vocale",
                                                                tint = AccentBlue,
                                                                modifier = Modifier.size(28.dp)
                                                        )
                                                }
                                        }
                                }
                        }

                        // Liste des catégories
                        LazyColumn(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp)
                        ) {
                                // Carte "Toutes" en première position
                                item(key = "all_articles") {
                                        AllArticlesCard(
                                                unboughtCount = totalUnbought,
                                                onClick = onAllArticlesClick
                                        )
                                }

                                // Autres catégories
                                items(categories, key = { it.id }) { category ->
                                        val unboughtCount =
                                                dataManager.getUnboughtCountByCategory(category.id)
                                        CategoryCard(
                                                category = category,
                                                unboughtCount = unboughtCount,
                                                onClick = { onCategoryClick(category) },
                                                onEditClick = {
                                                        categoryToEdit = category
                                                        showEditDialog = true
                                                }
                                        )
                                }
                        }
                }

                // Bouton flottant d'ajout
                FloatingActionButton(
                        onClick = {
                                categoryToEdit = null
                                showAddDialog = true
                        },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                        containerColor = AccentBlue,
                        contentColor = White,
                        shape = CircleShape
                ) {
                        Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Ajouter une catégorie",
                                modifier = Modifier.size(28.dp)
                        )
                }
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

/** Carte spéciale "Toutes" pour voir tous les articles à acheter */
@Composable
fun AllArticlesCard(unboughtCount: Int, onClick: () -> Unit) {
        Card(
                modifier =
                        Modifier.fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clickable(onClick = onClick),
                colors = CardDefaults.cardColors(containerColor = AccentBlue.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        // Icône
                        Box(
                                modifier =
                                        Modifier.size(48.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(AccentBlue),
                                contentAlignment = Alignment.Center
                        ) {
                                Icon(
                                        imageVector = Icons.Default.List,
                                        contentDescription = null,
                                        tint = White,
                                        modifier = Modifier.size(28.dp)
                                )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Nom
                        Text(
                                text = "📋 Toutes",
                                color = White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                        )

                        // Badge nombre d'articles non achetés
                        if (unboughtCount > 0) {
                                Box(
                                        modifier =
                                                Modifier.size(32.dp)
                                                        .clip(CircleShape)
                                                        .background(AccentBlue),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Text(
                                                text = unboughtCount.toString(),
                                                color = White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                        )
                                }
                        }
                }
        }
}
