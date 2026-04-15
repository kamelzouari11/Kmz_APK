package com.example.simpleiptv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * TextButton avec gestion automatique du focus pour TV (scale + highlight).
 */
@Composable
fun FocusableTextButton(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
) {
        var isFocused by remember { mutableStateOf(false) }
        TextButton(
                onClick = onClick,
                colors = ButtonDefaults.textButtonColors(
                        contentColor = if (isFocused) Color.Black
                                else MaterialTheme.colorScheme.primary
                ),
                modifier = modifier
                        .onFocusChanged { isFocused = it.isFocused }
                        .scale(if (isFocused) 1.05f else 1f)
                        .background(
                                if (isFocused) Color.White else Color.Transparent,
                                MaterialTheme.shapes.extraSmall
                        )
        ) { Text(text) }
}

/**
 * Button destructif (rouge) avec gestion du focus pour TV.
 */
@Composable
fun FocusableDestructiveButton(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
) {
        var isFocused by remember { mutableStateOf(false) }
        Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFocused) Color.White else Color.Red,
                        contentColor = if (isFocused) Color.Black else Color.White
                ),
                modifier = modifier
                        .onFocusChanged { isFocused = it.isFocused }
                        .scale(if (isFocused) 1.05f else 1f)
                        .border(
                                if (isFocused) 2.dp else 0.dp,
                                Color.White,
                                MaterialTheme.shapes.extraSmall
                        )
        ) { Text(text) }
}
