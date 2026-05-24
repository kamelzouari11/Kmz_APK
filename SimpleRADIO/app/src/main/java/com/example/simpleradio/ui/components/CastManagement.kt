package com.example.simpleradio.ui.components

import android.content.Context
import androidx.compose.runtime.*
import androidx.media3.common.Player
import com.example.simpleradio.cast.CastHelper
import com.example.simpleradio.ui.MainViewModel
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession

@Composable
fun rememberCastManagement(
        context: Context,
        viewModel: MainViewModel,
        exoPlayer: Player?
): Pair<CastHelper, CastSession?> {
    var castSession by remember { mutableStateOf<CastSession?>(null) }

    val castHelper = remember {
        var tempHelper: CastHelper? = null
        tempHelper =
                CastHelper(
                        context = context,
                        onSessionStatusChanged = { castSession = it },
                        onSessionStarted = { session ->
                            viewModel.playingRadio?.let { radio ->
                                tempHelper?.loadMedia(
                                        session,
                                        radio,
                                        viewModel.currentArtist,
                                        viewModel.currentTitle,
                                        viewModel.currentArtworkUrl
                                )
                                try {
                                    exoPlayer?.volume = 0f
                                } catch (_: Exception) {}
                            }
                        }
                )
        tempHelper
    }

    LaunchedEffect(Unit) {
        if (castHelper.initCast()) {
            try {
                CastContext.getSharedInstance(context)
                        .sessionManager
                        .addSessionManagerListener(
                                castHelper.sessionManagerListener,
                                CastSession::class.java
                        )
            } catch (_: Exception) {}
        }
    }

    return Pair(castHelper, castSession)
}
