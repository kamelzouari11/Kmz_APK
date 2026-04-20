package com.example.simpleiptv.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.example.simpleiptv.data.local.entities.FavoriteListEntity
import com.example.simpleiptv.ui.components.TvInput

@Composable
fun GenericFavoriteDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, isGlobal: Boolean) -> Unit,
    showGlobalToggle: Boolean = false,
    defaultIsGlobal: Boolean = false
) {
    var name by remember { mutableStateOf("") }
    var isGlobal by remember { mutableStateOf(defaultIsGlobal) }
    val focusManager = LocalFocusManager.current
    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                Column {
                    TvInput(
                            value = name,
                            onValueChange = { name = it },
                            label = "Nom du dossier",
                            focusManager = focusManager,
                            modifier = Modifier.fillMaxWidth()
                    )
                    if (showGlobalToggle) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { isGlobal = !isGlobal }.padding(8.dp)
                        ) {
                            Checkbox(checked = isGlobal, onCheckedChange = { isGlobal = it })
                            Text(if (isGlobal) "Liste globale (tous profils)" else "Liste du profil actif")
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { 
                    if (name.isNotBlank()) {
                        onConfirm(name, isGlobal)
                    }
                }) { Text("Ajouter") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
fun GenericFavoriteDialog(
        title: String,
        lists: List<FavoriteListEntity>,
        onDismiss: () -> Unit,
        onConfirm: (Int) -> Unit
) {
    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                if (lists.isEmpty()) {
                    Text("Aucun dossier de favoris créé.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(lists, key = { it.id }) { list ->
                            Text(
                                    text = list.name,
                                    modifier =
                                            Modifier.fillMaxWidth()
                                                    .clickable { onConfirm(list.id) }
                                                    .padding(16.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } }
    )
}
