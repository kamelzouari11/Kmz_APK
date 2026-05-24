package com.example.simpleiptv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.simpleiptv.data.local.entities.ProfileEntity
import com.example.simpleiptv.ui.dialogs.GenericFavoriteDialog
import com.example.simpleiptv.ui.dialogs.ProfileFormDialog
import com.example.simpleiptv.ui.dialogs.ProfileManagerDialog
import com.example.simpleiptv.ui.viewmodel.MainViewModel

@Composable
fun MainDialogs(viewModel: MainViewModel) {
    val dialogState = viewModel.uiState.dialogState

    if (dialogState.showProfileManager) {
        ProfileManagerDialog(
                profiles = viewModel.uiState.profiles,
                onDismiss = { viewModel.hideProfileManager() },
                onSelectProfile = { viewModel.selectProfile(it.id) },
                onAdd = { viewModel.showAddProfileDialog() },
                onDeleteProfile = { viewModel.deleteProfile(it) },
                onEdit = { profile ->
                    viewModel.showAddProfileDialog(profile)
                },
                onPurge = { viewModel.purgeProfiles() },
                loadedProfileIds = viewModel.uiState.loadedProfileIds
        )
    }

    if (dialogState.showAddProfileDialog) {
        ProfileFormDialog(
                profile = dialogState.profileToEdit,
                onDismiss = { viewModel.hideAddProfileDialog() },
                onSave = { name, url, user, pass, mac, type ->
                    val newProfile =
                            ProfileEntity(
                                    id = dialogState.profileToEdit?.id ?: 0,
                                    profileName = name,
                                    url = url,
                                    username = user,
                                    password = pass,
                                    macAddress = mac,
                                    type = type,
                                    isSelected = dialogState.profileToEdit?.isSelected ?: false
                            )
                    if (dialogState.profileToEdit != null) {
                        viewModel.updateProfile(newProfile)
                    } else {
                        viewModel.addProfile(newProfile)
                    }
                    viewModel.hideAddProfileDialog()
                }
        )
    }

    if (dialogState.showAddListDialog) {
        GenericFavoriteDialog(
                title = "Nouveau dossier de favoris",
                onDismiss = { viewModel.hideAddListDialog() },
                onConfirm = { name: String, isGlobal: Boolean ->
                    viewModel.addFavoriteList(name, isGlobal)
                    viewModel.hideAddListDialog()
                },
                showGlobalToggle = true
        )
    }

    if (dialogState.showDeleteFavoriteListConfirm) {
        AlertDialog(
                onDismissRequest = { viewModel.hideDeleteFavoriteListConfirm() },
                title = { Text("Supprimer le dossier") },
                text = {
                    Text("Voulez-vous vraiment supprimer le dossier de favoris '${dialogState.favoriteListToDelete?.name}' ?")
                },
                confirmButton = {
                    var isFocused by remember { mutableStateOf(false) }
                    Box(
                            modifier =
                                    Modifier.onFocusChanged { isFocused = it.isFocused }
                                            .clickable { viewModel.confirmRemoveFavoriteList() }
                                            .focusable()
                                            .background(
                                                    if (isFocused) Color.White
                                                    else Color.Transparent,
                                                    MaterialTheme.shapes.small
                                            )
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                                "Supprimer",
                                color = if (isFocused) Color.Black else Color.Red
                        )
                    }
                },
                dismissButton = {
                    var isFocused by remember { mutableStateOf(false) }
                    Box(
                            modifier =
                                    Modifier.onFocusChanged { isFocused = it.isFocused }
                                            .clickable { viewModel.hideDeleteFavoriteListConfirm() }
                                            .focusable()
                                            .background(
                                                    if (isFocused) Color.White
                                                    else Color.Transparent,
                                                    MaterialTheme.shapes.small
                                            )
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                                "Annuler",
                                color = if (isFocused) Color.Black else MaterialTheme.colorScheme.primary
                        )
                    }
                }
        )
    }

    if (dialogState.channelToFavorite != null) {
        GenericFavoriteDialog(
                title = "Ajouter '${dialogState.channelToFavorite?.name}' à :",
                lists = dialogState.targetFavoriteLists,
                onDismiss = { viewModel.clearChannelToFavorite() },
                onConfirm = { listId: Int ->
                    viewModel.addChannelToFavoriteList(
                            dialogState.channelToFavorite!!,
                            listId
                    )
                    viewModel.clearChannelToFavorite()
                }
        )
    }

    if (viewModel.uiState.failedProfileToReload != null) {
        AlertDialog(
                onDismissRequest = { viewModel.clearSyncError() },
                title = { 
                    Text(
                        "Profil Invalide", 
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    ) 
                },
                text = {
                    Text(
                            "Le profil '${viewModel.uiState.failedProfileToReload?.profileName}' n'a pas pu être chargé. Il est peut-être invalide ou l'URL est expirée.",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium
                    )
                },
                containerColor = Color(0xFF1E1E1E),
                confirmButton = {
                    var isFocused by remember { mutableStateOf(false) }
                    Box(
                            modifier =
                                    Modifier.onFocusChanged { isFocused = it.isFocused }
                                            .clickable { viewModel.clearSyncError() }
                                            .focusable()
                                            .background(
                                                    if (isFocused) Color.White.copy(alpha = 0.2f)
                                                    else Color.Transparent,
                                                    androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                                            )
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                                "Garder",
                                color = if (isFocused) Color.White else Color(0xFFBB86FC)
                        )
                    }
                },
                dismissButton = {
                    var isFocused by remember { mutableStateOf(false) }
                    Box(
                            modifier =
                                    Modifier.onFocusChanged { isFocused = it.isFocused }
                                            .clickable {
                                                viewModel.uiState.failedProfileToReload?.let {
                                                    viewModel.deleteProfile(it)
                                                }
                                                viewModel.clearSyncError()
                                            }
                                            .focusable()
                                            .background(
                                                    if (isFocused) Color.White.copy(alpha = 0.2f)
                                                    else Color.Transparent,
                                                    androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                                            )
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) { Text("Supprimer", color = if (isFocused) Color.White else Color(0xFFEF5350)) }
                }
        )
    }
}
