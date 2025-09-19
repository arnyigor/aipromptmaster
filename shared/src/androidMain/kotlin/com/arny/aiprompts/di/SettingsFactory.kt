@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.arny.aiprompts.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.russhwolf.settings.Settings
import com.russhwolf.settings.datastore.DataStoreSettings

private val Context.dataStore by preferencesDataStore(name = "settings")

actual class SettingsFactory(private val context: Context) {

    actual fun create(name: String): Settings {
        return DataStoreSettings(context.dataStore)
    }
}
