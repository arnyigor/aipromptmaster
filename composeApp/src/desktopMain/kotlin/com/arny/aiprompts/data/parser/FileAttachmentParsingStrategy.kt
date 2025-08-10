package com.arny.aiprompts.data.parser

import com.arny.aiprompts.domain.model.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import org.jsoup.nodes.Element
import com.benasher44.uuid.uuid4

class FileAttachmentParsingStrategy(
    private val httpClient: HttpClient,
    private val config: ParserConfig
) : ParsingStrategy {

    override fun parse(postElement: Element): PromptData? {
        // 1. Сначала парсим пост так, как будто это STANDARD_PROMPT
        val baseData = StandardPromptParsingStrategy(config).parse(postElement) ?: return null

        // 2. Находим ссылку на файл
        val attachmentLink = postElement.selectFirst(config.selectors["attachmentLink"]!!.selector) ?: return baseData
        val fileUrl = attachmentLink.absUrl("href")

        // 3. Скачиваем содержимое файла
        val fileContent: String
        runBlocking {
            fileContent = httpClient.get(fileUrl).bodyAsText()
        }
        if (fileContent.isBlank()) return baseData // Если файл пуст, возвращаем базовые данные

        // 4. Обогащаем базовые данные
        // Добавляем содержимое файла как еще один вариант промпта
        val fileVariant = PromptVariant(type = "file_content", content = fileContent)

        return baseData.copy(
            variants = baseData.variants + fileVariant, // Добавляем к существующим вариантам
            category = "imported_file"
        )
    }
}