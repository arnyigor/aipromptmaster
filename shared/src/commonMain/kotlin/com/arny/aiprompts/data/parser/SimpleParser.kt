package com.arny.aiprompts.data.parser

import com.arny.aiprompts.domain.interfaces.IFileParser
import com.arny.aiprompts.domain.model.Author
import com.arny.aiprompts.domain.model.FileAttachment
import com.arny.aiprompts.domain.model.FileType
import com.arny.aiprompts.domain.model.RawPostData
import kotlinx.datetime.*
import org.jsoup.Jsoup

/**
 * Простой и надежный парсер, который реализует интерфейс IFileParser.
 * Его задача - извлечь только ту информацию, в которой мы уверены на 100%,
 * и провести базовую эвристическую оценку, является ли пост промптом.
 */
class SimpleParser : IFileParser {

    /**
     * Определяет тип файла по его расширению
     */
    private fun determineFileType(filename: String): FileType {
        val extension = filename.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "txt", "md", "rtf" -> FileType.TEXT
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg" -> FileType.IMAGE
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx" -> FileType.DOCUMENT
            "zip", "rar", "7z", "tar", "gz" -> FileType.ARCHIVE
            else -> FileType.OTHER
        }
    }

    /**
     * Извлекает размер файла из текста ссылки (если указан)
     */
    private fun extractFileSize(linkText: String): String? {
        val sizeRegex = "(\\d+(?:\\.\\d+)?\\s*(?:KB|MB|GB|bytes?|B))".toRegex(RegexOption.IGNORE_CASE)
        return sizeRegex.find(linkText)?.value
    }

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
                val authorId =
                    postElement.selectFirst("span.normalname a")?.attr("href")?.substringAfter("showuser=") ?: ""

                // Ищем дату редактирования, если ее нет - ищем дату создания.
                val editDateStr = postElement.selectFirst("span.edit")?.text()
                val createDateStr = postElement.selectFirst("td.row2[width='99%']")?.text()
                val date = parseDate(editDateStr ?: createDateStr)
                val updatedDate = if (editDateStr != null) parseDate(editDateStr) else null

                // Получаем весь HTML-контент тела поста для предпросмотра
                val contentElement = postElement.selectFirst("div.postcolor") ?: return@mapNotNull null
                val contentHtml = contentElement.html()

                // --- Извлекаем ссылку на пост ---
                val postUrl = postElement.selectFirst("a[href*='showtopic']")?.absUrl("href")
                    ?: postElement.selectFirst("a[href*='index.php?showtopic']")?.absUrl("href")

                // --- Извлекаем все вложения ---
                val attachments = postElement.select("a.attach-file").mapNotNull { link ->
                    val href = link.absUrl("href")
                    val filename = link.text().trim()
                    if (href.isNotBlank() && filename.isNotBlank()) {
                        FileAttachment(
                            url = href,
                            filename = filename,
                            fileSize = extractFileSize(link.text()),
                            fileType = determineFileType(filename)
                        )
                    } else null
                }

                // --- Простой "детектор промптов" (эвристика) ---
                val lowercasedHtml = contentHtml.lowercase()
                val hasTxtAttachment = attachments.any { it.fileType == FileType.TEXT }
                val isLikelyPrompt = lowercasedHtml.contains("промпт")
                        || lowercasedHtml.contains("prompt")
                        || hasTxtAttachment

                // Для обратной совместимости сохраняем первый .txt файл
                val firstTxtAttachment = attachments.firstOrNull { it.fileType == FileType.TEXT }

                // Собираем и возвращаем модель с "сырыми" данными
                RawPostData(
                    postId = postId,
                    author = Author(id = authorId, name = authorName),
                    date = date,
                    updatedDate = updatedDate,
                    fullHtmlContent = contentHtml,
                    isLikelyPrompt = isLikelyPrompt,
                    fileAttachmentUrl = firstTxtAttachment?.url, // Для обратной совместимости
                    attachments = attachments,
                    postUrl = postUrl
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

        // Регулярное выражение для поиска даты в формате ДД.ММ.ГГ или ДД.ММ.ГГГГ, время ЧЧ:ММ:СС опционально
        val regexWithSeconds = "(\\d{2}\\.\\d{2}\\.\\d{2,4}),?\\s(\\d{2}:\\d{2}:\\d{2})".toRegex()
        val regexWithTime = "(\\d{2}\\.\\d{2}\\.\\d{2,4}),?\\s(\\d{2}:\\d{2})".toRegex()
        val regexDateOnly = "(\\d{2}\\.\\d{2}\\.\\d{2,4})".toRegex()

        val matchWithSeconds = regexWithSeconds.find(text)
        val matchWithTime = regexWithTime.find(text)
        val matchDateOnly = regexDateOnly.find(text)

        return when {
            matchWithSeconds != null -> {
                matchWithSeconds.destructured.let { (date, time) ->
                    try {
                        val dateParts = date.split(".").map { it.toInt() }
                        val timeParts = time.split(":").map { it.toInt() }

                        val day = dateParts[0]
                        val month = dateParts[1]
                        // Обрабатываем как двухзначный ("23"), так и четырехзначный ("2023") год
                        val year = if (dateParts[2] < 100) 2000 + dateParts[2] else dateParts[2]
                        val hour = timeParts[0]
                        val minute = timeParts[1]
                        val second = timeParts[2]

                        // Создаем LocalDateTime и конвертируем в Instant (UTC)
                        LocalDateTime(year, month, day, hour, minute, second)
                            .toInstant(TimeZone.UTC)
                    } catch (e: Exception) {
                        defaultInstant
                    }
                }
            }
            matchWithTime != null -> {
                matchWithTime.destructured.let { (date, time) ->
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
                        defaultInstant
                    }
                }
            }
            matchDateOnly != null -> {
                matchDateOnly.destructured.let { (date) ->
                    try {
                        val dateParts = date.split(".").map { it.toInt() }

                        val day = dateParts[0]
                        val month = dateParts[1]
                        // Обрабатываем как двухзначный ("23"), так и четырехзначный ("2023") год
                        val year = if (dateParts[2] < 100) 2000 + dateParts[2] else dateParts[2]

                        // Создаем LocalDateTime с временем 00:00 и конвертируем в Instant (UTC)
                        LocalDateTime(year, month, day, 0, 0)
                            .toInstant(TimeZone.UTC)
                    } catch (e: Exception) {
                        defaultInstant
                    }
                }
            }
            else -> defaultInstant
        }
    }
}