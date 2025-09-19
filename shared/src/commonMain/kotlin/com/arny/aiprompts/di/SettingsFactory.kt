package com.arny.aiprompts.di

import com.russhwolf.settings.Settings

/**
 * Ожидаемая фабрика, которая будет создавать экземпляр Settings.
 * Платформы должны предоставить свою реализацию.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class SettingsFactory {
    val context: Any?
    fun create(name: String): Settings
}