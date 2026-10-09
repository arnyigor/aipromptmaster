package com.arny.aiprompts.platform

import com.russhwolf.settings.Settings
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import java.util.prefs.Preferences
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class EncryptedJvmSettings(
    private val delegate: Preferences,
    private val secretKey: SecretKey
) : Settings {
    private fun cipher() = Cipher.getInstance("AES/GCM/NoPadding")

    private fun encrypt(value: String): String {
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        val gcmSpec = GCMParameterSpec(128, iv)
        val cipher = cipher()
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        val encryptedBytes = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val combined = iv + encryptedBytes
        return Base64.getEncoder().encodeToString(combined)
    }

    private fun decrypt(encryptedValue: String): String? {
        return try {
            val combined = Base64.getDecoder().decode(encryptedValue)
            val iv = combined.copyOfRange(0, 12)
            val encryptedBytes = combined.copyOfRange(12, combined.size)
            val gcmSpec = GCMParameterSpec(128, iv)
            val cipher = cipher()
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    // --- Базовые методы интерфейса ---
    override val keys: Set<String> @Synchronized get() {
        val all = delegate.keys().toSet()
        val internal = all.flatMap { key -> chunkKeys(key, delegate.get(key, null)) }.toSet()
        return all - internal
    }
    override val size: Int get() = keys.size
    override fun clear() = delegate.clear()
    @Synchronized override fun remove(key: String) { removeChunks(key, delegate.get(key, null)); delegate.remove(key) }
    override fun hasKey(key: String): Boolean = delegate.keys().contains(key)

    // --- Реализация для String ---
    @Synchronized override fun putString(key: String, value: String) {
        val old = delegate.get(key, null)
        val encrypted = encrypt(value)
        if (encrypted.length <= Preferences.MAX_VALUE_LENGTH) delegate.put(key, encrypted)
        else {
            val id = java.util.UUID.randomUUID().toString().take(8)
            val chunks = buildList {
                var start = 0
                while (start < value.length) {
                    var end = (start + 1200).coerceAtMost(value.length)
                    if (end < value.length && value[end - 1].isHighSurrogate()) end--
                    add(value.substring(start, end)); start = end
                }
            }
            require(chunks.size <= 10000) { "Настройки слишком велики" }
            val written = mutableListOf<String>()
            try {
                chunks.forEachIndexed { i, chunk ->
                    val chunkKey = "$key-$id-$i"
                    delegate.put(chunkKey, encrypt(chunk)); written.add(chunkKey)
                }
                delegate.put(key, "chunks:v1:$id:${chunks.size}")
            } catch (e: Exception) {
                written.forEach { runCatching { delegate.remove(it) } }
                throw e
            }
        }
        removeChunks(key, old)
    }
    private fun chunkKeys(key: String, value: String?): List<String> {
        if (value?.startsWith("chunks:v1:") != true) return emptyList()
        val parts = value.split(':')
        val count = parts.getOrNull(3)?.toIntOrNull()?.takeIf { it in 1..10000 } ?: return emptyList()
        return List(count) { "$key-${parts[2]}-$it" }
    }
    private fun removeChunks(key: String, value: String?) {
        chunkKeys(key, value).forEach { delegate.remove(it) }
    }
    @Synchronized override fun getString(key: String, defaultValue: String): String = getStringOrNull(key) ?: defaultValue
    @Synchronized override fun getStringOrNull(key: String): String? {
        val value = delegate.get(key, null) ?: return null
        if (!value.startsWith("chunks:v1:")) return decrypt(value)
        val parts = value.split(':')
        val count = parts.getOrNull(3)?.toIntOrNull()?.takeIf { it in 1..10000 } ?: return null
        return buildString {
            repeat(count) {
                val chunk = delegate.get("$key-${parts[2]}-$it", null)?.let(::decrypt) ?: return null
                append(chunk)
            }
        }
    }

    // --- Реализация для Int ---
    override fun putInt(key: String, value: Int) = putString(key, value.toString())
    override fun getInt(key: String, defaultValue: Int): Int =
        getStringOrNull(key)?.toIntOrNull() ?: defaultValue
    override fun getIntOrNull(key: String): Int? = getStringOrNull(key)?.toIntOrNull()

    // --- Реализация для Long ---
    override fun putLong(key: String, value: Long) = putString(key, value.toString())
    override fun getLong(key: String, defaultValue: Long): Long =
        getStringOrNull(key)?.toLongOrNull() ?: defaultValue
    override fun getLongOrNull(key: String): Long? = getStringOrNull(key)?.toLongOrNull()

    // --- Реализация для Float ---
    override fun putFloat(key: String, value: Float) = putString(key, value.toString())
    override fun getFloat(key: String, defaultValue: Float): Float =
        getStringOrNull(key)?.toFloatOrNull() ?: defaultValue
    override fun getFloatOrNull(key: String): Float? = getStringOrNull(key)?.toFloatOrNull()

    // --- Реализация для Double ---
    override fun putDouble(key: String, value: Double) = putString(key, value.toString())
    override fun getDouble(key: String, defaultValue: Double): Double =
        getStringOrNull(key)?.toDoubleOrNull() ?: defaultValue
    override fun getDoubleOrNull(key: String): Double? = getStringOrNull(key)?.toDoubleOrNull()

    // --- Реализация для Boolean ---
    override fun putBoolean(key: String, value: Boolean) = putString(key, value.toString())
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        getStringOrNull(key)?.toBooleanStrictOrNull() ?: defaultValue
    override fun getBooleanOrNull(key: String): Boolean? = getStringOrNull(key)?.toBooleanStrictOrNull()
}