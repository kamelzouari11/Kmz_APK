package com.kmz.shazamplayer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kmz.shazamplayer.model.Track

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackRow(track: Track, isSelected: Boolean, onClick: () -> Unit) {
    Card(
            onClick = onClick,
            colors =
                    CardDefaults.cardColors(
                            containerColor =
                                    if (isSelected) Color(0xFF0088FF).copy(alpha = 0.2f)
                                    else Color.White.copy(alpha = 0.05f)
                    ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            border = if (isSelected) BorderStroke(1.dp, Color(0xFF0088FF)) else null
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                    modifier =
                            Modifier.size(40.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.Gray.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = Color(0xFF0088FF)
                    )
                } else {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                        track.title,
                        color = if (isSelected) Color(0xFF0088FF) else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )
                Text(track.artist, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
                Text(track.tagTime, color = Color.DarkGray, fontSize = 10.sp)
            }
        }
    }
}
