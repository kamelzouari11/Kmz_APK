package com.example.simpleiptv.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.simpleiptv.data.local.entities.ChannelEntity
import com.example.simpleiptv.data.local.entities.ProfileEntity

/**
 * Info bar shown at the bottom of the player when the overlay is hidden.
 * Displays current channel name, profile info, and list label.
 */
@Composable
fun PlayerInfoBar(
        channelName: String,
        playingChannel: ChannelEntity?,
        profiles: List<ProfileEntity>,
        listLabel: String
) {
        val activeProfile = remember(playingChannel, profiles) {
                profiles.find { it.id == playingChannel?.profileId }
        }
        Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.BottomStart
        ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text(
                                text = channelName,
                                color = Color.White.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.titleMedium
                        )
                        if (activeProfile != null) {
                                Text(
                                        text = "${activeProfile.profileName}  •  ${activeProfile.url}",
                                        color = Color(0xFFBB86FC).copy(alpha = 0.85f),
                                        style = MaterialTheme.typography.labelMedium
                                )
                        }
                        if (listLabel.isNotEmpty()) {
                                Text(
                                        text = listLabel,
                                        color = Color.White.copy(alpha = 0.45f),
                                        style = MaterialTheme.typography.bodySmall
                                )
                        }
                }
        }
}
