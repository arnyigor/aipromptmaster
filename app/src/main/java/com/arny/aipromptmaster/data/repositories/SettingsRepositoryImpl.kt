package com.arny.aipromptmaster.data.repositories

import com.arny.aipromptmaster.data.prefs.PrefsConstants
import com.arny.aipromptmaster.data.prefs.SecurePrefs
import com.arny.aipromptmaster.domain.repositories.ISettingsRepository

class SettingsRepositoryImpl(
    private val securePrefs: SecurePrefs,
) : ISettingsRepository {
    private val providerChanges by lazy { kotlinx.coroutines.flow.MutableStateFlow(loadProviders()) }
    override fun observeProviders(): kotlinx.coroutines.flow.Flow<com.arny.promptcontract.ProviderConfig> = providerChanges

    private var cachedApiKey: String? = null
    private var cacheTime: Long = 0

    private companion object {
        const val CACHE_EXPIRATION_MS = 60 * 60 * 1000L // 60 minutes
    }

    // Существующие методы для API ключа
    override fun saveApiKey(apiKey: String) {
        if (securePrefs.get<String>("provider_profiles_v1") != null) {
            val config = loadProviders()
            saveProviders(config.copy(profiles = config.profiles.map { if (it.id == config.activeId) it.copy(apiKey = apiKey) else it }))
            return
        }
        securePrefs.put(PrefsConstants.OR_API_KEY, apiKey)
        cachedApiKey = apiKey
        cacheTime = System.currentTimeMillis()
    }

    override fun getApiKey(): String? {
        securePrefs.get<String>("provider_profiles_v1")?.let { return loadProviders().active.apiKey }
        return if (isCacheValid()) {
            cachedApiKey
        } else {
            cachedApiKey = securePrefs.get(PrefsConstants.OR_API_KEY)
            cacheTime = System.currentTimeMillis()
            cachedApiKey
        }
    }

    private fun isCacheValid(): Boolean {
        return cachedApiKey != null &&
                (System.currentTimeMillis() - cacheTime) < CACHE_EXPIRATION_MS
    }

    override fun loadProviders(): com.arny.promptcontract.ProviderConfig {
        val json = securePrefs.get<String>("provider_profiles_v1")
        return if (json != null) kotlinx.serialization.json.Json.decodeFromString<com.arny.promptcontract.ProviderConfig>(json).checked()
        else com.arny.promptcontract.ProviderConfig(profiles = listOf(com.arny.promptcontract.ProviderProfile.openRouter(securePrefs.get<String>(PrefsConstants.OR_API_KEY).orEmpty())))
    }

    override fun saveProviders(config: com.arny.promptcontract.ProviderConfig) {
        securePrefs.put("provider_profiles_v1", kotlinx.serialization.json.Json.encodeToString(com.arny.promptcontract.ProviderConfig.serializer(), config.checked()))
        cachedApiKey = null
        providerChanges.value = config
    }
}

