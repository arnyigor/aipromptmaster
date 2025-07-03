package com.arny.aipromptmaster.domain.repositories

import kotlinx.coroutines.flow.Flow

interface ISettingsRepository {
    fun saveApiKey(apiKey: String)
    fun getApiKey(): String?
    fun setSelectedModelId(id: String?)
    fun getSelectedModelId(): Flow<String?>
}