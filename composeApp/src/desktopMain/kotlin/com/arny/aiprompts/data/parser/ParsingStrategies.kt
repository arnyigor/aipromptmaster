package com.arny.aiprompts.data.parser

import com.arny.aiprompts.domain.model.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.jsoup.nodes.Element
import com.arny.aiprompts.domain.model.ParserConfig
import com.arny.aiprompts.data.parser.cleanHtmlToText
import com.benasher44.uuid.uuid4

sealed interface ParsingStrategy {
    fun parse(postElement: Element): PromptData? // Принимает Element
}

// Стратегия для стандартных промптов
class StandardPromptParsingStrategy(
    private val config: ParserConfig
) : ParsingStrategy {
    override fun parse(postElement: Element): PromptData? {
        val extractor = ConfigurableExtractor(config.selectors)
        try {
            val postId = postElement.attr("data-post").ifBlank { return null }
            val authorName = extractor.extract(postElement, "authorName") ?: "Unknown"
            val authorId = extractor.extract(postElement, "authorId") ?: ""

            // --- ПРИОРИТЕТНАЯ ДАТА РЕДАКТИРОВАНИЯ ---
            val editDateStr = extractor.extract(postElement, "editDate")
            val createDateStr = extractor.extract(postElement, "creationDate")
            val dateInstant = parseDate(editDateStr ?: createDateStr)

            val contentBody = postElement.selectFirst(config.selectors["contentBody"]!!.selector) ?: return null
            val allSpoilers = contentBody.select(config.selectors["spoilers"]!!.selector)
            val firstSpoiler = allSpoilers.firstOrNull()

            // --- НОВАЯ ЛОГИКА ИЗВЛЕЧЕНИЯ ---
            // 1. Описание - это все, что ДО первого спойлера.
            val descriptionHtml = Element("div")
            firstSpoiler?.previousElementSiblings()?.forEach { descriptionHtml.appendChild(it.clone()) }
            var description = cleanHtmlToText(descriptionHtml)
            
            // 2. Контент - это содержимое первого спойлера
            val content = cleanHtmlToText(firstSpoiler?.selectFirst(config.selectors["spoilerBody"]!!.selector))
            if (content.isBlank()) return null // Если нет контента, это не промпт

            // 3. Если есть второй спойлер, добавляем его содержимое к описанию
            allSpoilers.getOrNull(1)?.let {
                description += "\n\n" + cleanHtmlToText(it)
            }
            if (description.isBlank()) {
                description = content.take(200) + "..."
            }

            // 4. Улучшенная логика извлечения заголовка
            var title = firstSpoiler?.selectFirst(config.selectors["spoilerTitle"]!!.selector)?.text()
                ?.replace("ПРОМПТ №\\d+".toRegex(), "")?.trim()

            if (title.isNullOrBlank()) {
                title = description.lines().firstOrNull { it.trim().length in 5..80 }
            }
            if (title.isNullOrBlank()) {
                title = "Prompt $postId"
            }
            // --- КОНЕЦ НОВОЙ ЛОГИКИ ---

            return PromptData(
                id = uuid4().toString(),
                sourceId = postId,
                title = title,
                description = description.trim(),
                variants = listOf(PromptVariant(content = content)),
                author = Author(id = authorId, name = authorName),
                createdAt = dateInstant.toEpochMilliseconds(),
                updatedAt = dateInstant.toEpochMilliseconds(),
                category = "imported"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}

fun parseDate(text: String?): Instant {
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

// "Пустая" стратегия для постов, которые мы не хотим парсить
class DiscussionParsingStrategy : ParsingStrategy {
    override fun parse(postElement: Element): PromptData? {
        // Просто возвращаем null, чтобы этот пост был проигнорирован
        return null
    }
}


class ExternalResourceParsingStrategy(private val config: ParserConfig) : ParsingStrategy {
    override fun parse(postElement: Element): PromptData? {
        // Используем StandardPromptParsingStrategy, чтобы получить базовые данные (автор, дата, title)
        val baseData = StandardPromptParsingStrategy(config).parse(postElement) ?: return null

        val contentBody = postElement.selectFirst(config.selectors["contentBody"]!!.selector) ?: return null
        val firstLink = contentBody.selectFirst("a")?.absUrl("href")

        // Если не удалось извлечь ссылку, пропускаем
        if (firstLink.isNullOrBlank()) return null

        // Заменяем description и content на информацию о ссылке
        return baseData.copy(
            description = "Внешний ресурс. Оригинальный текст: ${baseData.description}",
            variants = listOf(PromptVariant(type = "link", content = firstLink))
        )
    }
}

// TODO: Реализовать другие стратегии (Jailbreak, etc.)