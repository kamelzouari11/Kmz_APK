package com.kmz.shazamplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kmz.shazamplayer.model.Track

@Composable
fun PlayerBottomBar(
        track: Track,
        isPlaying: Boolean,
        onTogglePlay: () -> Unit,
        onClick: () -> Unit
) {
    Surface(
            color = Color(0xFF252525), // Plus clair pour discerner
            modifier = Modifier.fillMaxWidth().height(105.dp).clickable { onClick() }
    ) {
        Row(
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                    model = track.artworkUrl,
                    contentDescription = null,
                    modifier =
                            Modifier.size(75.dp) // Augmenté pour rester proportionnel
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.DarkGray),
                    contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            @Suppress("DEPRECATION")
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                            track.title,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false)
                    )
                    if (track.tagTime == "Playlist Discovery") {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                                color = Color(0xFF00FF88).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                    "DISCOVERY",
                                    color = Color(0xFF00FF88),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(track.artist, color = Color.Gray, fontSize = 11.sp, maxLines = 1)
            }
            IconButton(onClick = onTogglePlay) {
                Icon(
                        imageVector =
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp) // Icone un peu plus grande
                )
            }
        }
    }
}
