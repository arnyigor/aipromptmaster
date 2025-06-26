package com.arny.aipromptmaster.data.repositories

import com.arny.aipromptmaster.data.prefs.SecurePrefs
import com.arny.aipromptmaster.domain.repositories.ISettingsRepository
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(
    private val securePrefs: SecurePrefs
) : ISettingsRepository {
    private var cachedApiKey: String? = null
    private var cacheTime: Long = 0
    private companion object {
        const val CACHE_EXPIRATION_MS = 60 * 60 * 1000L // 60 minutes
    }

    override fun saveApiKey(apiKey: String) {
        securePrefs.setOpenRouterApiKey(apiKey)
        cachedApiKey = apiKey
        cacheTime = System.currentTimeMillis()
    }

    override fun getApiKey(): String? {
        return if (isCacheValid()) {
            cachedApiKey
        } else {
            cachedApiKey = securePrefs.getOpenRouterApiKey()
            cacheTime = System.currentTimeMillis()
            cachedApiKey
        }
    }

    private fun isCacheValid(): Boolean {
        return cachedApiKey != null &&
            (System.currentTimeMillis() - cacheTime) < CACHE_EXPIRATION_MS
    }
}