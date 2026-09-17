package com.mescomptes.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class PortfolioCalculatorTest {
    @Test
    fun `achats et souscriptions ajoutent, ventes et rachats retirent`() {
        val movements = listOf(
            movement(OrderType.ACHAT, 100),
            movement(OrderType.SOUSCRIPTION, 20),
            movement(OrderType.VENTE, 15),
            movement(OrderType.RACHAT, 5),
        )

        assertEquals(100, calculatePositions(movements).single().quantity)
    }

    @Test
    fun `une position soldee disparait du portefeuille`() {
        val movements = listOf(movement(OrderType.ACHAT, 10), movement(OrderType.VENTE, 10))

        assertEquals(emptyList<PortfolioPosition>(), calculatePositions(movements))
    }

    @Test
    fun `la valeur indicative utilise le dernier prix connu`() {
        val movements = listOf(
            movement(OrderType.ACHAT, 12, operationDate = 1, unitPrice = "2.10"),
            movement(OrderType.VENTE, 2, operationDate = 2, unitPrice = "2.55"),
            movement(OrderType.ACHAT, 1, operationDate = 3),
        )

        val position = calculatePositions(movements).single()

        assertEquals(BigDecimal("2.55"), position.lastKnownUnitPrice)
        assertEquals(BigDecimal("28.05"), position.indicativeValue)
    }

    @Test
    fun `le libelle mouvement montre titulaire banque et fin du RIB`() {
        val account = AccountEntity(
            holder = "Kamel Ben Salah",
            cin = "12345678",
            bank = "Banque de Tunisie",
            rib = "10 006 03512345678901 89",
            number = "123456",
        )

        assertEquals("Kamel Ben Salah · Banque · 67890189", account.movementDisplayName)
    }

    private fun movement(
        type: OrderType,
        quantity: Int,
        operationDate: Long = 1,
        unitPrice: String? = null,
    ) = MovementEntity(
        accountId = 1,
        operationDate = operationDate,
        title = "SICAV",
        orderType = type,
        quantity = quantity,
        unitPrice = unitPrice,
    )
}
