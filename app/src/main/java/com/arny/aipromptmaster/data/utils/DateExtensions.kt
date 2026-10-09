package com.arny.aipromptmaster.data.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.getDefault()).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

@OptIn(kotlin.time.ExperimentalTime::class)
fun String.toIsoDate(): Date = try {
    if (endsWith("Z") || matches(Regex(".*[+-]\\d{2}:\\d{2}$")))
        Date(kotlin.time.Instant.parse(this).toEpochMilliseconds())
    else synchronized(isoFormat) { isoFormat.parse(this) ?: Date() }
} catch (e: Exception) {
    Date()
}

fun Date.toIsoString(): String = synchronized(isoFormat) { isoFormat.format(this) }
