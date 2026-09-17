package com.example.myiptv

import android.app.UiModeManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.myiptv.ui.MainRoute
import com.example.myiptv.ui.MainViewModel
import com.example.myiptv.ui.theme.MyIptvTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val isTelevision: Boolean
        get() {
            val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
            return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
                packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        }
    private var useImmersiveMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyIptvTheme {
                MainRoute(
                    viewModel = viewModel,
                    onQuit = ::finishAndRemoveTask,
                    onPlayerModeChanged = ::applyPlayerMode,
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemUiVisibility()
    }

    private fun applyPlayerMode(fullScreen: Boolean) {
        val television = isTelevision
        requestedOrientation = when {
            television -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            fullScreen -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        useImmersiveMode = television || fullScreen
        applySystemUiVisibility()
    }

    private fun applySystemUiVisibility() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (useImmersiveMode) {
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        } else {
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }
}
