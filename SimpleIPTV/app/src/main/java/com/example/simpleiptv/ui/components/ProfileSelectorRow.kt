package com.example.simpleiptv.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.simpleiptv.data.local.entities.ProfileEntity

/**
 * Minimal profile selector row where each item is a toggle.
 * ON = Background theme color.
 * OFF = Neutral background.
 * Active = White border.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfileSelectorRow(
    profiles: List<ProfileEntity>,
    activeProfileId: Int,
    loadedProfileIds: Set<Int>,
    loadingProfileIds: Set<Int> = emptySet(),
    onProfileClick: (Int) -> Unit,
    onProfileLongClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(profiles, key = { it.id }) { profile ->
            val isSelected = profile.id == activeProfileId
            val isEnabled = profile.isEnabled
            val isLoaded = loadedProfileIds.contains(profile.id)
            val isLoading = loadingProfileIds.contains(profile.id)
            var isFocused by remember { mutableStateOf(false) }

            // Un profil est "ON" s'il est activé ET (chargé OU en cours de chargement)
            // OU s'il est le profil actif.
            val isActuallyOn = (isEnabled && (isLoaded || isLoading)) || isSelected

            // Animation de pulsation si chargement en cours (optimisé pour ne tourner que si nécessaire)
            val alphaPulse = if (isLoading) {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 0.8f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "alpha"
                ).value
            } else {
                0.4f
            }

            Box(
                modifier = Modifier
                    .wrapContentWidth()
                    .height(42.dp)
                    .onFocusChanged { isFocused = it.isFocused }
                    .onKeyEvent { event ->
                        val isConfirmKey = event.key == Key.DirectionCenter || event.key == Key.Enter
                        if (isConfirmKey && event.nativeKeyEvent.isLongPress) {
                            onProfileLongClick(profile.id)
                            true
                        } else {
                            false
                        }
                    }
                    .combinedClickable(
                        onClick = { onProfileClick(profile.id) },
                        onLongClick = { onProfileLongClick(profile.id) }
                    )
                    .focusable()
                    .background(
                        color = when {
                            isFocused -> Color.White
                            isSelected -> Color(0xFFBB86FC)
                            isActuallyOn -> Color(0xFFBB86FC).copy(alpha = if (isLoading) alphaPulse else 0.4f)
                            else -> Color(0xFF1E1E1E).copy(alpha = 0.6f)
                        },
                        shape = MaterialTheme.shapes.small
                    )
                    .border(
                        width = if (isFocused) 2.dp else 1.dp,
                        color = when {
                            isFocused -> Color.White
                            isSelected -> Color.White.copy(alpha = 0.5f)
                            isActuallyOn -> Color(0xFFBB86FC).copy(alpha = 0.6f)
                            else -> Color.Gray.copy(alpha = 0.3f)
                        },
                        shape = MaterialTheme.shapes.small
                    )
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = profile.profileName.ifEmpty { "P${profile.id}" },
                    color = when {
                        isFocused -> Color.Black
                        isLoaded -> Color(0xFF4CAF50) // VERT = Chargé
                        isLoading -> Color.White.copy(alpha = 0.7f)
                        else -> Color.White           // BLANC = Non chargé
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}
