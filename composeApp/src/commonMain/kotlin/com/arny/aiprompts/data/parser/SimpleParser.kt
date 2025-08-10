package com.arny.aiprompts.data.parser

import com.arny.aiprompts.domain.interfaces.IFileParser
import com.arny.aiprompts.domain.model.Author
import com.arny.aiprompts.domain.model.RawPostData
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Простой и надежный парсер, который реализует интерфейс IFileParser.
 * Его задача - извлечь только ту информацию, в которой мы уверены на 100%,
 * и провести базовую эвристическую оценку, является ли пост промптом.
 */
class SimpleParser : IFileParser {

    /**
     * Парсит строку с HTML-контентом и возвращает список "сырых" постов.
     * @param htmlContent Строка, содержащая HTML-код страницы форума.
     * @return Список объектов RawPostData.
     */
    override fun parse(htmlContent: String): List<RawPostData> {
        val document = Jsoup.parse(htmlContent)
        // Находим все контейнеры постов на странице
        return document.select("table[data-post]").mapNotNull { postElement ->
            try {
                // Извлекаем только 100% надежные данные. Если чего-то нет, пропускаем пост.
                val postId = postElement.attr("data-post").ifBlank { return@mapNotNull null }
                val authorName = postElement.selectFirst("span.normalname a")?.text() ?: "Unknown"
                val authorId = postElement.selectFirst("span.normalname a")?.attr("href")?.substringAfter("showuser=") ?: ""

                // Ищем дату редактирования, если ее нет - ищем дату создания.
                val editDateStr = postElement.selectFirst("span.edit")?.text()
                val createDateStr = postElement.selectFirst("td.row2[width='99%']")?.text()
                val date = parseDate(editDateStr ?: createDateStr)

                // Получаем весь HTML-контент тела поста для предпросмотра
                val contentElement = postElement.selectFirst("div.postcolor") ?: return@mapNotNull null
                val contentHtml = contentElement.html()

                // --- Простой "детектор промптов" (эвристика) ---
                val lowercasedHtml = contentHtml.lowercase()
                val attachment = postElement.selectFirst("a.attach-file[href*=.txt]")
                val isLikelyPrompt = lowercasedHtml.contains("промпт")
                        || lowercasedHtml.contains("prompt")
                        || attachment != null

                // Собираем и возвращаем модель с "сырыми" данными
                RawPostData(
                    postId = postId,
                    author = Author(id = authorId, name = authorName),
                    date = date,
                    fullHtmlContent = contentHtml,
                    isLikelyPrompt = isLikelyPrompt,
                    fileAttachmentUrl = attachment?.absUrl("href") // Сохраняем URL на .txt файл, если он есть
                )
            } catch (e: Exception) {
                // Если при парсинге одного конкретного поста произошла ошибка,
                // мы логируем ее и просто пропускаем этот пост, не прерывая весь процесс.
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Вспомогательная функция для парсинга даты из текстовой строки.
     * @param text Строка, потенциально содержащая дату (например, "Отредактировано: user, 21.10.25, 18:00").
     * @return Объект Instant. Если дата не найдена или ошибка парсинга, возвращается текущее время.
     */
    private fun parseDate(text: String?): Instant {
        val defaultInstant = Clock.System.now()
        if (text == null) return defaultInstant

        // Регулярное выражение для поиска даты в формате ДД.ММ.ГГ или ДД.ММ.ГГГГ, и времени ЧЧ:ММ
        val regex = "(\\d{2}\\.\\d{2}\\.\\d{2,4}),?\\s(\\d{2}:\\d{2})".toRegex()
        val match = regex.find(text)

        return match?.destructured?.let { (date, time) ->
            try {
                val dateParts = date.split(".").map { it.toInt() }
                val timeParts = time.split(":").map { it.toInt() }

                val day = dateParts[0]
                val month = dateParts[1]
                // Обрабатываем как двухзначный ("23"), так и четырехзначный ("2023") год
                val year = if (dateParts[2] < 100) 2000 + dateParts[2] else dateParts[2]
                val hour = timeParts[0]
                val minute = timeParts[1]

                // Создаем LocalDateTime и конвертируем в Instant (UTC)
                LocalDateTime(year, month, day, hour, minute)
                    .toInstant(TimeZone.UTC)
            } catch (e: Exception) {
                // В случае ошибки (например, некорректная дата "99.99.99"), возвращаем текущее время
                defaultInstant
            }
        } ?: defaultInstant
    }
}