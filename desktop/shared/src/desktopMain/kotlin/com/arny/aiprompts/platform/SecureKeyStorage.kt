package com.arny.aiprompts.platform

import com.starxg.keytar.Keytar
import java.util.Base64

/**
 * Кросс-платформенная обертка над keytar-java для безопасного хранения ключа.
 */
class SecureKeyStorage {
    private val serviceName = "com.arny.aiprompts"

    fun saveKey(alias: String, key: ByteArray) {
        val keyAsString = Base64.getEncoder().encodeToString(key)
        return Keytar.getInstance().setPassword(serviceName, alias, keyAsString)
    }

    fun getKey(alias: String): ByteArray? {
        val keyAsString = Keytar.getInstance().getPassword(serviceName, alias)
        return keyAsString?.let {
            try {
                Base64.getDecoder().decode(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }

    fun deleteKey(alias: String): Boolean {
        return Keytar.getInstance().deletePassword(serviceName, alias)
    }
}