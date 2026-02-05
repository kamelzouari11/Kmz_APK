package com.example.simpleradio.data

import android.content.Context
import com.example.simpleradio.data.model.RadioCountry
import com.example.simpleradio.data.model.RadioTag
import java.io.BufferedReader
import java.io.InputStreamReader

/** Classe pour charger les listes de pays et genres depuis les fichiers CSV locaux */
class CsvDataLoader(private val context: Context) {

    /** Charge la liste des pays depuis pays.csv Format CSV: Nom,Code ISO,Nombre de Stations,Rang */
    fun loadCountries(): List<RadioCountry> {
        val countries = mutableListOf<RadioCountry>()

        try {
            context.assets.open("pays.csv").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    // Ignorer la première ligne (en-tête)
                    reader.readLine()

                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let { csvLine ->
                            if (csvLine.isNotBlank()) {
                                val parts = csvLine.split(",")
                                if (parts.size >= 2) {
                                    countries.add(
                                            RadioCountry(
                                                    name = parts[0].trim(),
                                                    iso_3166_1 = parts[1].trim(),
                                                    stationcount =
                                                            parts.getOrNull(2)
                                                                    ?.trim()
                                                                    ?.toIntOrNull()
                                                                    ?: 0
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return countries
    }

    /** Charge la liste des genres depuis genres.csv Format CSV: Genre,Nombre de Stations */
    fun loadGenres(): List<RadioTag> {
        val genres = mutableListOf<RadioTag>()

        try {
            context.assets.open("genres.csv").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    // Ignorer la première ligne (en-tête)
                    reader.readLine()

                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let { csvLine ->
                            if (csvLine.isNotBlank()) {
                                val parts = csvLine.split(",")
                                if (parts.isNotEmpty()) {
                                    genres.add(
                                            RadioTag(
                                                    name = parts[0].trim(),
                                                    stationcount =
                                                            parts.getOrNull(1)
                                                                    ?.trim()
                                                                    ?.toIntOrNull()
                                                                    ?: 0
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return genres
    }
}
