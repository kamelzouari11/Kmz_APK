package com.kmz.shazamplayer.util

import com.kmz.shazamplayer.model.Track
import java.io.InputStream
import java.util.Locale

/** Reads the official privacy export, minimal date/artist/title CSV and legacy library export. */
object CsvParser {
    fun parse(inputStream: InputStream): List<Track> {
        val rows = inputStream.bufferedReader(Charsets.UTF_8).use { records(it.readText()) }
                .filter { row -> row.any { it.isNotBlank() } }
                .dropWhile { it.size == 1 && it[0].trim().equals("Shazam Library", true) }
        require(rows.isNotEmpty()) { "Le fichier CSV est vide." }
        val header = rows.first().map { it.trim().removePrefix("\uFEFF").lowercase(Locale.ROOT) }
        fun column(vararg names: String) = header.indexOfFirst { it in names }
        val date = column("date", "tagtime")
        val artist = column("artist", "artiste")
        val title = column("title", "titre")
        require(date >= 0 && artist >= 0 && title >= 0) {
            "CSV non reconnu : colonnes date, artist et title requises (SyncedSongs.csv)."
        }
        val index = column("index")
        val url = column("url")
        val key = column("trackkey")
        val tracks = rows.drop(1).mapIndexed { position, row ->
            require(row.size == header.size) { "Ligne ${position + 2} : nombre de colonnes incorrect." }
            fun value(i: Int) = row.getOrNull(i)?.trim().orEmpty().takeUnless { it == "N/A" }.orEmpty()
            require(value(artist).isNotEmpty() && value(title).isNotEmpty() &&
                    DATE.matches(value(date))) {
                "Ligne ${position + 2} : artiste, titre ou date invalide (date attendue : AAAA-MM-JJ)."
            }
            Track(
                index = value(index).ifEmpty { (position + 1).toString() },
                tagTime = value(date), title = value(title), artist = value(artist),
                shazamUrl = value(url), trackKey = value(key)
            )
        }
        require(tracks.isNotEmpty()) { "Le CSV ne contient aucun morceau." }
        return tracks
    }

    private val DATE = Regex("\\d{4}-\\d{2}-\\d{2}(?:[T ].*)?")

    // Quoted fields may contain commas, escaped quotes and line breaks.
    private fun records(text: String): List<List<String>> {
        val result = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' && quoted && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { row.add(field.toString()); field.setLength(0) }
                (c == '\n' || c == '\r') && !quoted -> {
                    row.add(field.toString()); field.setLength(0); result.add(row); row = mutableListOf()
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                }
                else -> field.append(c)
            }
            i++
        }
        require(!quoted) { "CSV invalide : guillemets non fermés." }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); result.add(row) }
        return result
    }
}
