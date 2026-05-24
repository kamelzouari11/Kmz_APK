package com.example.simpleiptv.ui

import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.example.simpleiptv.VideoPlayerView
import com.example.simpleiptv.data.local.entities.ChannelEntity
import com.example.simpleiptv.ui.components.MainDialogs
import com.example.simpleiptv.ui.viewmodel.GeneratorType
import com.example.simpleiptv.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
        viewModel: MainViewModel,
        exoPlayer: Player?,
        onSave: () -> Unit,
        onRestore: () -> Unit,
        getStreamUrl: suspend (com.example.simpleiptv.data.local.entities.ChannelEntity) -> String
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val playerReturnFocusRequester = remember { FocusRequester() }

    // Pull-to-refresh state (simple version using Material3)
    val isRefreshing = viewModel.uiState.isLoading

    // Scroll States for Memory / Sync between Screen A and B
    val mainCountryScrollState = rememberLazyListState()
    val mainCategoryScrollState = rememberLazyListState()
    val mainChannelScrollState = rememberLazyListState()

    val onChannelClick: (ChannelEntity) -> Unit = { channel ->
        viewModel.setPlayingChannel(channel)
        viewModel.setFullScreenPlayer(true)
        exoPlayer?.let { player ->
            scope.launch {
                try {
                    // Utilise le profileId de la chaîne (important pour la recherche globale
                    // où la chaîne peut appartenir à un profil différent du profil actif)
                    val streamUrl = getStreamUrl(channel)
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
        if (viewModel.uiState.playingChannel != null) {
            viewModel.setFullScreenPlayer(true)
            exoPlayer?.let {
                if (!it.isPlaying && it.mediaItemCount > 0) {
                    it.prepare()
                    it.play()
                }
            }
        }
    }

    // Intercepter le bouton BACK : on ne quitte JAMAIS l'application par Back.
    // En mode player, on revient à la liste. Sinon on ne fait rien.
    BackHandler(enabled = true) {
        when {
            viewModel.uiState.isFullScreenPlayer -> {
                viewModel.setFullScreenPlayer(false)
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (viewModel.uiState.isFullScreenPlayer && viewModel.uiState.playingChannel != null && exoPlayer != null) {
            VideoPlayerView(
                    exoPlayer = exoPlayer,
                    channelName = viewModel.uiState.playingChannel!!.name,
                    currentChannels = viewModel.uiState.lastList,
                    categories = viewModel.uiState.filteredCategories,
                    selectedCategoryId = viewModel.uiState.selectedCategoryId,
                    countries = viewModel.uiState.countryFilters,
                    selectedCountry = viewModel.uiState.selectedCountryFilter,
                    onCountrySelected = {
                        viewModel.setCountryFilter(it)
                        viewModel.selectCategory(null)
                    },
                    onChannelSelected = { onChannelClick(it) },
                    onCategorySelected = {
                        viewModel.selectCategory(it.category_id)
                    },
                    onBack = {
                        viewModel.setFullScreenPlayer(false)
                    },
                    onFavoriteClick = { channel -> viewModel.initFavoriteAction(channel) },
                    allFavoriteIds = viewModel.uiState.allFavoriteIds,
                    isLandscape = isLandscape,
                    playingChannel = viewModel.uiState.playingChannel,
                    countriesScrollState = mainCountryScrollState,
                    categoriesScrollState = mainCategoryScrollState,
                    channelsScrollState = mainChannelScrollState,
                    listLabel = viewModel.uiState.lastListLabel,
                    profiles = viewModel.uiState.profiles,
                    activeProfileId = viewModel.uiState.activeProfileId,
                                 onProfileSelected = { profileId -> viewModel.selectProfile(profileId) },
                                 viewModel = viewModel
                            )
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    MainHeader(
                            viewModel = viewModel,
                            onSave = onSave,
                            onRestore = onRestore,
                            player = exoPlayer,
                            onGoToPlayer = { viewModel.setFullScreenPlayer(true) },
                            playerReturnFocusRequesterOut = playerReturnFocusRequester
                    )

                    // Message d'erreur si présent
                    viewModel.uiState.syncError?.let { error ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Erreur de synchronisation",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = error,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                    )
                                }
                                TextButton(onClick = { viewModel.clearSyncError() }) {
                                    Text("Fermer")
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isLandscape) {
                            MainContentLandscape(
                                    viewModel = viewModel,
                                    onChannelClick = onChannelClick,
                                    countryScrollState = mainCountryScrollState,
                                    categoryScrollState = mainCategoryScrollState,
                                    channelScrollState = mainChannelScrollState,
                                    playerReturnFocusRequester = playerReturnFocusRequester
                            )
                        } else {
                            MainContentPortrait(
                                    viewModel = viewModel,
                                    onChannelClick = onChannelClick,
                                    channelScrollState = mainChannelScrollState,
                                    playerReturnFocusRequester = playerReturnFocusRequester
                            )
                        }

                        // Indicateur de chargement centralisé
                        if (isRefreshing && viewModel.uiState.channels.isEmpty()) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
            }
        }
    }

    MainDialogs(viewModel)
}
