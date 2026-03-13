package com.arny.aipromptmaster.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation keys для Navigation 3
 * Каждый ключ представляет экран приложения
 */
@Serializable
sealed class AppNavKey : NavKey

// ============ HOME TAB ============
@Serializable
data class PromptsKey(val navScreen: String = "") : AppNavKey()

@Serializable
data class PromptDetailKey(val promptId: String) : AppNavKey()

// ============ CHATS TAB ============
@Serializable
data object ChatHistoryKey : AppNavKey()

@Serializable
data class ChatDetailKey(val chatId: String?) : AppNavKey()

@Serializable
data class SystemPromptKey(val chatId: String?) : AppNavKey()

@Serializable
data object ModelsKey : AppNavKey()

// ============ SETTINGS TAB ============
@Serializable
data object SettingsKey : AppNavKey()

@Serializable
data class PromptEditKey(val promptId: String?) : AppNavKey()
