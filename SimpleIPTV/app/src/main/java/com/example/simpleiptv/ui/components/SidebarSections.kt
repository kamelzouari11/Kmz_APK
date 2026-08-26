package com.example.simpleiptv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.simpleiptv.ui.viewmodel.SearchScope

/**
 * "Récents" sidebar item.
 * Used in LazyListScope via item { }.
 */
@Composable
fun RecentsSection(
        isSelected: Boolean,
        onShowRecents: () -> Unit,
        onClearRecents: () -> Unit,
        upFocusRequester: FocusRequester
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
                                .focusProperties {
                                    up = upFocusRequester
                                }
                )
        }
}

/**
 * Favorites header with scope toggles and add button.
 */
@Composable
fun FavoritesHeader(
        isSelected: Boolean,
        favoriteScope: SearchScope,
        onToggleFavoriteScope: () -> Unit,
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
                                        tint = if (favoriteScope == SearchScope.ALL_PROFILES) Color(0xFFBB86FC) else Color.Gray,
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
