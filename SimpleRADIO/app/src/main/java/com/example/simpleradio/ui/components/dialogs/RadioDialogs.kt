package com.example.simpleradio.ui.components.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun AddCustomUrlDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Ajouter WebRadio") },
            text = {
                Column {
                    TextField(
                            value = name,
                            onValueChange = { name = it },
                            placeholder = { Text("Nom (ex: Impact FM)") },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                    TextField(
                            value = url,
                            onValueChange = { url = it },
                            placeholder = { Text("URL (http...)") },
                            modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                        onClick = {
                            if (name.isNotBlank() && url.isNotBlank()) {
                                onConfirm(name, url)
                            }
                        }
                ) { Text("Ajouter") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
fun AddFavoriteListDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Nouvelle Liste") },
            text = {
                TextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("Nom de la liste") }
                )
            },
            confirmButton = {
                TextButton(
                        onClick = {
                            if (name.isNotBlank()) {
                                onConfirm(name)
                            }
                        }
                ) { Text("Ajouter") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
fun SearchDialog(initialQuery: String, onDismiss: () -> Unit, onSearch: (String) -> Unit) {
    var tempQuery by remember { mutableStateOf(initialQuery) }
    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Rechercher une Radio") },
            text = {
                OutlinedTextField(
                        value = tempQuery,
                        onValueChange = { tempQuery = it },
                        placeholder = { Text("Ex: Jazz, Pop, France...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            if (tempQuery.isNotEmpty()) {
                                IconButton(onClick = { tempQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Réinitialiser")
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch(tempQuery) })
                )
            },
            confirmButton = {
                TextButton(onClick = { onSearch(tempQuery) }) { Text("Rechercher") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
fun <T> GenericFavoriteDialog(
        title: String,
        items: List<T>,
        getName: (T) -> String,
        getId: (T) -> Int,
        onDismiss: () -> Unit,
        onToggle: (Int) -> Unit,
        selectedIdsProvider: suspend () -> List<Int>
) {
    val selectedIds = remember { mutableStateListOf<Int>() }
    LaunchedEffect(Unit) {
        selectedIds.clear()
        selectedIds.addAll(selectedIdsProvider())
    }
    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                if (items.isEmpty()) Text("Aucune liste.")
                else
                        LazyColumn {
                            items(items) { list ->
                                Row(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .clickable {
                                                            val id = getId(list)
                                                            onToggle(id)
                                                            if (selectedIds.contains(id)) {
                                                                selectedIds.remove(id)
                                                            } else {
                                                                selectedIds.add(id)
                                                            }
                                                        }
                                                        .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                            checked = selectedIds.contains(getId(list)),
                                            onCheckedChange = null
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(getName(list))
                                }
                            }
                        }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } }
    )
}
