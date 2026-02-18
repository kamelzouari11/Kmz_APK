package com.example.simpleiptv.ui

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.example.simpleiptv.ui.components.HeaderIconButton
import com.example.simpleiptv.ui.components.TvInput
import com.example.simpleiptv.ui.viewmodel.GeneratorType
import com.example.simpleiptv.ui.viewmodel.MainViewModel
import com.example.simpleiptv.ui.viewmodel.MediaMode
import kotlinx.coroutines.launch

@Composable
fun MainHeader(
        viewModel: MainViewModel,
        isLandscape: Boolean,
        onSave: () -> Unit,
        onRestore: () -> Unit,
        player: Player?
) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val focusManager = LocalFocusManager.current
        val focusRequester = remember { FocusRequester() }

        LaunchedEffect(isLandscape) {
                if (isLandscape) {
                        try {
                                focusRequester.requestFocus()
                        } catch (e: Exception) {}
                }
        }

        Card(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
                Column {
                        if (isLandscape) {
                                Row(
                                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(25.dp)
                                ) {
                                        AsyncImage(
                                                model = "file:///android_asset/app_logo.jpg",
                                                contentDescription = null,
                                                modifier = Modifier.size(60.dp),
                                                contentScale = ContentScale.Fit
                                        )

                                        TvInput(
                                                value = viewModel.searchQuery,
                                                onValueChange = {
                                                        viewModel.searchQuery = it
                                                        viewModel.lastGeneratorType =
                                                                GeneratorType.SEARCH
                                                        viewModel.refreshChannels(debounce = true)
                                                },
                                                label = "Rechercher...",
                                                focusManager = focusManager,
                                                leadingIcon = Icons.Default.Search,
                                                modifier = Modifier.width(180.dp)
                                        )

                                        HeaderIconButton(
                                                icon = Icons.Default.Tv,
                                                desc = "Live TV",
                                                onClick = {
                                                        viewModel.setMediaMode(
                                                                com.example.simpleiptv.ui.viewmodel
                                                                        .MediaMode.LIVE
                                                        )
                                                },
                                                tintNormal =
                                                        if (viewModel.currentMediaMode ==
                                                                        com.example.simpleiptv.ui
                                                                                .viewmodel.MediaMode
                                                                                .LIVE
                                                        )
                                                                Color.Cyan
                                                        else Color.Gray,
                                                isSelected =
                                                        viewModel.currentMediaMode ==
                                                                com.example.simpleiptv.ui.viewmodel
                                                                        .MediaMode.LIVE
                                        )

                                        HeaderIconButton(
                                                icon = Icons.Default.Movie,
                                                desc = "VOD",
                                                onClick = {
                                                        viewModel.setMediaMode(
                                                                com.example.simpleiptv.ui.viewmodel
                                                                        .MediaMode.VOD
                                                        )
                                                },
                                                tintNormal =
                                                        if (viewModel.currentMediaMode ==
                                                                        com.example.simpleiptv.ui
                                                                                .viewmodel.MediaMode
                                                                                .VOD
                                                        )
                                                                Color.Cyan
                                                        else Color.Gray,
                                                isSelected =
                                                        viewModel.currentMediaMode ==
                                                                com.example.simpleiptv.ui.viewmodel
                                                                        .MediaMode.VOD
                                        )

                                        if (viewModel.searchQuery.isNotBlank()) {
                                                HeaderIconButton(
                                                        icon = Icons.Default.Close,
                                                        desc = "Effacer",
                                                        onClick = {
                                                                viewModel.searchQuery = ""
                                                                viewModel.refreshChannels()
                                                                focusManager.clearFocus()
                                                        },
                                                        tintNormal = MaterialTheme.colorScheme.error
                                                )
                                        }

                                        HeaderIconButton(
                                                icon = Icons.Default.PlayArrow,
                                                desc = "Player",
                                                onClick = { viewModel.isFullScreenPlayer = true },
                                                modifier = Modifier.focusRequester(focusRequester),
                                                tintNormal =
                                                        if (viewModel.playingChannel != null)
                                                                Color.Green
                                                        else Color.Gray
                                        )

                                        HeaderIconButton(
                                                icon = Icons.Default.Person,
                                                desc = "Profils",
                                                onClick = { viewModel.showProfileManager = true }
                                        )
                                        HeaderIconButton(
                                                icon = Icons.Default.Refresh,
                                                desc = "Actualiser",
                                                onClick = {
                                                        scope.launch {
                                                                viewModel.profiles
                                                                        .find {
                                                                                it.id ==
                                                                                        viewModel
                                                                                                .activeProfileId
                                                                        }
                                                                        ?.let {
                                                                                viewModel
                                                                                        .refreshDatabase(
                                                                                                it
                                                                                        )
                                                                        }
                                                        }
                                                }
                                        )
                                        HeaderIconButton(
                                                icon = Icons.Default.CloudUpload,
                                                desc = "Sauvegarder",
                                                onClick = onSave
                                        )
                                        HeaderIconButton(
                                                icon = Icons.Default.CloudDownload,
                                                desc = "Restaurer",
                                                onClick = onRestore
                                        )
                                        HeaderIconButton(
                                                icon = Icons.Default.PowerSettingsNew,
                                                desc = "Quitter",
                                                onClick = {
                                                        player?.stop()
                                                        (context as? Activity)?.finishAffinity()
                                                },
                                                tintNormal = Color.Red
                                        )
                                }
                        } else {
                                Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                                AsyncImage(
                                                        model =
                                                                "file:///android_asset/app_logo.jpg",
                                                        contentDescription = null,
                                                        modifier = Modifier.size(40.dp),
                                                        contentScale =
                                                                androidx.compose.ui.layout
                                                                        .ContentScale.Fit
                                                )
                                                HeaderIconButton(
                                                        icon = Icons.Default.Search,
                                                        desc = "Recherche",
                                                        onClick = {
                                                                viewModel.isSearchVisibleOnMobile =
                                                                        !viewModel
                                                                                .isSearchVisibleOnMobile
                                                        },
                                                        tintNormal = Color.White
                                                )
                                                HeaderIconButton(
                                                        icon = Icons.Default.Tv,
                                                        desc = "Live",
                                                        onClick = {
                                                                viewModel.setMediaMode(
                                                                        com.example.simpleiptv.ui
                                                                                .viewmodel.MediaMode
                                                                                .LIVE
                                                                )
                                                        },
                                                        tintNormal =
                                                                if (viewModel.currentMediaMode ==
                                                                                com.example
                                                                                        .simpleiptv
                                                                                        .ui
                                                                                        .viewmodel
                                                                                        .MediaMode
                                                                                        .LIVE
                                                                )
                                                                        Color.Cyan
                                                                else Color.White,
                                                        isSelected =
                                                                viewModel.currentMediaMode ==
                                                                        com.example.simpleiptv.ui
                                                                                .viewmodel.MediaMode
                                                                                .LIVE
                                                )
                                                HeaderIconButton(
                                                        icon = Icons.Default.Movie,
                                                        desc = "VOD",
                                                        onClick = {
                                                                viewModel.setMediaMode(
                                                                        com.example.simpleiptv.ui
                                                                                .viewmodel.MediaMode
                                                                                .VOD
                                                                )
                                                        },
                                                        tintNormal =
                                                                if (viewModel.currentMediaMode ==
                                                                                com.example
                                                                                        .simpleiptv
                                                                                        .ui
                                                                                        .viewmodel
                                                                                        .MediaMode
                                                                                        .VOD
                                                                )
                                                                        Color.Cyan
                                                                else Color.White,
                                                        isSelected =
                                                                viewModel.currentMediaMode ==
                                                                        com.example.simpleiptv.ui
                                                                                .viewmodel.MediaMode
                                                                                .VOD
                                                )
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                                HeaderIconButton(
                                                        icon = Icons.Default.Person,
                                                        desc = "Profils",
                                                        onClick = {
                                                                viewModel.showProfileManager = true
                                                        }
                                                )
                                                HeaderIconButton(
                                                        icon = Icons.Default.Refresh,
                                                        desc = "Actualiser",
                                                        onClick = {
                                                                scope.launch {
                                                                        viewModel.profiles
                                                                                .find {
                                                                                        it.id ==
                                                                                                viewModel
                                                                                                        .activeProfileId
                                                                                }
                                                                                ?.let {
                                                                                        viewModel
                                                                                                .refreshDatabase(
                                                                                                        it
                                                                                                )
                                                                                }
                                                                }
                                                        }
                                                )
                                                HeaderIconButton(
                                                        icon = Icons.Default.CloudUpload,
                                                        desc = "Sauvegarder",
                                                        onClick = onSave
                                                )
                                                HeaderIconButton(
                                                        icon = Icons.Default.CloudDownload,
                                                        desc = "Restaurer",
                                                        onClick = onRestore
                                                )
                                                if (viewModel.playingChannel != null) {
                                                        HeaderIconButton(
                                                                icon = Icons.Default.PlayArrow,
                                                                desc = "Retour Player",
                                                                onClick = {
                                                                        viewModel
                                                                                .isFullScreenPlayer =
                                                                                true
                                                                },
                                                                tintNormal = Color.Green
                                                        )
                                                }
                                                HeaderIconButton(
                                                        icon = Icons.Default.PowerSettingsNew,
                                                        desc = "Quitter",
                                                        onClick = {
                                                                player?.stop()
                                                                (context as? Activity)
                                                                        ?.finishAffinity()
                                                        },
                                                        tintNormal = Color.Red
                                                )
                                        }
                                }
                        }
                }
        }
}
