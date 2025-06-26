package com.arny.aipromptmaster.data.prefs

import android.content.Context
import javax.inject.Inject
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class SecurePrefs @Inject constructor(
    context: Context
) {
    // Создание или получение мастер-ключа
    val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    // Инициализация EncryptedSharedPreferences
    val prefs = EncryptedSharedPreferences.create(
        "PreferencesFilename",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun setOpenRouterApiKey(apiKey: String) {
        prefs.edit { putString(PrefsConstants.OR_API_KEY, apiKey) }
    }

    fun getOpenRouterApiKey(): String? {
        return prefs.getString(PrefsConstants.OR_API_KEY, null)
    }
}