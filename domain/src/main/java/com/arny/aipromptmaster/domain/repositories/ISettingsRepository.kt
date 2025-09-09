package com.arny.aipromptmaster.domain.repositories

import kotlinx.coroutines.flow.Flow

interface ISettingsRepository {
    fun saveApiKey(apiKey: String)
    fun getApiKey(): String?
    fun setSelectedModelId(id: String?)
    fun getSelectedModelId(): Flow<String?>
    fun getFavoriteModelIds(): Flow<Set<String>>
    suspend fun setFavoriteModelIds(favoriteIds: Set<String>)
    suspend fun addToFavorites(modelId: String)
    suspend fun removeFromFavorites(modelId: String)
    suspend fun isFavorite(modelId: String): Boolean
}