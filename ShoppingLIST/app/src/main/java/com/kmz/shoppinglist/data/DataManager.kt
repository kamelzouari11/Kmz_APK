package com.kmz.shoppinglist.data

import android.content.Context
import android.os.Environment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/** Structure pour l'export/import complet */
data class FullDatabase(val categories: List<Category>, val articles: List<Article>)

/** Gestion de la persistance des données avec SharedPreferences et JSON */
class DataManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("shopping_list_data", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_CATEGORIES = "categories"
        private const val KEY_ARTICLES = "articles"
        private const val KEY_ICON_MODE = "ui_icon_mode"
        private const val KEY_FILTER_PRIORITY = "ui_filter_priority"
        private const val KEY_ALL_EXPANDED = "ui_all_expanded"
        private const val KEY_BOUGHT_EXPANDED = "ui_bought_expanded"
    }

    // ============ UI STATE ============

    fun getIconMode(): Boolean = prefs.getBoolean(KEY_ICON_MODE, false)
    fun setIconMode(enabled: Boolean) = prefs.edit().putBoolean(KEY_ICON_MODE, enabled).apply()

    fun getFilterPriority(): Priority {
        val name = prefs.getString(KEY_FILTER_PRIORITY, Priority.OPTIONAL.name)
        return try {
            Priority.valueOf(name!!)
        } catch (e: Exception) {
            Priority.OPTIONAL
        }
    }

    fun setFilterPriority(priority: Priority) =
            prefs.edit().putString(KEY_FILTER_PRIORITY, priority.name).apply()

    fun getAllExpanded(): Boolean = prefs.getBoolean(KEY_ALL_EXPANDED, false)
    fun setAllExpanded(expanded: Boolean) =
            prefs.edit().putBoolean(KEY_ALL_EXPANDED, expanded).apply()

    fun getBoughtExpanded(): Boolean = prefs.getBoolean(KEY_BOUGHT_EXPANDED, false)
    fun setBoughtExpanded(expanded: Boolean) =
            prefs.edit().putBoolean(KEY_BOUGHT_EXPANDED, expanded).apply()

    fun getExpandedCategoryIds(): Set<Long> {
        val json = prefs.getString("ui_expanded_categories", "[]")
        return try {
            val type = object : TypeToken<Set<Long>>() {}.type
            gson.fromJson(json, type) ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun setExpandedCategoryIds(ids: Set<Long>) {
        prefs.edit().putString("ui_expanded_categories", gson.toJson(ids)).apply()
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
            val oldItem = articles[index]
            val newIsBought = !oldItem.isBought
            // Si on repasse l'article en "à acheter" (newIsBought == false), on reset la priorité à
            // NORMAL
            val newPriority = if (!newIsBought) Priority.NORMAL else oldItem.priority

            articles[index] = oldItem.copy(isBought = newIsBought, priority = newPriority)
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
            val downloadDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
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
            val downloadDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadDir, "shopping_list_backup.json")
            if (!file.exists())
                    return Pair(
                            false,
                            "Fichier shopping_list_backup.json non trouvé dans Downloads"
                    )

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
