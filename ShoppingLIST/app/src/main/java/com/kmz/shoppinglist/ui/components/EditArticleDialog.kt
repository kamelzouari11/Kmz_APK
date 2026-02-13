package com.kmz.shoppinglist.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kmz.shoppinglist.data.*
import com.kmz.shoppinglist.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditArticleDialog(
        article: Article?,
        categories: List<Category>,
        currentCategoryId: Long,
        onSave: (name: String, frenchName: String, iconId: String, categoryId: Long) -> Unit,
        onDelete: () -> Unit,
        onCreateNew: (categoryId: Long) -> Unit,
        onDismiss: () -> Unit
) {
        var name by remember { mutableStateOf(article?.name ?: "") }
        var frenchName by remember { mutableStateOf(article?.frenchName ?: "") }
        var iconId by remember { mutableStateOf(article?.iconId ?: "") }
        var selectedCategoryId by remember {
                mutableLongStateOf(article?.categoryId ?: currentCategoryId)
        }
        var showDeleteConfirm by remember { mutableStateOf(false) }

        val context = LocalContext.current
        val iconProvider = remember { LocalIconProvider(context) }
        var iconSearchResults by remember {
                mutableStateOf(
                        if (frenchName.isNotBlank()) iconProvider.searchIcons(frenchName)
                        else iconProvider.getAllIcons().take(20)
                )
        }

        val imagePickerLauncher =
                rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                        if (uri != null && frenchName.isNotBlank()) {
                                val result = iconProvider.saveIconFromUri(uri, frenchName)
                                if (result.first != null) {
                                        iconId = result.first!!
                                        iconSearchResults =
                                                listOf(iconId) +
                                                        iconProvider.searchIcons(frenchName)
                                                                .filter { it != iconId }
                                        Toast.makeText(
                                                        context,
                                                        "✓ Icône ajoutée !",
                                                        Toast.LENGTH_SHORT
                                                )
                                                .show()
                                } else {
                                        Toast.makeText(
                                                        context,
                                                        "❌ ${result.second}",
                                                        Toast.LENGTH_LONG
                                                )
                                                .show()
                                }
                        }
                }

        Dialog(
                onDismissRequest = onDismiss,
                properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
                Surface(
                        modifier =
                                Modifier.fillMaxWidth(0.95f)
                                        .wrapContentHeight()
                                        .clip(RoundedCornerShape(24.dp)),
                        color = DarkGray
                ) {
                        Column(
                                modifier =
                                        Modifier.padding(20.dp)
                                                .verticalScroll(rememberScrollState())
                        ) {
                                DialogHeader(
                                        title =
                                                if (article != null) "Modifier l'article"
                                                else "Nouvel article",
                                        icon = Icons.Default.Edit,
                                        onDismiss = onDismiss
                                )

                                // Nom de l'article
                                OutlinedTextField(
                                        value = name,
                                        onValueChange = { name = it },
                                        label = { Text("Nom de l'article", color = TextGray) },
                                        colors =
                                                OutlinedTextFieldDefaults.colors(
                                                        focusedTextColor = White,
                                                        unfocusedTextColor = White,
                                                        focusedBorderColor = AccentBlue,
                                                        unfocusedBorderColor = MediumGray,
                                                        cursorColor = AccentBlue
                                                ),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Nom en français (recherche icône)
                                OutlinedTextField(
                                        value = frenchName,
                                        onValueChange = {
                                                if (it.length <= 40) {
                                                        frenchName = it
                                                        iconSearchResults =
                                                                if (it.isNotBlank())
                                                                        iconProvider.searchIcons(it)
                                                                else
                                                                        iconProvider
                                                                                .getAllIcons()
                                                                                .take(20)
                                                }
                                        },
                                        label = {
                                                Text(
                                                        "Nom en français (pour l'icône)",
                                                        color = TextGray
                                                )
                                        },
                                        colors =
                                                OutlinedTextFieldDefaults.colors(
                                                        focusedTextColor = White,
                                                        unfocusedTextColor = White,
                                                        focusedBorderColor = AccentBlue,
                                                        unfocusedBorderColor = MediumGray,
                                                        cursorColor = AccentBlue
                                                ),
                                        modifier = Modifier.fillMaxWidth()
                                )

                                IconActionToolbar(
                                        name = name,
                                        frenchName = frenchName,
                                        onIconCreated = { newIconId ->
                                                iconId = newIconId
                                                iconSearchResults =
                                                        listOf(iconId) +
                                                                iconProvider.searchIcons(frenchName)
                                                                        .filter { it != newIconId }
                                        },
                                        onSave = {
                                                onSave(
                                                        name.trim(),
                                                        frenchName.trim(),
                                                        iconId,
                                                        selectedCategoryId
                                                )
                                        },
                                        imagePickerLauncher = imagePickerLauncher,
                                        iconProvider = iconProvider
                                )

                                IconSelectionGrid(
                                        iconSearchResults = iconSearchResults,
                                        selectedIconId = iconId,
                                        onIconSelected = { iconId = it },
                                        iconProvider = iconProvider
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Sélection de catégorie
                                Text(
                                        text = "Catégorie :",
                                        color = TextGray,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Row(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                        categories.forEach { cat ->
                                                FilterChip(
                                                        selected = cat.id == selectedCategoryId,
                                                        onClick = { selectedCategoryId = cat.id },
                                                        label = { Text(cat.name) },
                                                        colors =
                                                                FilterChipDefaults.filterChipColors(
                                                                        selectedContainerColor =
                                                                                AccentBlue,
                                                                        selectedLabelColor = White,
                                                                        containerColor = MediumGray,
                                                                        labelColor = TextGray
                                                                )
                                                )
                                        }
                                }

                                if (article != null) {
                                        Spacer(modifier = Modifier.height(24.dp))
                                        Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.Center
                                        ) {
                                                IconButton(
                                                        onClick = { showDeleteConfirm = true },
                                                        modifier =
                                                                Modifier.size(48.dp)
                                                                        .clip(CircleShape)
                                                                        .background(AccentRed)
                                                ) {
                                                        Icon(
                                                                Icons.Default.Delete,
                                                                contentDescription = "Supprimer",
                                                                tint = White,
                                                                modifier = Modifier.size(28.dp)
                                                        )
                                                }
                                                Spacer(modifier = Modifier.width(24.dp))
                                                IconButton(
                                                        onClick = {
                                                                onCreateNew(selectedCategoryId)
                                                        },
                                                        modifier =
                                                                Modifier.size(48.dp)
                                                                        .clip(CircleShape)
                                                                        .background(AccentBlue)
                                                ) {
                                                        Icon(
                                                                Icons.Default.Add,
                                                                contentDescription =
                                                                        "Créer nouveau",
                                                                tint = White,
                                                                modifier = Modifier.size(28.dp)
                                                        )
                                                }
                                        }
                                }
                        }
                }
        }

        if (showDeleteConfirm) {
                DeleteConfirmationDialog(
                        title = "Supprimer l'article ?",
                        onConfirm = {
                                showDeleteConfirm = false
                                onDelete()
                        },
                        onDismiss = { showDeleteConfirm = false }
                )
        }
}
