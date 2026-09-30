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
    private val cipher: Cipher = Cipher.getInstance("AES/GCM/NoPadding")

    private fun encrypt(value: String): String {
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        val gcmSpec = GCMParameterSpec(128, iv)
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
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    // --- Базовые методы интерфейса ---
    override val keys: Set<String> get() = delegate.keys().toSet()
    override val size: Int get() = delegate.keys().size
    override fun clear() = delegate.clear()
    override fun remove(key: String) = delegate.remove(key)
    override fun hasKey(key: String): Boolean = delegate.keys().contains(key)

    // --- Реализация для String ---
    override fun putString(key: String, value: String) = delegate.put(key, encrypt(value))
    override fun getString(key: String, defaultValue: String): String =
        delegate.get(key, null)?.let { decrypt(it) } ?: defaultValue
    override fun getStringOrNull(key: String): String? =
        delegate.get(key, null)?.let { decrypt(it) }

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