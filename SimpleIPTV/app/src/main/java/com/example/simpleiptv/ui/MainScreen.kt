package com.example.simpleiptv.ui

import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.example.simpleiptv.VideoPlayerView
import com.example.simpleiptv.data.local.entities.ChannelEntity
import com.example.simpleiptv.ui.components.MainDialogs
import com.example.simpleiptv.ui.components.MobileSearchRow
import com.example.simpleiptv.ui.viewmodel.GeneratorType
import com.example.simpleiptv.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
        viewModel: MainViewModel,
        exoPlayer: Player?,
        onSave: () -> Unit,
        onRestore: () -> Unit,
        getStreamUrl: suspend (String) -> String
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

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
            BackHandler {
                viewModel.isFullScreenPlayer = false
                exoPlayer?.stop()
            }
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
                    onBack = {
                        viewModel.isFullScreenPlayer = false
                        exoPlayer.stop()
                    },
                    isLandscape = isLandscape,
                    playingChannel = viewModel.playingChannel,
                    countriesScrollState = mainCountryScrollState,
                    categoriesScrollState = mainCategoryScrollState,
                    channelsScrollState = mainChannelScrollState
            )
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    MainHeader(
                            viewModel = viewModel,
                            isLandscape = isLandscape,
                            onSave = onSave,
                            onRestore = onRestore,
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
                            MainContentLandscape(
                                    viewModel = viewModel,
                                    onChannelClick = onChannelClick,
                                    countryScrollState = mainCountryScrollState,
                                    categoryScrollState = mainCategoryScrollState,
                                    channelScrollState = mainChannelScrollState
                            )
                        } else {
                            MainContentPortrait(viewModel, onChannelClick)
                        }
                    }
                }
            }
        }
    }

    MainDialogs(viewModel)
}
