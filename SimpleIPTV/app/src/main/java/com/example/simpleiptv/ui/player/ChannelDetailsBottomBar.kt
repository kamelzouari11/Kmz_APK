package com.example.simpleiptv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.simpleiptv.data.local.entities.ChannelEntity
import com.example.simpleiptv.data.local.entities.ProfileEntity

@Composable
fun ChannelDetailsBottomBar(
    channel: ChannelEntity,
    profile: ProfileEntity?,
    category: String = "N/C",
    prevChannelName: String = "Début",
    nextChannelName: String = "Fin",
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF121212).copy(alpha = 0.9f))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                AsyncImage(
                    model = channel.stream_icon,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$category • ${profile?.profileName ?: "Profil"} (${profile?.url ?: "N/C"})",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${profile?.profileName ?: "Profil"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFBB86FC).copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 10.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Navigation Previous
                var isPrevFocused by remember { mutableStateOf(false) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(prevChannelName, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isPrevFocused) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f))
                            .border(
                                width = if (isPrevFocused) 2.dp else 0.dp,
                                color = if (isPrevFocused) Color(0xFFBB86FC) else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .onFocusChanged { isPrevFocused = it.isFocused }
                            .focusable()
                            .clickable { onPrevious() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }

                Spacer(Modifier.width(16.dp))

                // Live Indicator
                Text(
                    text = "LIVE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFBB86FC)
                )

                Spacer(Modifier.width(16.dp))

                // Navigation Next
                var isNextFocused by remember { mutableStateOf(false) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(nextChannelName, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isNextFocused) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f))
                            .border(
                                width = if (isNextFocused) 2.dp else 0.dp,
                                color = if (isNextFocused) Color(0xFFBB86FC) else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .onFocusChanged { isNextFocused = it.isFocused }
                            .focusable()
                            .clickable { onNext() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}
