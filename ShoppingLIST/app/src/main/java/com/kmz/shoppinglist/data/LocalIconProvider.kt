package com.kmz.shoppinglist.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import java.io.File
import java.net.URL
import kotlin.concurrent.thread

/** Fournisseur d'icônes stockées sur le stockage externe. */
class LocalIconProvider(private val context: Context) {

    companion object {
        private const val TAG = "LocalIconProvider"
        private val ICONS_PATH =
                File(Environment.getExternalStorageDirectory(), "ShoppingListIcons")
    }

    init {
        try {
            if (!ICONS_PATH.exists()) ICONS_PATH.mkdirs()
        } catch (e: Exception) {
            Log.e(TAG, "Erreur initialisation: ${e.message}")
        }
    }

    fun getAllIcons(): List<String> {
        return try {
            if (!ICONS_PATH.exists()) return emptyList()
            val files =
                    ICONS_PATH.listFiles { file ->
                        val name = file.name.lowercase()
                        name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".jpg")
                    }
                            ?: return emptyList()
            files.map { it.name.substringBeforeLast(".") }.distinct().sorted()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun searchIcons(query: String): List<String> =
            getAllIcons().filter { it.lowercase().contains(query.lowercase().trim()) }

    fun getIconPath(iconId: String): String? {
        val files = ICONS_PATH.listFiles() ?: return null
        return files
                .find { it.name.substringBeforeLast(".").equals(iconId, ignoreCase = true) }
                ?.absolutePath
    }

    fun saveIconFromUri(uri: Uri, frenchName: String): Pair<String?, String> {
        try {
            val normalizedName = normalizeName(frenchName)
            val mimeType = context.contentResolver.getType(uri)
            val ext =
                    if (mimeType?.contains("webp") == true) "webp"
                    else if (mimeType?.contains("jpg") == true) "jpg" else "png"
            val targetFile = File(ICONS_PATH, "$normalizedName.$ext")

            val inputStream =
                    context.contentResolver.openInputStream(uri) ?: return Pair(null, "Flux vide")
            val outputStream = java.io.FileOutputStream(targetFile)
            inputStream.copyTo(outputStream)
            outputStream.close()
            inputStream.close()

            return Pair(normalizedName, "OK")
        } catch (e: Exception) {
            return Pair(null, e.message ?: "Erreur")
        }
    }

    /**
     * TÉLÉCHARGEMENT DIRECT DEPUIS UNE URL Note: Doit être appelé depuis un thread séparé
     * idéalement, mais on va gérer l'ouverture de stream ici.
     */
    fun saveIconFromUrl(
            urlStr: String,
            frenchName: String,
            onResult: (Pair<String?, String>) -> Unit
    ) {
        thread {
            try {
                val normalizedName = normalizeName(frenchName)
                val ext =
                        when {
                            urlStr.lowercase().contains(".webp") -> "webp"
                            urlStr.lowercase().contains(".jpg") ||
                                    urlStr.lowercase().contains(".jpeg") -> "jpg"
                            else -> "png"
                        }
                val targetFile = File(ICONS_PATH, "$normalizedName.$ext")

                val connection = URL(urlStr).openConnection()
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")

                val inputStream = connection.getInputStream()
                val outputStream = java.io.FileOutputStream(targetFile)
                inputStream.copyTo(outputStream)
                outputStream.close()
                inputStream.close()

                onResult(Pair(normalizedName, "Téléchargé avec succès"))
            } catch (e: Exception) {
                onResult(Pair(null, "Erreur lien: ${e.message}"))
            }
        }
    }

    private fun normalizeName(name: String) =
            name.lowercase()
                    .trim()
                    .replace(Regex("[^a-z0-9àâäéèêëïîôùûüç_-]"), "_")
                    .replace(Regex("_+"), "_")
                    .trimEnd('_')
                    .ifBlank { "icon_${System.currentTimeMillis()}" }

    /**
     * Supprime le fichier icône [iconId] uniquement s'il n'est plus référencé par aucun article ni
     * aucune catégorie. Les icônes par défaut ("panier", "categorie") ne sont jamais supprimées.
     *
     * @return true si le fichier a été supprimé, false sinon.
     */
    fun deleteIconIfOrphan(
            iconId: String,
            allArticles: List<Article>,
            allCategories: List<Category>
    ): Boolean {
        // Protéger les icônes par défaut
        val protected = setOf("panier", "categorie")
        if (iconId.isBlank() || iconId in protected) return false

        // Vérifier qu'aucun article ni catégorie n'utilise encore cet iconId
        val usedByArticle = allArticles.any { it.iconId == iconId }
        val usedByCategory = allCategories.any { it.iconId == iconId }
        if (usedByArticle || usedByCategory) return false

        // Chercher le fichier correspondant (toutes extensions)
        val files = ICONS_PATH.listFiles() ?: return false
        val target =
                files.find { it.name.substringBeforeLast(".").equals(iconId, ignoreCase = true) }
        return if (target != null && target.exists()) {
            val deleted = target.delete()
            Log.d(
                    TAG,
                    if (deleted) "🗑️ Icône orpheline supprimée : ${target.name}"
                    else "⚠️ Échec suppression : ${target.name}"
            )
            deleted
        } else false
    }

    fun getIconsDirectory(): File = ICONS_PATH

    fun getIconsDirectoryPath(): String = ICONS_PATH.absolutePath
}
