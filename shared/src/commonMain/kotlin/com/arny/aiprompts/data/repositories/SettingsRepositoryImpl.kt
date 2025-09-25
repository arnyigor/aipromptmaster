package com.arny.aiprompts.data.repositories

import com.arny.aiprompts.di.SettingsFactory
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class SettingsRepositoryImpl(private val settingsFactory: SettingsFactory) : ISettingsRepository {

    private val settings: Settings = settingsFactory.create("app_settings")
    private val _selectedId = MutableStateFlow<String?>(null)

    init {
        // Инициализируем из persistent storage
        _selectedId.value = settings.getStringOrNull("selected_model_id")
    }

    override fun saveApiKey(apiKey: String) {
        settings.putString("api_key", apiKey)
    }

    override fun getApiKey(): String? {
        return settings.getStringOrNull("api_key")
    }

    override fun setSelectedModelId(id: String?) {
        settings.putString("selected_model_id", id ?: "")
        _selectedId.update { id }
    }

    override fun getSelectedModelId(): Flow<String?> = _selectedId

    override fun setLastSyncTime(timestamp: Long) {
        settings.putLong("last_sync_timestamp", timestamp)
    }

    override fun getLastSyncTime(): Long {
        return settings.getLongOrNull("last_sync_timestamp") ?: 0L
    }
}