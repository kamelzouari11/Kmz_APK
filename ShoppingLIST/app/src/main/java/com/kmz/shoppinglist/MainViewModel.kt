package com.kmz.shoppinglist

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.kmz.shoppinglist.data.Article
import com.kmz.shoppinglist.data.DataManager
import com.kmz.shoppinglist.ui.screens.Screen
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val dataManager = DataManager(application)

    var currentScreen by mutableStateOf<Screen>(Screen.Categories)
        private set

    fun navigateTo(screen: Screen) {
        currentScreen = screen
    }

    fun handleBackPress(): Boolean {
        if (currentScreen != Screen.Categories) {
            currentScreen = Screen.Categories
            return true
        }
        return false
    }

    private fun calculateSimilarity(s1: String, s2: String): Double {
        if (s1.isEmpty() || s2.isEmpty()) return 0.0
        val longer = if (s1.length > s2.length) s1 else s2
        val shorter = if (s1.length > s2.length) s2 else s1
        val longerLength = longer.length
        if (longerLength == 0) return 1.0

        val commonChars = shorter.count { longer.contains(it) }
        return commonChars.toDouble() / longerLength.toDouble()
    }

    private fun findClosestArticle(spokenText: String): Article? {
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

        // Recherche par similarité
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

    fun processVoiceResult(spokenText: String): String {
        val foundArticle = findClosestArticle(spokenText)
        return if (foundArticle != null) {
            if (!foundArticle.isBought) {
                "✓ \"${foundArticle.name}\" est déjà dans la liste à acheter"
            } else {
                dataManager.toggleArticleBought(foundArticle.id)
                "✓ \"${foundArticle.name}\" ajouté à la liste à acheter"
            }
        } else {
            "❌ Aucun article trouvé pour \"$spokenText\""
        }
    }
}
