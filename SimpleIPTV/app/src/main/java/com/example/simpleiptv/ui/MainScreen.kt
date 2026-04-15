package com.example.simpleiptv.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
        getStreamUrl: suspend (String, Int?) -> String
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Scroll States for Memory / Sync between Screen A and B
    val mainCountryScrollState = rememberLazyListState()
    val mainCategoryScrollState = rememberLazyListState()
    val mainChannelScrollState = rememberLazyListState()

    val onChannelClick: (ChannelEntity) -> Unit = { channel ->
        viewModel.playingChannel = channel
        viewModel.isFullScreenPlayer = true
        exoPlayer?.let { player ->
            scope.launch {
                try {
                    // Utilise le profileId de la chaîne (important pour la recherche globale
                    // où la chaîne peut appartenir à un profil différent du profil actif)
                    val streamUrl = getStreamUrl(channel.stream_id, channel.profileId)
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

    // Intercepter le bouton BACK : on ne quitte JAMAIS l'application par Back.
    // En mode player, on revient à la liste. Sinon on ne fait rien.
    BackHandler(enabled = true) {
        when {
            viewModel.isFullScreenPlayer -> {
                viewModel.isFullScreenPlayer = false
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (viewModel.isFullScreenPlayer && viewModel.playingChannel != null && exoPlayer != null) {
            VideoPlayerView(
                    exoPlayer = exoPlayer,
                    channelName = viewModel.playingChannel!!.name,
                    // Toujours passer la DERNIÈRE LISTE chargée (lastList mémorise la liste
                    // quelle que soit la source : catégorie, récents, ou recherche globale)
                    currentChannels = viewModel.lastList,
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
                    onBack = {
                        // Retour au menu sans arrêter la lecture
                        viewModel.isFullScreenPlayer = false
                    },
                    isLandscape = true,
                    playingChannel = viewModel.playingChannel,
                    countriesScrollState = mainCountryScrollState,
                    categoriesScrollState = mainCategoryScrollState,
                    channelsScrollState = mainChannelScrollState,
                    listLabel = viewModel.lastListLabel,
                    profiles = viewModel.profiles
            )
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    MainHeader(
                            viewModel = viewModel,
                            onSave = onSave,
                            onRestore = onRestore,
                            player = exoPlayer,
                            onGoToPlayer = { viewModel.isFullScreenPlayer = true }
                    )

                    if (viewModel.isLoading) {
                        LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        MainContentLandscape(
                                viewModel = viewModel,
                                onChannelClick = onChannelClick,
                                countryScrollState = mainCountryScrollState,
                                categoryScrollState = mainCategoryScrollState,
                                channelScrollState = mainChannelScrollState
                        )
                    }
                }
            }
        }
    }

    MainDialogs(viewModel)
}
