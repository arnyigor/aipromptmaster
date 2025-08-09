package com.arny.aiprompts.data.parser

import com.arny.aiprompts.domain.model.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.jsoup.nodes.Element

// Базовый интерфейс
sealed interface ParsingStrategy {
    fun parse(postElement: Element): PromptData?
}

// Стратегия для стандартных промптов
class StandardPromptParsingStrategy : ParsingStrategy {
    override fun parse(postElement: Element): PromptData? {
        try {
            val postId = postElement.attr("data-post").ifBlank { return null }
            val authorName = postElement.selectFirst("span.normalname")?.text() ?: "Unknown"
            val authorId = postElement.selectFirst("span.normalname a")?.attr("href")?.substringAfter("showuser=") ?: ""

            // --- ПАРСИНГ ДАТЫ ---
            // Пример строки: "Отредактировано: arny, 18.07.2025 - 20:05"
            val editInfo = postElement.selectFirst("span.edit")?.text()
            val dateInstant = parseDate(editInfo)

            // --- ПАРСИНГ КОНТЕНТА ---
            val spoilers = postElement.select("div.post-block.spoil .block-body")
            if (spoilers.isEmpty()) return null

            val title = postElement.selectFirst("div.post-block.spoil .block-title")?.text()
                ?.replace("ПРОМПТ №\\d+".toRegex(), "")?.trim() ?: "Prompt $postId"
            val content = spoilers.first()?.html()?.replace("<br>", "\n") ?: return null
            val description = spoilers.getOrNull(1)?.text() ?: content.take(200)

            return PromptData(
                id = postId,
                title = title.ifBlank { "Prompt $postId" },
                description = description,
                variants = listOf(PromptVariant(content = content)),
                author = Author(id = authorId, name = authorName),
                createdAt = dateInstant.toEpochMilliseconds(),
                updatedAt = dateInstant.toEpochMilliseconds(),
                category = "imported" // Ставим категорию "imported"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun parseDate(text: String?): Instant {
        val defaultInstant = Clock.System.now()
        if (text == null) return defaultInstant

        // Regex для поиска "ДД.ММ.ГГГГ - ЧЧ:ММ"
        val regex = "(\\d{2}\\.\\d{2}\\.\\d{4}\\s-\\s\\d{2}:\\d{2})".toRegex()
        val match = regex.find(text)

        return match?.value?.let { dateString ->
            try {
                // Пытаемся распарсить дату и время
                val parts = dateString.split(" - ")
                val dateParts = parts[0].split(".")
                val timeParts = parts[1].split(":")

                val day = dateParts[0].toInt()
                val month = dateParts[1].toInt()
                val year = dateParts[2].toInt()
                val hour = timeParts[0].toInt()
                val minute = timeParts[1].toInt()

                // Создаем LocalDateTime и конвертируем в Instant
                LocalDateTime(year, month, day, hour, minute)
                    .toInstant(TimeZone.UTC) // Используем UTC, т.к. таймзона на форуме неизвестна
            } catch (e: Exception) {
                // Если парсинг не удался, возвращаем текущее время
                defaultInstant
            }
        } ?: defaultInstant
    }
}

// "Пустая" стратегия для постов, которые мы не хотим парсить
class DiscussionParsingStrategy : ParsingStrategy {
    override fun parse(postElement: Element): PromptData? {
        // Просто возвращаем null, чтобы этот пост был проигнорирован
        return null
    }
}

// TODO: Реализовать другие стратегии (FileAttachment, Jailbreak, etc.)