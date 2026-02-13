package com.kmz.shoppinglist.data

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
