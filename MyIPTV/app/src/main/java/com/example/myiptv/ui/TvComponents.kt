package com.example.myiptv.ui

import android.content.pm.PackageManager
import android.content.res.Configuration
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myiptv.ui.theme.MyIptvPalette

@Composable
internal fun isAndroidTv(): Boolean {
    val configuration = LocalConfiguration.current
    val packageManager = LocalContext.current.packageManager
    return configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}

@Composable
internal fun isTvLandscape(): Boolean = isAndroidTv() &&
    LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

@Composable
internal fun Modifier.tvFocusBorder(
    shape: Shape = RoundedCornerShape(10.dp),
    enabled: Boolean = true,
): Modifier {
    val applyFocusBorder = enabled && isTvLandscape()
    var focused by remember { mutableStateOf(false) }
    if (!applyFocusBorder) return this
    return onFocusChanged { focused = it.isFocused }
        .then(
            if (focused) {
                Modifier.border(3.dp, MyIptvPalette.Negative, shape)
            } else {
                Modifier
            },
        )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvListItem(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
    content: @Composable BoxScope.(focused: Boolean, contentColor: Color) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var remoteLongClickHandled by remember { mutableStateOf(false) }
    val background = when {
        selected -> MyIptvPalette.ActiveSurface
        focused -> MyIptvPalette.CardHover
        else -> MyIptvPalette.Card
    }
    val border = when {
        focused -> MyIptvPalette.Negative
        selected -> MyIptvPalette.EmeraldAccent
        else -> MyIptvPalette.Border
    }
    val borderWidth = when {
        focused -> 3.dp
        selected -> 2.dp
        else -> 1.dp
    }
    val contentColor = when {
        selected -> MyIptvPalette.EmeraldAccent
        focused -> MyIptvPalette.White
        else -> MyIptvPalette.BackgroundLight
    }
    val shape = RoundedCornerShape(8.dp)
    val clickModifier = if (onLongClick == null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier.combinedClickable(
            onClick = {
                if (remoteLongClickHandled) {
                    remoteLongClickHandled = false
                    onLongClick()
                } else {
                    onClick()
                }
            },
            onLongClick = onLongClick,
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(borderWidth, border, shape)
            .onFocusChanged { focusState ->
                val gainedFocus = !focused && focusState.isFocused
                focused = focusState.isFocused
                if (!focusState.isFocused) remoteLongClickHandled = false
                if (gainedFocus) onFocused()
            }
            .onPreviewKeyEvent { event ->
                if (onLongClick == null) return@onPreviewKeyEvent false
                val nativeEvent = event.nativeKeyEvent
                val isConfirmKey = nativeEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                    nativeEvent.keyCode == AndroidKeyEvent.KEYCODE_ENTER
                if (
                    isConfirmKey &&
                    nativeEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                    nativeEvent.repeatCount > 0 &&
                    !remoteLongClickHandled
                ) {
                    remoteLongClickHandled = true
                    true
                } else {
                    false
                }
            }
            .then(clickModifier)
            .focusable()
            .padding(contentPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        content(focused, contentColor)
    }
}

@Composable
fun MenuButton(
    text: String,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        active -> MyIptvPalette.ActiveSurface
        focused -> MyIptvPalette.CardHover
        else -> MyIptvPalette.Card
    }
    val foreground = when {
        !enabled -> MyIptvPalette.Disabled
        active -> MyIptvPalette.EmeraldAccent
        focused -> MyIptvPalette.White
        else -> MyIptvPalette.EmeraldLight
    }
    val border = when {
        focused -> MyIptvPalette.Negative
        active -> MyIptvPalette.EmeraldAccent
        else -> MyIptvPalette.Border
    }
    val borderWidth = when {
        focused -> 3.dp
        active -> 2.dp
        else -> 1.dp
    }
    val shape = RoundedCornerShape(10.dp)
    Surface(
        modifier = modifier
            .clip(shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick)
            .focusable(enabled),
        color = background,
        shape = shape,
        border = BorderStroke(borderWidth, border),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            color = foreground,
            style = MaterialTheme.typography.labelLarge,
            maxLines = maxLines,
            overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun MenuActionButton(
    label: String,
    icon: ImageVector,
    showLabel: Boolean,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        active -> MyIptvPalette.ActiveSurface
        focused -> MyIptvPalette.CardHover
        else -> MyIptvPalette.Card
    }
    val foreground = when {
        !enabled -> MyIptvPalette.Disabled
        active -> MyIptvPalette.EmeraldAccent
        focused -> MyIptvPalette.White
        else -> MyIptvPalette.EmeraldLight
    }
    val border = when {
        focused -> MyIptvPalette.Negative
        active -> MyIptvPalette.EmeraldAccent
        else -> MyIptvPalette.Border
    }
    val borderWidth = when {
        focused -> 3.dp
        active -> 2.dp
        else -> 1.dp
    }
    val shape = RoundedCornerShape(12.dp)

    Surface(
        modifier = modifier
            .clip(shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick)
            .focusable(enabled),
        color = background,
        shape = shape,
        border = BorderStroke(borderWidth, border),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (showLabel) 12.dp else 9.dp,
                vertical = 8.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(28.dp),
                tint = foreground,
            )
            if (showLabel) {
                Text(
                    text = label,
                    color = foreground,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
