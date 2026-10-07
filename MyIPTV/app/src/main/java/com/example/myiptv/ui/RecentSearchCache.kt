package com.example.myiptv.ui

import android.content.Context

internal object RecentSearchCache {
    private const val separator = "\u001F"
    private const val limit = 8

    fun load(context: Context, key: String): List<String> = context
        .getSharedPreferences("recent_searches", Context.MODE_PRIVATE)
        .getString(key, "")
        .orEmpty()
        .split(separator)
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinctBy(String::lowercase)
        .take(limit)

    fun add(context: Context, key: String, value: String): List<String> {
        val normalized = value.trim().replace(Regex("\\s+"), " ")
        if (normalized.isEmpty()) return load(context, key)
        val values = (listOf(normalized) + load(context, key)
            .filterNot { it.equals(normalized, ignoreCase = true) })
            .take(limit)
        context.getSharedPreferences("recent_searches", Context.MODE_PRIVATE)
            .edit()
            .putString(key, values.joinToString(separator))
            .apply()
        return values
    }
}
