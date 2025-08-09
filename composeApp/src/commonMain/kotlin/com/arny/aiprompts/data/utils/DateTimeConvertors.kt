package com.arny.aiprompts.data.utils

import kotlinx.datetime.*
import java.util.Date

/**
 * Converts a java.util.Date to a kotlinx.datetime.LocalDateTime.
 */
fun Date.toKotlinxLocalDateTime(): LocalDateTime {
    // 1. Convert the java.util.Date to a kotlinx.datetime.Instant
    //    by using the epoch milliseconds value.
    val ktxInstant = Instant.fromEpochMilliseconds(this.time)

    // 2. Convert the Instant to a LocalDateTime using the system's
    //    default time zone. The 'currentSystemDefault()' function
    //    is what requires the kotlinx.datetime.TimeZone import.
    return ktxInstant.toLocalDateTime(TimeZone.currentSystemDefault())
}

fun Date?.toIsoString(): String? {
    return  this?.toKotlinxLocalDateTime()?.toInstant()?.toIsoString()
}

/**
 * Converts a kotlinx.datetime.LocalDateTime back to a java.util.Date.
 */
fun LocalDateTime.toJavaDate(): Date {
    // This conversion also requires a time zone to correctly
    // convert the local date-time to a specific moment in time (Instant).
    val ktxInstant = this.toInstant(TimeZone.currentSystemDefault())
    return Date(ktxInstant.toEpochMilliseconds())
}

fun Instant.toJavaDate(): Date {
    return Date(this.toEpochMilliseconds())
}

fun LocalDateTime.toInstant(): Instant {
    return this.toInstant(TimeZone.currentSystemDefault())
}


/**
 * Парсит строку в формате ISO 8601 в объект Instant.
 *
 * @return Распарсенный Instant или текущий момент времени, если парсинг не удался.
 */
fun String.toInstant(): Instant {
    return try {
        // Instant.parse() напрямую работает с форматом ISO 8601
        Instant.parse(this)
    } catch (e: Exception) {
        // В случае ошибки возвращаем текущее время, как в вашем примере
        Clock.System.now()
    }
}

/**
 * Форматирует Instant в строку формата ISO 8601.
 *
 * kotlinx-datetime по умолчанию использует формат, который может включать 'Z' на конце.
 * Например: "2025-07-14T18:09:47.755110Z"
 * Этот формат полностью совместим со стандартом.
 *
 * @return Строка в формате ISO 8601.
 */
fun Instant.toIsoString(): String {
    // Просто вызываем toString(), который по умолчанию форматирует в ISO 8601 (UTC)
    return this.toString()
}

/**
 * Если вам нужно отформатировать дату в определенном часовом поясе (например, локальном)
 * и без 'Z' на конце, можно сделать так.
 *
 * @param timeZone Часовой пояс для форматирования.
 * @return Строка вида "2025-07-14T18:09:47.755110"
 */
fun Instant.toLocalDateTimeString(timeZone: TimeZone = TimeZone.UTC): String {
    // Преобразуем Instant в LocalDateTime в указанном часовом поясе
    val localDateTime = this.toLocalDateTime(timeZone)
    // toString() для LocalDateTime не добавляет 'Z'
    return localDateTime.toString()
}