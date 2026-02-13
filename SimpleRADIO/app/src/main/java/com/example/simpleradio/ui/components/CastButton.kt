package com.example.simpleradio.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory

/**
 * Reusable Cast button that wraps the MediaRouteButton in a composable. Always visible on screen.
 */
@Composable
fun CastButton(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(48.dp), contentAlignment = Alignment.Center) {
        AndroidView(
                factory = { ctx ->
                    MediaRouteButton(ctx).apply {
                        CastButtonFactory.setUpMediaRouteButton(ctx, this)
                        try {
                            @Suppress("DEPRECATION") this.setAlwaysVisible(true)
                        } catch (_: Exception) {}
                    }
                }
        )
    }
}
