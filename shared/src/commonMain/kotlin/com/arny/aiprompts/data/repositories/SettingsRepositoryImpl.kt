package com.arny.aiprompts.data.repositories

import com.arny.aiprompts.di.SettingsFactory
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Реализация репозитория настроек с использованием Multiplatform Settings.
 * На Android использует EncryptedSharedPreferences (через SettingsFactory).
 * На Desktop использует шифрование через Keytar.
 */
class SettingsRepositoryImpl(private val settingsFactory: SettingsFactory) : ISettingsRepository {

    private val settings: Settings = settingsFactory.create("app_settings")
    private val _selectedId = MutableStateFlow<String?>(null)

    init {
        _selectedId.value = settings.getStringOrNull("selected_model_id")
    }

    // === API Keys ===
    override fun saveApiKey(apiKey: String) {
        settings.putString("api_key", apiKey)
    }

    override fun getApiKey(): String? {
        return settings.getStringOrNull("api_key")
    }

    override fun saveOpenRouterApiKey(apiKey: String) {
        settings.putString("openrouter_api_key", apiKey)
    }

    override fun getOpenRouterApiKey(): String? {
        return settings.getStringOrNull("openrouter_api_key")
    }

    // === Model Selection ===
    override fun setSelectedModelId(id: String?) {
        settings.putString("selected_model_id", id ?: "")
        _selectedId.update { id }
    }

    override fun getSelectedModelId(): Flow<String?> = _selectedId

    // === Sync ===
    override fun setLastSyncTime(timestamp: Long) {
        settings.putLong("last_sync_timestamp", timestamp)
    }

    override fun getLastSyncTime(): Long =
        settings.getLongOrNull("last_sync_timestamp") ?: 0L

    // === Custom Base URL ===
    override fun saveBaseUrl(url: String) {
        settings.putString("api_base_url", url)
    }

    override fun getBaseUrl(): String? {
        return settings.getStringOrNull("api_base_url")
    }

    // === GitHub Sync ===
    override fun getGitHubToken(): String? {
        return settings.getStringOrNull("github_token")
    }

    override fun saveGitHubToken(token: String) {
        settings.putString("github_token", token)
    }

    override fun getGitHubRepo(): String? {
        return settings.getStringOrNull("github_repo")
    }

    override fun saveGitHubRepo(repo: String) {
        settings.putString("github_repo", repo)
    }

    // === User Context ===
    override fun getUserContext(): String {
        return settings.getString("user_context", "")
    }

    override fun saveUserContext(context: String) {
        settings.putString("user_context", context)
    }
}
