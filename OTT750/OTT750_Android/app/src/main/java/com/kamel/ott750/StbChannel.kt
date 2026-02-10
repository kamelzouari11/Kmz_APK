package com.kamel.ott750

/**
 * Représente une chaîne TV du récepteur STB.
 * Format basé sur le protocole gmscreen.
 */
data class StbChannel(
    val programId: String,           // ID unique (14 chiffres): "00050029700001"
    val name: String,                // Nom de la chaîne
    val programIndex: Int,           // Position dans la liste
    val programType: Int,            // 0=Radio, 1=TV
    val isHD: Boolean,               // Qualité HD
    var favMark: Int,                // Masque de bits des favoris (0-63 pour 6 groupes)
    var favorGroupIds: MutableSet<Int>, // Set des IDs de groupes favoris
    val isLocked: Boolean,           // Chaîne verrouillée
    val channelType: Int,            // Type de chaîne
    val provider: String,            // Nom du fournisseur (ex: Canal+, Sky)
    var isSelected: Boolean = false  // Pour la sélection UI
) : java.io.Serializable {
    /**
     * Retourne true si la chaîne appartient au groupe favori spécifié
     */
    fun isInFavoriteGroup(groupId: Int): Boolean {
        return favorGroupIds.contains(groupId)
    }
    
    /**
     * Ajoute la chaîne à un groupe favori
     */
    fun addToFavoriteGroup(groupId: Int) {
        favorGroupIds.add(groupId)
        updateFavMark()
    }
    
    /**
     * Retire la chaîne d'un groupe favori
     */
    fun removeFromFavoriteGroup(groupId: Int) {
        favorGroupIds.remove(groupId)
        updateFavMark()
    }
    
    /**
     * Met à jour le favMark basé sur les groupes
     */
    private fun updateFavMark() {
        favMark = if (favorGroupIds.isEmpty()) 0 else {
            favorGroupIds.fold(0) { acc, id -> acc or (1 shl (id - 1)) }
        }
    }
    
    /**
     * Génère le FavorGroupID au format STB ("2:1:" pour groupes 1 et 2)
     */
    fun getFavorGroupIdString(): String {
        return if (favorGroupIds.isEmpty()) "" else {
            favorGroupIds.joinToString(":") + ":"
        }
    }
    
    companion object {
        /**
         * Parse le FavorGroupID du STB ("2:1:" -> Set(1, 2))
         */
        fun parseFavorGroupIds(str: String): MutableSet<Int> {
            if (str.isBlank()) return mutableSetOf()
            return str.split(":").mapNotNull { it.toIntOrNull() }.toMutableSet()
        }
    }
}

/**
 * Groupe de favoris
 */
data class FavoriteGroup(
    val id: Int,
    val name: String
) : java.io.Serializable
