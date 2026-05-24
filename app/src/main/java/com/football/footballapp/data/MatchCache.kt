package com.football.footballapp.data

import android.util.Log
import com.football.footballapp.data.model.Match
import com.squareup.moshi.Types
import java.io.File

/**
 * Cache disque des matchs par date.
 * Persistant entre lancements de l'app → permet de N'JAMAIS faire de requête réseau
 * automatique : on ne sync que sur action explicite (refresh manuel).
 */
class MatchCache(rootDir: File) {
    private val dir: File = File(rootDir, "matches").apply { mkdirs() }
    private val adapter = Network.moshi.adapter<List<Match>>(
        Types.newParameterizedType(List::class.java, Match::class.java)
    )

    fun load(date: String): List<Match>? {
        val file = fileFor(date)
        if (!file.exists()) return null
        return try {
            adapter.fromJson(file.readText())
        } catch (e: Exception) {
            Log.w(TAG, "load $date failed: ${e.message}")
            null
        }
    }

    fun save(date: String, matches: List<Match>) {
        try {
            fileFor(date).writeText(adapter.toJson(matches))
        } catch (e: Exception) {
            Log.w(TAG, "save $date failed: ${e.message}")
        }
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun fileFor(date: String) = File(dir, "$date.json")

    companion object {
        private const val TAG = "MatchCache"
    }
}
