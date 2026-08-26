package com.example.simpleiptv.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

    Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
                modifier =
                        Modifier.fillMaxSize()
                                .imePadding()
                                .navigationBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 16.dp)
        ) {
            val compactPortrait = maxWidth < 420.dp
            val dialogHorizontalPadding = if (compactPortrait) 16.dp else 24.dp
            val dialogVerticalPadding = if (compactPortrait) 16.dp else 22.dp
            val fieldSpacing = if (compactPortrait) 6.dp else 8.dp
            val maxDialogHeight = maxHeight * 0.92f

            Surface(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .widthIn(max = 560.dp)
                                    .heightIn(max = maxDialogHeight)
                                    .align(Alignment.Center),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF1E1E1E),
                    tonalElevation = 6.dp
            ) {
                Column(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .padding(
                                                horizontal = dialogHorizontalPadding,
                                                vertical = dialogVerticalPadding
                                        ),
                        verticalArrangement = Arrangement.spacedBy(fieldSpacing)
                ) {
                    Text(
                            if (profile == null) "Ajouter un profil" else "Modifier le profil",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                    )

                    Column(
                            verticalArrangement = Arrangement.spacedBy(fieldSpacing),
                            modifier =
                                    Modifier.weight(1f, fill = false)
                                            .verticalScroll(rememberScrollState())
                                            .padding(top = 6.dp, bottom = 10.dp)
                    ) {
                        TvInput(
                                value = name,
                                onValueChange = { text -> name = text },
                                label = "Nom du profil",
                                focusManager = focusManager
                        )

                        ProtocolSelector(
                                type = type,
                                compact = compactPortrait,
                                onTypeChange = { type = it }
                        )

                        if (type == "xtream") {
                            if (profile == null) {
                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                                ) {
                                    Text(
                                            "Utiliser URL M3U",
                                            color = Color.White.copy(alpha = 0.86f),
                                            modifier = Modifier.weight(1f)
                                    )
                                    Switch(
                                            checked = isM3uMode,
                                            onCheckedChange = { isM3uMode = it }
                                    )
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
                    }

                    Button(
                            onClick = { onSave(name, url, user, pass, mac, type) },
                            colors = ButtonDefaults.buttonColors(
                                    containerColor =
                                            if (isSaveFocused) Color.White
                                            else MaterialTheme.colorScheme.primary,
                                    contentColor = if (isSaveFocused) Color.Black else Color.White
                            ),
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .onFocusChanged { isSaveFocused = it.isFocused }
                                            .scale(if (isSaveFocused) 1.02f else 1f)
                                            .background(
                                                    if (isSaveFocused)
                                                            Color.White
                                                    else Color.Transparent,
                                                    MaterialTheme.shapes.medium
                                            ),
                            shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Enregistrer le profil")
                    }

                    TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Annuler")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProtocolSelector(
        type: String,
        compact: Boolean,
        onTypeChange: (String) -> Unit
) {
    val optionContent: @Composable (String, String) -> Unit = { value, label ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = type == value, onClick = { onTypeChange(value) })
            Text(label, color = Color.White.copy(alpha = 0.88f))
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
        Text("Protocole", color = Color.Gray, style = MaterialTheme.typography.labelMedium)
        if (compact) {
            Column(
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    modifier = Modifier.fillMaxWidth()
            ) {
                optionContent("xtream", "Xtream")
                optionContent("stalker", "Stalker / MAC")
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                optionContent("xtream", "Xtream")
                Spacer(modifier = Modifier.width(16.dp))
                optionContent("stalker", "Stalker / MAC")
            }
        }
    }
}
