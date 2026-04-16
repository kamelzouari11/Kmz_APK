package com.example.simpleiptv

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.simpleiptv.data.IptvRepository
import com.example.simpleiptv.data.local.AppDatabase
import com.example.simpleiptv.ui.MainScreen
import com.example.simpleiptv.ui.theme.SimpleIPTVTheme
import com.example.simpleiptv.ui.viewmodel.MainViewModel
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
        private var controllerFuture:
                com.google.common.util.concurrent.ListenableFuture<MediaController>? =
                null
        private var isInitialized by mutableStateOf(false)
        private var viewModel: MainViewModel? = null
        private var iptvRepository: IptvRepository? = null

        override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                lifecycleScope.launch {
                        try {
                                val database = AppDatabase.getDatabase(this@MainActivity)
                                val dao = database.iptvDao()
                                iptvRepository = IptvRepository(dao)

                                // Créer un profil par défaut si aucun profil n'existe
                                iptvRepository!!.createDefaultProfileIfNeeded()

                                viewModel =
                                        ViewModelProvider(
                                                this@MainActivity,
                                                object : ViewModelProvider.Factory {
                                                        override fun <T : ViewModel> create(
                                                                modelClass: Class<T>
                                                        ): T {
                                                                @Suppress("UNCHECKED_CAST")
                                                                return MainViewModel(
                                                                        iptvRepository!!
                                                                ) as
                                                                        T
                                                        }
                                                }
                                        )[MainViewModel::class.java]

                                controllerFuture =
                                        MediaController.Builder(
                                                        this@MainActivity,
                                                        SessionToken(
                                                                this@MainActivity,
                                                                ComponentName(
                                                                        this@MainActivity,
                                                                        PlaybackService::class.java
                                                                )
                                                        )
                                                )
                                                .buildAsync()

                                isInitialized = true
                        } catch (e: Throwable) {
                                e.printStackTrace()
                                android.util.Log.e("SimpleIPTV", "Startup failure", e)
                        }
                }

                setContent {
                        if (!isInitialized) {
                                Box(
                                        modifier = Modifier.fillMaxSize().background(Color.Black),
                                        contentAlignment = Alignment.Center
                                ) { CircularProgressIndicator(color = Color.Green) }
                        } else {
                                var exoPlayerState by remember { mutableStateOf<Player?>(null) }
                                LaunchedEffect(controllerFuture) {
                                        controllerFuture?.addListener(
                                                {
                                                        try {
                                                                exoPlayerState =
                                                                        controllerFuture?.get()
                                                        } catch (e: Exception) {}
                                                },
                                                MoreExecutors.directExecutor()
                                        )
                                }

                                SimpleIPTVTheme(darkTheme = true) {
                                        val coroutineScope = rememberCoroutineScope()
                                        MainScreen(
                                                viewModel = viewModel!!,
                                                exoPlayer = exoPlayerState,
                                                onSave = {
                                                        coroutineScope.launch {
                                                                val json =
                                                                        iptvRepository!!
                                                                                .exportDatabaseToJson()
                                                                com.example.simpleiptv.utils
                                                                        .BackupUtils.saveToCloud(
                                                                        this@MainActivity,
                                                                        json
                                                                )
                                                        }
                                                },
                                                onRestore = {
                                                        coroutineScope.launch {
                                                                val json =
                                                                        com.example.simpleiptv.utils
                                                                                .BackupUtils
                                                                                .fetchFromCloud(
                                                                                        this@MainActivity
                                                                                )
                                                                if (json != null) {
                                                                        iptvRepository!!
                                                                                .importDatabaseFromJson(
                                                                                        json
                                                                                )
                                                                }
                                                        }
                                                },
                                                getStreamUrl = { streamId, profileId ->
                                                        // Si un profileId explicite est fourni (recherche globale),
                                                        // utiliser ce profil même s'il n'est pas le profil actif.
                                                        val profile =
                                                                if (profileId != null) {
                                                                        viewModel!!.profiles.find { it.id == profileId }
                                                                                ?: viewModel!!.profiles.find { it.id == viewModel!!.activeProfileId }
                                                                } else {
                                                                        viewModel!!.profiles.find { it.id == viewModel!!.activeProfileId }
                                                                }
                                                        if (profile != null)
                                                                iptvRepository!!.getStreamUrl(
                                                                        profile,
                                                                        streamId
                                                                )
                                                        else ""
                                                }
                                        )
                                }
                        }
                }
        }

        override fun onDestroy() {
                // Stopper le player et tuer le service proprement
                // pour que l'audio ne continue pas en arrière-plan
                controllerFuture?.let { future ->
                        if (future.isDone) {
                                try {
                                        future.get()?.let { controller ->
                                                controller.stop()
                                                controller.clearMediaItems()
                                        }
                                } catch (e: Exception) {}
                        }
                        MediaController.releaseFuture(future)
                }
                // Tuer le service PlaybackService explicitement
                stopService(android.content.Intent(this, PlaybackService::class.java))
                super.onDestroy()
        }
}
