package com.kmz.shazamplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kmz.shazamplayer.model.Track
import com.kmz.shazamplayer.ui.components.YouTubePlayer
import com.kmz.shazamplayer.ui.components.YouTubePlayerCommand
import com.kmz.shazamplayer.util.formatTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenPlayer(
        track: Track,
        artworkUrl: String?,
        isPlaying: Boolean,
        isShuffle: Boolean,
        isRepeat: Boolean,
        currentPosition: Long,
        duration: Long,
        isDiscovery: Boolean,
        isUsingYouTube: Boolean,
        youtubeVideoId: String?,
        youtubeChannel: String?,
        youtubeCommand: YouTubePlayerCommand?,
        isTrackLoading: Boolean,
        playbackError: String?,
        onClose: () -> Unit,
        onTogglePlay: () -> Unit,
        onPrevious: () -> Unit,
        onNext: () -> Unit,
        onShuffleToggle: () -> Unit,
        onRepeatToggle: () -> Unit,
        onCycleStream: () -> Unit,
        onSeek: (Long) -> Unit,
        onArtistRadio: () -> Unit,
        isSearchingPlaylists: Boolean,
        discoveryCreator: String? = null,
        discoveryCreatorId: Long = 0L,
        onCreatorClick: (Long, String) -> Unit = { _, _ -> },
        onYouTubeReady: (Long) -> Unit,
        onYouTubeStateChanged: (Int) -> Unit,
        onYouTubeProgress: (Long, Long) -> Unit,
        onYouTubeError: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                                .height(200.dp)
                                .background(Color(0xFF111111)),
                verticalAlignment = Alignment.Top
        ) {
            // The YouTube viewport is exactly the minimum permitted 200 x 200 CSS pixels.
            Box(
                    modifier = Modifier.size(200.dp),
                    contentAlignment = Alignment.Center
            ) {
                if (youtubeVideoId != null) {
                    YouTubePlayer(
                            videoId = youtubeVideoId,
                            command = youtubeCommand,
                            modifier = Modifier.fillMaxSize(),
                            onReady = onYouTubeReady,
                            onStateChanged = onYouTubeStateChanged,
                            onProgress = onYouTubeProgress,
                            onError = onYouTubeError
                    )
                } else {
                    AsyncImage(
                            model = artworkUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                    )
                    if (isTrackLoading || playbackError != null || isUsingYouTube) {
                        Box(
                                modifier =
                                        Modifier.fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.65f)),
                                contentAlignment = Alignment.Center
                        ) {
                            when {
                                isTrackLoading ->
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator(color = Color.White)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                    if (isUsingYouTube) "Recherche YouTube…"
                                                    else "Recherche SoundCloud…",
                                                    color = Color.White,
                                                    fontSize = 12.sp
                                            )
                                        }
                                playbackError != null ->
                                        Text(
                                                playbackError,
                                                color = Color(0xFFFF8A80),
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(12.dp)
                                        )
                                else -> Text("Aucune vidéo", color = Color.Gray)
                            }
                        }
                    }
                }
            }

            // Reuse the 160 dp beside the player for navigation and metadata.
            Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(8.dp)
            ) {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                            onClick = onClose,
                            modifier = Modifier.background(Color.White, CircleShape).size(36.dp)
                    ) {
                        Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "Fermer le lecteur",
                                tint = Color.Black,
                                modifier = Modifier.size(28.dp)
                        )
                    }

                    IconButton(
                            onClick = onArtistRadio,
                            modifier = Modifier.background(Color.White, CircleShape).size(36.dp)
                    ) {
                        if (isSearchingPlaylists) {
                            CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color(0xFF0088FF),
                                    strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                    imageVector =
                                            if (isDiscovery) Icons.Default.LibraryMusic
                                            else Icons.Default.AutoAwesome,
                                    contentDescription = "Recherche artiste",
                                    tint =
                                            if (isDiscovery) Color(0xFF0088FF)
                                            else Color(0xFF00FF88),
                                    modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                        text = track.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                )
                Text(
                        text = track.artist,
                        color = Color.Gray,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )
                youtubeChannel?.let { channel ->
                    Text(
                            text = "YouTube · $channel",
                            color = Color(0xFFFF4E45),
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                    )
                }
                if (isDiscovery && discoveryCreator != null) {
                    Text(
                            text = "by $discoveryCreator",
                            color = Color(0xFF00FF88).copy(alpha = 0.7f),
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier =
                                    Modifier.clickable {
                                        onCreatorClick(discoveryCreatorId, discoveryCreator)
                                    }
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                        onClick = onCycleStream,
                        modifier = Modifier.align(Alignment.End).size(36.dp)
                ) {
                    Icon(
                            Icons.Default.HighQuality,
                            contentDescription = "Autre résultat YouTube",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp)) {
            Box(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .weight(1f)
                                    .padding(bottom = 4.dp)
                                    .background(Color(0xFF151515), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                        model = artworkUrl,
                        contentDescription = "Pochette de ${track.title}",
                        modifier = Modifier.fillMaxSize().padding(2.dp),
                        contentScale = ContentScale.Fit
                )
                if (artworkUrl.isNullOrBlank()) {
                    Icon(
                            Icons.Default.Album,
                            contentDescription = null,
                            tint = Color.DarkGray,
                            modifier = Modifier.size(88.dp)
                    )
                }
            }

            // Barre de progression
            val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
            Slider(
                    value = progress,
                    onValueChange = { onSeek((it * duration).toLong()) },
                    colors =
                            SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color.White,
                                    inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)
                            )
            )
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTime(currentPosition), color = Color.Gray, fontSize = 11.sp)
                Text(formatTime(duration), color = Color.Gray, fontSize = 11.sp)
            }

            // Contrôles
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onShuffleToggle) {
                    Icon(
                            Icons.Default.Shuffle,
                            contentDescription = null,
                            tint = if (isShuffle) Color(0xFF0088FF) else Color.Gray,
                            modifier = Modifier.size(26.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
                        Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                        )
                    }
                    Surface(
                            onClick = onTogglePlay,
                            shape = CircleShape,
                            color = Color.White,
                            modifier = Modifier.size(68.dp).padding(8.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                    imageVector =
                                            if (isPlaying) Icons.Default.Pause
                                            else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                        Icon(
                                Icons.Default.SkipNext,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                        )
                    }
                }

                IconButton(onClick = onRepeatToggle) {
                    Icon(
                            Icons.Default.Repeat,
                            contentDescription = null,
                            tint = if (isRepeat) Color(0xFF00FF88) else Color.Gray,
                            modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
