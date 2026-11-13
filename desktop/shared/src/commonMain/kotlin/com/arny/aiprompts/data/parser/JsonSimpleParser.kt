package com.arny.aiprompts.data.parser

object JsonSimpleParser {
    // Ищет ключ "source_id" и возвращает его значение
    private val sourceIdRegex = "\"source_id\"\\s*:\\s*\"([^\"]+)\"".toRegex()

    fun parseSourceId(jsonContent: String): String? {
        return sourceIdRegex.find(jsonContent)?.groupValues?.get(1)
    }
}