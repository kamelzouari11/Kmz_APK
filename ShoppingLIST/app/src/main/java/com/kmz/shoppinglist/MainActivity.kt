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
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import com.kmz.shoppinglist.data.Article
import com.kmz.shoppinglist.data.Category
import com.kmz.shoppinglist.data.DataManager
import com.kmz.shoppinglist.ui.screens.AllArticlesScreen
import com.kmz.shoppinglist.ui.screens.ArticlesScreen
import com.kmz.shoppinglist.ui.screens.CategoriesScreen
import com.kmz.shoppinglist.ui.screens.IconManagerScreen
import com.kmz.shoppinglist.ui.theme.Black
import com.kmz.shoppinglist.ui.theme.ShoppingListTheme
import java.util.Locale

class MainActivity : ComponentActivity(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
                .components {
                    add(SvgDecoder.Factory()) // Support des SVG
                }
                .crossfade(true)
                .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissions()
        setContent {
            ShoppingListTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Black) { ShoppingListApp() }
            }
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+)
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
            // Android 10 et inférieur
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

sealed class Screen {
    object Categories : Screen()
    object AllArticles : Screen()
    object IconManager : Screen()
    data class CategoryArticles(val category: Category) : Screen()
}

@Composable
fun ShoppingListApp() {
    val context = LocalContext.current
    val activity = context as? Activity
    val dataManager = remember { DataManager(context) }

    // État de navigation
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Categories) }

    // Calcul de similarité simple (basé sur les caractères communs)
    fun calculateSimilarity(s1: String, s2: String): Double {
        if (s1.isEmpty() || s2.isEmpty()) return 0.0
        val longer = if (s1.length > s2.length) s1 else s2
        val shorter = if (s1.length > s2.length) s2 else s1
        val longerLength = longer.length
        if (longerLength == 0) return 1.0

        val commonChars = shorter.count { longer.contains(it) }
        return commonChars.toDouble() / longerLength.toDouble()
    }

    // Fonction pour trouver l'article le plus proche par nom (similarité phonétique)
    fun findClosestArticle(spokenText: String): Article? {
        val allArticles = dataManager.getArticles()
        val normalizedSpoken = spokenText.lowercase(Locale.FRENCH).trim()

        // Recherche exacte d'abord
        allArticles
                .find {
                    it.name.lowercase(Locale.FRENCH) == normalizedSpoken ||
                            it.frenchName?.lowercase(Locale.FRENCH) == normalizedSpoken
                }
                ?.let {
                    return it
                }

        // Recherche par contenance
        allArticles
                .find {
                    it.name.lowercase(Locale.FRENCH).contains(normalizedSpoken) ||
                            normalizedSpoken.contains(it.name.lowercase(Locale.FRENCH)) ||
                            it.frenchName?.lowercase(Locale.FRENCH)?.contains(normalizedSpoken) ==
                                    true ||
                            it.frenchName?.let { fn ->
                                normalizedSpoken.contains(fn.lowercase(Locale.FRENCH))
                            } == true
                }
                ?.let {
                    return it
                }

        // Recherche par similarité (distance de Levenshtein simplifiée)
        val scored =
                allArticles.map { article ->
                    val nameSimilarity =
                            calculateSimilarity(
                                    normalizedSpoken,
                                    article.name.lowercase(Locale.FRENCH)
                            )
                    val frenchNameSimilarity =
                            article.frenchName?.let {
                                calculateSimilarity(normalizedSpoken, it.lowercase(Locale.FRENCH))
                            }
                                    ?: 0.0
                    article to kotlin.math.max(nameSimilarity, frenchNameSimilarity)
                }

        return scored.maxByOrNull { it.second }?.takeIf { it.second > 0.5 }?.first
    }

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
                        val foundArticle = findClosestArticle(spokenText)

                        if (foundArticle != null) {
                            // Vérifier si l'article est déjà dans la liste à acheter
                            if (!foundArticle.isBought) {
                                // L'article est déjà dans la liste à acheter
                                Toast.makeText(
                                                context,
                                                "✓ \"${foundArticle.name}\" est déjà dans la liste à acheter",
                                                Toast.LENGTH_LONG
                                        )
                                        .show()
                            } else {
                                // L'article est acheté, le remettre dans la liste à acheter
                                dataManager.toggleArticleBought(foundArticle.id)
                                Toast.makeText(
                                                context,
                                                "✓ \"${foundArticle.name}\" ajouté à la liste à acheter",
                                                Toast.LENGTH_LONG
                                        )
                                        .show()
                            }
                        } else {
                            // Aucun article trouvé
                            Toast.makeText(
                                            context,
                                            "❌ Aucun article trouvé pour \"$spokenText\"",
                                            Toast.LENGTH_LONG
                                    )
                                    .show()
                        }
                    }
                }
            }

    // Fonction pour lancer la reconnaissance vocale (français + arabe)
    fun startVoiceRecognition() {
        val intent =
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    // Support multilingue : français et arabe
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
    BackHandler(enabled = currentScreen != Screen.Categories) {
        when (currentScreen) {
            is Screen.AllArticles, is Screen.IconManager, is Screen.CategoryArticles -> {
                currentScreen = Screen.Categories
            }
            else -> {
                // Ne rien faire sur l'écran principal
            }
        }
    }

    // Afficher l'écran approprié selon la navigation
    when (val screen = currentScreen) {
        is Screen.Categories -> {
            CategoriesScreen(
                    dataManager = dataManager,
                    onCategoryClick = { category ->
                        currentScreen = Screen.CategoryArticles(category)
                    },
                    onAllArticlesClick = { currentScreen = Screen.AllArticles },
                    onIconManagerClick = { currentScreen = Screen.IconManager },
                    onMicClick = { startVoiceRecognition() }
            )
        }
        is Screen.AllArticles -> {
            AllArticlesScreen(
                    dataManager = dataManager,
                    onBackClick = { currentScreen = Screen.Categories }
            )
        }
        is Screen.IconManager -> {
            IconManagerScreen(
                    dataManager = dataManager,
                    onBackClick = { currentScreen = Screen.Categories }
            )
        }
        is Screen.CategoryArticles -> {
            ArticlesScreen(
                    category = screen.category,
                    dataManager = dataManager,
                    onBackClick = { currentScreen = Screen.Categories }
            )
        }
    }
}
