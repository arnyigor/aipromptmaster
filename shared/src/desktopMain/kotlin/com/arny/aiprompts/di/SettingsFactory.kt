package com.arny.aiprompts.di

import com.arny.aiprompts.platform.EncryptedJvmSettings
import com.arny.aiprompts.platform.SecureKeyStorage
import com.russhwolf.settings.Settings
import java.security.SecureRandom
import java.util.Base64
import java.util.prefs.Preferences
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class SettingsFactory(actual val context: Any? = null) {
    private val secureStorage = SecureKeyStorage()
    private val keyAlias = "app-settings-encryption-key"

    // Кэшируем ключ для избежания повторных обращений к системному хранилищу
    @Volatile
    private var cachedSecretKey: SecretKey? = null
    private val keyLock = Any()

    actual fun create(name: String): Settings {
        val delegate = Preferences.userRoot().node(name)
        val secretKey = getOrCreateSecretKey()
        return EncryptedJvmSettings(delegate, secretKey)
    }

    /**
     * Получает или создаёт SecretKey с гарантией thread-safety
     */
    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey = synchronized(keyLock) {
        // Double-checked locking для производительности
        cachedSecretKey?.let { return it }

        // 1. Пытаемся загрузить существующий ключ
        var keyBytes = secureStorage.getKey(keyAlias)

        // 2. Если ключ не найден (первый запуск), генерируем новый
        if (keyBytes == null) {
            println("🔑 [SettingsFactory] Generating new encryption key")
            keyBytes = ByteArray(32) // 256-bit AES key
            SecureRandom().nextBytes(keyBytes)

            try {
                secureStorage.saveKey(keyAlias, keyBytes)
                println("✅ [SettingsFactory] Encryption key saved to secure storage")
            } catch (e: Exception) {
                println("❌ [SettingsFactory] Failed to save key: ${e.message}")
                throw IllegalStateException("Cannot initialize secure storage", e)
            }
        } else {
            println("✅ [SettingsFactory] Loaded existing encryption key")
        }

        // 3. Валидация размера ключа
        require(keyBytes.size == 32) {
            "Invalid key size: ${keyBytes.size} bytes (expected 32)"
        }

        // 4. Создаём и кэшируем SecretKey
        val secretKey = SecretKeySpec(keyBytes, "AES")
        cachedSecretKey = secretKey
        return secretKey
    }

    /**
     * Очистка кэша (для тестов или смены ключа)
     */
    fun clearKeyCache() = synchronized(keyLock) {
        cachedSecretKey = null
    }

    /**
     * Rotation ключа (для периодической смены)
     */
    fun rotateEncryptionKey(): SecretKey = synchronized(keyLock) {
        println("🔄 [SettingsFactory] Rotating encryption key")

        // Генерируем новый ключ
        val newKeyBytes = ByteArray(32)
        SecureRandom().nextBytes(newKeyBytes)

        // Сохраняем с новым alias для избежания потери данных
        val newKeyAlias = "$keyAlias-${System.currentTimeMillis()}"
        secureStorage.saveKey(newKeyAlias, newKeyBytes)

        val newSecretKey = SecretKeySpec(newKeyBytes, "AES")
        cachedSecretKey = newSecretKey

        println("✅ [SettingsFactory] New key saved as '$newKeyAlias'")
        return newSecretKey
    }
}
