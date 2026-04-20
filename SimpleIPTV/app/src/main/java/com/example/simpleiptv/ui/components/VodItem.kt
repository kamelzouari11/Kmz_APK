package com.example.simpleiptv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.simpleiptv.data.local.entities.ChannelEntity

@Composable
fun VodItem(
        channel: ChannelEntity,
        isPlaying: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        profile: com.example.simpleiptv.data.local.entities.ProfileEntity? = null
) {
        var isFocused by remember { mutableStateOf(false) }

        Card(
                modifier =
                        modifier.padding(4.dp)
                                .fillMaxWidth()
                                .aspectRatio(0.7f) // Format affiche de film standard
                                .onFocusChanged { isFocused = it.isFocused }
                                .scale(if (isFocused) 1.02f else 1f)
                                .clickable { onClick() }
                                .focusable(),
                colors =
                        CardDefaults.cardColors(
                                containerColor =
                                        if (isFocused) Color.White.copy(alpha = 0.1f)
                                        else Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                border =
                        if (isFocused) BorderStroke(3.dp, Color.White)
                        else if (isPlaying) BorderStroke(2.dp, Color.Green)
                        else BorderStroke(1.dp, Color.Gray.copy(alpha = 0.1f)),
                elevation =
                        CardDefaults.cardElevation(defaultElevation = if (isFocused) 4.dp else 0.dp)
        ) {
                Column(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                        .data(channel.stream_icon)
                                        .size(400) // Taille adaptée pour les affiches VOD
                                        .crossfade(true)
                                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                        .build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().weight(0.70f), // 70% Height
                                contentScale = ContentScale.Crop
                        )
                        Column(
                                modifier = Modifier.fillMaxWidth().weight(0.25f).padding(horizontal = 4.dp), // 25% Height
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                        ) {
                                Text(
                                        text = channel.name,
                                        modifier = Modifier.fillMaxWidth(),
                                        style = MaterialTheme.typography.titleSmall,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                )
                                if (profile != null) {
                                        Text(
                                                text = "${profile.profileName}  •  ${profile.url}",
                                                color = if (isFocused) Color.DarkGray else MaterialTheme.colorScheme.onSurface.copy(alpha=0.5f),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = androidx.compose.ui.unit.TextUnit(11f, androidx.compose.ui.unit.TextUnitType.Sp),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                        )
                                }
                        }
                        Spacer(modifier = Modifier.weight(0.05f)) // 5% Spacing
                }
        }
}
