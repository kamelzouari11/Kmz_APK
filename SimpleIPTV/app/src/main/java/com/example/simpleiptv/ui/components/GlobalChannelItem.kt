package com.example.simpleiptv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.simpleiptv.data.local.ChannelWithProfile

/**
 * Tuile de chaîne pour la recherche globale multi-profils.
 * Affiche le nom de la chaîne sur la ligne principale, et en dessous,
 * en petites lettres grises : le nom du profil et l'URL du serveur.
 */
@Composable
fun GlobalChannelItem(
        item: ChannelWithProfile,
        isPlaying: Boolean,
        isFavorite: Boolean = false,
        onClick: () -> Unit,
        onFavoriteClick: () -> Unit,
        modifier: Modifier = Modifier
) {
        var isFocused by remember { mutableStateOf(false) }

        Surface(
                modifier =
                        modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 3.dp)
                                .onFocusChanged { isFocused = it.isFocused }
                                .scale(if (isFocused) 1.02f else 1f)
                                .clickable { onClick() }
                                .focusable(),
                shape = MaterialTheme.shapes.small,
                color =
                        when {
                                isFocused -> Color.White.copy(alpha = 0.95f)
                                isPlaying ->
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                else -> MaterialTheme.colorScheme.surface
                        },
                border =
                        when {
                                isPlaying -> BorderStroke(2.dp, Color(0xFFBB86FC))
                                isFocused -> BorderStroke(1.dp, Color.White.copy(alpha = 0.6f))
                                else -> BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f))
                        }
        ) {
                Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        // Logo de la chaîne
                        AsyncImage(
                                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                        .data(item.stream_icon)
                                        .size(80)
                                        .crossfade(true)
                                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                                        .build(),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                contentScale = ContentScale.Fit
                        )

                        Spacer(Modifier.width(10.dp))

                        // Texte : nom + sous-titre profil
                        Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        text = item.name,
                                        color =
                                                when {
                                                        isFocused -> Color.Black
                                                        isPlaying -> Color(0xFFBB86FC)
                                                        else -> MaterialTheme.colorScheme.onSurface
                                                },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyLarge
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                        text = "${item.profileName}  •  ${item.profileUrl}",
                                        color =
                                                if (isFocused) Color.DarkGray
                                                else Color.Gray.copy(alpha = 0.75f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 13.sp,
                                        style = MaterialTheme.typography.bodySmall
                                )
                        }

                        // Icône "en lecture"
                        if (isPlaying) {
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color(0xFFBB86FC),
                                        modifier = Modifier.size(20.dp)
                                )
                        }

                        Spacer(Modifier.width(8.dp))

                        var isFavFocused by remember { mutableStateOf(false) }
                        Surface(
                                modifier = Modifier.size(50.dp)
                                        .onFocusChanged { state -> isFavFocused = state.isFocused }
                                        .scale(if (isFavFocused) 1.1f else 1f)
                                        .clickable { onFavoriteClick() }
                                        .focusable(),
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = if (isFavFocused) Color.White else Color.Transparent
                        ) {
                                Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = null,
                                                tint = if (isFavorite) Color(0xFFBB86FC) else if (isFavFocused) Color.Black else Color.Gray,
                                                modifier = Modifier.size(28.dp)
                                        )
                                }
                        }
                }
        }
}
