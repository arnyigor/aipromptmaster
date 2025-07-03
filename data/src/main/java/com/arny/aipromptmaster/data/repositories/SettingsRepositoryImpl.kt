package com.arny.aipromptmaster.data.repositories

import android.content.SharedPreferences
import com.arny.aipromptmaster.data.prefs.Prefs
import com.arny.aipromptmaster.data.prefs.PrefsConstants
import com.arny.aipromptmaster.data.prefs.PrefsConstants.PREFS_SELECTED_MODEL
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
    }

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

    /**
     * Сохраняет ID выбранной модели.
     * Этот метод будет триггерить обновление в getSelectedModelIdFlow().
     */
    override fun setSelectedModelId(id: String?) {
        if (id == null) {
            prefs.remove(PREFS_SELECTED_MODEL)
        } else {
            prefs.put(PREFS_SELECTED_MODEL, id)
        }
    }

    /**
     * Предоставляет РЕАКТИВНЫЙ поток с ID выбранной модели.
     * Эмитит новое значение каждый раз, когда оно меняется в SharedPreferences.
     */
    override fun getSelectedModelId(): Flow<String?> = callbackFlow<String?> {
        // 1. Создаем слушателя изменений
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            // Реагируем только на изменение НАШЕГО ключа
            if (key == PREFS_SELECTED_MODEL) {
                // Запускаем корутину в контексте flow, чтобы безопасно считать значение
                // и отправить его в поток.
                launch {
                    send(prefs.get(PREFS_SELECTED_MODEL))
                }
            }
        }

        // 2. Эмитим самое первое, текущее значение.
        // Это важно, чтобы подписчик сразу получил состояние.
        send(prefs.get(PREFS_SELECTED_MODEL))

        // 3. Регистрируем нашего слушателя
        prefs.settings.registerOnSharedPreferenceChangeListener(listener)

        // 4. awaitClose будет вызван, когда Flow будет отменен (например, viewModelScope завершится).
        // Здесь мы ОБЯЗАНЫ отписаться, чтобы избежать утечек памяти.
        awaitClose {
            prefs.settings.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }.flowOn(dispatcher) // Выполнять всю логику в фоновом потоке
}
