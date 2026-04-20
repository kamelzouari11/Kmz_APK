package com.example.simpleiptv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.simpleiptv.data.local.entities.FavoriteListEntity
import com.example.simpleiptv.ui.viewmodel.FavoriteListScope
import com.example.simpleiptv.ui.viewmodel.GeneratorType
import com.example.simpleiptv.ui.viewmodel.SearchScope

/**
 * "Récents" sidebar item with scope toggle.
 * Used in LazyListScope via item { }.
 */
@Composable
fun RecentsSection(
        isSelected: Boolean,
        recentScope: SearchScope,
        onShowRecents: () -> Unit,
        onClearRecents: () -> Unit,
        onToggleRecentScope: () -> Unit
) {
        Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
        ) {
                SidebarItem(
                        text = "Récents",
                        icon = Icons.Default.History,
                        isSelected = isSelected,
                        onClick = onShowRecents,
                        onDelete = onClearRecents,
                        modifier = Modifier.weight(1f)
                )
                if (isSelected) {
                        IconButton(
                                onClick = onToggleRecentScope,
                                modifier = Modifier.size(36.dp)
                        ) {
                                Icon(
                                        imageVector = if (recentScope == SearchScope.ALL_PROFILES) Icons.Default.Groups else Icons.Default.Person,
                                        contentDescription = if (recentScope == SearchScope.ALL_PROFILES) "Tous profils" else "Profil actif",
                                        tint = if (recentScope == SearchScope.ALL_PROFILES) Color(0xFF4CAF50) else Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                )
                        }
                }
        }
}

/**
 * Favorites header with scope toggles and add button.
 */
@Composable
fun FavoritesHeader(
        isSelected: Boolean,
        favoriteScope: SearchScope,
        favoriteListScope: FavoriteListScope,
        onToggleFavoriteScope: () -> Unit,
        onToggleFavoriteListScope: () -> Unit,
        onShowAddListDialog: () -> Unit
) {
        Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
                Text(
                        text = "Favoris",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                )
                // Bouton scope favoris
                if (isSelected) {
                        IconButton(
                                onClick = onToggleFavoriteScope,
                                modifier = Modifier.size(32.dp)
                        ) {
                                Icon(
                                        imageVector = if (favoriteScope == SearchScope.ALL_PROFILES) Icons.Default.Groups else Icons.Default.Person,
                                        contentDescription = if (favoriteScope == SearchScope.ALL_PROFILES) "Tous profils" else "Profil actif",
                                        tint = if (favoriteScope == SearchScope.ALL_PROFILES) Color(0xFF4CAF50) else Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                )
                        }
                        IconButton(
                                onClick = onToggleFavoriteListScope,
                                modifier = Modifier.size(32.dp)
                        ) {
                                Icon(
                                        imageVector = if (favoriteListScope == FavoriteListScope.ALL_LISTS) Icons.Default.Star else Icons.Default.Person,
                                        contentDescription = if (favoriteListScope == FavoriteListScope.ALL_LISTS) "Toutes listes" else "Liste profil",
                                        tint = if (favoriteListScope == FavoriteListScope.ALL_LISTS) Color(0xFF4CAF50) else Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                )
                        }
                }
                var isAddFocused by remember { mutableStateOf(false) }
                IconButton(
                        onClick = onShowAddListDialog,
                        modifier = Modifier.size(40.dp).onFocusChanged {
                                isAddFocused = it.isFocused
                        }
                ) {
                        Icon(
                                Icons.Default.Add,
                                null,
                                tint = if (isAddFocused) Color.Black else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                                        .background(
                                                if (isAddFocused) Color.White else Color.Transparent,
                                                MaterialTheme.shapes.small
                                        )
                        )
                }
        }
}
