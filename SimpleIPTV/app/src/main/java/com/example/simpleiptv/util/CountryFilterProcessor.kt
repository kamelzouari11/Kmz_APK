package com.example.simpleiptv.util

import com.example.simpleiptv.data.local.entities.CategoryEntity

object CountryFilterProcessor {
    /**
     * Extrait les préfixes de pays depuis une liste de catégories.
     * Ex: "France TF1" → "FRAN", "France M6" → "FRAN", "UK BBC" → "UK  "
     * Retourne ["ALL"] + préfixes distincts triés.
     */
    fun computeCountryFilters(categories: List<CategoryEntity>): List<String> {
        val groups = categories
            .mapNotNull { cat ->
                val name = cat.category_name.trim()
                if (name.startsWith("-") || name.isEmpty()) return@mapNotNull null

                val spaceIndex = name.indexOf(' ')
                val length = if (spaceIndex in 1..4) spaceIndex else minOf(4, name.length)
                if (length > 0) name.substring(0, length).uppercase() else null
            }
            .distinct()
            .filter { it != "ALL" }

        return listOf("ALL") + groups
    }
}
