package com.arny.aiprompts.data.parser

import org.jsoup.Jsoup

// Модель для одного блока контента
data class ParsedPostBlock(
    val type: BlockType,
    val content: String,
    val title: String? = null // Для спойлеров
)

enum class BlockType {
    TEXT, SPOILER, QUOTE
}

/**
 * Парсер, который "нарезает" HTML-контент поста на структурированные блоки.
 */
class PostStructureParser {
    fun parse(htmlContent: String): List<ParsedPostBlock> {
        val document = Jsoup.parse(htmlContent).body()
        val blocks = mutableListOf<ParsedPostBlock>()

        document.children().forEach { element ->
            when {
                // Если это спойлер
                element.hasClass("post-block") && element.hasClass("spoil") -> {
                    val title = element.selectFirst(".block-title")?.text()
                    val bodyElement = element.selectFirst(".block-body")
                    val body = bodyElement?.text() ?: ""
                    blocks.add(ParsedPostBlock(BlockType.SPOILER, body, title))
                }
                // Если это цитата
                element.hasClass("post-block") && element.hasClass("quote") -> {
                    val body = element.text()
                    blocks.add(ParsedPostBlock(BlockType.QUOTE, body))
                }
                // Любой другой элемент с текстом
                else -> {
                    val text = element.text()
                    if (text.isNotBlank()) {
                        blocks.add(ParsedPostBlock(BlockType.TEXT, text))
                    }
                }
            }
        }
        return blocks
    }
}