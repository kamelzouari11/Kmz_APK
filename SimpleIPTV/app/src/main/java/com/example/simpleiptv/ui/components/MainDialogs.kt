package com.example.simpleiptv.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
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
                onPurge = { viewModel.purgeProfiles() }
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
                onConfirm = { name: String ->
                    viewModel.addFavoriteList(name)
                    viewModel.showAddListDialog = false
                }
        )
    }

    if (viewModel.channelToFavorite != null) {
        GenericFavoriteDialog(
                title = "Ajouter '${viewModel.channelToFavorite?.name}' à :",
                lists = viewModel.favoriteLists,
                onDismiss = { viewModel.channelToFavorite = null },
                onConfirm = { listId: Int ->
                    viewModel.addChannelToFavoriteList(
                            viewModel.channelToFavorite!!.stream_id,
                            listId
                    )
                    viewModel.channelToFavorite = null
                }
        )
    }

    if (viewModel.syncError != null) {
        AlertDialog(
                onDismissRequest = { viewModel.syncError = null },
                title = { Text("Échec de synchronisation") },
                text = { Text(viewModel.syncError!!) },
                confirmButton = {
                    Button(onClick = { viewModel.syncError = null }) { Text("D'accord") }
                },
                dismissButton = {
                    val activeProfile =
                            viewModel.profiles.find { it.id == viewModel.activeProfileId }
                    if (activeProfile != null) {
                        TextButton(
                                onClick = {
                                    viewModel.deleteProfile(activeProfile)
                                    viewModel.syncError = null
                                }
                        ) {
                            Text(
                                    "Supprimer ce profil",
                                    color = androidx.compose.ui.graphics.Color.Red
                            )
                        }
                    }
                }
        )
    }
}
