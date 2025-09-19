package com.arny.aiprompts.di

import com.arny.aiprompts.platform.EncryptedJvmSettings
import com.arny.aiprompts.platform.SecureKeyStorage
import com.russhwolf.settings.Settings
import java.security.SecureRandom
import java.util.Base64
import java.util.prefs.Preferences
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

actual class SettingsFactory(actual val context: Any? = null) {
    // Используем нашу обертку над Keytar/Keychain/Credential Manager
    private val secureStorage = SecureKeyStorage()
    private val keyAlias = "app-settings-encryption-key"

    actual fun create(name: String): Settings {
        val delegate = Preferences.userRoot().node(name)

        // 1. Пытаемся загрузить ключ из системного хранилища (Keychain/Credential Manager)
        var keyBytes = secureStorage.getKey(keyAlias)

        // 2. Если ключ не найден (первый запуск), генерируем новый...
        if (keyBytes == null) {
            keyBytes = ByteArray(32) // 256-bit AES key
            SecureRandom().nextBytes(keyBytes)
            secureStorage.saveKey(keyAlias, keyBytes)
        }

        // 3. Создаем объект SecretKey из полученных байт
        val secretKey: SecretKey = SecretKeySpec(keyBytes, "AES")

        // 4. Передаем этот ключ в EncryptedJvmSettings
        return EncryptedJvmSettings(delegate, secretKey)
    }
}