package com.arny.aipromptmaster.domain.repositories

interface ISettingsRepository : com.arny.promptcontract.ProviderStore, com.arny.promptcontract.PersonalVaultStore {
    fun getChatGeneration(conversationId: String): com.arny.promptcontract.ChatGenerationConfig?
    fun saveChatGeneration(conversationId: String, settings: com.arny.promptcontract.ChatGenerationConfig)
    fun saveApiKey(apiKey: String)
    fun getApiKey(): String?
}
