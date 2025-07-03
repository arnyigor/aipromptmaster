package com.arny.aipromptmaster.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.arny.aipromptmaster.data.utils.SingletonHolder
import androidx.core.content.edit

class Prefs private constructor(context: Context) {
    val settings: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    companion object : SingletonHolder<Prefs, Context>(::Prefs)

    inline fun <reified T> get(key: String): T? {
        return settings.all[key] as? T
    }

    fun getAll(): Map<String, *>? {
        return settings.all
    }

    fun put(key: String?, value: Any?) {
        settings.edit { put(key, value) }
    }

    fun remove(vararg key: String) {
        settings.edit {
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