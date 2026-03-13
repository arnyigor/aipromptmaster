package com.arny.aipromptmaster.data.repositories

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/**
 * 1️⃣ Создаём DataStore один раз для каждого Context.
 *    Это делается через расширение‑свойство, которое хранит Lazy‑объект,
 *    поэтому каждый `Context` получает свой собственный экземпляр.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sync_metadata"
)

/**
 * 2️⃣ Класс принимает Context в конструкторе и предоставляет
 *    асинхронные методы чтения/записи времени последней синхронизации.
 *
 *    В отличие от объекта‑синглтона, такой подход упрощает тестирование
 *    (можно передать мок `Context`) и позволяет инжектить его через Koin/Dagger.
 */
class SyncMetadata(private val context: Context) {

    // Ключ для хранения времени последней синхронизации
    private val lastSyncKey = stringPreferencesKey("last_sync_time")

    /** Возвращает время последней синхронизации (мс). Если данных нет – 0. */
    suspend fun getLastSync(): Long =
        context.dataStore.data.first()[lastSyncKey]?.toLongOrNull() ?: 0L

    /** Сохраняет текущее время как время последней синхронизации. */
    suspend fun setLastSync(timestamp: Long) =
        context.dataStore.edit { it[lastSyncKey] = timestamp.toString() }
}
