package fr.kmz.projects.utils

import java.text.NumberFormat
import java.util.Locale

object FormattingUtils {
    private val frenchLocale = Locale("fr", "FR")
    // We'll format currency values and append the Tunisian dinar symbol 'dt'

    /**
     * Formate un montant en centimes en format français avec espace comme séparateur de milliers
     * Ex: 1500000 -> "15 000,00 dt"
     */
    fun formatCurrency(amountInCentimes: Long): String {
        val value = amountInCentimes / 100.0
        val formatter = NumberFormat.getNumberInstance(frenchLocale)
        formatter.minimumFractionDigits = 2
        formatter.maximumFractionDigits = 2
        formatter.isGroupingUsed = true
        return "${formatter.format(value)} dt"
    }

    /**
     * Formate un montant en entier avec espace comme séparateur de milliers
     * Ex: 15000 -> "15 000"
     */
    fun formatNumber(number: Long): String {
        val formatter = NumberFormat.getInstance(frenchLocale)
        formatter.isGroupingUsed = true
        return formatter.format(number)
    }

    /**
     * Format a whole amount (no decimals) in dinars
     */
    fun formatCurrencyNoDecimals(amountInCentimes: Long): String {
        val dinars = amountInCentimes / 100
        val formatter = NumberFormat.getIntegerInstance(frenchLocale)
        formatter.isGroupingUsed = true
        return "${formatter.format(dinars)} dt"
    }

    /**
     * Convertir une chaîne de caractères en centimes
     * Ex: "150.50" -> 15050
     */
    fun stringToCentimes(value: String): Long {
        return try {
            val normalized = value.trim()
                .replace("\u00A0", "") // non-breaking space
                .replace(" ", "")
                .replace("\u202F", "") // thin space
                .replace(',', '.')
            val amount = normalized.toDouble()
            kotlin.math.round(amount * 100).toLong()
        } catch (e: Exception) {
            0L
        }
    }
}
