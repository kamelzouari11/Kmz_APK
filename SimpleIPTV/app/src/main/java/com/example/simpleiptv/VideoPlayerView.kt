package com.example.simpleiptv

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.example.simpleiptv.data.local.entities.CategoryEntity
import com.example.simpleiptv.data.local.entities.ChannelEntity

enum class OverlayMode {
        NONE,
        CHANNELS,
        CATEGORIES
}

@Composable
fun VideoPlayerView(
        exoPlayer: Player,
        channelName: String,
        currentChannels: List<ChannelEntity>,
        categories: List<CategoryEntity>,
        onChannelSelected: (ChannelEntity) -> Unit,
        onCategorySelected: (CategoryEntity) -> Unit,
        onBack: () -> Unit
) {
        var overlayMode by remember { mutableStateOf(OverlayMode.NONE) }

        // Focus Handling for Key Interception
        val requester = remember { FocusRequester() }

        LaunchedEffect(Unit) { requester.requestFocus() }

        Box(
                modifier =
                        Modifier.fillMaxSize()
                                .background(Color.Black)
                                .focusRequester(requester)
                                .focusable()
                                .onKeyEvent { event ->
                                        if (event.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                                                when (event.nativeKeyEvent.keyCode) {
                                                        KeyEvent.KEYCODE_DPAD_CENTER,
                                                        KeyEvent.KEYCODE_ENTER,
                                                        KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                                                if (overlayMode == OverlayMode.NONE
                                                                ) {
                                                                        overlayMode =
                                                                                OverlayMode.CHANNELS
                                                                }
                                                                // If overlay is open, let standard
                                                                // navigation handle
                                                                // clicks (which are not key events
                                                                // here)
                                                                return@onKeyEvent true
                                                        }
                                                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                                                                if (overlayMode ==
                                                                                OverlayMode.NONE ||
                                                                                overlayMode ==
                                                                                        OverlayMode
                                                                                                .CHANNELS
                                                                ) {
                                                                        overlayMode =
                                                                                OverlayMode
                                                                                        .CATEGORIES
                                                                        return@onKeyEvent true
                                                                }
                                                                return@onKeyEvent false
                                                        }
                                                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                                                if (overlayMode ==
                                                                                OverlayMode
                                                                                        .CATEGORIES
                                                                ) {
                                                                        overlayMode =
                                                                                OverlayMode.CHANNELS
                                                                        return@onKeyEvent true
                                                                }
                                                                return@onKeyEvent false
                                                        }
                                                        KeyEvent.KEYCODE_BACK -> {
                                                                if (overlayMode != OverlayMode.NONE
                                                                ) {
                                                                        overlayMode =
                                                                                OverlayMode.NONE
                                                                        return@onKeyEvent true
                                                                }
                                                                onBack()
                                                                return@onKeyEvent true
                                                        }
                                                }
                                        }
                                        false
                                }
        ) {
                // 1. The Video Surface
                AndroidView(
                        factory = { context ->
                                PlayerView(context).apply {
                                        player = exoPlayer
                                        useController = false // We handle our own UI
                                        keepScreenOn = true
                                }
                        },
                        modifier = Modifier.fillMaxSize()
                )

                // 2. The Channel List Overlay (Right Side)
                if (overlayMode == OverlayMode.CHANNELS) {
                        val listState =
                                rememberLazyListState(
                                        initialFirstVisibleItemIndex =
                                                currentChannels
                                                        .indexOfFirst { it.name == channelName }
                                                        .coerceAtLeast(0)
                                )
                        // Auto-focus logic for the list
                        val channelListRequester = remember { FocusRequester() }
                        LaunchedEffect(Unit) { channelListRequester.requestFocus() }

                        Box(
                                modifier =
                                        Modifier.fillMaxSize()
                                                .background(
                                                        Color.Black.copy(alpha = 0.3f)
                                                ) // Semi-transparent
                                                .padding(24.dp),
                                contentAlignment = Alignment.CenterEnd
                        ) {
                                Column(
                                        modifier =
                                                Modifier.fillMaxHeight()
                                                        .width(300.dp) // Sidebar width
                                                        .background(
                                                                MaterialTheme.colorScheme.surface
                                                                        .copy(alpha = 0.1f)
                                                        )
                                ) {
                                        Text(
                                                "Chaînes",
                                                color = Color.White,
                                                modifier = Modifier.padding(16.dp),
                                                style = MaterialTheme.typography.titleMedium
                                        )

                                        LazyColumn(state = listState) {
                                                items(
                                                        items = currentChannels,
                                                        key = { it.stream_id }
                                                ) { channel -> // KEY IS CRITICAL
                                                        val isCurrent = channel.name == channelName
                                                        var isFocused by remember {
                                                                mutableStateOf(false)
                                                        }

                                                        Row(
                                                                modifier =
                                                                        Modifier.fillMaxWidth()
                                                                                .padding(4.dp)
                                                                                .onFocusChanged {
                                                                                        isFocused =
                                                                                                it.isFocused
                                                                                }
                                                                                .clickable {
                                                                                        onChannelSelected(
                                                                                                channel
                                                                                        )
                                                                                }
                                                                                .then(
                                                                                        if (isCurrent
                                                                                        )
                                                                                                Modifier.focusRequester(
                                                                                                        channelListRequester
                                                                                                )
                                                                                        else
                                                                                                Modifier
                                                                                ) // Focus current
                                                                                // item
                                                                                .focusable()
                                                                                .scale(
                                                                                        if (isFocused
                                                                                        )
                                                                                                1.05f
                                                                                        else 1f
                                                                                )
                                                                                .background(
                                                                                        if (isFocused
                                                                                        )
                                                                                                Color.White
                                                                                                        .copy(
                                                                                                                alpha =
                                                                                                                        0.9f
                                                                                                        )
                                                                                        else if (isCurrent
                                                                                        )
                                                                                                Color.Green
                                                                                                        .copy(
                                                                                                                alpha =
                                                                                                                        0.3f
                                                                                                        )
                                                                                        else
                                                                                                Color.Transparent,
                                                                                        MaterialTheme
                                                                                                .shapes
                                                                                                .small
                                                                                )
                                                                                .padding(8.dp)
                                                        ) {
                                                                Text(
                                                                        channel.name,
                                                                        color =
                                                                                if (isFocused)
                                                                                        Color.Black
                                                                                else Color.White,
                                                                        maxLines = 1,
                                                                        overflow =
                                                                                TextOverflow
                                                                                        .Ellipsis
                                                                )
                                                        }
                                                }
                                        }
                                }
                        }
                }

                if (overlayMode == OverlayMode.CATEGORIES) {
                        val categoryListRequester = remember { FocusRequester() }
                        LaunchedEffect(Unit) { categoryListRequester.requestFocus() }

                        Box(
                                modifier =
                                        Modifier.fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.3f))
                                                .padding(24.dp),
                                contentAlignment = Alignment.CenterStart
                        ) {
                                Column(
                                        modifier =
                                                Modifier.fillMaxHeight()
                                                        .width(300.dp)
                                                        .background(
                                                                MaterialTheme.colorScheme.surface
                                                                        .copy(alpha = 0.1f)
                                                        )
                                ) {
                                        Text(
                                                "Catégories",
                                                color = Color.White,
                                                modifier = Modifier.padding(16.dp),
                                                style = MaterialTheme.typography.titleMedium
                                        )

                                        LazyColumn {
                                                items(
                                                        items = categories,
                                                        key = { it.category_id }
                                                ) { category ->
                                                        var isFocused by remember {
                                                                mutableStateOf(false)
                                                        }

                                                        Row(
                                                                modifier =
                                                                        Modifier.fillMaxWidth()
                                                                                .padding(4.dp)
                                                                                .onFocusChanged {
                                                                                        isFocused =
                                                                                                it.isFocused
                                                                                }
                                                                                .clickable {
                                                                                        onCategorySelected(
                                                                                                category
                                                                                        )
                                                                                        overlayMode =
                                                                                                OverlayMode
                                                                                                        .CHANNELS
                                                                                }
                                                                                .then(
                                                                                        if (categories
                                                                                                        .indexOf(
                                                                                                                category
                                                                                                        ) ==
                                                                                                        0
                                                                                        )
                                                                                                Modifier.focusRequester(
                                                                                                        categoryListRequester
                                                                                                )
                                                                                        else
                                                                                                Modifier
                                                                                )
                                                                                .focusable()
                                                                                .background(
                                                                                        if (isFocused
                                                                                        )
                                                                                                Color.White
                                                                                                        .copy(
                                                                                                                alpha =
                                                                                                                        0.9f
                                                                                                        )
                                                                                        else
                                                                                                Color.Transparent,
                                                                                        MaterialTheme
                                                                                                .shapes
                                                                                                .small
                                                                                )
                                                                                .padding(8.dp)
                                                        ) {
                                                                Text(
                                                                        category.category_name,
                                                                        color =
                                                                                if (isFocused)
                                                                                        Color.Black
                                                                                else Color.White,
                                                                        maxLines = 1,
                                                                        overflow =
                                                                                TextOverflow
                                                                                        .Ellipsis
                                                                )
                                                        }
                                                }
                                        }
                                }
                        }
                }

                // 4. Simple Channel Name Toast/Overlay when List is hidden
                if (overlayMode == OverlayMode.NONE) {
                        Box(
                                modifier =
                                        Modifier.align(Alignment.BottomStart)
                                                .padding(12.dp) // Reduced margin
                        ) {
                                Text(
                                        channelName,
                                        color = Color.White,
                                        style =
                                                MaterialTheme.typography.bodyMedium.copy(
                                                        shadow =
                                                                androidx.compose.ui.graphics.Shadow(
                                                                        color = Color.Black,
                                                                        blurRadius = 3f
                                                                )
                                                )
                                )
                        }
                }
        }
}
