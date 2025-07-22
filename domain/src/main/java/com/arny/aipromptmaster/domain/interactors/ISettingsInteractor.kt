package com.arny.aipromptmaster.domain.interactors

interface ISettingsInteractor {
    fun saveApiKey(apiKey: String)
    fun getApiKey(): String?
    suspend fun sendFeedback(content: String): Result<Unit>
}