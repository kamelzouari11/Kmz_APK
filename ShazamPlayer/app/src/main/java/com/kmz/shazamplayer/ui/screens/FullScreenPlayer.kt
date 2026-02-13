package com.kmz.shazamplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kmz.shazamplayer.model.Track
import com.kmz.shazamplayer.util.formatTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenPlayer(
        track: Track,
        isPlaying: Boolean,
        isShuffle: Boolean,
        isRepeat: Boolean,
        currentPosition: Long,
        duration: Long,
        isDiscovery: Boolean,
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
        onCreatorClick: (Long, String) -> Unit = { _, _ -> }
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Pochette (50% de la hauteur, 100% largeur, coins droits)
        Box(modifier = Modifier.fillMaxWidth().height(screenHeight * 0.5f)) {
            AsyncImage(
                    model = track.artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
            )

            Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                        onClick = onClose,
                        modifier = Modifier.background(Color.White, CircleShape).size(40.dp)
                ) {
                    Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(32.dp)
                    )
                }

                // Bouton Magic Artist / Toggle
                IconButton(
                        onClick = onArtistRadio,
                        modifier = Modifier.background(Color.White, CircleShape).size(40.dp)
                ) {
                    if (isSearchingPlaylists) {
                        CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color(0xFF0088FF),
                                strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                                imageVector =
                                        if (isDiscovery) Icons.Default.LibraryMusic
                                        else Icons.Default.AutoAwesome,
                                contentDescription = "Discovery Toggle",
                                tint = if (isDiscovery) Color(0xFF0088FF) else Color(0xFF00FF88),
                                modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                            text = track.title,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                    )
                    Text(
                            text = track.artist,
                            color = Color.Gray,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(top = 4.dp)
                    )
                    if (isDiscovery && discoveryCreator != null) {
                        Text(
                                text = "by $discoveryCreator",
                                color = Color(0xFF00FF88).copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier =
                                        Modifier.padding(top = 2.dp).clickable {
                                            onCreatorClick(discoveryCreatorId, discoveryCreator)
                                        }
                        )
                    }
                }
                // Bouton Permutation Flux (HQ)
                IconButton(onClick = onCycleStream) {
                    Icon(
                            Icons.Default.HighQuality,
                            contentDescription = "Alternative Stream",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

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

            @Suppress("DEPRECATION") Spacer(modifier = Modifier.weight(1f))

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
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
