package com.arny.aiprompts.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Утилиты для работы с Flow в UI слое.
 * Предоставляет throttle и debounce операторы.
 */
object FlowUtils {

    /**
     * Ограничивает частоту emit значений (throttleFirst).
     * Пропускает значения, если с последнего emit прошло меньше [intervalMs] ms.
     * Первое значение всегда проходит.
     *
     * Пример использования:
     * ```kotlin
     * uiEventFlow.throttleFirst(500).onEach { event ->
     *     // Обработка события
     * }.launchIn(viewModelScope)
     * ```
     *
     * @param intervalMs Минимальный интервал между emit в миллисекундах
     * @return ThrottledFlow с throttleFirst логикой
     */
    fun <T> Flow<T>.throttleFirst(intervalMs: Long): Flow<T> {
        return ThrottledFlow(this, intervalMs, allowFirst = true)
    }

    /**
     * Ограничивает частоту emit значений (throttleLast).
     * Последнее значение в окне [intervalMs] будет отправлено.
     *
     * @param intervalMs Интервал в миллисекундах
     * @return ThrottledFlow с throttleLast логикой
     */
    fun <T> Flow<T>.throttleLast(intervalMs: Long): Flow<T> {
        return ThrottledFlow(this, intervalMs, allowFirst = false)
    }
}

/**
 * Throttled flow implementation using channel for buffering.
 */
private class ThrottledFlow<T>(
    private val upstream: Flow<T>,
    private val intervalMs: Long,
    private val allowFirst: Boolean
) : Flow<T> {

    override suspend fun collect(collector: kotlinx.coroutines.flow.FlowCollector<T>) {
        val channel = kotlinx.coroutines.channels.Channel<T>(
            capacity = kotlinx.coroutines.channels.Channel.CONFLATED
        )
        
        var lastEmitTime = 0L

        upstream.collect { value ->
            val currentTime = System.currentTimeMillis()
            val timeSinceLastEmit = currentTime - lastEmitTime

            if (allowFirst) {
                // throttleFirst: пропускаем если слишком рано
                if (timeSinceLastEmit >= intervalMs) {
                    lastEmitTime = currentTime
                    collector.emit(value)
                }
            } else {
                // throttleLast: всегда перезаписываем последнее
                lastEmitTime = currentTime
                try {
                    channel.send(value)
                } catch (e: kotlinx.coroutines.channels.ClosedSendChannelException) {
                    // Channel closed, ignore
                }
            }
        }

        // Для throttleLast - читаем из канала
        if (!allowFirst) {
            kotlinx.coroutines.coroutineScope {
                try {
                    while (isActive) {
                        val value = channel.receive()
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastEmitTime >= intervalMs) {
                            lastEmitTime = currentTime
                            collector.emit(value)
                        }
                    }
                } catch (e: kotlinx.coroutines.channels.ClosedReceiveChannelException) {
                    // Channel closed, done
                }
            }
        }
    }
}

/**
 * StateFlow расширения для безопасного обновления.
 */
inline fun <T> MutableStateFlow<T>.updateAndGet(transform: (T) -> T): T {
    var newValue: T? = null
    value = transform(value).also { newValue = it }
    return newValue!!
}

/**
 * Запускает Flow в указанном scope с debounce.
 * Удобно для поисковых запросов.
 *
 * @param scope Корутин scope
 * @param debounceMs Задержка debounce в миллисекундах (по умолчанию 300ms)
 * @param action Дейтие при emit
 * @return Job для управления корутиной
 */
fun <T> Flow<T>.launchWithDebounce(
    scope: CoroutineScope,
    debounceMs: Long = 300,
    action: suspend (T) -> Unit
): Job {
    return scope.launch {
        collectLatest { value ->
            delay(debounceMs)
            action(value)
        }
    }
}

/**
 * Запускает Flow в указанном scope с throttle.
 * Ограничивает частоту выполнения action.
 *
 * @param scope Корутин scope
 * @param intervalMs Интервал в миллисекундах (по умолчанию 300ms)
 * @param action Дейтие при emit
 * @return Job для управления корутиной
 */
fun <T> Flow<T>.launchWithThrottle(
    scope: CoroutineScope,
    intervalMs: Long = 300,
    action: suspend (T) -> Unit
): Job {
    var lastEmitTime = 0L

    return scope.launch {
        collect { value ->
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastEmitTime >= intervalMs) {
                lastEmitTime = currentTime
                action(value)
            }
        }
    }
}
