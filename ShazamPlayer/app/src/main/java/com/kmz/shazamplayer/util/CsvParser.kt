package com.kmz.shazamplayer.util

import com.kmz.shazamplayer.model.Track
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object CsvParser {
    fun parse(inputStream: InputStream): List<Track> {
        val tracks = mutableListOf<Track>()
        val reader = BufferedReader(InputStreamReader(inputStream))

        var line: String?
        var lineCount = 0

        while (reader.readLine().also { line = it } != null) {
            lineCount++
            val currentLine = line ?: continue

            // Log pour débogage (sera visible dans Logcat)
            android.util.Log.d("CsvParser", "Line $lineCount: $currentLine")

            // Ignorer l'en-tête "Shazam Library" ou la ligne vide
            if (currentLine.contains("Shazam Library", ignoreCase = true) ||
                            currentLine.trim().isEmpty()
            ) {
                continue
            }

            // Ignorer la ligne des colonnes "Index,TagTime,Title,Artist,URL,TrackKey"
            if (currentLine.contains("Index,TagTime", ignoreCase = true)) {
                continue
            }

            val parts = parseCsvLine(currentLine)
            if (parts.size >= 6) {
                try {
                    tracks.add(
                            Track(
                                    index = parts[0].trim(),
                                    tagTime = parts[1].trim(),
                                    title = parts[2].trim(),
                                    artist = parts[3].trim(),
                                    shazamUrl = parts[4].trim(),
                                    trackKey = parts[5].trim()
                            )
                    )
                } catch (e: Exception) {
                    android.util.Log.e("CsvParser", "Error parsing line $lineCount: ${e.message}")
                }
            }
        }
        android.util.Log.d("CsvParser", "Total tracks parsed: ${tracks.size}")
        return tracks
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var curVal = StringBuilder()
        var inQuotes = false

        for (ch in line.toCharArray()) {
            if (inQuotes) {
                if (ch == '\"') {
                    inQuotes = false
                } else {
                    curVal.append(ch)
                }
            } else {
                if (ch == '\"') {
                    inQuotes = true
                } else if (ch == ',') {
                    result.add(curVal.toString())
                    curVal = StringBuilder()
                } else {
                    curVal.append(ch)
                }
            }
        }
        result.add(curVal.toString())
        return result
    }
}
