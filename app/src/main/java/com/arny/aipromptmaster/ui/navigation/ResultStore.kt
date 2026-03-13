package com.arny.aipromptmaster.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

class ResultStore {
    // Храним результаты по ключу (например, ID запроса или имя класса)
    private val results = mutableMapOf<String, MutableState<Any?>>()

    // Установка результата
    fun <T> setResult(key: String, value: T) {
        if (!results.containsKey(key)) {
            results[key] = mutableStateOf(value)
        } else {
            results[key]?.value = value
        }
    }

    // Получение результата (подписка)
    @Suppress("UNCHECKED_CAST")
    @Composable
    fun <T> observeResult(key: String): State<T?> {
        // Если ключа еще нет, создаем пустой стейт
        val state = results.getOrPut(key) { mutableStateOf(null) }
        return state as State<T?>
    }

    // Очистка результата (опционально)
    fun clearResult(key: String) {
        results.remove(key)
    }
}

@Composable
fun rememberResultStore() = remember { ResultStore() }


val LocalResultStore = staticCompositionLocalOf<ResultStore> { error("No ResultStore provided") }