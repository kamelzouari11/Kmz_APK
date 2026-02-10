package com.example.simpleiptv.ui

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.example.simpleiptv.R
import com.example.simpleiptv.ui.components.HeaderIconButton
import com.example.simpleiptv.ui.components.TvInput
import com.example.simpleiptv.ui.viewmodel.GeneratorType
import com.example.simpleiptv.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun MainHeader(
        viewModel: MainViewModel,
        isLandscape: Boolean,
        onSave: () -> Unit,
        openLauncher: ActivityResultLauncher<String>?,
        player: Player?
) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val focusManager = LocalFocusManager.current

        Card(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                colors =
                        CardDefaults.cardColors(
                                containerColor =
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
        ) {
                Column {
                        if (isLandscape) {
                                Row(
                                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(25.dp)
                                ) {
                                        AsyncImage(
                                                model = R.drawable.ic_app_logo,
                                                contentDescription = null,
                                                modifier = Modifier.size(60.dp)
                                        )

                                        Text(
                                                text = "SimpleIPTV",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = Color.White
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
                                                modifier = Modifier.width(250.dp)
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

                                        Spacer(Modifier.weight(1f))

                                        HeaderIconButton(
                                                icon = Icons.Default.PlayArrow,
                                                desc = "Player",
                                                onClick = { viewModel.isFullScreenPlayer = true },
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
                                                onClick = {
                                                        openLauncher?.launch("application/json")
                                                }
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
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                                Text(
                                                        text = "SimpleIPTV",
                                                        style =
                                                                MaterialTheme.typography
                                                                        .headlineSmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                )
                                                HeaderIconButton(
                                                        icon = Icons.Default.Search,
                                                        desc = "Recherche",
                                                        onClick = {
                                                                viewModel.isSearchVisibleOnMobile =
                                                                        !viewModel
                                                                                .isSearchVisibleOnMobile
                                                        },
                                                        tintNormal = Color.White // Brighter search
                                                        // button
                                                        )
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Box(
                                                        modifier = Modifier.weight(1f),
                                                        contentAlignment = Alignment.Center
                                                ) {
                                                        HeaderIconButton(
                                                                icon = Icons.Default.Person,
                                                                desc = "Profils",
                                                                onClick = {
                                                                        viewModel
                                                                                .showProfileManager =
                                                                                true
                                                                }
                                                        )
                                                }
                                                Box(
                                                        modifier = Modifier.weight(1f),
                                                        contentAlignment = Alignment.Center
                                                ) {
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
                                                }
                                                Box(
                                                        modifier = Modifier.weight(1f),
                                                        contentAlignment = Alignment.Center
                                                ) {
                                                        HeaderIconButton(
                                                                icon = Icons.Default.CloudUpload,
                                                                desc = "Sauvegarder",
                                                                onClick = onSave
                                                        )
                                                }
                                                Box(
                                                        modifier = Modifier.weight(1f),
                                                        contentAlignment = Alignment.Center
                                                ) {
                                                        HeaderIconButton(
                                                                icon = Icons.Default.CloudDownload,
                                                                desc = "Restaurer",
                                                                onClick = {
                                                                        openLauncher?.launch(
                                                                                "application/json"
                                                                        )
                                                                }
                                                        )
                                                }
                                                if (viewModel.playingChannel != null) {
                                                        Box(
                                                                modifier = Modifier.weight(1f),
                                                                contentAlignment = Alignment.Center
                                                        ) {
                                                                HeaderIconButton(
                                                                        icon =
                                                                                Icons.Default
                                                                                        .PlayArrow,
                                                                        desc = "Retour Player",
                                                                        onClick = {
                                                                                viewModel
                                                                                        .isFullScreenPlayer =
                                                                                        true
                                                                        },
                                                                        tintNormal = Color.Green
                                                                )
                                                        }
                                                }
                                                Box(
                                                        modifier = Modifier.weight(1f),
                                                        contentAlignment = Alignment.Center
                                                ) {
                                                        HeaderIconButton(
                                                                icon =
                                                                        Icons.Default
                                                                                .PowerSettingsNew,
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
}
