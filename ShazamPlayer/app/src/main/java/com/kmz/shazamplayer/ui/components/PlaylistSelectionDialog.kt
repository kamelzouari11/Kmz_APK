package com.kmz.shazamplayer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.kmz.shazamplayer.network.SoundCloudPlaylist

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistSelectionDialog(
        playlists: List<SoundCloudPlaylist>,
        onDismiss: () -> Unit,
        onSelect: (SoundCloudPlaylist) -> Unit
) {
    AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Playlists de l'artiste", color = Color.White, fontSize = 18.sp)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)) {
                    itemsIndexed(playlists) { _, playlist ->
                        Card(
                                onClick = { onSelect(playlist) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                colors =
                                        CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
                                shape = RoundedCornerShape(16.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                        model = playlist.artworkUrl,
                                        contentDescription = null,
                                        modifier =
                                                Modifier.size(70.dp)
                                                        .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(20.dp))
                                Column {
                                    Text(
                                            playlist.title,
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp,
                                            lineHeight = 18.sp
                                    )
                                    val infoText = buildString {
                                        append("${playlist.trackCount} titres")
                                        playlist.releaseYear?.let { append(" • $it") }
                                        append(" • ${playlist.likesCount} ❤️")
                                    }
                                    Text(infoText, color = Color.Gray, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {},
            containerColor = Color(0xFF1A1A1A),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
    )
}
