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
    val scope = rememberCoroutineScope()

    if (viewModel.showProfileManager) {
        ProfileManagerDialog(
                profiles = viewModel.profiles,
                onDismiss = { viewModel.showProfileManager = false },
                onSelectProfile = { viewModel.selectProfile(it.id) },
                onAdd = { viewModel.showAddProfileDialog = true },
                onDeleteProfile = { viewModel.deleteProfile(it) },
                onEdit = { profile ->
                    viewModel.profileToEdit = profile
                    viewModel.showAddProfileDialog = true
                },
                onPurge = { viewModel.purgeProfiles() },
                loadedProfileIds = viewModel.loadedProfileIds
        )
    }

    if (viewModel.showAddProfileDialog) {
        ProfileFormDialog(
                profile = viewModel.profileToEdit,
                onDismiss = {
                    viewModel.showAddProfileDialog = false
                    viewModel.profileToEdit = null
                },
                onSave = { name, url, user, pass, mac, type ->
                    val newProfile =
                            ProfileEntity(
                                    id = viewModel.profileToEdit?.id ?: 0,
                                    profileName = name,
                                    url = url,
                                    username = user,
                                    password = pass,
                                    macAddress = mac,
                                    type = type,
                                    isSelected = viewModel.profileToEdit?.isSelected ?: false
                            )
                    if (viewModel.profileToEdit != null) {
                        viewModel.updateProfile(newProfile)
                    } else {
                        viewModel.addProfile(newProfile)
                    }
                    viewModel.showAddProfileDialog = false
                    viewModel.profileToEdit = null
                }
        )
    }

    if (viewModel.showAddListDialog) {
        GenericFavoriteDialog(
                title = "Nouveau dossier de favoris",
                onDismiss = { viewModel.showAddListDialog = false },
                onConfirm = { name: String, isGlobal: Boolean ->
                    viewModel.addFavoriteList(name, isGlobal)
                    viewModel.showAddListDialog = false
                },
                showGlobalToggle = true
        )
    }

    if (viewModel.channelToFavorite != null) {
        GenericFavoriteDialog(
                title = "Ajouter '${viewModel.channelToFavorite?.name}' à :",
                lists = viewModel.targetFavoriteLists,
                onDismiss = { viewModel.channelToFavorite = null },
                onConfirm = { listId: Int ->
                    viewModel.addChannelToFavoriteList(
                            viewModel.channelToFavorite!!,
                            listId
                    )
                    viewModel.channelToFavorite = null
                }
        )
    }

    if (viewModel.failedProfileToReload != null) {
        AlertDialog(
                onDismissRequest = { viewModel.failedProfileToReload = null },
                title = { Text("Profil Invalide") },
                text = {
                    Text(
                            "Le profil '${viewModel.failedProfileToReload?.profileName}' n'a pas pu être chargé. Il est peut-être invalide ou l'URL est expirée."
                    )
                },
                confirmButton = {
                    var isFocused by remember { mutableStateOf(false) }
                    Box(
                            modifier =
                                    Modifier.onFocusChanged { isFocused = it.isFocused }
                                            .clickable { viewModel.failedProfileToReload = null }
                                            .focusable()
                                            .background(
                                                    if (isFocused) Color.White
                                                    else Color.Transparent,
                                                    MaterialTheme.shapes.small
                                            )
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                                "Garder",
                                color =
                                        if (isFocused) Color.Black
                                        else MaterialTheme.colorScheme.primary
                        )
                    }
                },
                dismissButton = {
                    var isFocused by remember { mutableStateOf(false) }
                    Box(
                            modifier =
                                    Modifier.onFocusChanged { isFocused = it.isFocused }
                                            .clickable {
                                                viewModel.failedProfileToReload?.let {
                                                    viewModel.deleteProfile(it)
                                                }
                                                viewModel.failedProfileToReload = null
                                            }
                                            .focusable()
                                            .background(
                                                    if (isFocused) Color.White
                                                    else Color.Transparent,
                                                    MaterialTheme.shapes.small
                                            )
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) { Text("Supprimer", color = if (isFocused) Color.Black else Color.Red) }
                }
        )
    }
}
