package com.mescomptes.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.mescomptes.app.data.AccountEntity
import com.mescomptes.app.data.BackupCodec
import com.mescomptes.app.data.DatabaseSnapshot
import com.mescomptes.app.data.MesComptesDatabase
import com.mescomptes.app.data.MovementEntity
import com.mescomptes.app.data.calculatePositions
import com.mescomptes.app.data.normalizedTitleKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal

class MesComptesViewModel(application: Application) : AndroidViewModel(application) {
    private val database = MesComptesDatabase.get(application)
    private val dao = database.dao()

    val accounts = dao.observeAccounts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val movements = dao.observeMovements().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun saveAccount(account: AccountEntity): String? {
        if (account.holder.isBlank()) return "Le titulaire est obligatoire."
        if (account.bank.isBlank()) return "La banque est obligatoire."
        if (account.cin.isBlank()) return "Le CIN est obligatoire."
        if (account.rib.isBlank()) return "Le RIB est obligatoire."
        if (account.number.isBlank()) return "Le numéro de compte est obligatoire."
        val clean = account.copy(
            holder = account.holder.trim(), cin = account.cin.trim().uppercase(),
            bank = account.bank.trim(), rib = account.rib.trim(), number = account.number.trim(),
        )
        if (clean.id == 0L) dao.insertAccount(clean) else dao.updateAccount(clean)
        return null
    }

    suspend fun deleteAccount(account: AccountEntity) = dao.deleteAccount(account)

    suspend fun saveMovement(movement: MovementEntity): String? {
        if (movement.accountId == 0L) return "Sélectionnez un compte."
        if (movement.title.isBlank()) return "Le titre est obligatoire."
        if (movement.quantity <= 0) return "La quantité doit être supérieure à zéro."
        val price = movement.unitPrice?.trim()?.takeIf { it.isNotEmpty() }?.let {
            try {
                BigDecimal(it.replace(',', '.')).stripTrailingZeros().toPlainString()
            } catch (_: NumberFormatException) {
                return "Le prix unitaire n'est pas valide."
            }
        }
        if (price != null && BigDecimal(price) < BigDecimal.ZERO) return "Le prix ne peut pas être négatif."
        val cleanTitle = movement.title.trim()
        val clean = movement.copy(title = cleanTitle, titleKey = normalizedTitleKey(cleanTitle), unitPrice = price)
        val previous = if (clean.id == 0L) null else dao.getMovement(clean.id)
        val movedToAnotherPosition = previous != null && (
            previous.accountId != clean.accountId || previous.titleKey != clean.titleKey
        )
        if (movedToAnotherPosition && previous!!.orderType.addsToPosition) {
            val oldPositionAfterMove = dao.positionBeforeMovement(previous.accountId, previous.titleKey, previous.id)
            if (oldPositionAfterMove < 0) {
                return "Modification impossible : l'ancienne position deviendrait négative."
            }
        }
        val balanceWithoutThisMovement = dao.positionBeforeMovement(clean.accountId, clean.titleKey, clean.id)
        val projectedBalance = balanceWithoutThisMovement + if (clean.orderType.addsToPosition) clean.quantity else -clean.quantity
        if (projectedBalance < 0) {
            return "Quantité indisponible : $balanceWithoutThisMovement titre(s) en portefeuille."
        }
        if (clean.id == 0L) dao.insertMovement(clean) else dao.updateMovement(clean)
        return null
    }

    suspend fun deleteMovement(movement: MovementEntity): String? {
        if (movement.orderType.addsToPosition) {
            val remaining = dao.positionBeforeMovement(movement.accountId, movement.titleKey, movement.id)
            if (remaining < 0) return "Suppression impossible : elle rendrait la position négative."
        }
        dao.deleteMovement(movement)
        return null
    }

    suspend fun createBackup(password: CharArray): ByteArray = BackupCodec.encrypt(
        DatabaseSnapshot(dao.getAccounts(), dao.getMovements()),
        password,
    )

    suspend fun restoreBackup(bytes: ByteArray, password: CharArray) {
        val snapshot = BackupCodec.decrypt(bytes, password)
        require(snapshot.accounts.all {
            it.holder.isNotBlank() && it.cin.isNotBlank() && it.bank.isNotBlank() && it.rib.isNotBlank() && it.number.isNotBlank()
        }) { "La sauvegarde contient un compte incomplet." }
        require(snapshot.movements.all { it.title.isNotBlank() && it.quantity > 0 }) {
            "La sauvegarde contient une opération invalide."
        }
        require(calculatePositions(snapshot.movements).none { it.quantity < 0 }) {
            "La sauvegarde contient une position négative."
        }
        database.withTransaction {
            dao.deleteAllMovements()
            dao.deleteAllAccounts()
            if (snapshot.accounts.isNotEmpty()) dao.restoreAccounts(snapshot.accounts)
            if (snapshot.movements.isNotEmpty()) dao.restoreMovements(snapshot.movements)
        }
    }
}
