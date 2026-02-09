package com.example.simpleiptv.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.simpleiptv.data.local.entities.ChannelEntity

@Composable
fun ChannelItem(
        channel: ChannelEntity,
        isPlaying: Boolean,
        onClick: () -> Unit,
        onFavoriteClick: () -> Unit,
        modifier: Modifier = Modifier,
        debugInfo: String = ""
) {
        var isChannelFocused by remember { mutableStateOf(false) }
        var isFavFocused by remember { mutableStateOf(false) }
        val context = LocalContext.current

        Row(
                modifier = Modifier.fillMaxWidth().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
                Surface(
                        modifier =
                                modifier.weight(1f)
                                        .height(60.dp)
                                        .onFocusChanged { state ->
                                                isChannelFocused = state.isFocused
                                        }
                                        .scale(if (isChannelFocused) 1.02f else 1f)
                                        .clickable { onClick() }
                                        .focusable(),
                        shape = MaterialTheme.shapes.small,
                        color =
                                when {
                                        isChannelFocused -> Color.White.copy(alpha = 0.95f)
                                        isPlaying ->
                                                MaterialTheme.colorScheme.primaryContainer.copy(
                                                        alpha = 0.6f
                                                )
                                        else -> MaterialTheme.colorScheme.surface
                                },
                        border =
                                when {
                                        isPlaying -> BorderStroke(2.dp, Color.Green)
                                        else -> BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f))
                                }
                ) {
                        Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                AsyncImage(
                                        model = channel.stream_icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp),
                                        contentScale = ContentScale.Fit
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                        text = channel.name,
                                        color =
                                                when {
                                                        isChannelFocused -> Color.Black
                                                        isPlaying -> Color.Green
                                                        else -> MaterialTheme.colorScheme.onSurface
                                                },
                                        maxLines = 1,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                )
                                if (isPlaying) {
                                        Spacer(Modifier.width(8.dp))
                                        Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = Color.Green,
                                                modifier = Modifier.size(20.dp)
                                        )
                                }
                        }
                }

                Spacer(Modifier.width(8.dp))

                Surface(
                        modifier =
                                Modifier.size(60.dp)
                                        .onFocusChanged { state -> isFavFocused = state.isFocused }
                                        .scale(if (isFavFocused) 1.1f else 1f)
                                        .clickable {
                                                if (debugInfo.isNotEmpty()) {
                                                        Toast.makeText(
                                                                        context,
                                                                        debugInfo,
                                                                        Toast.LENGTH_SHORT
                                                                )
                                                                .show()
                                                }
                                                onFavoriteClick()
                                        }
                                        .focusable(),
                        shape = CircleShape,
                        color =
                                if (isFavFocused) Color.White.copy(alpha = 0.2f)
                                else Color.Transparent
                ) {
                        Box(contentAlignment = Alignment.Center) {
                                Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = if (isFavFocused) Color.White else Color.Gray,
                                        modifier = Modifier.size(32.dp)
                                )
                        }
                }
        }
}
