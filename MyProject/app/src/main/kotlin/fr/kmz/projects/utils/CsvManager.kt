package fr.kmz.projects.utils

import fr.kmz.projects.data.model.LotWithSousLots

object CsvManager {

    private const val HEADER = "Lot,Sous-Lot,Article,Quantite,Unite,Prix_Unitaire"

    fun exportToCsv(lots: List<LotWithSousLots>): String {
        val lines = mutableListOf<String>()
        lines.add(HEADER)

        for (lotWithSousLots in lots) {
            val lotName = escapeCsv(lotWithSousLots.lot.nom)

            if (lotWithSousLots.sousLots.isEmpty()) {
                lines.add("$lotName,,,,,")
                continue
            }

            for (sousLotWithArticles in lotWithSousLots.sousLots) {
                val sousLotName = escapeCsv(sousLotWithArticles.sousLot.nom)

                if (sousLotWithArticles.articles.isEmpty()) {
                    lines.add("$lotName,$sousLotName,,,,")
                    continue
                }

                for (article in sousLotWithArticles.articles) {
                    val articleName = escapeCsv(article.nom)
                    val quantite = article.quantite
                    val unite = escapeCsv(article.unite)
                    val prix = article.prixUnitaire

                    lines.add("$lotName,$sousLotName,$articleName,$quantite,$unite,$prix")
                }
            }
        }
        return lines.joinToString("\n")
    }

    // Le parseur inverse CSV -> Objets
    fun parseCsv(csvContent: String): List<CsvRow> {
        val rows = mutableListOf<CsvRow>()
        val lines = csvContent.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return rows

        val header = lines.first() // on ignore le header
        for (i in 1 until lines.size) {
            val line = lines[i]
            // Un split basique pour les CSV simples qui ne contiennent pas de virgules dans les
            // valeurs
            // Pour un parseur plus robuste, il faudrait utiliser une librairie ou une regex
            // complexe
            val parts = line.split(",")
            if (parts.isNotEmpty()) {
                val lotName = parts.getOrNull(0)?.trim()?.removeSurrounding("\"") ?: ""
                val sousLotName = parts.getOrNull(1)?.trim()?.removeSurrounding("\"") ?: ""
                val articleName = parts.getOrNull(2)?.trim()?.removeSurrounding("\"") ?: ""
                val quantiteStr = parts.getOrNull(3)?.trim() ?: "0"
                val unite = parts.getOrNull(4)?.trim()?.removeSurrounding("\"") ?: ""
                val prixStr = parts.getOrNull(5)?.trim() ?: "0"

                if (lotName.isNotBlank()) {
                    rows.add(
                            CsvRow(
                                    lotName = lotName,
                                    sousLotName = sousLotName,
                                    articleName = articleName,
                                    quantite = quantiteStr.toIntOrNull() ?: 1,
                                    unite = unite,
                                    prixUnitaire = prixStr.toLongOrNull() ?: 0L
                            )
                    )
                }
            }
        }
        return rows
    }

    private fun escapeCsv(text: String): String {
        return if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            "\"${text.replace("\"", "\"\"")}\""
        } else {
            text
        }
    }
}

data class CsvRow(
        val lotName: String,
        val sousLotName: String,
        val articleName: String,
        val quantite: Int,
        val unite: String,
        val prixUnitaire: Long
)
