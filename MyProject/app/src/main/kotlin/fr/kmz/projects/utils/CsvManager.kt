package fr.kmz.projects.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import fr.kmz.projects.data.model.Beneficiaire
import fr.kmz.projects.data.model.Chapitre
import fr.kmz.projects.data.model.Depense

object CsvManager {

    private const val HEADER = "Date,Chapitre,Beneficiaire,Montant,Nature"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun exportToCsv(depenses: List<DepenseWithNames>): String {
        val lines = mutableListOf<String>()
        lines.add(HEADER)

        for (depense in depenses) {
            val date = dateFormat.format(Date(depense.date))
            val chapitre = escapeCsv(depense.chapitreNom)
            val beneficiaire = escapeCsv(depense.beneficiaireNom)
            val montant = depense.montant
            val nature = escapeCsv(depense.nature)
            lines.add("$date,$chapitre,$beneficiaire,$montant,$nature")
        }
        return lines.joinToString("\n")
    }

    fun parseCsv(csvContent: String): ParsedData {
        val chapitresMap = mutableMapOf<String, Chapitre>()
        val beneficiairesMap = mutableMapOf<String, Beneficiaire>()
        val depenses = mutableListOf<Depense>()

        val lines = csvContent.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return ParsedData(emptyList(), emptyList(), emptyList())

        for (i in 1 until lines.size) {
            val line = lines[i]
            val parts = smartSplit(line)
            if (parts.size >= 5) {
                val dateStr = parts[0].trim()
                val chapitreNom = parts[1].trim().unescapeCsv()
                val beneficiaireNom = parts[2].trim().unescapeCsv()
                val montantStr = parts[3].trim()
                val nature = parts[4].trim().unescapeCsv()

                if (dateStr.isNotBlank() && chapitreNom.isNotBlank() && beneficiaireNom.isNotBlank()) {
                    val date = try {
                        dateFormat.parse(dateStr)?.time ?: System.currentTimeMillis()
                    } catch (e: Exception) {
                        System.currentTimeMillis()
                    }
                    val montant = montantStr.toLongOrNull() ?: 0L

                    // Create or reuse chapitre
                    val chapitre = chapitresMap.getOrPut(chapitreNom) {
                        Chapitre(nom = chapitreNom)
                    }

                    // Create or reuse beneficiaire
                    val beneficiaire = beneficiairesMap.getOrPut(beneficiaireNom) {
                        Beneficiaire(nom = beneficiaireNom)
                    }

                    // Create depense (IDs will be reassigned by Room on insert)
                    depenses.add(
                        Depense(
                            date = date,
                            chapitreId = chapitre.id,
                            beneficiaireId = beneficiaire.id,
                            montant = montant,
                            nature = nature
                        )
                    )
                }
            }
        }

        return ParsedData(
            chapitres = chapitresMap.values.toList(),
            beneficiaires = beneficiairesMap.values.toList(),
            depenses = depenses
        )
    }

    private fun escapeCsv(text: String): String {
        return if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            "\"${text.replace("\"", "\"\"")}\""
        } else {
            text
        }
    }

    private fun String.unescapeCsv(): String {
        return if (startsWith("\"") && endsWith("\"")) {
            substring(1, length - 1).replace("\"\"", "\"")
        } else {
            this
        }
    }

    private fun smartSplit(line: String): List<String> {
        val parts = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && (i == 0 || line[i - 1] != '\\') -> {
                    inQuotes = !inQuotes
                    current.append(c)
                }
                c == ',' && !inQuotes -> {
                    parts.add(current.toString())
                    current = StringBuilder()
                }
                else -> current.append(c)
            }
            i++
        }
        parts.add(current.toString())
        return parts
    }
}

data class DepenseWithNames(
    val date: Long,
    val chapitreNom: String,
    val beneficiaireNom: String,
    val montant: Long,
    val nature: String
)

data class ParsedData(
    val chapitres: List<Chapitre>,
    val beneficiaires: List<Beneficiaire>,
    val depenses: List<Depense>
)
