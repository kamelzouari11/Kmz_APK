package com.example.simpleiptv.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
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
        onSave: () -> Unit = {},
        onRestore: () -> Unit = {},
        player: Player? = null,
        onGoToPlayer: () -> Unit = {}
) {
        val context = LocalContext.current
        val activity = context as? Activity
        val scope = rememberCoroutineScope()
        val focusManager = LocalFocusManager.current
        val density = LocalDensity.current

        // --- Logique commune de l'historique ---
        var searchFieldFocused by remember { mutableStateOf(false) }
        val showHistory = searchFieldFocused &&
                viewModel.searchHistory.isNotEmpty() &&
                viewModel.searchQuery.isBlank()
        
        val searchFieldFocusRequester = remember { FocusRequester() }
        val firstHistItemFocusRequester = remember { FocusRequester() }
        val liveButtonFocusRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            try {
                liveButtonFocusRequester.requestFocus()
            } catch (e: Exception) {}
        }

        val popupOffsetY = with(density) { 62.dp.roundToPx() }

        @Composable
        fun HistoryPopup() {
            if (showHistory) {
                // Ce BackHandler est plus prioritaire que le BackHandler global de MainScreen.
                // BACK depuis la liste historique → retour au champ de recherche (sans fermer l'app).
                BackHandler {
                    searchFieldFocused = false
                    try { searchFieldFocusRequester.requestFocus() } catch (e: Exception) {}
                }
                Popup(
                    alignment = Alignment.TopEnd,
                    offset = IntOffset(0, popupOffsetY),
                    onDismissRequest = {
                        searchFieldFocused = false
                        try { searchFieldFocusRequester.requestFocus() } catch (e: Exception) {}
                    },
                    properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true)
                ) {
                    Card(
                        modifier = Modifier
                            .width(280.dp)
                            .heightIn(max = 350.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF232323)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 15.dp)
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            itemsIndexed(viewModel.searchHistory) { index, histQuery ->
                                var isItemFocused by remember { mutableStateOf(false) }
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { isItemFocused = it.isFocused }
                                        .then(if (index == 0) Modifier.focusRequester(firstHistItemFocusRequester) else Modifier)
                                        .onPreviewKeyEvent { event ->
                                            when {
                                                // Flèche UP sur le 1er item → retour au champ de recherche
                                                index == 0 && event.key == Key.DirectionUp && event.type == KeyEventType.KeyDown -> {
                                                    searchFieldFocusRequester.requestFocus()
                                                    true
                                                }
                                                // BACK depuis n'importe quel item → ferme la liste
                                                event.key == Key.Back && event.type == KeyEventType.KeyDown -> {
                                                    searchFieldFocused = false
                                                    try { searchFieldFocusRequester.requestFocus() } catch (e: Exception) {}
                                                    true
                                                }
                                                else -> false
                                            }
                                        },
                                    onClick = {
                                        viewModel.searchQuery = histQuery
                                        viewModel.commitSearchToHistory() // Met à jour le timestamp → remonte en 1ère position
                                        viewModel.lastGeneratorType = GeneratorType.GLOBAL_SEARCH
                                        viewModel.refreshChannels()
                                        searchFieldFocused = false
                                    },
                                    color = if (isItemFocused) Color.White else Color.Transparent,
                                    contentColor = if (isItemFocused) Color.Black else Color.White
                                ) {
                                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.History, null, modifier = Modifier.size(18.dp), tint = if (isItemFocused) Color.Black else Color.Gray)
                                        Spacer(Modifier.width(12.dp))
                                        Text(histQuery, fontSize = 15.sp, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // ----------------------------------------

        Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                shape = RectangleShape,
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
                Column {
                                Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        AsyncImage(model = "file:///android_asset/app_logo.jpg", null, modifier = Modifier.size(50.dp), contentScale = ContentScale.Fit)

                                        Spacer(modifier = Modifier.width(24.dp))

                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            HeaderIconButton(
                                                icon = Icons.Default.Tv, 
                                                desc = "Live TV", 
                                                onClick = { viewModel.setMediaMode(MediaMode.LIVE) }, 
                                                isSelected = (viewModel.currentMediaMode == MediaMode.LIVE),
                                                modifier = Modifier.focusRequester(liveButtonFocusRequester)
                                            )
                                            HeaderIconButton(Icons.Default.Movie, "Movies", { viewModel.setMediaMode(MediaMode.VOD) }, isSelected = (viewModel.currentMediaMode == MediaMode.VOD))
                                        }

                                        Spacer(modifier = Modifier.width(32.dp))

                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            HeaderIconButton(Icons.Default.CloudDownload, "Import", onRestore)
                                            HeaderIconButton(Icons.Default.CloudUpload, "Export", onSave)
                                            HeaderIconButton(Icons.Default.Refresh, "Sync", { 
                                                viewModel.profiles.find { it.id == viewModel.activeProfileId }?.let { scope.launch { viewModel.refreshDatabase(it) } }
                                            })
                                            HeaderIconButton(Icons.Default.Person, "Profiles", { viewModel.showProfileManager = true })
                                        }

                                        Spacer(modifier = Modifier.width(32.dp))

                                        HeaderIconButton(Icons.Default.PowerSettingsNew, "Exit", { activity?.finish() }, tintNormal = Color.Red)

                                        Spacer(modifier = Modifier.width(48.dp))

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.width(220.dp).height(60.dp)) {
                                                TvInput(
                                                    value = viewModel.searchQuery,
                                                    onValueChange = {
                                                        viewModel.searchQuery = it
                                                        viewModel.lastGeneratorType = GeneratorType.GLOBAL_SEARCH
                                                        viewModel.refreshChannels(debounce = true)
                                                    },
                                                    label = "Rechercher...",
                                                    focusManager = focusManager,
                                                    leadingIcon = Icons.Default.Search,
                                                    modifier = Modifier.fillMaxSize().focusRequester(searchFieldFocusRequester),
                                                    onNativeFocus = { searchFieldFocused = it },
                                                    onFocusChanged = { searchFieldFocused = it.isFocused },
                                                    onDownPressed = { if (showHistory) firstHistItemFocusRequester.requestFocus() },
                                                    onConfirm = { 
                                                        viewModel.commitSearchToHistory()
                                                    }
                                                )
                                                HistoryPopup()
                                            }
                                                
                                            if (viewModel.searchQuery.isNotEmpty()) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                HeaderIconButton(
                                                    icon = Icons.Default.Close,
                                                    desc = "Effacer",
                                                    onClick = { 
                                                        viewModel.searchQuery = ""
                                                        viewModel.refreshChannels()
                                                    },
                                                    tintNormal = Color.Gray
                                                )
                                            }
                                        }
                                }

                        // --- Profil courant (commun aux deux modes) ---
                        viewModel.profiles.find { it.id == viewModel.activeProfileId }?.let { currentProfile ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = Color(0xFF4CAF50)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "${currentProfile.profileName}  •  ${currentProfile.url}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontSize = 17.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Lecture en cours — cliquable pour revenir au player
                        if (player != null && (player.isPlaying || player.mediaItemCount > 0) && viewModel.playingChannel != null) {
                                var isNowPlayingFocused by remember { mutableStateOf(false) }
                                Row(
                                        modifier = Modifier
                                                .fillMaxWidth()
                                                .onFocusChanged { isNowPlayingFocused = it.isFocused }
                                                .scale(if (isNowPlayingFocused) 1.02f else 1f)
                                                .background(
                                                        if (isNowPlayingFocused)
                                                                Color.White.copy(alpha = 0.95f)
                                                        else
                                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                )
                                                .clickable { onGoToPlayer() }
                                                .focusable()
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Icon(
                                                Icons.Default.PlayArrow,
                                                null,
                                                modifier = Modifier.size(24.dp),
                                                tint = if (isNowPlayingFocused) Color.Black else MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                                text = "▶  ${viewModel.playingChannel!!.name}" +
                                                        (viewModel.profiles.find { it.id == viewModel.activeProfileId }
                                                                ?.profileName?.let { "  •  $it" } ?: ""),
                                                style = MaterialTheme.typography.titleMedium,
                                                color = if (isNowPlayingFocused) Color.Black else MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                                text = "Retour au player ›",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                color = if (isNowPlayingFocused) Color.Black else MaterialTheme.colorScheme.primary
                                        )
                                }
                        }
                }
        }
}
