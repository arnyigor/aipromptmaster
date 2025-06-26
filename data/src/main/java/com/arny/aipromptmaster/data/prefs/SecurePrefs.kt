package com.arny.aipromptmaster.data.prefs

import android.content.Context
import com.arny.aipromptmaster.data.utils.CryptoHelper
import javax.inject.Inject
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class SecurePrefs @Inject constructor(
    context: Context,
    private val cryptoHelper: CryptoHelper
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
        val encrypted = cryptoHelper.encrypt(apiKey)
        prefs.edit { putString(PrefsConstants.OR_API_KEY, encrypted) }
    }

    fun getOpenRouterApiKey(): String? {
        val encrypted = prefs.getString(PrefsConstants.OR_API_KEY, null)
        return encrypted?.let { cryptoHelper.decrypt(it) }
    }
}