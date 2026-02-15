package com.arny.aiprompts.data.repositories

import kotlinx.coroutines.flow.Flow

/**
 * Интерфейс репозитория настроек приложения.
 * Поддерживает хранение API ключей, настроек моделей, GitHub синхронизации и персонализации.
 */
interface ISettingsRepository {
    // === API Keys ===
    fun saveApiKey(apiKey: String)
    fun getApiKey(): String?
    fun saveOpenRouterApiKey(apiKey: String)
    fun getOpenRouterApiKey(): String?
    
    // === Model Selection ===
    fun setSelectedModelId(id: String?)
    fun getSelectedModelId(): Flow<String?>
    
    // === Sync ===
    fun setLastSyncTime(timestamp: Long)
    fun getLastSyncTime(): Long
    
    // === Custom Base URL (для LMStudio и других OpenAI-совместимых сервисов) ===
    fun saveBaseUrl(url: String)
    fun getBaseUrl(): String?
    
    // === GitHub Sync ===
    fun getGitHubToken(): String?
    fun saveGitHubToken(token: String)
    fun getGitHubRepo(): String?
    fun saveGitHubRepo(repo: String)
    
    // === User Context (персонализация системного промпта) ===
    fun getUserContext(): String
    fun saveUserContext(context: String)
}
