package com.example.simpleiptv.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.example.simpleiptv.data.local.entities.FavoriteListEntity
import com.example.simpleiptv.ui.components.TvInput

@Composable
fun GenericFavoriteDialog(title: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
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
                }
            },
            confirmButton = {
                Button(onClick = { if (name.isNotBlank()) onConfirm(name) }) { Text("Ajouter") }
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
                        items(lists) { list ->
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
