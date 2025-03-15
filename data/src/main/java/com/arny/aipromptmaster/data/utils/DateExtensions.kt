package com.arny.aipromptmaster.data.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.getDefault()).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

fun String.toDate(): Date = try {
    isoFormat.parse(this) ?: Date()
} catch (e: Exception) {
    Date()
}

fun Date.toIsoString(): String = isoFormat.format(this) 