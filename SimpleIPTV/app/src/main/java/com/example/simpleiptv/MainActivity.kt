package com.example.simpleiptv

import android.content.ComponentName
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.simpleiptv.data.IptvRepository
import com.example.simpleiptv.data.api.XtreamClient
import com.example.simpleiptv.data.local.AppDatabase
import com.example.simpleiptv.ui.MainScreen
import com.example.simpleiptv.ui.theme.SimpleIPTVTheme
import com.example.simpleiptv.ui.viewmodel.MainViewModel
import com.google.common.util.concurrent.MoreExecutors

class MainActivity : ComponentActivity() {
        private var controllerFuture:
                com.google.common.util.concurrent.ListenableFuture<MediaController>? =
                null

        override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                val database = AppDatabase.getDatabase(this)
                val iptvApi = XtreamClient.create("http://j.delta2022.xyz:8880/")
                val iptvRepository = IptvRepository(iptvApi, database.iptvDao())

                val viewModel =
                        ViewModelProvider(
                                this,
                                object : ViewModelProvider.Factory {
                                        override fun <T : ViewModel> create(
                                                modelClass: Class<T>
                                        ): T {
                                                if (modelClass.isAssignableFrom(
                                                                MainViewModel::class.java
                                                        )
                                                ) {
                                                        @Suppress("UNCHECKED_CAST")
                                                        return MainViewModel(iptvRepository) as T
                                                }
                                                throw IllegalArgumentException(
                                                        "Unknown ViewModel class"
                                                )
                                        }
                                }
                        )[MainViewModel::class.java]

                controllerFuture =
                        MediaController.Builder(
                                        this,
                                        SessionToken(
                                                this,
                                                ComponentName(this, PlaybackService::class.java)
                                        )
                                )
                                .buildAsync()

                setContent {
                        var exoPlayerState by remember { mutableStateOf<Player?>(null) }

                        LaunchedEffect(controllerFuture) {
                                controllerFuture?.addListener(
                                        {
                                                try {
                                                        exoPlayerState = controllerFuture?.get()
                                                } catch (e: Exception) {}
                                        },
                                        MoreExecutors.directExecutor()
                                )
                        }

                        SimpleIPTVTheme(darkTheme = true) {
                                MainScreen(
                                        viewModel = viewModel,
                                        exoPlayer = exoPlayerState,
                                        exportJson = { iptvRepository.exportDatabaseToJson() },
                                        importJson = { iptvRepository.importDatabaseFromJson(it) },
                                        getStreamUrl = { streamId ->
                                                val profile =
                                                        viewModel.profiles.find {
                                                                it.id == viewModel.activeProfileId
                                                        }
                                                if (profile != null)
                                                        iptvRepository.getStreamUrl(
                                                                profile,
                                                                streamId
                                                        )
                                                else ""
                                        }
                                )
                        }
                }
        }

        override fun onDestroy() {
                super.onDestroy()
                controllerFuture?.let { MediaController.releaseFuture(it) }
        }
}
