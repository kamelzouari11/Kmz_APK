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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
        isFavorite: Boolean = false,
        onClick: () -> Unit,
        onFavoriteClick: () -> Unit,
        modifier: Modifier = Modifier,
        debugInfo: String = "",
        profile: com.example.simpleiptv.data.local.entities.ProfileEntity? = null
) {
        var isChannelFocused by remember { mutableStateOf(false) }
        var isFavFocused by remember { mutableStateOf(false) }
        val context = LocalContext.current
        val channelScale by animateFloatAsState(
                targetValue = if (isChannelFocused) 1.02f else 1f,
                animationSpec = tween(150),
                label = "channelScale"
        )
        val favScale by animateFloatAsState(
                targetValue = if (isFavFocused) 1.1f else 1f,
                animationSpec = tween(150),
                label = "favScale"
        )

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
                                                 .scale(channelScale)
                                                 .clickable { onClick() }
                                                 .focusable(),
                                         shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                         color =
                                          when {
                                              isChannelFocused -> Color.White.copy(alpha = 0.2f)
                                              isPlaying ->
                                                      Color(0xFFBB86FC).copy(
                                                          alpha = 0.3f
                                                      )
                                              else -> Color.Transparent
                                          },
                                         border =
                                         when {
                                             isPlaying -> BorderStroke(2.dp, Color(0xFFBB86FC))
                                             isChannelFocused -> BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
                                             else -> BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f))
                                         }
                                 ) {

                        Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                 AsyncImage(
                                         model = coil.request.ImageRequest.Builder(context)
                                                 .data(channel.stream_icon)
                                                 .size(120) // Légèrement plus grand pour la netteté mais limité
                                                 .crossfade(true)
                                                 .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                                 .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                                 .build(),
                                         contentDescription = null,
                                         modifier = Modifier.size(40.dp),
                                         contentScale = ContentScale.Fit
                                 )

                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                                text = channel.name,
                                         color =
                                             when {
                                                 isChannelFocused -> Color.White
                                                 isPlaying -> Color(0xFFBB86FC)
                                                 else -> Color.White.copy(alpha = 0.8f)
                                             },

                                                maxLines = 1,
                                                style = MaterialTheme.typography.bodyMedium,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        if (profile != null) {
                                                Text(
                                                        text = "${profile.profileName}  •  ${profile.url}",
                                 color = if (isChannelFocused)
                                                                 Color.White
                                                                 else
                                                                 Color.White.copy(alpha = 0.5f),

                                                        maxLines = 1,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp)
                                                )
                                        }
                                }
                                if (isPlaying) {
                                        Spacer(Modifier.width(8.dp))
                                        Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = Color(0xFFBB86FC),
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
                                        .scale(favScale)
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
                                 color = if (isFavFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent

                ) {
                        Box(contentAlignment = Alignment.Center) {
                                 Icon(
                                         imageVector = Icons.Default.Star,
                                         contentDescription = null,
                                         tint = if (isFavFocused) Color.White else if (isFavorite) Color(0xFFBB86FC) else Color.Gray.copy(alpha = 0.6f),
                                         modifier = Modifier.size(32.dp)
                                     )

                        }
                }
        }
}
