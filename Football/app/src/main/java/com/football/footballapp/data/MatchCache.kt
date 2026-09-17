package com.football.footballapp.data

import android.util.Log
import com.football.footballapp.data.model.Match
import com.squareup.moshi.Types
import java.io.File

/**
 * Cache disque des matchs par date.
 * Persistant entre lancements de l'app : une journée en cache peut être affichée
 * immédiatement pendant que la date courante est actualisée en arrière-plan.
 */
class MatchCache(rootDir: File) {
    private val dir: File = File(rootDir, "matches").apply { mkdirs() }
    private val adapter = Network.moshi.adapter<List<Match>>(
        Types.newParameterizedType(List::class.java, Match::class.java)
    )

    @Synchronized
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

    /** Charge toutes les journées JSON disponibles pour la recherche locale. */
    fun loadAll(): Map<String, List<Match>> = dir.listFiles()
        .orEmpty()
        .asSequence()
        .filter { it.isFile && CACHE_FILE.matches(it.name) }
        .sortedBy { it.name }
        .mapNotNull { file ->
            val date = file.name.removeSuffix(".json")
            load(date)?.let { matches -> date to matches }
        }
        .toMap()

    @Synchronized
    fun save(date: String, matches: List<Match>) {
        try {
            val target = fileFor(date)
            val temporary = File(dir, "$date.json.tmp")
            temporary.writeText(adapter.toJson(matches))
            if (!temporary.renameTo(target)) {
                target.writeText(temporary.readText())
                temporary.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "save $date failed: ${e.message}")
        }
    }

    @Synchronized
    fun remove(date: String) {
        fileFor(date).delete()
        File(dir, "$date.json.tmp").delete()
    }

    @Synchronized
    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun fileFor(date: String) = File(dir, "$date.json")

    companion object {
        private const val TAG = "MatchCache"
        private val CACHE_FILE = Regex("\\d{4}-\\d{2}-\\d{2}\\.json")
    }
}
