package com.kmz.shoppinglist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.kmz.shoppinglist.data.Category
import com.kmz.shoppinglist.data.LocalIconProvider
import com.kmz.shoppinglist.ui.theme.*

/** Carte de catégorie pour l'écran niveau 1 (Grille 2x2) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryCard(category: Category, unboughtCount: Int, onClick: () -> Unit) {
        val context = LocalContext.current
        val iconProvider = remember { LocalIconProvider(context) }
        val iconUrl = iconProvider.getIconPath(category.getIconIdSafe())

        Card(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                shape = RoundedCornerShape(0.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
                Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                                model = iconUrl ?: "",
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                        )
                        Text(
                                text = category.name,
                                color = White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                modifier =
                                        Modifier.align(Alignment.BottomCenter)
                                                .padding(bottom = 4.dp),
                                maxLines = 1
                        )
                        if (unboughtCount > 0) {
                                Box(
                                        modifier =
                                                Modifier.align(Alignment.TopEnd)
                                                        .padding(10.dp)
                                                        .size(24.dp)
                                                        .clip(CircleShape)
                                                        .background(LightGray),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Text(
                                                text = unboughtCount.toString(),
                                                color = White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                        )
                                }
                        }
                }
        }
}

/** Carte spéciale "Toutes" (Grille 2x2) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllArticlesCard(unboughtCount: Int, onClick: () -> Unit) {
        Card(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                shape = RoundedCornerShape(0.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
                Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                                model = "file:///android_asset/shopping_list_all.png",
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(10.dp)
                        )
                        Text(
                                text = "Toutes",
                                color = White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier =
                                        Modifier.align(Alignment.BottomCenter)
                                                .padding(bottom = 4.dp)
                        )
                        if (unboughtCount > 0) {
                                Box(
                                        modifier =
                                                Modifier.align(Alignment.TopEnd)
                                                        .padding(10.dp)
                                                        .size(26.dp)
                                                        .clip(CircleShape)
                                                        .background(LightGray),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Text(
                                                text = unboughtCount.toString(),
                                                color = White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.ExtraBold
                                        )
                                }
                        }
                }
        }
}
