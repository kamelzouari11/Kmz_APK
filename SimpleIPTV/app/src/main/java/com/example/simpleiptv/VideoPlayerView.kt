package com.example.simpleiptv

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.simpleiptv.data.local.entities.CategoryEntity
import com.example.simpleiptv.data.local.entities.ChannelEntity

@Composable
fun VideoPlayerView(
        exoPlayer: Player,
        channelName: String,
        currentChannels: List<ChannelEntity>,
        categories: List<CategoryEntity>,
        selectedCategoryId: String?,
        countries: List<String> = emptyList(),
        selectedCountry: String = "ALL",
        onCountrySelected: (String) -> Unit = {},
        onChannelSelected: (ChannelEntity) -> Unit,
        onCategorySelected: (CategoryEntity) -> Unit,
        onBack: () -> Unit,
        interactive: Boolean = true,
        isLandscape: Boolean = true,
        playingChannel: ChannelEntity? = null,
        countriesScrollState: LazyListState? = null,
        categoriesScrollState: LazyListState? = null,
        channelsScrollState: LazyListState? = null
) {
        var isOverlayVisible by remember { mutableStateOf(false) }
        var showFullOverlay by remember(isLandscape) { mutableStateOf(isLandscape) }
        val focusRequester = remember { FocusRequester() }
        val vodFocusRequester = remember { FocusRequester() }
        val isVod = playingChannel?.type == "VOD"

        var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

        // Use passed states or fallback to local ones if null (though MainScreen passes them)
        val countryState = countriesScrollState ?: rememberLazyListState()
        val categoryState = categoriesScrollState ?: rememberLazyListState()
        val channelState = channelsScrollState ?: rememberLazyListState()

        LaunchedEffect(isOverlayVisible, isVod) {
                if (isVod) {
                        vodFocusRequester.requestFocus()
                } else if (isOverlayVisible && interactive) {
                        // Priority: Focus the playing channel item if visible in the memory
                        focusRequester.requestFocus()
                }
        }

        if (isOverlayVisible && !isVod) {
                BackHandler { onBack() }
        }

        Box(
                modifier =
                        Modifier.fillMaxSize()
                                .background(Color.Black)
                                .then(
                                        if (!isVod) {
                                                Modifier.clickable(enabled = interactive) {
                                                        if (!isOverlayVisible) {
                                                                showFullOverlay = isLandscape
                                                                isOverlayVisible = true
                                                        } else {
                                                                isOverlayVisible = false
                                                        }
                                                }
                                                        .focusable()
                                                        .onKeyEvent { event ->
                                                                if (event.nativeKeyEvent.action ==
                                                                                KeyEvent.ACTION_DOWN
                                                                ) {
                                                                        when (event.nativeKeyEvent
                                                                                        .keyCode
                                                                        ) {
                                                                                KeyEvent.KEYCODE_DPAD_CENTER,
                                                                                KeyEvent.KEYCODE_ENTER,
                                                                                KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                                                                        if (!interactive
                                                                                        )
                                                                                                return@onKeyEvent false
                                                                                        if (!isOverlayVisible
                                                                                        ) {
                                                                                                showFullOverlay =
                                                                                                        true
                                                                                                isOverlayVisible =
                                                                                                        true
                                                                                        }
                                                                                        return@onKeyEvent true
                                                                                }
                                                                                KeyEvent.KEYCODE_BACK -> {
                                                                                        if (!interactive
                                                                                        )
                                                                                                return@onKeyEvent false
                                                                                        if (isOverlayVisible
                                                                                        ) {
                                                                                                isOverlayVisible =
                                                                                                        false
                                                                                                return@onKeyEvent true
                                                                                        }
                                                                                        onBack()
                                                                                        return@onKeyEvent true
                                                                                }
                                                                        }
                                                                }
                                                                false
                                                        }
                                        } else {
                                                // In VOD, we rely entirely on the AndroidView for
                                                // keys/clicks
                                                Modifier
                                        }
                                )
        ) {
                // 1. The Video Surface
                var isPlayerFocused by remember { mutableStateOf(false) }

                AndroidView(
                        factory = { context ->
                                PlayerView(context).apply {
                                        player = exoPlayer
                                        useController = isVod
                                        keepScreenOn = true
                                        isFocusable = true
                                        isFocusableInTouchMode = true
                                        playerViewRef = this
                                }
                        },
                        update = { view ->
                                view.useController = isVod
                                playerViewRef = view
                        },
                        modifier =
                                Modifier.fillMaxSize()
                                        .onFocusChanged { isPlayerFocused = it.isFocused }
                                        .then(
                                                if (isVod) {
                                                        Modifier.focusRequester(vodFocusRequester)
                                                                .focusable()
                                                                .clickable {
                                                                        playerViewRef
                                                                                ?.showController()
                                                                }
                                                                .onKeyEvent { event ->
                                                                        if (event.nativeKeyEvent
                                                                                        .action ==
                                                                                        KeyEvent.ACTION_DOWN
                                                                        ) {
                                                                                when (event.nativeKeyEvent
                                                                                                .keyCode
                                                                                ) {
                                                                                        KeyEvent.KEYCODE_BACK -> {
                                                                                                if (playerViewRef
                                                                                                                ?.isControllerFullyVisible ==
                                                                                                                true
                                                                                                ) {
                                                                                                        playerViewRef
                                                                                                                ?.hideController()
                                                                                                        true
                                                                                                } else {
                                                                                                        onBack()
                                                                                                        true
                                                                                                }
                                                                                        }
                                                                                        KeyEvent.KEYCODE_DPAD_UP,
                                                                                        KeyEvent.KEYCODE_DPAD_DOWN,
                                                                                        KeyEvent.KEYCODE_DPAD_CENTER,
                                                                                        KeyEvent.KEYCODE_ENTER,
                                                                                        KeyEvent.KEYCODE_DPAD_LEFT,
                                                                                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                                                                                playerViewRef
                                                                                                        ?.showController()
                                                                                                false // Let the view handle the actual seek/play/pause
                                                                                        }
                                                                                        else ->
                                                                                                false
                                                                                }
                                                                        } else {
                                                                                false
                                                                        }
                                                                }
                                                } else {
                                                        Modifier
                                                }
                                        )
                )

                // Visual indicator for focus in VOD
                if (isVod && isPlayerFocused) {
                        Box(
                                modifier =
                                        Modifier.fillMaxSize()
                                                .padding(2.dp)
                                                .background(Color.Cyan.copy(alpha = 0.05f))
                                                .border(
                                                        2.dp,
                                                        Color.Cyan.copy(alpha = 0.3f),
                                                        MaterialTheme.shapes.small
                                                )
                        )
                }

                // 2. The Overlays (Side by Side) - Only for LIVE
                if (isOverlayVisible && !isVod) {
                        Row(
                                modifier =
                                        Modifier.fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.6f))
                                                .padding(24.dp),
                                horizontalArrangement =
                                        if (showFullOverlay) Arrangement.Start else Arrangement.End
                        ) {
                                if (showFullOverlay) {
                                        // Country List (Leftmost)
                                        if (countries.size > 1) {
                                                Column(
                                                        modifier =
                                                                Modifier.fillMaxHeight()
                                                                        .width(150.dp)
                                                ) {
                                                        Text(
                                                                text = "Pays",
                                                                color = Color.White,
                                                                modifier = Modifier.padding(16.dp),
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .titleMedium
                                                        )
                                                        LazyColumn(
                                                                state = countryState,
                                                                modifier = Modifier.weight(1f)
                                                        ) {
                                                                items(countries, key = { it }) {
                                                                        country ->
                                                                        val isSelected =
                                                                                country ==
                                                                                        selectedCountry
                                                                        var isFocused by remember {
                                                                                mutableStateOf(
                                                                                        false
                                                                                )
                                                                        }
                                                                        Row(
                                                                                modifier =
                                                                                        Modifier.fillMaxWidth()
                                                                                                .padding(
                                                                                                        4.dp
                                                                                                )
                                                                                                .onFocusChanged {
                                                                                                        isFocused =
                                                                                                                it.isFocused
                                                                                                }
                                                                                                .clickable {
                                                                                                        onCountrySelected(
                                                                                                                country
                                                                                                        )
                                                                                                }
                                                                                                .focusable()
                                                                                                .background(
                                                                                                        color =
                                                                                                                when {
                                                                                                                        isFocused ->
                                                                                                                                Color.White
                                                                                                                                        .copy(
                                                                                                                                                alpha =
                                                                                                                                                        0.9f
                                                                                                                                        )
                                                                                                                        isSelected ->
                                                                                                                                Color.Green
                                                                                                                                        .copy(
                                                                                                                                                alpha =
                                                                                                                                                        0.2f
                                                                                                                                        )
                                                                                                                        else ->
                                                                                                                                Color.Transparent
                                                                                                                },
                                                                                                        shape =
                                                                                                                MaterialTheme
                                                                                                                        .shapes
                                                                                                                        .small
                                                                                                )
                                                                                                .padding(
                                                                                                        8.dp
                                                                                                )
                                                                        ) {
                                                                                Text(
                                                                                        text =
                                                                                                country,
                                                                                        color =
                                                                                                if (isFocused
                                                                                                )
                                                                                                        Color.Black
                                                                                                else if (isSelected
                                                                                                )
                                                                                                        Color.Green
                                                                                                else
                                                                                                        Color.White,
                                                                                        style =
                                                                                                MaterialTheme
                                                                                                        .typography
                                                                                                        .titleLarge,
                                                                                        maxLines =
                                                                                                1,
                                                                                        overflow =
                                                                                                TextOverflow
                                                                                                        .Ellipsis
                                                                                )
                                                                        }
                                                                }
                                                        }
                                                }
                                                Spacer(Modifier.width(16.dp))
                                        }

                                        // Category List (Middle)
                                        Column(modifier = Modifier.fillMaxHeight().width(280.dp)) {
                                                Text(
                                                        text = "Catégories",
                                                        color = Color.White,
                                                        modifier = Modifier.padding(16.dp),
                                                        style = MaterialTheme.typography.titleMedium
                                                )
                                                LazyColumn(
                                                        state = categoryState,
                                                        modifier = Modifier.weight(1f)
                                                ) {
                                                        itemsIndexed(
                                                                categories,
                                                                key = { _, cat -> cat.category_id }
                                                        ) { index, category ->
                                                                val isSelected =
                                                                        category.category_id ==
                                                                                selectedCategoryId
                                                                val isInitialFocus =
                                                                        if (selectedCategoryId !=
                                                                                        null
                                                                        )
                                                                                isSelected
                                                                        else index == 0
                                                                var isFocused by remember {
                                                                        mutableStateOf(false)
                                                                }

                                                                Row(
                                                                        modifier =
                                                                                Modifier.fillMaxWidth()
                                                                                        .padding(
                                                                                                4.dp
                                                                                        )
                                                                                        .then(
                                                                                                if (isInitialFocus
                                                                                                )
                                                                                                        Modifier.focusRequester(
                                                                                                                focusRequester
                                                                                                        )
                                                                                                else
                                                                                                        Modifier
                                                                                        )
                                                                                        .onFocusChanged {
                                                                                                isFocused =
                                                                                                        it.isFocused
                                                                                        }
                                                                                        .clickable {
                                                                                                onCategorySelected(
                                                                                                        category
                                                                                                )
                                                                                        }
                                                                                        .focusable()
                                                                                        .background(
                                                                                                color =
                                                                                                        when {
                                                                                                                isFocused ->
                                                                                                                        Color.White
                                                                                                                                .copy(
                                                                                                                                        alpha =
                                                                                                                                                0.9f
                                                                                                                                )
                                                                                                                isSelected ->
                                                                                                                        Color.Green
                                                                                                                                .copy(
                                                                                                                                        alpha =
                                                                                                                                                0.2f
                                                                                                                                )
                                                                                                                else ->
                                                                                                                        Color.Transparent
                                                                                                        },
                                                                                                shape =
                                                                                                        MaterialTheme
                                                                                                                .shapes
                                                                                                                .small
                                                                                        )
                                                                                        .padding(
                                                                                                8.dp
                                                                                        )
                                                                ) {
                                                                        Text(
                                                                                text =
                                                                                        category.category_name,
                                                                                color =
                                                                                        if (isFocused
                                                                                        )
                                                                                                Color.Black
                                                                                        else if (isSelected
                                                                                        )
                                                                                                Color.Green
                                                                                        else
                                                                                                Color.White,
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .titleLarge,
                                                                                maxLines = 1,
                                                                                overflow =
                                                                                        TextOverflow
                                                                                                .Ellipsis
                                                                        )
                                                                }
                                                        }
                                                }
                                        }

                                        Spacer(Modifier.width(16.dp))
                                }

                                // Channel List (Rightmost)
                                Column(
                                        modifier =
                                                Modifier.fillMaxHeight()
                                                        .width(
                                                                if (showFullOverlay) 320.dp
                                                                else 400.dp
                                                        )
                                ) {
                                        Text(
                                                text = "Chaînes",
                                                color = Color.White,
                                                modifier = Modifier.padding(16.dp),
                                                style = MaterialTheme.typography.titleMedium
                                        )
                                        LazyColumn(
                                                state = channelState,
                                                modifier = Modifier.weight(1f)
                                        ) {
                                                items(currentChannels, key = { it.stream_id }) {
                                                        channel ->
                                                        val isPlaying =
                                                                channel.stream_id ==
                                                                        playingChannel?.stream_id
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
                                                                                .then(
                                                                                        if (isPlaying
                                                                                        )
                                                                                                Modifier.focusRequester(
                                                                                                        focusRequester
                                                                                                )
                                                                                        else
                                                                                                Modifier
                                                                                )
                                                                                .clickable {
                                                                                        isOverlayVisible =
                                                                                                false
                                                                                        onChannelSelected(
                                                                                                channel
                                                                                        )
                                                                                }
                                                                                .focusable()
                                                                                .background(
                                                                                        color =
                                                                                                when {
                                                                                                        isFocused ->
                                                                                                                Color.White
                                                                                                                        .copy(
                                                                                                                                alpha =
                                                                                                                                        0.9f
                                                                                                                        )
                                                                                                        isPlaying ->
                                                                                                                Color.Green
                                                                                                                        .copy(
                                                                                                                                alpha =
                                                                                                                                        0.2f
                                                                                                                        )
                                                                                                        else ->
                                                                                                                Color.Transparent
                                                                                                },
                                                                                        shape =
                                                                                                MaterialTheme
                                                                                                        .shapes
                                                                                                        .small
                                                                                )
                                                                                .padding(8.dp),
                                                                verticalAlignment =
                                                                        Alignment.CenterVertically
                                                        ) {
                                                                AsyncImage(
                                                                        model = channel.stream_icon,
                                                                        contentDescription = null,
                                                                        modifier =
                                                                                Modifier.size(
                                                                                        24.dp
                                                                                ),
                                                                        contentScale =
                                                                                ContentScale.Fit
                                                                )
                                                                Spacer(Modifier.width(12.dp))
                                                                Text(
                                                                        text = channel.name,
                                                                        color =
                                                                                if (isFocused)
                                                                                        Color.Black
                                                                                else if (isPlaying)
                                                                                        Color.Green
                                                                                else Color.White,
                                                                        maxLines = 1,
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .titleLarge,
                                                                        overflow =
                                                                                TextOverflow
                                                                                        .Ellipsis,
                                                                        modifier =
                                                                                Modifier.weight(1f)
                                                                )
                                                                if (isPlaying) {
                                                                        Icon(
                                                                                imageVector =
                                                                                        Icons.Default
                                                                                                .PlayArrow,
                                                                                contentDescription =
                                                                                        null,
                                                                                tint =
                                                                                        if (isFocused
                                                                                        )
                                                                                                Color.Black
                                                                                        else
                                                                                                Color.Green,
                                                                                modifier =
                                                                                        Modifier.size(
                                                                                                16.dp
                                                                                        )
                                                                        )
                                                                }
                                                        }
                                                }
                                        }
                                }
                        }
                }

                // 3. Simple Indicator when overlay is hidden (Bottom Left version)
                if (!isOverlayVisible && interactive) {
                        Box(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                contentAlignment = Alignment.BottomStart
                        ) {
                                Text(
                                        text = channelName,
                                        color = Color.White.copy(alpha = 0.6f),
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier =
                                                Modifier.padding(
                                                        horizontal = 12.dp,
                                                        vertical = 6.dp
                                                )
                                )
                        }
                }
        }
}
