package fr.kmz.projects.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import fr.kmz.projects.data.model.Projet

object CsvManager {

    private const val HEADER = "Type,Projet,Date,Chapitre,Beneficiaire,Montant,Nature,Objet"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun exportToCsv(data: GithubBackupData): String {
        val lines = mutableListOf<String>()
        lines.add(HEADER)

        data.projets.forEach { projet ->
            lines.add("PROJET,${escapeCsv(projet.nom)},,,,,,")
        }

        data.chapitres.forEach { chapitre ->
            lines.add("CHAPITRE,${escapeCsv(chapitre.projetNom)},,${escapeCsv(chapitre.nom)},,,,")
        }

        data.beneficiaires.forEach { beneficiaire ->
            lines.add("BENEFICIAIRE,${escapeCsv(beneficiaire.projetNom)},,,${escapeCsv(beneficiaire.nom)},,,")
        }

        for (depense in data.depenses) {
            val projet = escapeCsv(depense.projetNom)
            val date = dateFormat.format(Date(depense.date))
            val chapitre = escapeCsv(depense.chapitreNom)
            val beneficiaire = escapeCsv(depense.beneficiaireNom)
            val montant = depense.montant
            val nature = escapeCsv(depense.nature)
            val objet = escapeCsv(depense.objet)
            lines.add("DEPENSE,$projet,$date,$chapitre,$beneficiaire,$montant,$nature,$objet")
        }
        return lines.joinToString("\n")
    }

    fun parseCsv(csvContent: String): ParsedData {
        val projetsSet = mutableSetOf<String>()
        val chapitresSet = mutableSetOf<String>()
        val beneficiairesSet = mutableSetOf<String>()
        val depenses = mutableListOf<ParsedDepense>()

        val lines = csvContent.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return ParsedData(emptyList(), emptyList(), emptyList(), emptyList())

        val header = smartSplit(lines.first()).map { it.trim().unescapeCsv().lowercase() }
        val hasTypeColumn = header.firstOrNull() in listOf("type", "ligne", "rowtype")
        val hasProjetColumn = hasTypeColumn || header.firstOrNull() in listOf("projet", "project")

        lineLoop@ for (i in 1 until lines.size) {
            val line = lines[i]
            val parts = smartSplit(line)
            val rowType = if (hasTypeColumn) parts.getOrNull(0)?.trim()?.unescapeCsv()?.uppercase() else "DEPENSE"
            val typeOffset = if (hasTypeColumn) 1 else 0
            val minColumns = if (hasProjetColumn) 6 else 5
            if (parts.size >= minColumns + typeOffset) {
                val offset = typeOffset + if (hasProjetColumn) 1 else 0
                val projetNom = if (hasProjetColumn) {
                    parts[typeOffset].trim().unescapeCsv().ifBlank { DEFAULT_PROJECT_NAME }
                } else {
                    DEFAULT_PROJECT_NAME
                }

                when (rowType) {
                    "PROJET" -> {
                        projetsSet.add(projetNom)
                        continue@lineLoop
                    }
                    "CHAPITRE" -> {
                        val chapitreNom = parts.getOrNull(offset + 1)?.trim()?.unescapeCsv().orEmpty()
                        if (chapitreNom.isNotBlank()) {
                            projetsSet.add(projetNom)
                            chapitresSet.add("$projetNom\u0000$chapitreNom")
                        }
                        continue@lineLoop
                    }
                    "BENEFICIAIRE" -> {
                        val beneficiaireNom = parts.getOrNull(offset + 2)?.trim()?.unescapeCsv().orEmpty()
                        if (beneficiaireNom.isNotBlank()) {
                            projetsSet.add(projetNom)
                            beneficiairesSet.add("$projetNom\u0000$beneficiaireNom")
                        }
                        continue@lineLoop
                    }
                }

                val dateStr = parts[offset].trim()
                val chapitreNom = parts[offset + 1].trim().unescapeCsv()
                val beneficiaireNom = parts[offset + 2].trim().unescapeCsv()
                val montantStr = parts[offset + 3].trim()
                val nature = parts[offset + 4].trim().unescapeCsv()
                val objet = parts.getOrNull(offset + 5)?.trim()?.unescapeCsv() ?: ""

                if (dateStr.isNotBlank() && chapitreNom.isNotBlank() && beneficiaireNom.isNotBlank()) {
                    val date = try {
                        dateFormat.parse(dateStr)?.time ?: System.currentTimeMillis()
                    } catch (e: Exception) {
                        System.currentTimeMillis()
                    }
                    val montant = montantStr.toLongOrNull() ?: 0L

                    projetsSet.add(projetNom)
                    chapitresSet.add("$projetNom\u0000$chapitreNom")
                    beneficiairesSet.add("$projetNom\u0000$beneficiaireNom")

                    depenses.add(
                        ParsedDepense(
                            projetNom = projetNom,
                            date = date,
                            chapitreNom = chapitreNom,
                            beneficiaireNom = beneficiaireNom,
                            montant = montant,
                            nature = nature,
                            objet = objet
                        )
                    )
                }
            }
        }

        return ParsedData(
            projets = projetsSet.map { Projet(nom = it) },
            chapitres = chapitresSet.map {
                val parts = it.split("\u0000", limit = 2)
                ParsedChapitre(projetNom = parts[0], nom = parts.getOrElse(1) { "" })
            },
            beneficiaires = beneficiairesSet.map {
                val parts = it.split("\u0000", limit = 2)
                ParsedBeneficiaire(projetNom = parts[0], nom = parts.getOrElse(1) { "" })
            },
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

data class GithubBackupData(
    val projets: List<Projet>,
    val chapitres: List<ChapitreWithProject>,
    val beneficiaires: List<BeneficiaireWithProject>,
    val depenses: List<DepenseWithNames>
)

data class ChapitreWithProject(
    val projetNom: String,
    val nom: String
)

data class BeneficiaireWithProject(
    val projetNom: String,
    val nom: String
)

data class DepenseWithNames(
    val projetNom: String,
    val date: Long,
    val chapitreNom: String,
    val beneficiaireNom: String,
    val montant: Long,
    val nature: String,
    val objet: String
)

data class ParsedDepense(
    val projetNom: String,
    val date: Long,
    val chapitreNom: String,
    val beneficiaireNom: String,
    val montant: Long,
    val nature: String,
    val objet: String
)

data class ParsedData(
    val projets: List<Projet>,
    val chapitres: List<ParsedChapitre>,
    val beneficiaires: List<ParsedBeneficiaire>,
    val depenses: List<ParsedDepense>
)

data class ParsedChapitre(
    val projetNom: String,
    val nom: String
)

data class ParsedBeneficiaire(
    val projetNom: String,
    val nom: String
)

private const val DEFAULT_PROJECT_NAME = "Projet par défaut"
