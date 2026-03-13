package com.arny.aipromptmaster.data.utils

import kotlinx.coroutines.flow.Flow

/**
 * Собирает данные из Flow, накапливает их и вызывает [onUpdate] не чаще, чем раз в [periodMillis].
 * Гарантирует финальный вызов [onUpdate] при успешном завершении потока.
 *
 * @param initialValue Начальное значение аккумулятора.
 * @param periodMillis Интервал обновления (в мс). По умолчанию 300мс (комфортно для глаза, экономно для БД).
 * @param accumulator Функция объединения текущего накопленного значения и нового элемента.
 * @param onUpdate Suspend-функция, которая выполняет запись (в БД/UI).
 * @return Финальное накопленное значение [R].
 */
suspend fun <T, R> Flow<T>.collectWithThrottling(
    initialValue: R,
    periodMillis: Long = 300L,
    accumulator: (R, T) -> R,
    onUpdate: suspend (R) -> Unit
): R {
    var currentValue = initialValue
    var lastUpdate = 0L

    try {
        collect { value ->
            // 1. Аккумулируем данные (синхронная операция, очень быстрая)
            currentValue = accumulator(currentValue, value)

            // 2. Проверяем время
            val now = System.currentTimeMillis()
            if (now - lastUpdate >= periodMillis) {
                // 3. Пишем в БД
                onUpdate(currentValue)
                lastUpdate = now
            }
        }
    } finally {
        // 4. CRITICAL SECTION: Финальный сброс буфера (Flush)
        // Гарантируем, что "хвост" сообщения запишется даже при отмене корутины (если CancellationException позволяет)
        // или при нормальном завершении.
        // Проверяем, отличается ли то, что мы сейчас имеем, от того, что писали в последний раз.
        // (Для упрощения здесь просто пишем всегда, так как UPDATE идемпотентен)
        if (lastUpdate != 0L || currentValue != initialValue) {
            try {
                onUpdate(currentValue)
            } catch (e: Exception) {
                // Логируем ошибку записи финала, чтобы не перекрыть основное исключение, если оно было
                System.err.println("Failed to flush final state: ${e.message}")
            }
        }
    }
    return currentValue
}