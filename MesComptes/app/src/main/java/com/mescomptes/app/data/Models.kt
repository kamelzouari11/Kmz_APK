package com.mescomptes.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.util.Locale

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val holder: String,
    val cin: String,
    val bank: String,
    val rib: String,
    val number: String,
) {
    val displayName: String
        get() {
            val suffix = number.filter(Char::isLetterOrDigit).takeLast(4)
            return if (suffix.isBlank()) "$bank · $holder" else "$bank · ••••$suffix"
        }

    /** Compact identity used when choosing an account for a movement. */
    val movementDisplayName: String
        get() {
            val bankName = bank.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
            val ribSuffix = rib.filter(Char::isDigit).takeLast(8)
            return buildList {
                holder.trim().takeIf(String::isNotEmpty)?.let { add(it) }
                bankName.takeIf(String::isNotEmpty)?.let { add(it) }
                ribSuffix.takeIf(String::isNotEmpty)?.let { add(it) }
            }.joinToString(" · ").ifBlank { displayName }
        }
}

enum class OrderType(val label: String, val journalLabel: String, val addsToPosition: Boolean) {
    ACHAT("Achat", "Acht", true),
    SOUSCRIPTION("Souscription", "Sscp", true),
    VENTE("Vente", "Vte", false),
    RACHAT("Rachat", "Rcht", false),
}

@Entity(
    tableName = "movements",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("accountId"), Index(value = ["accountId", "titleKey"])],
)
data class MovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    /** Epoch day: independent of timezone and ideal for a bank operation date. */
    val operationDate: Long,
    val title: String,
    val titleKey: String = normalizedTitleKey(title),
    val orderType: OrderType,
    val quantity: Int,
    /** Normalized decimal text avoids floating-point rounding of financial values. */
    val unitPrice: String? = null,
)

data class PortfolioPosition(
    val accountId: Long,
    val title: String,
    val quantity: Int,
    val lastKnownUnitPrice: BigDecimal?,
) {
    val indicativeValue: BigDecimal?
        get() = lastKnownUnitPrice?.multiply(quantity.toBigDecimal())
}

fun calculatePositions(movements: List<MovementEntity>): List<PortfolioPosition> =
    movements
        .groupBy { it.accountId to it.titleKey }
        .map { (_, operations) ->
            val lastKnownUnitPrice = operations
                .asSequence()
                .filter { !it.unitPrice.isNullOrBlank() }
                .maxWithOrNull(compareBy<MovementEntity> { it.operationDate }.thenBy { it.id })
                ?.unitPrice
                ?.let { runCatching { BigDecimal(it) }.getOrNull() }
            PortfolioPosition(
                accountId = operations.first().accountId,
                title = operations.first().title.trim(),
                quantity = operations.sumOf {
                    if (it.orderType.addsToPosition) it.quantity else -it.quantity
                },
                lastKnownUnitPrice = lastKnownUnitPrice,
            )
        }
        .filter { it.quantity != 0 }
        .sortedBy { it.title.lowercase() }

fun normalizedTitleKey(title: String): String = title.trim().lowercase(Locale.ROOT)
