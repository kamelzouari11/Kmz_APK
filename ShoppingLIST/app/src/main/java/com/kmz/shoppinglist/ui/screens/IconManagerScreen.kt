package com.kmz.shoppinglist.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kmz.shoppinglist.data.*
import com.kmz.shoppinglist.ui.theme.*

/** Écran de gestion des icônes locales (depuis assets/icons/) */
@Composable
fun IconManagerScreen(dataManager: DataManager, onBackClick: () -> Unit) {
        val context = LocalContext.current
        val iconProvider = remember { LocalIconProvider(context) }

        var categories by remember { mutableStateOf(dataManager.getCategories()) }
        var articles by remember { mutableStateOf(dataManager.getArticles()) }

        // État pour l'élément sélectionné
        var selectedItem by remember { mutableStateOf<Any?>(null) }
        var searchResults by remember { mutableStateOf<List<String>>(emptyList()) }
        var currentFrenchName by remember { mutableStateOf("") }

        // Tab sélectionné (0 = Catégories, 1 = Articles)
        var selectedTab by remember { mutableStateOf(0) }

        Box(modifier = Modifier.fillMaxSize().background(Black)) {
                Column(modifier = Modifier.fillMaxSize()) {
                        // En-tête
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth().background(DarkGray).padding(16.dp)
                        ) {
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
                                                        text = "🎨 Icônes Dynamiques",
                                                        color = White,
                                                        fontSize = 18.sp,
                                                        fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                        text =
                                                                "Dossier: ${iconProvider.getIconsDirectoryPath()}",
                                                        color = TextGray,
                                                        fontSize = 10.sp
                                                )
                                        }
                                }
                        }

                        // Tabs
                        TabRow(
                                selectedTabIndex = selectedTab,
                                containerColor = DarkGray,
                                contentColor = White
                        ) {
                                Tab(
                                        selected = selectedTab == 0,
                                        onClick = { selectedTab = 0 },
                                        text = { Text("Catégories (${categories.size})") }
                                )
                                Tab(
                                        selected = selectedTab == 1,
                                        onClick = { selectedTab = 1 },
                                        text = { Text("Articles (${articles.size})") }
                                )
                        }

                        // Liste des éléments
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
                                                if (categoryArticles.isNotEmpty()) {
                                                        item {
                                                                Text(
                                                                        text = category.name,
                                                                        color = TextGray,
                                                                        fontSize = 12.sp,
                                                                        fontWeight =
                                                                                FontWeight.Bold,
                                                                        modifier =
                                                                                Modifier.padding(
                                                                                        start =
                                                                                                16.dp,
                                                                                        top = 12.dp,
                                                                                        bottom =
                                                                                                4.dp
                                                                                )
                                                                )
                                                        }
                                                        items(categoryArticles, key = { it.id }) {
                                                                article ->
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

                        // Panel de sélection d'icône locale
                        if (selectedItem != null) {
                                IconSelectionPanel(
                                        itemName =
                                                when (val item = selectedItem) {
                                                        is Category -> item.name
                                                        is Article -> item.name
                                                        else -> ""
                                                },
                                        frenchName = currentFrenchName,
                                        onFrenchNameChange = {
                                                currentFrenchName = it
                                                // Mettre à jour en temps réel la recherche
                                                searchResults = iconProvider.searchIcons(it)
                                        },
                                        searchResults = searchResults,
                                        onIconSelected = { iconId ->
                                                when (val item = selectedItem) {
                                                        is Category -> {
                                                                // Categories n'ont pas encore
                                                                // updateArticleFrenchName mais on
                                                                // peut surcharger iconId
                                                                dataManager.updateCategoryIcon(
                                                                        item.id,
                                                                        iconId
                                                                )
                                                                categories =
                                                                        dataManager.getCategories()
                                                        }
                                                        is Article -> {
                                                                dataManager.updateArticleIcon(
                                                                        item.id,
                                                                        iconId
                                                                )
                                                                dataManager.updateArticleFrenchName(
                                                                        item.id,
                                                                        currentFrenchName
                                                                )
                                                                articles = dataManager.getArticles()
                                                        }
                                                }
                                                selectedItem = null
                                        },
                                        onDismiss = { selectedItem = null }
                                )
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
                                                .background(MediumGray),
                                contentAlignment = Alignment.Center
                        ) {
                                AsyncImage(
                                        model =
                                                ImageRequest.Builder(context)
                                                        .data(
                                                                iconUrl ?: ""
                                                        ) // Fallback géré par l'absence d'image
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

                        Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = White,
                                modifier = Modifier.size(24.dp)
                        )
                }
        }
}

@Composable
fun IconSelectionPanel(
        itemName: String,
        frenchName: String,
        onFrenchNameChange: (String) -> Unit,
        searchResults: List<String>,
        onIconSelected: (String) -> Unit,
        onDismiss: () -> Unit
) {
        val context = LocalContext.current
        val iconProvider = remember { LocalIconProvider(context) }

        Card(
                modifier = Modifier.fillMaxWidth().heightIn(min = 250.dp, max = 400.dp),
                colors = CardDefaults.cardColors(containerColor = MediumGray),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                                Text(
                                        text = "Modifier: $itemName",
                                        color = White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                )
                                IconButton(onClick = onDismiss) {
                                        Icon(Icons.Default.Close, null, tint = TextGray)
                                }
                        }

                        // Case pour renseigner le nom en français
                        OutlinedTextField(
                                value = frenchName,
                                onValueChange = onFrenchNameChange,
                                label = {
                                        Text("Nom en Français (pour l'icône)", color = AccentBlue)
                                },
                                colors =
                                        OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = White,
                                                unfocusedTextColor = White,
                                                focusedBorderColor = AccentBlue,
                                                unfocusedBorderColor = LightGray
                                        ),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                singleLine = true
                        )

                        Text(
                                text = "Icônes trouvées dans le dossier dynamique :",
                                color = TextGray,
                                fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (searchResults.isEmpty()) {
                                Box(Modifier.fillMaxWidth().height(100.dp), Alignment.Center) {
                                        Text("Aucun fichier .png correspondant", color = TextGray)
                                }
                        } else {
                                LazyRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                        items(searchResults) { iconId ->
                                                val path = iconProvider.getIconPath(iconId)
                                                Card(
                                                        modifier =
                                                                Modifier.size(120.dp).clickable {
                                                                        onIconSelected(iconId)
                                                                },
                                                        colors =
                                                                CardDefaults.cardColors(
                                                                        containerColor = DarkGray
                                                                ),
                                                        shape = RoundedCornerShape(16.dp)
                                                ) {
                                                        Column(
                                                                Modifier.fillMaxSize()
                                                                        .padding(8.dp),
                                                                horizontalAlignment =
                                                                        Alignment
                                                                                .CenterHorizontally,
                                                                verticalArrangement =
                                                                        Arrangement.Center
                                                        ) {
                                                                AsyncImage(
                                                                        model =
                                                                                ImageRequest
                                                                                        .Builder(
                                                                                                context
                                                                                        )
                                                                                        .data(path)
                                                                                        .crossfade(
                                                                                                true
                                                                                        )
                                                                                        .build(),
                                                                        contentDescription = iconId,
                                                                        modifier =
                                                                                Modifier.size(80.dp)
                                                                )
                                                                Spacer(Modifier.height(4.dp))
                                                                Text(
                                                                        text = iconId.take(12),
                                                                        color = White,
                                                                        fontSize = 10.sp,
                                                                        maxLines = 1
                                                                )
                                                        }
                                                }
                                        }
                                }
                        }
                }
        }
}
