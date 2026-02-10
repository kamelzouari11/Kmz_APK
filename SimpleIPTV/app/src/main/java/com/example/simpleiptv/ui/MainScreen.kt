package com.example.simpleiptv.ui

import android.content.ContentValues
import android.content.res.Configuration
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.example.simpleiptv.VideoPlayerView
import com.example.simpleiptv.data.local.entities.ChannelEntity
import com.example.simpleiptv.data.local.entities.ProfileEntity
import com.example.simpleiptv.ui.components.TvInput
import com.example.simpleiptv.ui.dialogs.GenericFavoriteDialog
import com.example.simpleiptv.ui.dialogs.ProfileFormDialog
import com.example.simpleiptv.ui.dialogs.ProfileManagerDialog
import com.example.simpleiptv.ui.viewmodel.GeneratorType
import com.example.simpleiptv.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MainScreen(
        viewModel: MainViewModel,
        exoPlayer: Player?,
        exportJson: suspend () -> String,
        importJson: suspend (String) -> Unit,
        getStreamUrl: suspend (String) -> String
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // SAF Launchers
    // Direct Save to Downloads (Avoids DocumentsUI crash on some TV boxes)
    val onSave: () -> Unit = {
        scope.launch(Dispatchers.IO) {
            try {
                val json = exportJson()
                val fileName = "simple_iptv_backup_${System.currentTimeMillis() / 1000}.json"
                val resolver = context.contentResolver

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val contentValues =
                            ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                                put(
                                        MediaStore.MediaColumns.RELATIVE_PATH,
                                        Environment.DIRECTORY_DOWNLOADS
                                )
                            }
                    val uri =
                            resolver.insert(
                                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                    contentValues
                            )
                    uri?.let {
                        resolver.openOutputStream(it)?.use { stream ->
                            stream.write(json.toByteArray())
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                            context,
                                            "Sauvegardé : $fileName dans Téléchargements",
                                            Toast.LENGTH_LONG
                                    )
                                    .show()
                        }
                    }
                            ?: throw Exception("Erreur création fichier MediaStore")
                } else {
                    val downloadsDir =
                            Environment.getExternalStoragePublicDirectory(
                                    Environment.DIRECTORY_DOWNLOADS
                            )
                    if (!downloadsDir.exists()) downloadsDir.mkdirs()
                    val file = java.io.File(downloadsDir, fileName)
                    file.writeText(json)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                                        context,
                                        "Sauvegardé : $fileName dans Téléchargements",
                                        Toast.LENGTH_LONG
                                )
                                .show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                                    context,
                                    "Erreur export: ${e.localizedMessage}",
                                    Toast.LENGTH_LONG
                            )
                            .show()
                }
            }
        }
    }

    val openDocumentLauncher =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri
                ->
                uri?.let {
                    try {
                        context.contentResolver.openInputStream(it)?.use { inputStream ->
                            viewModel.backupJsonToRestore =
                                    inputStream.bufferedReader().use { it.readText() }
                            viewModel.showRestoreConfirmDialog = true
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Read Error: ${e.message}", Toast.LENGTH_SHORT)
                                .show()
                    }
                }
            }

    val onChannelClick: (ChannelEntity) -> Unit = { channel ->
        viewModel.playingChannel = channel
        viewModel.isFullScreenPlayer = true
        exoPlayer?.let { player ->
            scope.launch {
                try {
                    val streamUrl = getStreamUrl(channel.stream_id)
                    if (streamUrl.isNotEmpty()) {
                        val meta =
                                MediaMetadata.Builder()
                                        .setTitle(channel.name)
                                        .setArtworkUri(channel.stream_icon?.toUri())
                                        .build()
                        val mediaItem =
                                MediaItem.Builder().setUri(streamUrl).setMediaMetadata(meta).build()
                        player.clearMediaItems()
                        player.setMediaItem(mediaItem)
                        player.prepare()
                        player.play()
                    } else {
                        Toast.makeText(
                                        context,
                                        "Impossible de récupérer le lien",
                                        Toast.LENGTH_SHORT
                                )
                                .show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Erreur lecture: ${e.message}", Toast.LENGTH_SHORT)
                            .show()
                }
            }
        }
        viewModel.addToRecents(channel.stream_id)
    }

    // Auto-go to player if playing
    LaunchedEffect(Unit) {
        if (viewModel.playingChannel != null) {
            viewModel.isFullScreenPlayer = true
            exoPlayer?.let {
                if (!it.isPlaying && it.mediaItemCount > 0) {
                    it.prepare()
                    it.play()
                }
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (viewModel.isFullScreenPlayer) {
            BackHandler { viewModel.isFullScreenPlayer = false }
        }

        if (viewModel.isFullScreenPlayer && viewModel.playingChannel != null && exoPlayer != null) {
            VideoPlayerView(
                    exoPlayer = exoPlayer,
                    channelName = viewModel.playingChannel!!.name,
                    currentChannels = viewModel.channels,
                    categories = viewModel.filteredCategories,
                    selectedCategoryId = viewModel.selectedCategoryId,
                    countries = viewModel.countryFilters,
                    selectedCountry = viewModel.selectedCountryFilter,
                    onCountrySelected = {
                        viewModel.selectedCountryFilter = it
                        viewModel.selectedCategoryId = null
                        viewModel.refreshChannels()
                    },
                    onChannelSelected = { onChannelClick(it) },
                    onCategorySelected = {
                        viewModel.selectedCategoryId = it.category_id
                        viewModel.selectedFavoriteListId = -1
                        viewModel.searchQuery = ""
                        viewModel.lastGeneratorType = GeneratorType.CATEGORY
                        viewModel.refreshChannels()
                    },
                    onBack = { viewModel.isFullScreenPlayer = false },
                    isLandscape = isLandscape
            )
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                // Background video if any
                if (viewModel.playingChannel != null && exoPlayer != null) {
                    VideoPlayerView(
                            exoPlayer = exoPlayer,
                            channelName = viewModel.playingChannel!!.name,
                            currentChannels = viewModel.channels,
                            categories = viewModel.filteredCategories,
                            selectedCategoryId = viewModel.selectedCategoryId,
                            countries = viewModel.countryFilters,
                            selectedCountry = viewModel.selectedCountryFilter,
                            onCountrySelected = {
                                viewModel.selectedCountryFilter = it
                                viewModel.selectedCategoryId = null
                                viewModel.refreshChannels()
                            },
                            onChannelSelected = { onChannelClick(it) },
                            onCategorySelected = {
                                viewModel.selectedCategoryId = it.category_id
                                viewModel.selectedFavoriteListId = -1
                                viewModel.searchQuery = ""
                                viewModel.lastGeneratorType = GeneratorType.CATEGORY
                                viewModel.refreshChannels()
                            },
                            onBack = { viewModel.isFullScreenPlayer = false },
                            interactive = false,
                            isLandscape = isLandscape
                    )
                }

                Column(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))
                ) {
                    MainHeader(
                            viewModel = viewModel,
                            isLandscape = isLandscape,
                            onSave = onSave,
                            openLauncher = openDocumentLauncher,
                            player = exoPlayer
                    )

                    if (!isLandscape && viewModel.isSearchVisibleOnMobile) {
                        MobileSearchRow(viewModel)
                    }

                    if (viewModel.isLoading) {
                        LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        if (isLandscape) {
                            MainContentLandscape(viewModel, onChannelClick)
                        } else {
                            MainContentPortrait(viewModel, onChannelClick)
                        }
                    }
                }
            }
        }
    }

    DialogsContainer(viewModel, importJson)
}

@Composable
fun MobileSearchRow(viewModel: MainViewModel) {
    Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            colors =
                    CardDefaults.cardColors(
                            containerColor =
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
    ) {
        TvInput(
                value = viewModel.searchQuery,
                onValueChange = {
                    viewModel.searchQuery = it
                    viewModel.refreshChannels(debounce = true)
                },
                label = "Filtrer les chaînes...",
                focusManager = LocalFocusManager.current,
                leadingIcon = Icons.Default.Search,
                modifier = Modifier.padding(4.dp)
        )
    }
}

@Composable
fun DialogsContainer(viewModel: MainViewModel, importJson: suspend (String) -> Unit) {
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

    if (viewModel.showRestoreConfirmDialog) {
        AlertDialog(
                onDismissRequest = { viewModel.showRestoreConfirmDialog = false },
                title = { Text("Restaurer la sauvegarde ?") },
                text = { Text("Cela écrasera vos profils et favoris actuels.") },
                confirmButton = {
                    Button(
                            onClick = {
                                scope.launch {
                                    importJson(viewModel.backupJsonToRestore)
                                    viewModel.showRestoreConfirmDialog = false
                                }
                            }
                    ) { Text("Restaurer") }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.showRestoreConfirmDialog = false }) {
                        Text("Annuler")
                    }
                }
        )
    }
}
