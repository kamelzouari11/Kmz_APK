package com.example.simpleiptv.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.example.simpleiptv.data.local.entities.ProfileEntity
import com.example.simpleiptv.ui.components.TvInput

@Composable
fun ProfileFormDialog(
        profile: ProfileEntity? = null,
        onDismiss: () -> Unit,
        onSave: (String, String, String, String, String?, String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var name by remember { mutableStateOf(profile?.profileName ?: "") }
    var url by remember { mutableStateOf(profile?.url ?: "") }
    var user by remember { mutableStateOf(profile?.username ?: "") }
    var pass by remember { mutableStateOf(profile?.password ?: "") }
    var mac by remember { mutableStateOf(profile?.macAddress ?: "") }
    var type by remember { mutableStateOf(profile?.type ?: "xtream") }

    var isM3uMode by remember { mutableStateOf(false) }
    var m3uUrlInput by remember { mutableStateOf("") }
    var isSaveFocused by remember { mutableStateOf(false) }

    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (profile == null) "Ajouter un profil" else "Modifier le profil") },
            text = {
                Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    TvInput(
                            value = name,
                            onValueChange = { text -> name = text },
                            label = "Nom du profil",
                            focusManager = focusManager
                    )

                    Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Text("Protocole: ", color = Color.Gray)
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = type == "xtream", onClick = { type = "xtream" })
                            Text("Xtream")
                            Spacer(modifier = Modifier.width(16.dp))
                            RadioButton(
                                    selected = type == "stalker",
                                    onClick = { type = "stalker" }
                            )
                            Text("Stalker / MAC")
                        }
                    }

                    if (type == "xtream") {
                        if (profile == null) {
                            Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) {
                                Text("Utiliser URL M3U", modifier = Modifier.weight(1f))
                                Switch(checked = isM3uMode, onCheckedChange = { isM3uMode = it })
                            }
                        }

                        if (isM3uMode) {
                            TvInput(
                                    value = m3uUrlInput,
                                    onValueChange = { input ->
                                        m3uUrlInput = input
                                        Regex("username=([^&]+)").find(input)?.let {
                                            user = it.groupValues[1]
                                        }
                                        Regex("password=([^&]+)").find(input)?.let {
                                            pass = it.groupValues[1]
                                        }
                                        Regex("^(https?://[^/?]+)").find(input)?.let {
                                            url = it.groupValues[1] + "/"
                                        }
                                    },
                                    label = "Lien M3U",
                                    focusManager = focusManager
                            )
                        }
                    }

                    TvInput(
                            value = url,
                            onValueChange = { text -> url = text },
                            label =
                                    if (type == "stalker") "URL Portal (http://...)"
                                    else "URL Serveur",
                            focusManager = focusManager
                    )

                    if (type == "xtream") {
                        TvInput(
                                value = user,
                                onValueChange = { text -> user = text },
                                label = "Utilisateur",
                                focusManager = focusManager
                        )
                        TvInput(
                                value = pass,
                                onValueChange = { text -> pass = text },
                                label = "Mot de passe",
                                isPassword = false,
                                focusManager = focusManager
                        )
                    } else {
                        TvInput(
                                value = mac,
                                onValueChange = { text -> mac = text },
                                label = "MAC Address (00:1A:79:...)",
                                focusManager = focusManager
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                            onClick = { onSave(name, url, user, pass, mac, type) },
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .onFocusChanged { isSaveFocused = it.isFocused }
                                            .scale(if (isSaveFocused) 1.05f else 1f)
                                            .background(
                                                    if (isSaveFocused)
                                                            Color.White.copy(alpha = 0.1f)
                                                    else Color.Transparent,
                                                    MaterialTheme.shapes.medium
                                            ),
                            shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Enregistrer le Profil")
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
