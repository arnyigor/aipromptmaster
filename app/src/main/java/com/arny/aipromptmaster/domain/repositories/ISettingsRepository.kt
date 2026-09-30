package com.arny.aipromptmaster.domain.repositories

interface ISettingsRepository : com.arny.promptcontract.ProviderStore {
    fun saveApiKey(apiKey: String)
    fun getApiKey(): String?
}
