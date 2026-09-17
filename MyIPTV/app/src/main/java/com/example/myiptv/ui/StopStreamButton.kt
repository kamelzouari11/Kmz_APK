package com.example.myiptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.myiptv.ui.theme.MyIptvPalette

@Composable
fun StopStreamButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(MyIptvPalette.Surface.copy(alpha = 0.88f))
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) MyIptvPalette.Negative else MyIptvPalette.Border,
                shape = shape,
            )
            .semantics { contentDescription = "Arrêter le streaming" },
    ) {
        Box(Modifier.size(18.dp).background(Color.White))
    }
}
