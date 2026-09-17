package com.kmz.shazamplayer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kmz.shazamplayer.model.Track
import com.kmz.shazamplayer.ui.components.TrackRow

@Composable
fun PlaylistScreen(
        tracks: List<Track>,
        selectedIndex: Int,
        isDiscovery: Boolean,
        isPreparingNewPipePlaylist: Boolean,
        newPipePlaylistProgress: Int,
        newPipePlaylistTargetCount: Int,
        onTrackClick: (Int) -> Unit,
        onOpenInNewPipe: () -> Unit,
        onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
            }
            val title = if (isDiscovery) "Mode Découverte" else "Ma Library Shazam"
            Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (isPreparingNewPipePlaylist) {
                    Text(
                            "Préparation NewPipe $newPipePlaylistProgress/$newPipePlaylistTargetCount",
                            color = Color(0xFFFF4E45),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                    )
                } else if (isDiscovery) {
                    Text(
                            "Playlist générée par Artiste",
                            color = Color(0xFF00FF88),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                    )
                }
            }
            IconButton(
                    onClick = onOpenInNewPipe,
                    enabled = tracks.isNotEmpty() && !isPreparingNewPipePlaylist
            ) {
                if (isPreparingNewPipePlaylist) {
                    CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color(0xFFFF4E45),
                            strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                            Icons.Default.Headphones,
                            contentDescription = "Lire la playlist dans NewPipe",
                            tint = Color(0xFFFF4E45)
                    )
                }
            }
        }

        if (tracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Aucun morceau ne correspond aux filtres", color = Color.Gray)
            }
        } else {
            LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(tracks) { index, track ->
                    TrackRow(track, isSelected = index == selectedIndex) { onTrackClick(index) }
                }
            }
        }
    }
}
