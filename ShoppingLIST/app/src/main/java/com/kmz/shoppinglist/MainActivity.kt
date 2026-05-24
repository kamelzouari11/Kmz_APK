package com.kmz.shoppinglist

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import com.kmz.shoppinglist.ui.screens.*
import com.kmz.shoppinglist.ui.theme.Black
import com.kmz.shoppinglist.ui.theme.ShoppingListTheme

class MainActivity : ComponentActivity(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
                .components { add(SvgDecoder.Factory()) }
                .crossfade(true)
                .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissions()
        setContent {
            ShoppingListTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Black) {
                    val viewModel: MainViewModel = viewModel()
                    ShoppingListApp(viewModel)
                }
            }
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        } else {
            val permissions =
                    mutableListOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
            val toRequest =
                    permissions.filter {
                        ContextCompat.checkSelfPermission(this, it) !=
                                PackageManager.PERMISSION_GRANTED
                    }
            if (toRequest.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, toRequest.toTypedArray(), 101)
            }
        }
    }
}

@Composable
fun ShoppingListApp(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current

    // Launcher pour la reconnaissance vocale
    val speechRecognizerLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val spokenText =
                            result.data
                                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                                    ?.firstOrNull()
                    if (!spokenText.isNullOrEmpty()) {
                        val message = viewModel.processVoiceResult(spokenText)
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
            }

    // Fonction pour lancer la reconnaissance vocale
    fun startVoiceRecognition() {
        val intent =
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
                    putExtra(
                            RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES,
                            arrayListOf("fr-FR", "ar-SA", "ar-TN", "ar")
                    )
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "🎤 قل اسم المنتج / Dites le nom...")
                }
        try {
            speechRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Reconnaissance vocale non disponible", Toast.LENGTH_SHORT)
                    .show()
        }
    }

    // Gestion du bouton back système
    BackHandler(enabled = viewModel.currentScreen != Screen.Categories) {
        viewModel.handleBackPress()
    }

    // Afficher l'écran approprié
    when (val screen = viewModel.currentScreen) {
        is Screen.Categories -> {
            CategoriesScreen(
                    dataManager = viewModel.dataManager,
                    onCategoryClick = { category ->
                        viewModel.navigateTo(Screen.CategoryArticles(category))
                    },
                    onAllArticlesClick = { viewModel.navigateTo(Screen.AllArticles) },
                    onIconManagerClick = { viewModel.navigateTo(Screen.IconManager) },
                    onMicClick = { startVoiceRecognition() }
            )
        }
        is Screen.AllArticles -> {
            AllArticlesScreen(
                    dataManager = viewModel.dataManager,
                    onBackClick = { viewModel.handleBackPress() },
                    onMicClick = { startVoiceRecognition() }
            )
        }
        is Screen.IconManager -> {
            IconManagerScreen(
                    dataManager = viewModel.dataManager,
                    onBackClick = { viewModel.handleBackPress() }
            )
        }
        is Screen.CategoryArticles -> {
            ArticlesScreen(
                    category = screen.category,
                    dataManager = viewModel.dataManager,
                    onBackClick = { viewModel.handleBackPress() },
                    onMicClick = { startVoiceRecognition() }
            )
        }
    }
}
