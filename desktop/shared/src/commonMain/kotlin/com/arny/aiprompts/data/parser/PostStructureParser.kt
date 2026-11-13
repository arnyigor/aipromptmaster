package com.arny.aiprompts.data.parser

import com.arny.aiprompts.domain.model.FileAttachment
import org.jsoup.Jsoup

// Модель для одного блока контента
data class ParsedPostBlock(
    val type: BlockType,
    val content: String,
    val title: String? = null, // Для спойлеров
    val attachment: FileAttachment? = null // Для вложений
)

enum class BlockType {
    TEXT, SPOILER, QUOTE, ATTACHMENT
}

/**
 * Парсер, который "нарезает" HTML-контент поста на структурированные блоки.
 */
class PostStructureParser {
    fun parse(htmlContent: String, attachments: List<FileAttachment> = emptyList()): List<ParsedPostBlock> {
        val document = Jsoup.parse(htmlContent).body()
        val blocks = mutableListOf<ParsedPostBlock>()

        // Добавляем текстовые блоки из HTML
        document.children().forEach { element ->
            when {
                // Если это спойлер
                element.hasClass("post-block") && element.hasClass("spoil") -> {
                    val title = element.selectFirst(".block-title")?.text()
                    val bodyElement = element.selectFirst(".block-body")
                    val body = bodyElement?.html()?.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n") ?: ""
                    blocks.add(ParsedPostBlock(BlockType.SPOILER, body, title))
                }
                // Если это цитата
                element.hasClass("post-block") && element.hasClass("quote") -> {
                    val body = element.html().replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                    blocks.add(ParsedPostBlock(BlockType.QUOTE, body))
                }
                // Любой другой элемент с текстом
                else -> {
                    val text = element.html().replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                    if (text.isNotBlank()) {
                        blocks.add(ParsedPostBlock(BlockType.TEXT, text))
                    }
                }
            }
        }

        // Добавляем блоки вложений
        attachments.forEach { attachment ->
            blocks.add(ParsedPostBlock(
                type = BlockType.ATTACHMENT,
                content = attachment.filename,
                attachment = attachment
            ))
        }

        return blocks
    }
}