package com.example.simpleradio.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun MainItem(
        title: String,
        iconUrl: String?,
        isPlaying: Boolean,
        onClick: () -> Unit,
        onAddFavorite: () -> Unit,
        modifier: Modifier = Modifier,
        subtitle: String? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    var isFavFocused by remember { mutableStateOf(false) }

    Row(
            modifier = modifier.fillMaxWidth().height(65.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        Card(
                modifier =
                        Modifier.weight(0.833f)
                                .fillMaxHeight()
                                .onFocusChanged { isFocused = it.isFocused }
                                .clickable { onClick() },
                border =
                        if (isPlaying && !isFocused)
                                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        else null,
                colors =
                        CardDefaults.cardColors(
                                containerColor =
                                        if (isFocused) Color.White
                                        else if (isPlaying)
                                                MaterialTheme.colorScheme.secondaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                        )
        ) {
            Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                        model = iconUrl,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        contentScale = ContentScale.Fit
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                            title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isFocused) Color.Black else Color.Unspecified
                    )
                    if (subtitle != null) {
                        Text(
                                subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isFocused) Color.DarkGray else Color.Gray
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        IconButton(
                onClick = onAddFavorite,
                modifier =
                        Modifier.weight(0.083f)
                                .fillMaxHeight()
                                .onFocusChanged { isFavFocused = it.isFocused }
                                .background(
                                        if (isFavFocused) Color.White
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                        CircleShape
                                )
        ) {
            Icon(
                    Icons.Default.FavoriteBorder,
                    null,
                    tint = if (isFavFocused) Color.Black else Color.Gray
            )
        }
    }
}
