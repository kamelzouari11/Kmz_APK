package com.mescomptes.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class DatabaseSnapshot(
    val accounts: List<AccountEntity>,
    val movements: List<MovementEntity>,
)

/** Password-protected, portable backup: PBKDF2-SHA256 + AES-256-GCM. */
object BackupCodec {
    private val magic = byteArrayOf(0x4D, 0x43, 0x50, 0x31) // MCP1
    private const val iterations = 210_000
    private const val saltSize = 16
    private const val ivSize = 12

    fun encrypt(snapshot: DatabaseSnapshot, password: CharArray): ByteArray {
        require(password.size >= 6) { "Le mot de passe doit contenir au moins 6 caractères." }
        return try {
            val salt = ByteArray(saltSize).also { SecureRandom().nextBytes(it) }
            val iv = ByteArray(ivSize).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
            val encrypted = cipher.doFinal(toJson(snapshot).toByteArray(Charsets.UTF_8))
            ByteBuffer.allocate(magic.size + salt.size + iv.size + encrypted.size)
                .put(magic).put(salt).put(iv).put(encrypted).array()
        } finally {
            password.fill('\u0000')
        }
    }

    fun decrypt(bytes: ByteArray, password: CharArray): DatabaseSnapshot {
        return try {
            require(bytes.size > magic.size + saltSize + ivSize) { "Fichier de sauvegarde invalide." }
            val buffer = ByteBuffer.wrap(bytes)
            val readMagic = ByteArray(magic.size).also { buffer.get(it) }
            require(readMagic.contentEquals(magic)) { "Ce fichier n'est pas une sauvegarde MesComptes." }
            val salt = ByteArray(saltSize).also { buffer.get(it) }
            val iv = ByteArray(ivSize).also { buffer.get(it) }
            val encrypted = ByteArray(buffer.remaining()).also { buffer.get(it) }
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
                fromJson(cipher.doFinal(encrypted).toString(Charsets.UTF_8))
            } catch (_: Exception) {
                throw IllegalArgumentException("Mot de passe incorrect ou sauvegarde endommagée.")
            }
        } finally {
            password.fill('\u0000')
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun toJson(snapshot: DatabaseSnapshot): String = JSONObject().apply {
        put("version", 1)
        put("accounts", JSONArray().apply {
            snapshot.accounts.forEach { account -> put(JSONObject().apply {
                put("id", account.id); put("holder", account.holder); put("cin", account.cin)
                put("bank", account.bank); put("rib", account.rib); put("number", account.number)
            }) }
        })
        put("movements", JSONArray().apply {
            snapshot.movements.forEach { movement -> put(JSONObject().apply {
                put("id", movement.id); put("accountId", movement.accountId)
                put("operationDate", movement.operationDate); put("title", movement.title)
                put("orderType", movement.orderType.name); put("quantity", movement.quantity)
                put("unitPrice", movement.unitPrice ?: JSONObject.NULL)
            }) }
        })
    }.toString()

    private fun fromJson(text: String): DatabaseSnapshot {
        val root = JSONObject(text)
        require(root.getInt("version") == 1) { "Version de sauvegarde non prise en charge." }
        val accountsJson = root.getJSONArray("accounts")
        val accounts = buildList {
            for (i in 0 until accountsJson.length()) accountsJson.getJSONObject(i).run {
                add(AccountEntity(getLong("id"), getString("holder"), getString("cin"), getString("bank"), getString("rib"), getString("number")))
            }
        }
        val accountIds = accounts.mapTo(mutableSetOf()) { it.id }
        val movementsJson = root.getJSONArray("movements")
        val movements = buildList {
            for (i in 0 until movementsJson.length()) movementsJson.getJSONObject(i).run {
                val accountId = getLong("accountId")
                require(accountId in accountIds) { "La sauvegarde contient une opération orpheline." }
                add(MovementEntity(
                    id = getLong("id"), accountId = accountId,
                    operationDate = getLong("operationDate"), title = getString("title"),
                    orderType = OrderType.valueOf(getString("orderType")), quantity = getInt("quantity"),
                    unitPrice = if (isNull("unitPrice")) null else getString("unitPrice"),
                ))
            }
        }
        return DatabaseSnapshot(accounts, movements)
    }
}
