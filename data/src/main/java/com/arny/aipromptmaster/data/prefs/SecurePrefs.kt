package com.arny.aipromptmaster.data.prefs

import android.content.Context
import android.content.SharedPreferences
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


    inline fun <reified T> get(key: String): T? {
        return prefs.all[key] as? T
    }

    fun getAll(): Map<String, *>? = prefs.all

    fun put(key: String?, value: Any?) {
        prefs.edit { put(key, value) }
    }

    fun remove(vararg key: String) {
        prefs.edit {
            for (k in key) {
                this.remove(k)
            }
        }
    }

    private fun SharedPreferences.Editor.put(key: String?, value: Any?): SharedPreferences.Editor {
        when (value) {
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Float -> putFloat(key, value)
            is Double -> putFloat(key, value.toFloat())
            is String -> putString(key, value)
            is Boolean -> putBoolean(key, value)
        }
        return this
    }
}