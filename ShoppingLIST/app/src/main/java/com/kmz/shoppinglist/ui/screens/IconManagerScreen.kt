package com.kmz.shoppinglist.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kmz.shoppinglist.data.*
import com.kmz.shoppinglist.ui.components.*
import com.kmz.shoppinglist.ui.theme.*

/** Écran de gestion des icônes locales (depuis assets/icons/) */
@Composable
fun IconManagerScreen(dataManager: DataManager, onBackClick: () -> Unit) {
        val context = LocalContext.current
        val iconProvider = remember { LocalIconProvider(context) }

        var categories by remember { mutableStateOf(dataManager.getCategories()) }
        var articles by remember { mutableStateOf(dataManager.getArticles()) }

        var selectedItem by remember { mutableStateOf<Any?>(null) }
        var searchResults by remember { mutableStateOf<List<String>>(emptyList()) }
        var currentFrenchName by remember { mutableStateOf("") }
        var selectedTab by remember { mutableStateOf(0) }
        var collapsedCategories by remember { mutableStateOf(categories.map { it.id }.toSet()) }

        Box(modifier = Modifier.fillMaxSize().background(BlueNoir)) {
                Column(modifier = Modifier.fillMaxSize()) {
                        SimpleScreenHeader(
                                title = "Modification Création",
                                onBackClick = onBackClick
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                                Button(
                                        onClick = { selectedTab = 0 },
                                        modifier = Modifier.weight(1f),
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        containerColor =
                                                                if (selectedTab == 0) AccentBlue
                                                                else MediumGray
                                                ),
                                        shape = RoundedCornerShape(12.dp)
                                ) { Text("Catégories", color = White) }

                                Button(
                                        onClick = { selectedTab = 1 },
                                        modifier = Modifier.weight(1f),
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        containerColor =
                                                                if (selectedTab == 1) AccentBlue
                                                                else MediumGray
                                                ),
                                        shape = RoundedCornerShape(12.dp)
                                ) { Text("Articles", color = White) }
                        }

                        LazyColumn(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                                if (selectedTab == 0) {
                                        items(categories, key = { it.id }) { category ->
                                                IconItemRow(
                                                        name = category.name,
                                                        frenchName = category.frenchName ?: "",
                                                        iconId = category.getIconIdSafe(),
                                                        isSelected = selectedItem == category,
                                                        onClick = {
                                                                selectedItem = category
                                                                currentFrenchName =
                                                                        category.frenchName ?: ""
                                                                searchResults =
                                                                        iconProvider.searchIcons(
                                                                                currentFrenchName
                                                                                        .ifBlank {
                                                                                                category.name
                                                                                        }
                                                                        )
                                                        }
                                                )
                                        }
                                } else {
                                        val articlesByCategory = articles.groupBy { it.categoryId }
                                        categories.forEach { category ->
                                                val categoryArticles =
                                                        articlesByCategory[category.id]
                                                                ?: emptyList()
                                                val isCollapsed =
                                                        collapsedCategories.contains(category.id)

                                                if (categoryArticles.isNotEmpty()) {
                                                        item {
                                                                Row(
                                                                        modifier =
                                                                                Modifier.fillMaxWidth()
                                                                                        .clickable {
                                                                                                collapsedCategories =
                                                                                                        if (isCollapsed
                                                                                                        )
                                                                                                                collapsedCategories -
                                                                                                                        category.id
                                                                                                        else
                                                                                                                collapsedCategories +
                                                                                                                        category.id
                                                                                        }
                                                                                        .padding(
                                                                                                horizontal =
                                                                                                        16.dp,
                                                                                                vertical =
                                                                                                        8.dp
                                                                                        ),
                                                                        verticalAlignment =
                                                                                Alignment
                                                                                        .CenterVertically
                                                                ) {
                                                                        Icon(
                                                                                imageVector =
                                                                                        if (isCollapsed
                                                                                        )
                                                                                                Icons.Default
                                                                                                        .KeyboardArrowRight
                                                                                        else
                                                                                                Icons.Default
                                                                                                        .KeyboardArrowDown,
                                                                                contentDescription =
                                                                                        null,
                                                                                tint = TextGray,
                                                                                modifier =
                                                                                        Modifier.size(
                                                                                                20.dp
                                                                                        )
                                                                        )
                                                                        Spacer(
                                                                                modifier =
                                                                                        Modifier.width(
                                                                                                8.dp
                                                                                        )
                                                                        )
                                                                        Text(
                                                                                text =
                                                                                        category.name,
                                                                                color = TextGray,
                                                                                fontSize = 14.sp,
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold,
                                                                                modifier =
                                                                                        Modifier.weight(
                                                                                                1f
                                                                                        )
                                                                        )
                                                                        Text(
                                                                                text =
                                                                                        "${categoryArticles.size} articles",
                                                                                color =
                                                                                        TextDarkGray,
                                                                                fontSize = 12.sp
                                                                        )
                                                                }
                                                        }

                                                        if (!isCollapsed) {
                                                                items(
                                                                        categoryArticles,
                                                                        key = { it.id }
                                                                ) { article ->
                                                                        IconItemRow(
                                                                                name = article.name,
                                                                                frenchName =
                                                                                        article.frenchName
                                                                                                ?: "",
                                                                                iconId =
                                                                                        article.getIconIdSafe(),
                                                                                isSelected =
                                                                                        selectedItem ==
                                                                                                article,
                                                                                onClick = {
                                                                                        selectedItem =
                                                                                                article
                                                                                        currentFrenchName =
                                                                                                article.frenchName
                                                                                                        ?: ""
                                                                                        searchResults =
                                                                                                iconProvider
                                                                                                        .searchIcons(
                                                                                                                currentFrenchName
                                                                                                                        .ifBlank {
                                                                                                                                article.name
                                                                                                                        }
                                                                                                        )
                                                                                }
                                                                        )
                                                                }
                                                        }
                                                }
                                        }
                                }
                        }

                        if (selectedItem != null) {
                                when (val item = selectedItem) {
                                        is Category -> {
                                                EditCategoryDialog(
                                                        category = item,
                                                        onSave = { name, frenchName, iconId ->
                                                                dataManager.updateCategory(
                                                                        item.copy(
                                                                                name = name,
                                                                                frenchName =
                                                                                        frenchName,
                                                                                iconId = iconId
                                                                        )
                                                                )
                                                                categories =
                                                                        dataManager.getCategories()
                                                                selectedItem = null
                                                        },
                                                        onDelete = {
                                                                dataManager.deleteCategory(item.id)
                                                                categories =
                                                                        dataManager.getCategories()
                                                                selectedItem = null
                                                        },
                                                        onCreateNew = { selectedItem = null },
                                                        onDismiss = { selectedItem = null }
                                                )
                                        }
                                        is Article -> {
                                                EditArticleDialog(
                                                        article = item,
                                                        categories = categories,
                                                        currentCategoryId = item.categoryId,
                                                        onSave = {
                                                                name,
                                                                frenchName,
                                                                iconId,
                                                                categoryId ->
                                                                val updatedArticle =
                                                                        item.copy(
                                                                                name = name,
                                                                                frenchName =
                                                                                        frenchName,
                                                                                iconId = iconId,
                                                                                categoryId =
                                                                                        categoryId
                                                                        )
                                                                if (item.id == -1L)
                                                                        dataManager.addArticle(
                                                                                updatedArticle
                                                                        )
                                                                else
                                                                        dataManager.updateArticle(
                                                                                updatedArticle
                                                                        )
                                                                articles = dataManager.getArticles()
                                                                selectedItem = null
                                                        },
                                                        onDelete = {
                                                                if (item.id != -1L)
                                                                        dataManager.deleteArticle(
                                                                                item.id
                                                                        )
                                                                articles = dataManager.getArticles()
                                                                selectedItem = null
                                                        },
                                                        onCreateNew = { categoryId ->
                                                                selectedItem =
                                                                        Article(
                                                                                id = -1,
                                                                                name = "",
                                                                                frenchName = "",
                                                                                iconId = "panier",
                                                                                categoryId =
                                                                                        categoryId,
                                                                                isBought = false
                                                                        )
                                                        },
                                                        onDismiss = { selectedItem = null }
                                                )
                                        }
                                }
                        }
                }
        }
}

@Composable
fun IconItemRow(
        name: String,
        frenchName: String,
        iconId: String,
        isSelected: Boolean,
        onClick: () -> Unit
) {
        val context = LocalContext.current
        val iconProvider = remember { LocalIconProvider(context) }
        val iconUrl = iconProvider.getIconPath(iconId)

        Card(
                modifier =
                        Modifier.fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clickable(onClick = onClick)
                                .then(
                                        if (isSelected)
                                                Modifier.border(
                                                        2.dp,
                                                        AccentBlue,
                                                        RoundedCornerShape(12.dp)
                                                )
                                        else Modifier
                                ),
                colors =
                        CardDefaults.cardColors(
                                containerColor = if (isSelected) MediumGray else DarkGray
                        ),
                shape = RoundedCornerShape(12.dp)
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Box(
                                modifier =
                                        Modifier.size(72.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color.Transparent),
                                contentAlignment = Alignment.Center
                        ) {
                                AsyncImage(
                                        model =
                                                ImageRequest.Builder(context)
                                                        .data(iconUrl ?: "")
                                                        .crossfade(true)
                                                        .build(),
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        contentScale = ContentScale.Fit
                                )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        text = name,
                                        color = White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                )
                                if (frenchName.isNotBlank()) {
                                        Text(
                                                text = "($frenchName)",
                                                color = TextGray,
                                                fontSize = 12.sp
                                        )
                                }
                        }
                }
        }
}
