@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING",
    "MISSING_DEPENDENCY_SUPERCLASS_WARNING"
)

package com.arny.aiprompts.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.Settings
import com.russhwolf.settings.datastore.DataStoreSettings

private val Context.dataStore by preferencesDataStore(name = "settings")

actual class SettingsFactory(actual val context: Any?) {

    constructor(context: Context) : this(context as Any?)

    @OptIn(ExperimentalSettingsApi::class, ExperimentalSettingsImplementation::class)
    actual fun create(name: String): Settings {
        return DataStoreSettings((context as Context).dataStore) as Settings
    }
}
