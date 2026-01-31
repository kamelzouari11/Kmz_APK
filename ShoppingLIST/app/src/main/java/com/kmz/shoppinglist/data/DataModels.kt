package com.kmz.shoppinglist.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import android.os.Environment
import android.util.Log

/** Structure pour l'export/import complet */
data class FullDatabase(
    val categories: List<Category>,
    val articles: List<Article>
)

/** Niveaux de priorité des articles */
enum class Priority(val displayOrder: Int) {
    URGENT(0), // 🔴 Rouge pâle
    IMPORTANT(1), // 🟠 Orange
    NORMAL(2), // ⚪ Blanc
    OPTIONAL(3) // Gris
}

/** Représente un article dans la liste */
data class Article(
        val id: Long = System.currentTimeMillis(),
        val name: String,
        val categoryId: Long,
        var priority: Priority = Priority.NORMAL,
        val isBought: Boolean = false,
        val iconId: String? = "panier", // Nom du fichier PNG local sans extension
        val frenchName: String? =
                null // Nom en français pour recherche d'icône si original en arabe
) {
    /** Obtenir l'iconId avec une valeur par défaut si null */
    fun getIconIdSafe(): String = iconId ?: "panier"
}

/** Représente une catégorie de courses */
data class Category(
        val id: Long = System.currentTimeMillis(),
        val name: String,
        val iconName: String = "categorie", // Legacy
        val iconId: String? = "categorie", // Nom du fichier PNG local
        val frenchName: String? = null // Nom en français pour recherche d'icône
) {
    /** Obtenir l'iconId avec une valeur par défaut si null */
    fun getIconIdSafe(): String = iconId ?: "categorie"
}

/** Gestion de la persistance des données avec SharedPreferences et JSON */
class DataManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("shopping_list_data", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_CATEGORIES = "categories"
        private const val KEY_ARTICLES = "articles"
    }

    // ============ CATÉGORIES ============

    fun getCategories(): List<Category> {
        val json = prefs.getString(KEY_CATEGORIES, null) ?: return getDefaultCategories()
        return try {
            val type = object : TypeToken<List<Category>>() {}.type
            val categories: List<Category> = gson.fromJson(json, type)
            // Assurer que toutes les catégories ont un iconId valide
            categories.map { cat ->
                if (cat.iconId.isNullOrEmpty()) {
                    cat.copy(iconId = "categorie")
                } else {
                    cat
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            getDefaultCategories()
        }
    }

    fun saveCategories(categories: List<Category>) {
        prefs.edit().putString(KEY_CATEGORIES, gson.toJson(categories)).apply()
    }

    fun addCategory(category: Category) {
        val categories = getCategories().toMutableList()
        categories.add(category)
        saveCategories(categories)
    }

    fun deleteCategory(categoryId: Long) {
        val categories = getCategories().filter { it.id != categoryId }
        saveCategories(categories)
        // Supprimer aussi les articles de cette catégorie
        val articlesBuffer = getArticles().filter { it.categoryId != categoryId }
        saveArticles(articlesBuffer)
    }

    private fun getDefaultCategories(): List<Category> {
        val defaults =
                listOf(
                        Category(id = 1, name = "Fruits et Légumes", iconName = "ic_category"),
                        Category(id = 2, name = "Viandes et Poissons", iconName = "ic_category"),
                        Category(id = 3, name = "Épicerie", iconName = "ic_category"),
                        Category(id = 4, name = "Grandes Surfaces", iconName = "ic_category")
                )
        saveCategories(defaults)
        return defaults
    }

    // ============ ARTICLES ============

    fun getArticles(): List<Article> {
        val json = prefs.getString(KEY_ARTICLES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Article>>() {}.type
            val articles: List<Article> = gson.fromJson(json, type)
            // Assurer que tous les articles ont un iconId valide
            articles.map { article ->
                if (article.iconId.isNullOrEmpty()) {
                    article.copy(iconId = "panier")
                } else {
                    article
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun getArticlesByCategory(categoryId: Long): List<Article> {
        return getArticles()
                .filter { it.categoryId == categoryId }
                .sortedWith(compareBy({ it.isBought }, { it.priority.displayOrder }))
    }

    fun getUnboughtCountByCategory(categoryId: Long): Int {
        return getArticles().count { it.categoryId == categoryId && !it.isBought }
    }

    fun saveArticles(articles: List<Article>) {
        prefs.edit().putString(KEY_ARTICLES, gson.toJson(articles)).apply()
    }

    fun addArticle(article: Article) {
        val articles = getArticles().toMutableList()
        articles.add(article)
        saveArticles(articles)
    }

    fun deleteArticle(articleId: Long) {
        val articles = getArticles().filter { it.id != articleId }
        saveArticles(articles)
    }

    fun toggleArticleBought(articleId: Long) {
        val articles = getArticles().toMutableList()
        val index = articles.indexOfFirst { it.id == articleId }
        if (index >= 0) {
            articles[index] = articles[index].copy(isBought = !articles[index].isBought)
            saveArticles(articles)
        }
    }

    fun updateArticlePriority(articleId: Long, priority: Priority) {
        val articles = getArticles().toMutableList()
        val index = articles.indexOfFirst { it.id == articleId }
        if (index >= 0) {
            articles[index] = articles[index].copy(priority = priority)
            saveArticles(articles)
        }
    }

    fun updateCategoryIcon(categoryId: Long, iconId: String) {
        val categories = getCategories().toMutableList()
        val index = categories.indexOfFirst { it.id == categoryId }
        if (index >= 0) {
            categories[index] = categories[index].copy(iconId = iconId)
            saveCategories(categories)
        }
    }

    fun updateArticleIcon(articleId: Long, iconId: String) {
        val articles = getArticles().toMutableList()
        val index = articles.indexOfFirst { it.id == articleId }
        if (index >= 0) {
            articles[index] = articles[index].copy(iconId = iconId)
            saveArticles(articles)
        }
    }

    fun updateArticleFrenchName(articleId: Long, frenchName: String) {
        val articles = getArticles().toMutableList()
        val index = articles.indexOfFirst { it.id == articleId }
        if (index >= 0) {
            articles[index] = articles[index].copy(frenchName = frenchName)
            saveArticles(articles)
        }
    }

    /** Mise à jour complète d'un article */
    fun updateArticle(article: Article) {
        val articles = getArticles().toMutableList()
        val index = articles.indexOfFirst { it.id == article.id }
        if (index >= 0) {
            articles[index] = article
            saveArticles(articles)
        }
    }

    /** Mise à jour complète d'une catégorie */
    fun updateCategory(category: Category) {
        val categories = getCategories().toMutableList()
        val index = categories.indexOfFirst { it.id == category.id }
        if (index >= 0) {
            categories[index] = category
            saveCategories(categories)
        }
    }

    // ============ EXPORT / IMPORT ============

    fun exportDatabase(): Pair<Boolean, String> {
        return try {
            val db = FullDatabase(getCategories(), getArticles())
            val json = gson.toJson(db)
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadDir, "shopping_list_backup.json")
            file.writeText(json)
            Pair(true, file.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.message ?: "Erreur inconnue")
        }
    }

    fun importDatabase(): Pair<Boolean, String> {
        return try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadDir, "shopping_list_backup.json")
            if (!file.exists()) return Pair(false, "Fichier shopping_list_backup.json non trouvé dans Downloads")
            
            val json = file.readText()
            val db = gson.fromJson(json, FullDatabase::class.java)
            
            saveCategories(db.categories)
            saveArticles(db.articles)
            
            Pair(true, "Données importées avec succès")
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, e.message ?: "Erreur inconnue")
        }
    }
}
