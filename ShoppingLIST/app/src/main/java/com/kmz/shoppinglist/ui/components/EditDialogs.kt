package com.kmz.shoppinglist.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.kmz.shoppinglist.data.*
import com.kmz.shoppinglist.ui.theme.*

/**
 * Dialogue d'édition complet pour un Article Permet de : modifier le nom, la traduction, l'icône,
 * changer de catégorie, supprimer
 */
@Composable
fun EditArticleDialog(
        article: Article?,
        categories: List<Category>,
        currentCategoryId: Long,
        onSave: (name: String, frenchName: String, iconId: String, categoryId: Long) -> Unit,
        onDelete: () -> Unit,
        onCreateNew: () -> Unit,
        onDismiss: () -> Unit
) {
        val context = LocalContext.current
        val iconProvider = remember { LocalIconProvider(context) }

        var name by remember(article) { mutableStateOf(article?.name ?: "") }
        var frenchName by remember(article) { mutableStateOf(article?.frenchName ?: "") }
        var iconId by remember(article) { mutableStateOf(article?.getIconIdSafe() ?: "panier") }
        var selectedCategoryId by
                remember(article) { mutableStateOf(article?.categoryId ?: currentCategoryId) }
        var showDeleteConfirm by remember { mutableStateOf(false) }
        var showCategorySelector by remember { mutableStateOf(false) }
        var iconSearchResults by remember { mutableStateOf(iconProvider.getAllIcons().take(20)) }

        // Trouver la catégorie sélectionnée
        val selectedCategory = categories.find { it.id == selectedCategoryId }

        Dialog(onDismissRequest = onDismiss) {
                Card(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkGray),
                        shape = RoundedCornerShape(20.dp)
                ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                                // En-tête avec stylo à gauche et fermer (X) à droite
                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        // Icône stylo (non cliquable, juste indicateur)
                                        Box(
                                                modifier =
                                                        Modifier.size(40.dp)
                                                                .clip(CircleShape)
                                                                .background(
                                                                        AccentBlue.copy(
                                                                                alpha = 0.2f
                                                                        )
                                                                ),
                                                contentAlignment = Alignment.Center
                                        ) {
                                                Icon(
                                                        Icons.Default.Edit,
                                                        contentDescription = null,
                                                        tint = AccentBlue,
                                                        modifier = Modifier.size(24.dp)
                                                )
                                        }

                                        Text(
                                                text =
                                                        if (article != null) "Modifier l'article"
                                                        else "Nouvel article",
                                                color = White,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold
                                        )

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                        onClick = {
                                                                if (name.isNotBlank()) {
                                                                        onSave(
                                                                                name.trim(),
                                                                                frenchName.trim(),
                                                                                iconId,
                                                                                selectedCategoryId
                                                                        )
                                                                }
                                                        },
                                                        enabled = name.isNotBlank(),
                                                        modifier =
                                                                Modifier.size(40.dp)
                                                                        .clip(CircleShape)
                                                                        .background(
                                                                                if (name.isNotBlank()
                                                                                )
                                                                                        AccentGreen
                                                                                else MediumGray
                                                                        )
                                                ) {
                                                        Icon(
                                                                Icons.Default.Save,
                                                                null,
                                                                tint = White,
                                                                modifier = Modifier.size(24.dp)
                                                        )
                                                }

                                                Spacer(modifier = Modifier.width(8.dp))

                                                IconButton(onClick = onDismiss) {
                                                        Icon(
                                                                Icons.Default.Close,
                                                                null,
                                                                tint = TextGray
                                                        )
                                                }
                                        }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

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

                                // Traduction française avec bouton globe pour recherche Google
                                // Nom en français et buttons (Globe + Coller)
                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top
                                ) {
                                        OutlinedTextField(
                                                value = frenchName,
                                                onValueChange = {
                                                        if (it.length <= 40) {
                                                                frenchName = it
                                                                // Recherche d'icônes en temps réel
                                                                iconSearchResults =
                                                                        if (it.isNotBlank()) {
                                                                                iconProvider
                                                                                        .searchIcons(
                                                                                                it
                                                                                        )
                                                                        } else {
                                                                                iconProvider
                                                                                        .getAllIcons()
                                                                                        .take(20)
                                                                        }
                                                        }
                                                },
                                                label = {
                                                        Text("Nom en français", color = TextGray)
                                                },
                                                colors =
                                                        OutlinedTextFieldDefaults.colors(
                                                                focusedTextColor = White,
                                                                unfocusedTextColor = White,
                                                                focusedBorderColor = AccentBlue,
                                                                unfocusedBorderColor = MediumGray,
                                                                cursorColor = AccentBlue
                                                        ),
                                                singleLine = false,
                                                maxLines = 2,
                                                modifier = Modifier.weight(1f)
                                        )

                                        Spacer(modifier = Modifier.width(12.dp))

                                        // Column pour Globe (haut) et Coller (bas)
                                        Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                                // Bouton globe pour recherche Google Images
                                                IconButton(
                                                        onClick = {
                                                                if (frenchName.isNotBlank()) {
                                                                        val searchQuery =
                                                                                Uri.encode(
                                                                                        "png $frenchName"
                                                                                )
                                                                        val url =
                                                                                "https://www.google.com/search?tbm=isch&q=$searchQuery"
                                                                        val intent =
                                                                                Intent(
                                                                                        Intent.ACTION_VIEW,
                                                                                        Uri.parse(
                                                                                                url
                                                                                        )
                                                                                )
                                                                        context.startActivity(
                                                                                intent
                                                                        )
                                                                }
                                                        },
                                                        enabled = frenchName.isNotBlank(),
                                                        modifier =
                                                                Modifier.size(44.dp)
                                                                        .clip(CircleShape)
                                                                        .background(
                                                                                if (frenchName
                                                                                                .isNotBlank()
                                                                                )
                                                                                        AccentBlue
                                                                                else MediumGray
                                                                        )
                                                ) {
                                                        Icon(
                                                                Icons.Default.Language,
                                                                contentDescription =
                                                                        "Google Search",
                                                                tint = White,
                                                                modifier = Modifier.size(24.dp)
                                                        )
                                                }

                                                Spacer(modifier = Modifier.width(8.dp))

                                                // Bouton COLLER depuis le presse-papier
                                                IconButton(
                                                        onClick = {
                                                                if (frenchName.isNotBlank()) {
                                                                        val clipboard =
                                                                                context.getSystemService(
                                                                                        Context.CLIPBOARD_SERVICE
                                                                                ) as
                                                                                        ClipboardManager
                                                                        val clip =
                                                                                clipboard
                                                                                        .primaryClip
                                                                        if (clip != null &&
                                                                                        clip.itemCount >
                                                                                                0
                                                                        ) {
                                                                                val item =
                                                                                        clip.getItemAt(
                                                                                                0
                                                                                        )
                                                                                val imageUri =
                                                                                        item.uri
                                                                                val clipboardText =
                                                                                        item.text
                                                                                                ?.toString()

                                                                                if (imageUri != null
                                                                                ) {
                                                                                        val result =
                                                                                                iconProvider
                                                                                                        .saveIconFromUri(
                                                                                                                imageUri,
                                                                                                                frenchName
                                                                                                        )
                                                                                        if (result.first !=
                                                                                                        null
                                                                                        ) {
                                                                                                iconId =
                                                                                                        result.first!!
                                                                                                val s =
                                                                                                        iconProvider
                                                                                                                .searchIcons(
                                                                                                                        frenchName
                                                                                                                )
                                                                                                iconSearchResults =
                                                                                                        listOf(
                                                                                                                iconId
                                                                                                        ) +
                                                                                                                s
                                                                                                                        .filter {
                                                                                                                                it !=
                                                                                                                                        iconId
                                                                                                                        }
                                                                                                Toast.makeText(
                                                                                                                context,
                                                                                                                "✓ Icône créée !",
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
                                                                                } else if (clipboardText !=
                                                                                                null &&
                                                                                                clipboardText
                                                                                                        .startsWith(
                                                                                                                "http"
                                                                                                        )
                                                                                ) {
                                                                                        Toast.makeText(
                                                                                                        context,
                                                                                                        "Téléchargement...",
                                                                                                        Toast.LENGTH_SHORT
                                                                                                )
                                                                                                .show()
                                                                                        iconProvider
                                                                                                .saveIconFromUrl(
                                                                                                        clipboardText,
                                                                                                        frenchName
                                                                                                ) {
                                                                                                        result
                                                                                                        ->
                                                                                                        if (result.first !=
                                                                                                                        null
                                                                                                        ) {
                                                                                                                iconId =
                                                                                                                        result.first!!
                                                                                                                val s =
                                                                                                                        iconProvider
                                                                                                                                .searchIcons(
                                                                                                                                        frenchName
                                                                                                                                )
                                                                                                                iconSearchResults =
                                                                                                                        listOf(
                                                                                                                                iconId
                                                                                                                        ) +
                                                                                                                                s
                                                                                                                                        .filter {
                                                                                                                                                it !=
                                                                                                                                                        iconId
                                                                                                                                        }
                                                                                                                (context as?
                                                                                                                                android.app.Activity)
                                                                                                                        ?.runOnUiThread {
                                                                                                                                Toast.makeText(
                                                                                                                                                context,
                                                                                                                                                "✓ Image téléchargée !",
                                                                                                                                                Toast.LENGTH_SHORT
                                                                                                                                        )
                                                                                                                                        .show()
                                                                                                                        }
                                                                                                        } else {
                                                                                                                (context as?
                                                                                                                                android.app.Activity)
                                                                                                                        ?.runOnUiThread {
                                                                                                                                Toast.makeText(
                                                                                                                                                context,
                                                                                                                                                "❌ ${result.second}",
                                                                                                                                                Toast.LENGTH_LONG
                                                                                                                                        )
                                                                                                                                        .show()
                                                                                                                        }
                                                                                                        }
                                                                                                }
                                                                                } else {
                                                                                        Toast.makeText(
                                                                                                        context,
                                                                                                        "❌ Pas d'image ou de lien valide",
                                                                                                        Toast.LENGTH_SHORT
                                                                                                )
                                                                                                .show()
                                                                                }
                                                                        } else {
                                                                                Toast.makeText(
                                                                                                context,
                                                                                                "❌ Presse-papier vide",
                                                                                                Toast.LENGTH_SHORT
                                                                                        )
                                                                                        .show()
                                                                        }
                                                                }
                                                        },
                                                        enabled = frenchName.isNotBlank(),
                                                        modifier =
                                                                Modifier.size(48.dp)
                                                                        .clip(CircleShape)
                                                                        .background(
                                                                                if (frenchName
                                                                                                .isNotBlank()
                                                                                )
                                                                                        AccentGreen
                                                                                else MediumGray
                                                                        )
                                                ) {
                                                        Icon(
                                                                Icons.Default.ContentPaste,
                                                                contentDescription =
                                                                        "Coller l'image",
                                                                tint = White,
                                                                modifier = Modifier.size(24.dp)
                                                        )
                                                }
                                        }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Sélection d'icône
                                Text("Icône", color = TextGray, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(8.dp))

                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(iconSearchResults.take(10)) { icon ->
                                                val path = iconProvider.getIconPath(icon)
                                                val isSelected = icon == iconId

                                                Box(
                                                        modifier =
                                                                Modifier.size(56.dp)
                                                                        .clip(
                                                                                RoundedCornerShape(
                                                                                        8.dp
                                                                                )
                                                                        )
                                                                        .background(
                                                                                if (isSelected)
                                                                                        AccentBlue
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.3f
                                                                                                )
                                                                                else MediumGray
                                                                        )
                                                                        .border(
                                                                                width =
                                                                                        if (isSelected
                                                                                        )
                                                                                                2.dp
                                                                                        else 0.dp,
                                                                                color =
                                                                                        if (isSelected
                                                                                        )
                                                                                                AccentBlue
                                                                                        else
                                                                                                MediumGray,
                                                                                shape =
                                                                                        RoundedCornerShape(
                                                                                                8.dp
                                                                                        )
                                                                        )
                                                                        .clickable {
                                                                                iconId = icon
                                                                        },
                                                        contentAlignment = Alignment.Center
                                                ) {
                                                        AsyncImage(
                                                                model = path ?: "",
                                                                contentDescription = null,
                                                                modifier = Modifier.size(48.dp)
                                                        )
                                                }
                                        }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Sélection de catégorie (style bouton dropdown comme priorité)
                                Text("Catégorie", color = TextGray, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(8.dp))

                                Box {
                                        // Bouton affichant la catégorie actuelle en bleu
                                        Surface(
                                                modifier =
                                                        Modifier.clip(RoundedCornerShape(20.dp))
                                                                .clickable {
                                                                        showCategorySelector = true
                                                                },
                                                color = AccentBlue,
                                                shape = RoundedCornerShape(20.dp)
                                        ) {
                                                Row(
                                                        modifier =
                                                                Modifier.padding(
                                                                        horizontal = 16.dp,
                                                                        vertical = 10.dp
                                                                ),
                                                        verticalAlignment =
                                                                Alignment.CenterVertically
                                                ) {
                                                        Text(
                                                                text = selectedCategory?.name
                                                                                ?: "Sélectionner",
                                                                color = White,
                                                                fontSize = 14.sp,
                                                                fontWeight = FontWeight.Medium
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Icon(
                                                                Icons.Default.ArrowDropDown,
                                                                contentDescription = null,
                                                                tint = White,
                                                                modifier = Modifier.size(20.dp)
                                                        )
                                                }
                                        }

                                        // Menu dropdown pour sélectionner la catégorie
                                        DropdownMenu(
                                                expanded = showCategorySelector,
                                                onDismissRequest = { showCategorySelector = false },
                                                modifier = Modifier.background(MediumGray)
                                        ) {
                                                categories.forEach { category ->
                                                        DropdownMenuItem(
                                                                text = {
                                                                        Row(
                                                                                verticalAlignment =
                                                                                        Alignment
                                                                                                .CenterVertically
                                                                        ) {
                                                                                // Icône de la
                                                                                // catégorie
                                                                                val catIconPath =
                                                                                        iconProvider
                                                                                                .getIconPath(
                                                                                                        category.getIconIdSafe()
                                                                                                )
                                                                                if (catIconPath !=
                                                                                                null
                                                                                ) {
                                                                                        AsyncImage(
                                                                                                model =
                                                                                                        catIconPath,
                                                                                                contentDescription =
                                                                                                        null,
                                                                                                modifier =
                                                                                                        Modifier.size(
                                                                                                                24.dp
                                                                                                        )
                                                                                        )
                                                                                        Spacer(
                                                                                                modifier =
                                                                                                        Modifier.width(
                                                                                                                8.dp
                                                                                                        )
                                                                                        )
                                                                                }
                                                                                Text(
                                                                                        text =
                                                                                                category.name,
                                                                                        color =
                                                                                                if (category.id ==
                                                                                                                selectedCategoryId
                                                                                                )
                                                                                                        AccentBlue
                                                                                                else
                                                                                                        White
                                                                                )
                                                                                if (category.id ==
                                                                                                selectedCategoryId
                                                                                ) {
                                                                                        Spacer(
                                                                                                modifier =
                                                                                                        Modifier.width(
                                                                                                                8.dp
                                                                                                        )
                                                                                        )
                                                                                        Icon(
                                                                                                Icons.Default
                                                                                                        .Check,
                                                                                                contentDescription =
                                                                                                        null,
                                                                                                tint =
                                                                                                        AccentBlue,
                                                                                                modifier =
                                                                                                        Modifier.size(
                                                                                                                18.dp
                                                                                                        )
                                                                                        )
                                                                                }
                                                                        }
                                                                },
                                                                onClick = {
                                                                        selectedCategoryId =
                                                                                category.id
                                                                        showCategorySelector = false
                                                                }
                                                        )
                                                }
                                        }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                // Boutons d'action en bas : Poubelle rouge | + bleu | Disquette
                                // verte
                                // Tous de même dimension (48dp)
                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        // Bouton Supprimer (poubelle rouge) - si article existant
                                        if (article != null) {
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

                                                // Bouton Créer nouveau (+ bleu)
                                                IconButton(
                                                        onClick = onCreateNew,
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

        // Dialogue de confirmation de suppression
        if (showDeleteConfirm) {
                AlertDialog(
                        onDismissRequest = { showDeleteConfirm = false },
                        containerColor = DarkGray,
                        title = {
                                Text(
                                        "Supprimer l'article ?",
                                        color = White,
                                        fontWeight = FontWeight.Bold
                                )
                        },
                        text = { Text("Cette action est irréversible.", color = TextGray) },
                        confirmButton = {
                                TextButton(
                                        onClick = {
                                                showDeleteConfirm = false
                                                onDelete()
                                        }
                                ) {
                                        Text(
                                                "Supprimer",
                                                color = AccentRed,
                                                fontWeight = FontWeight.Bold
                                        )
                                }
                        },
                        dismissButton = {
                                TextButton(onClick = { showDeleteConfirm = false }) {
                                        Text("Annuler", color = TextGray)
                                }
                        }
                )
        }
}

/**
 * Dialogue d'édition complet pour une Catégorie Permet de : modifier le nom, la traduction,
 * l'icône, supprimer
 */
@Composable
fun EditCategoryDialog(
        category: Category?,
        onSave: (name: String, frenchName: String, iconId: String) -> Unit,
        onDelete: () -> Unit,
        onCreateNew: () -> Unit,
        onDismiss: () -> Unit
) {
        val context = LocalContext.current
        val iconProvider = remember { LocalIconProvider(context) }

        var name by remember(category) { mutableStateOf(category?.name ?: "") }
        var frenchName by remember(category) { mutableStateOf(category?.frenchName ?: "") }
        var iconId by
                remember(category) { mutableStateOf(category?.getIconIdSafe() ?: "categorie") }
        var showDeleteConfirm by remember { mutableStateOf(false) }
        var iconSearchResults by remember { mutableStateOf(iconProvider.getAllIcons().take(20)) }

        Dialog(onDismissRequest = onDismiss) {
                Card(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkGray),
                        shape = RoundedCornerShape(20.dp)
                ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                                // En-tête avec stylo à gauche et fermer (X) à droite
                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        // Icône stylo (non cliquable, juste indicateur)
                                        Box(
                                                modifier =
                                                        Modifier.size(40.dp)
                                                                .clip(CircleShape)
                                                                .background(
                                                                        AccentBlue.copy(
                                                                                alpha = 0.2f
                                                                        )
                                                                ),
                                                contentAlignment = Alignment.Center
                                        ) {
                                                Icon(
                                                        Icons.Default.Edit,
                                                        contentDescription = null,
                                                        tint = AccentBlue,
                                                        modifier = Modifier.size(24.dp)
                                                )
                                        }

                                        Text(
                                                text =
                                                        if (category != null)
                                                                "Modifier la catégorie"
                                                        else "Nouvelle catégorie",
                                                color = White,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold
                                        )

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                        onClick = {
                                                                if (name.isNotBlank()) {
                                                                        onSave(
                                                                                name.trim(),
                                                                                frenchName.trim(),
                                                                                iconId
                                                                        )
                                                                }
                                                        },
                                                        enabled = name.isNotBlank(),
                                                        modifier =
                                                                Modifier.size(40.dp)
                                                                        .clip(CircleShape)
                                                                        .background(
                                                                                if (name.isNotBlank()
                                                                                )
                                                                                        AccentGreen
                                                                                else MediumGray
                                                                        )
                                                ) {
                                                        Icon(
                                                                Icons.Default.Save,
                                                                null,
                                                                tint = White,
                                                                modifier = Modifier.size(24.dp)
                                                        )
                                                }

                                                Spacer(modifier = Modifier.width(8.dp))

                                                IconButton(onClick = onDismiss) {
                                                        Icon(
                                                                Icons.Default.Close,
                                                                null,
                                                                tint = TextGray
                                                        )
                                                }
                                        }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Nom de la catégorie
                                OutlinedTextField(
                                        value = name,
                                        onValueChange = { name = it },
                                        label = { Text("Nom de la catégorie", color = TextGray) },
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

                                // Nom en français et buttons (Globe + Coller)
                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top
                                ) {
                                        OutlinedTextField(
                                                value = frenchName,
                                                onValueChange = {
                                                        if (it.length <= 40) {
                                                                frenchName = it
                                                                iconSearchResults =
                                                                        if (it.isNotBlank()) {
                                                                                iconProvider
                                                                                        .searchIcons(
                                                                                                it
                                                                                        )
                                                                        } else {
                                                                                iconProvider
                                                                                        .getAllIcons()
                                                                                        .take(20)
                                                                        }
                                                        }
                                                },
                                                label = {
                                                        Text("Nom en français", color = TextGray)
                                                },
                                                colors =
                                                        OutlinedTextFieldDefaults.colors(
                                                                focusedTextColor = White,
                                                                unfocusedTextColor = White,
                                                                focusedBorderColor = AccentBlue,
                                                                unfocusedBorderColor = MediumGray,
                                                                cursorColor = AccentBlue
                                                        ),
                                                singleLine = false,
                                                maxLines = 2,
                                                modifier = Modifier.weight(1f)
                                        )

                                        Spacer(modifier = Modifier.width(12.dp))

                                        // Column pour Globe (haut) et Coller (bas)
                                        Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                                // Bouton globe pour recherche Google Images
                                                IconButton(
                                                        onClick = {
                                                                if (frenchName.isNotBlank()) {
                                                                        val searchQuery =
                                                                                Uri.encode(
                                                                                        "png $frenchName"
                                                                                )
                                                                        val url =
                                                                                "https://www.google.com/search?tbm=isch&q=$searchQuery"
                                                                        val intent =
                                                                                Intent(
                                                                                        Intent.ACTION_VIEW,
                                                                                        Uri.parse(
                                                                                                url
                                                                                        )
                                                                                )
                                                                        context.startActivity(
                                                                                intent
                                                                        )
                                                                }
                                                        },
                                                        enabled = frenchName.isNotBlank(),
                                                        modifier =
                                                                Modifier.size(44.dp)
                                                                        .clip(CircleShape)
                                                                        .background(
                                                                                if (frenchName
                                                                                                .isNotBlank()
                                                                                )
                                                                                        AccentBlue
                                                                                else MediumGray
                                                                        )
                                                ) {
                                                        Icon(
                                                                Icons.Default.Language,
                                                                contentDescription =
                                                                        "Google Search",
                                                                tint = White,
                                                                modifier = Modifier.size(24.dp)
                                                        )
                                                }

                                                Spacer(modifier = Modifier.width(8.dp))

                                                // Bouton COLLER depuis le presse-papier
                                                IconButton(
                                                        onClick = {
                                                                if (frenchName.isNotBlank()) {
                                                                        val clipboard =
                                                                                context.getSystemService(
                                                                                        Context.CLIPBOARD_SERVICE
                                                                                ) as
                                                                                        ClipboardManager
                                                                        val clip =
                                                                                clipboard
                                                                                        .primaryClip
                                                                        if (clip != null &&
                                                                                        clip.itemCount >
                                                                                                0
                                                                        ) {
                                                                                val item =
                                                                                        clip.getItemAt(
                                                                                                0
                                                                                        )
                                                                                val imageUri =
                                                                                        item.uri
                                                                                val clipboardText =
                                                                                        item.text
                                                                                                ?.toString()

                                                                                if (imageUri != null
                                                                                ) {
                                                                                        val result =
                                                                                                iconProvider
                                                                                                        .saveIconFromUri(
                                                                                                                imageUri,
                                                                                                                frenchName
                                                                                                        )
                                                                                        if (result.first !=
                                                                                                        null
                                                                                        ) {
                                                                                                iconId =
                                                                                                        result.first!!
                                                                                                val s =
                                                                                                        iconProvider
                                                                                                                .searchIcons(
                                                                                                                        frenchName
                                                                                                                )
                                                                                                iconSearchResults =
                                                                                                        listOf(
                                                                                                                iconId
                                                                                                        ) +
                                                                                                                s
                                                                                                                        .filter {
                                                                                                                                it !=
                                                                                                                                        iconId
                                                                                                                        }
                                                                                                Toast.makeText(
                                                                                                                context,
                                                                                                                "✓ Icône créée !",
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
                                                                                } else if (clipboardText !=
                                                                                                null &&
                                                                                                clipboardText
                                                                                                        .startsWith(
                                                                                                                "http"
                                                                                                        )
                                                                                ) {
                                                                                        Toast.makeText(
                                                                                                        context,
                                                                                                        "Téléchargement...",
                                                                                                        Toast.LENGTH_SHORT
                                                                                                )
                                                                                                .show()
                                                                                        iconProvider
                                                                                                .saveIconFromUrl(
                                                                                                        clipboardText,
                                                                                                        frenchName
                                                                                                ) {
                                                                                                        result
                                                                                                        ->
                                                                                                        if (result.first !=
                                                                                                                        null
                                                                                                        ) {
                                                                                                                iconId =
                                                                                                                        result.first!!
                                                                                                                val s =
                                                                                                                        iconProvider
                                                                                                                                .searchIcons(
                                                                                                                                        frenchName
                                                                                                                                )
                                                                                                                iconSearchResults =
                                                                                                                        listOf(
                                                                                                                                iconId
                                                                                                                        ) +
                                                                                                                                s
                                                                                                                                        .filter {
                                                                                                                                                it !=
                                                                                                                                                        iconId
                                                                                                                                        }
                                                                                                                (context as?
                                                                                                                                android.app.Activity)
                                                                                                                        ?.runOnUiThread {
                                                                                                                                Toast.makeText(
                                                                                                                                                context,
                                                                                                                                                "✓ Image téléchargée !",
                                                                                                                                                Toast.LENGTH_SHORT
                                                                                                                                        )
                                                                                                                                        .show()
                                                                                                                        }
                                                                                                        } else {
                                                                                                                (context as?
                                                                                                                                android.app.Activity)
                                                                                                                        ?.runOnUiThread {
                                                                                                                                Toast.makeText(
                                                                                                                                                context,
                                                                                                                                                "❌ ${result.second}",
                                                                                                                                                Toast.LENGTH_LONG
                                                                                                                                        )
                                                                                                                                        .show()
                                                                                                                        }
                                                                                                        }
                                                                                                }
                                                                                } else {
                                                                                        Toast.makeText(
                                                                                                        context,
                                                                                                        "❌ Pas d'image ou de lien valide",
                                                                                                        Toast.LENGTH_SHORT
                                                                                                )
                                                                                                .show()
                                                                                }
                                                                        } else {
                                                                                Toast.makeText(
                                                                                                context,
                                                                                                "❌ Presse-papier vide",
                                                                                                Toast.LENGTH_SHORT
                                                                                        )
                                                                                        .show()
                                                                        }
                                                                }
                                                        },
                                                        enabled = frenchName.isNotBlank(),
                                                        modifier =
                                                                Modifier.size(48.dp)
                                                                        .clip(CircleShape)
                                                                        .background(
                                                                                if (frenchName
                                                                                                .isNotBlank()
                                                                                )
                                                                                        AccentGreen
                                                                                else MediumGray
                                                                        )
                                                ) {
                                                        Icon(
                                                                Icons.Default.ContentPaste,
                                                                contentDescription =
                                                                        "Coller l'image",
                                                                tint = White,
                                                                modifier = Modifier.size(24.dp)
                                                        )
                                                }
                                        }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Sélection d'icône
                                Text("Icône", color = TextGray, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(8.dp))

                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(iconSearchResults.take(10)) { icon ->
                                                val path = iconProvider.getIconPath(icon)
                                                val isSelected = icon == iconId

                                                Box(
                                                        modifier =
                                                                Modifier.size(56.dp)
                                                                        .clip(
                                                                                RoundedCornerShape(
                                                                                        8.dp
                                                                                )
                                                                        )
                                                                        .background(
                                                                                if (isSelected)
                                                                                        AccentBlue
                                                                                                .copy(
                                                                                                        alpha =
                                                                                                                0.3f
                                                                                                )
                                                                                else MediumGray
                                                                        )
                                                                        .border(
                                                                                width =
                                                                                        if (isSelected
                                                                                        )
                                                                                                2.dp
                                                                                        else 0.dp,
                                                                                color =
                                                                                        if (isSelected
                                                                                        )
                                                                                                AccentBlue
                                                                                        else
                                                                                                MediumGray,
                                                                                shape =
                                                                                        RoundedCornerShape(
                                                                                                8.dp
                                                                                        )
                                                                        )
                                                                        .clickable {
                                                                                iconId = icon
                                                                        },
                                                        contentAlignment = Alignment.Center
                                                ) {
                                                        AsyncImage(
                                                                model = path ?: "",
                                                                contentDescription = null,
                                                                modifier = Modifier.size(48.dp)
                                                        )
                                                }
                                        }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                // Boutons d'action en bas : Poubelle rouge | + bleu | Disquette
                                // verte
                                // Tous de même dimension (48dp)
                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        // Bouton Supprimer (poubelle rouge) - si catégorie
                                        // existante
                                        if (category != null) {
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

                                                // Bouton Créer nouveau (+ bleu)
                                                IconButton(
                                                        onClick = onCreateNew,
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

        // Dialogue de confirmation de suppression
        if (showDeleteConfirm) {
                AlertDialog(
                        onDismissRequest = { showDeleteConfirm = false },
                        containerColor = DarkGray,
                        title = {
                                Text(
                                        "Supprimer la catégorie ?",
                                        color = White,
                                        fontWeight = FontWeight.Bold
                                )
                        },
                        text = {
                                Text(
                                        "Tous les articles de cette catégorie seront également supprimés.",
                                        color = TextGray
                                )
                        },
                        confirmButton = {
                                TextButton(
                                        onClick = {
                                                showDeleteConfirm = false
                                                onDelete()
                                        }
                                ) {
                                        Text(
                                                "Supprimer",
                                                color = AccentRed,
                                                fontWeight = FontWeight.Bold
                                        )
                                }
                        },
                        dismissButton = {
                                TextButton(onClick = { showDeleteConfirm = false }) {
                                        Text("Annuler", color = TextGray)
                                }
                        }
                )
        }
}
