@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING",
    "MISSING_DEPENDENCY_SUPERCLASS_WARNING"
)

package com.arny.aiprompts.di

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings

/**
 * Фабрика для создания защищенных настроек на Android.
 * Использует EncryptedSharedPreferences для аппаратного шифрования через Android Keystore.
 */
actual class SettingsFactory(actual val context: Any?) {
    constructor(context: Context) : this(context as Any?)

    actual fun create(name: String): Settings {
        val androidContext = context as? Context
            ?: throw IllegalStateException("Context must be provided for Android SettingsFactory")

        // 1. Создаем или получаем Master Key в Android Keystore
        val masterKey = MasterKey.Builder(androidContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        // 2. Создаем EncryptedSharedPreferences
        // Файл настроек будет зашифрован.
        val sharedPreferences = EncryptedSharedPreferences.create(
            androidContext,
            name, // Имя файла настроек (например, "app_settings")
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        // 3. Оборачиваем в интерфейс Multiplatform Settings
        return SharedPreferencesSettings(sharedPreferences)
    }
}
