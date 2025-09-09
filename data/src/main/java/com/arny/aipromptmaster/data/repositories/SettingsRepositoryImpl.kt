package com.arny.aipromptmaster.data.repositories

import android.content.SharedPreferences
import android.util.Log
import com.arny.aipromptmaster.data.prefs.Prefs
import com.arny.aipromptmaster.data.prefs.PrefsConstants
import com.arny.aipromptmaster.data.prefs.SecurePrefs
import com.arny.aipromptmaster.domain.repositories.ISettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(
    private val securePrefs: SecurePrefs,
    private val prefs: Prefs,
    private val dispatcher: CoroutineDispatcher
) : ISettingsRepository {

    private var cachedApiKey: String? = null
    private var cacheTime: Long = 0

    private companion object {
        const val CACHE_EXPIRATION_MS = 60 * 60 * 1000L // 60 minutes

        // Константы для ключей
        const val PREFS_SELECTED_MODEL = "selected_model_id"
        const val PREFS_FAVORITE_MODELS = "favorite_model_ids"
        const val FAVORITES_SEPARATOR = "," // Разделитель для списка избранных
    }

    // Существующие методы для API ключа
    override fun saveApiKey(apiKey: String) {
        securePrefs.put(PrefsConstants.OR_API_KEY, apiKey)
        cachedApiKey = apiKey
        cacheTime = System.currentTimeMillis()
    }

    override fun getApiKey(): String? {
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

    // Методы для выбранной модели
    override fun setSelectedModelId(id: String?) {
        if (id == null) {
            prefs.remove(PREFS_SELECTED_MODEL)
        } else {
            prefs.put(PREFS_SELECTED_MODEL, id)
        }
    }

    override fun getSelectedModelId(): Flow<String?> = callbackFlow<String?> {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == PREFS_SELECTED_MODEL) {
                launch {
                    send(prefs.get(PREFS_SELECTED_MODEL))
                }
            }
        }

        // Эмитим текущее значение
        send(prefs.get(PREFS_SELECTED_MODEL))

        // Регистрируем слушателя
        prefs.settings.registerOnSharedPreferenceChangeListener(listener)

        awaitClose {
            prefs.settings.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }.flowOn(dispatcher)


    override fun getFavoriteModelIds(): Flow<Set<String>> = callbackFlow<Set<String>> {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, key ->
            if (key == PREFS_FAVORITE_MODELS) {
                launch {
                    val favoriteIds = getCurrentFavoriteIds()
                    send(favoriteIds)
                }
            }
        }

        // Эмитим текущее значение
        val initial = getCurrentFavoriteIds()
        send(initial)

        // Регистрируем слушателя
        prefs.settings.registerOnSharedPreferenceChangeListener(listener)

        awaitClose {
            prefs.settings.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }.flowOn(dispatcher)


    override suspend fun setFavoriteModelIds(favoriteIds: Set<String>) {
        val serializedIds = serializeFavoriteIds(favoriteIds)
        if (serializedIds.isEmpty()) {
            prefs.remove(PREFS_FAVORITE_MODELS)
        } else {
            prefs.put(PREFS_FAVORITE_MODELS, serializedIds)
        }
    }

    override suspend fun addToFavorites(modelId: String) {
        val currentFavorites = getCurrentFavoriteIds()
        val updatedFavorites = currentFavorites + modelId
        setFavoriteModelIds(updatedFavorites)
    }

    override suspend fun removeFromFavorites(modelId: String) {
        val currentFavorites = getCurrentFavoriteIds()
        val updatedFavorites = currentFavorites - modelId
        setFavoriteModelIds(updatedFavorites)
    }

    override suspend fun isFavorite(modelId: String): Boolean {
        return getCurrentFavoriteIds().contains(modelId)
    }

    /**
     * Синхронно получает текущие избранные ID из SharedPreferences
     */
    private fun getCurrentFavoriteIds(): Set<String> =
        parseFavoriteIds(prefs.get<String?>(PREFS_FAVORITE_MODELS))

    /**
     * Преобразует строку из SharedPreferences в Set<String>
     */
    private fun parseFavoriteIds(serializedIds: String?): Set<String> =
        if (serializedIds.isNullOrBlank()) {
            emptySet()
        } else {
            serializedIds.split(FAVORITES_SEPARATOR)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        }

    /**
     * Преобразует Set<String> в строку для хранения в SharedPreferences
     */
    private fun serializeFavoriteIds(favoriteIds: Set<String>): String = favoriteIds
        .filter { it.isNotBlank() }
        .joinToString(FAVORITES_SEPARATOR)
}

