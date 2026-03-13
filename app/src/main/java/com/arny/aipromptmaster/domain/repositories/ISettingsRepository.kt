package com.arny.aipromptmaster.domain.repositories

interface ISettingsRepository {
    fun saveApiKey(apiKey: String)
    fun getApiKey(): String?
}