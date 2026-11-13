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
    override fun environmentKeyAvailable(): Boolean = !com.arny.aiprompts.platform.openRouterEnvironmentKey().isNullOrBlank()

    private val settings: Settings = settingsFactory.create("app_settings")
    override fun legacyPersonalFilesLoaded(): Boolean = settings.getBoolean("legacy_personal_files_loaded", false)
    override fun markLegacyPersonalFilesLoaded() = settings.putBoolean("legacy_personal_files_loaded", true)
    private val vaultPreferences = com.arny.promptcontract.PersonalVaultPreferences(settings::getStringOrNull, settings::putString)
    override fun loadSyncStage() = vaultPreferences.loadSyncStage()
    override fun saveSyncStage(stage: String?) = vaultPreferences.saveSyncStage(stage)
    override fun lastSuccessfulSync() = vaultPreferences.lastSuccessfulSync()
    override fun recordSuccessfulSync(value: String) = vaultPreferences.recordSuccessfulSync(value)
    override fun loadPersonalVault() = vaultPreferences.loadPersonalVault()
    override fun disconnectPersonalVault() = vaultPreferences.disconnectPersonalVault()
    override fun savePersonalVault(config: com.arny.promptcontract.PersonalVaultConfig) = vaultPreferences.savePersonalVault(config)
    override fun loadPersonalVaultBaseline() = vaultPreferences.loadPersonalVaultBaseline()
    override fun savePersonalVaultBaseline(baseline: com.arny.promptcontract.PersonalVaultBaseline) = vaultPreferences.savePersonalVaultBaseline(baseline)

    private val _selectedId = MutableStateFlow<String?>(null)
    private val providerChanges by lazy { MutableStateFlow(loadProviders()) }
    override fun observeProviders(): Flow<com.arny.promptcontract.ProviderConfig> = providerChanges

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
        if (settings.getStringOrNull("provider_profiles_v1") != null) return com.arny.aiprompts.platform.resolveProviderKey(loadProviders().active)
        return settings.getStringOrNull("openrouter_api_key")
    }

    // === Model Selection ===
    override fun setSelectedModelId(id: String?) {
        settings.putString("selected_model_id", id ?: "")
        _selectedId.update { id }
        if (settings.getStringOrNull("provider_profiles_v1") != null) {
            val config = loadProviders()
            saveProviders(config.copy(profiles = config.profiles.map { if (it.id == config.activeId) it.copy(modelId = id.orEmpty()) else it }))
        }
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
        if (settings.getStringOrNull("provider_profiles_v1") != null) return loadProviders().active.baseUrl
        return settings.getStringOrNull("api_base_url")
    }

    override fun loadProviders(): com.arny.promptcontract.ProviderConfig {
        settings.getStringOrNull("provider_profiles_v1")?.let {
            return kotlinx.serialization.json.Json.decodeFromString<com.arny.promptcontract.ProviderConfig>(it).checked()
        }
        val key = settings.getStringOrNull("openrouter_api_key").orEmpty()
        val url = settings.getStringOrNull("api_base_url").orEmpty()
        val openRouter = com.arny.promptcontract.ProviderProfile.openRouter(key).copy(modelId = settings.getStringOrNull("selected_model_id").orEmpty())
        return if (url.isBlank() || url.trimEnd('/') == openRouter.baseUrl) com.arny.promptcontract.ProviderConfig(listOf(openRouter))
        else com.arny.promptcontract.ProviderConfig(listOf(openRouter, com.arny.promptcontract.ProviderProfile("legacy-custom", "Сохранённый API", url, key, openRouter.modelId, requiresKey = false)), "legacy-custom")
    }
    override fun saveProviders(config: com.arny.promptcontract.ProviderConfig) {
        settings.putString("provider_profiles_v1", kotlinx.serialization.json.Json.encodeToString(com.arny.promptcontract.ProviderConfig.serializer(), config.checked()))
        _selectedId.value = config.active.modelId.takeIf { it.isNotBlank() }
        settings.putString("selected_model_id", config.active.modelId)
        providerChanges.value = config
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
