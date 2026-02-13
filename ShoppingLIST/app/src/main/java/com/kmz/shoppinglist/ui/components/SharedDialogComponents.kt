package com.kmz.shoppinglist.ui.components

import android.content.*
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
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
import com.kmz.shoppinglist.data.*
import com.kmz.shoppinglist.ui.theme.*

/** Entête partagée pour les dialogues d'édition */
@Composable
fun DialogHeader(
        title: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        onDismiss: () -> Unit
) {
    Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
                modifier =
                        Modifier.size(40.dp)
                                .clip(CircleShape)
                                .background(AccentBlue.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
        ) {
            Icon(
                    icon,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(24.dp)
            )
        }

        Text(text = title, color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)

        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = TextGray) }
    }
}

/** Barre d'outils pour la gestion des icônes (Globe, Paste, Folder, Save) */
@Composable
fun IconActionToolbar(
        name: String,
        frenchName: String,
        onIconCreated: (String) -> Unit,
        onSave: () -> Unit,
        imagePickerLauncher: ManagedActivityResultLauncher<String, Uri?>,
        iconProvider: LocalIconProvider
) {
    val context = LocalContext.current
    Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Globe (Recherche Google)
        IconButton(
                onClick = {
                    if (frenchName.isNotBlank()) {
                        val searchQuery = Uri.encode("$frenchName png transparent")
                        val url = "https://www.google.com/search?tbm=isch&q=$searchQuery"
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                },
                enabled = frenchName.isNotBlank(),
                modifier =
                        Modifier.size(48.dp)
                                .clip(CircleShape)
                                .background(if (frenchName.isNotBlank()) AccentBlue else MediumGray)
        ) {
            Icon(
                    Icons.Default.Language,
                    contentDescription = "Google Search",
                    tint = White,
                    modifier = Modifier.size(24.dp)
            )
        }

        // 2. Paste (Depuis le presse-papier)
        IconButton(
                onClick = {
                    if (frenchName.isNotBlank()) {
                        val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as
                                        ClipboardManager
                        val clip = clipboard.primaryClip
                        if (clip != null && clip.itemCount > 0) {
                            val item = clip.getItemAt(0)
                            val imageUri = item.uri
                            val clipboardText = item.text?.toString()

                            if (imageUri != null) {
                                val result = iconProvider.saveIconFromUri(imageUri, frenchName)
                                if (result.first != null) {
                                    onIconCreated(result.first!!)
                                    Toast.makeText(context, "✓ Icône créée !", Toast.LENGTH_SHORT)
                                            .show()
                                } else {
                                    Toast.makeText(context, "❌ ${result.second}", Toast.LENGTH_LONG)
                                            .show()
                                }
                            } else if (clipboardText != null && clipboardText.startsWith("http")) {
                                Toast.makeText(context, "Téléchargement...", Toast.LENGTH_SHORT)
                                        .show()
                                iconProvider.saveIconFromUrl(clipboardText, frenchName) { result ->
                                    if (result.first != null) {
                                        (context as? android.app.Activity)?.runOnUiThread {
                                            onIconCreated(result.first!!)
                                            Toast.makeText(
                                                            context,
                                                            "✓ Image téléchargée !",
                                                            Toast.LENGTH_SHORT
                                                    )
                                                    .show()
                                        }
                                    } else {
                                        (context as? android.app.Activity)?.runOnUiThread {
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
                            Toast.makeText(context, "❌ Presse-papier vide", Toast.LENGTH_SHORT)
                                    .show()
                        }
                    }
                },
                enabled = frenchName.isNotBlank(),
                modifier =
                        Modifier.size(48.dp)
                                .clip(CircleShape)
                                .background(
                                        if (frenchName.isNotBlank()) AccentGreen else MediumGray
                                )
        ) {
            Icon(
                    Icons.Default.ContentPaste,
                    contentDescription = "Coller l'image",
                    tint = White,
                    modifier = Modifier.size(24.dp)
            )
        }

        // 3. Folder (Fichier local)
        IconButton(
                onClick = {
                    if (frenchName.isNotBlank()) {
                        imagePickerLauncher.launch("image/*")
                    } else {
                        Toast.makeText(
                                        context,
                                        "Saisissez d'abord le nom français",
                                        Toast.LENGTH_SHORT
                                )
                                .show()
                    }
                },
                modifier =
                        Modifier.size(48.dp)
                                .clip(CircleShape)
                                .background(if (frenchName.isNotBlank()) AccentBlue else MediumGray)
        ) {
            Icon(
                    Icons.Default.Folder,
                    contentDescription = "Parcourir",
                    tint = White,
                    modifier = Modifier.size(24.dp)
            )
        }

        // 4. Save (Enregistrer)
        IconButton(
                onClick = onSave,
                enabled = name.isNotBlank(),
                modifier =
                        Modifier.size(48.dp)
                                .clip(CircleShape)
                                .background(if (name.isNotBlank()) AccentViolet else MediumGray)
        ) {
            Icon(
                    Icons.Default.Save,
                    contentDescription = "Enregistrer",
                    tint = White,
                    modifier = Modifier.size(28.dp)
            )
        }
    }
}

/** Grille de sélection d'icônes */
@Composable
fun IconSelectionGrid(
        iconSearchResults: List<String>,
        selectedIconId: String,
        onIconSelected: (String) -> Unit,
        iconProvider: LocalIconProvider
) {
    if (iconSearchResults.isNotEmpty()) {
        Text(
                text = "Choisissez l'icône :",
                color = TextGray,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyVerticalGrid(
                columns = GridCells.Adaptive(60.dp),
                modifier =
                        Modifier.height(180.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkGray)
                                .border(1.dp, MediumGray, RoundedCornerShape(8.dp))
                                .padding(4.dp),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(iconSearchResults) { id ->
                val iconUrl = iconProvider.getIconPath(id)
                val isSelected = id == selectedIconId
                Box(
                        modifier =
                                Modifier.size(60.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                                if (isSelected) AccentBlue.copy(alpha = 0.3f)
                                                else Color.Transparent
                                        )
                                        .border(
                                                if (isSelected) 2.dp else 0.dp,
                                                AccentBlue,
                                                RoundedCornerShape(8.dp)
                                        )
                                        .clickable { onIconSelected(id) }
                                        .padding(6.dp),
                        contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                            model = iconUrl ?: "",
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/** Dialogue de confirmation de suppression */
@Composable
fun DeleteConfirmationDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = DarkGray,
            title = { Text(title, color = White, fontWeight = FontWeight.Bold) },
            text = { Text("Cette action est irréversible.", color = TextGray) },
            confirmButton = {
                TextButton(onClick = { onConfirm() }) {
                    Text("Supprimer", color = AccentRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Annuler", color = TextGray) }
            }
    )
}
