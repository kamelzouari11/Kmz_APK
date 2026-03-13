package fr.kmz.projects.utils

import java.text.NumberFormat
import java.util.Locale

object FormattingUtils {
    private val frenchLocale = Locale("fr", "FR")
    // We'll format currency values and append the Tunisian dinar symbol 'dt'

    /**
     * Formate un montant en format français avec espace comme séparateur de milliers Ex: 1500 -> "1
     * 500 dt"
     */
    fun formatCurrency(amount: Long): String {
        val formatter = NumberFormat.getNumberInstance(frenchLocale)
        formatter.minimumFractionDigits = 0
        formatter.maximumFractionDigits = 0
        formatter.isGroupingUsed = true
        return "${formatter.format(amount)} dt"
    }

    /**
     * Formate un montant en entier avec espace comme séparateur de milliers Ex: 15000 -> "15 000"
     */
    fun formatNumber(number: Long): String {
        val formatter = NumberFormat.getInstance(frenchLocale)
        formatter.isGroupingUsed = true
        return formatter.format(number)
    }

    /** Format a whole amount (no decimals) in dinars */
    fun formatCurrencyNoDecimals(amount: Long): String = formatCurrency(amount)

    /** Convertir une chaîne de caractères en entier */
    fun stringToCentimes(value: String): Long {
        return try {
            val normalized =
                    value.trim()
                            .replace("\u00A0", "") // non-breaking space
                            .replace(" ", "")
                            .replace("\u202F", "") // thin space
                            .replace(',', '.')
            normalized.toDouble().toLong()
        } catch (e: Exception) {
            0L
        }
    }
}
