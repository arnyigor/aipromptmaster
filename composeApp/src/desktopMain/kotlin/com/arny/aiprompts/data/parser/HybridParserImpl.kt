package com.arny.aiprompts.data.parser

import com.arny.aiprompts.domain.interfaces.IHybridParser
import com.arny.aiprompts.presentation.ui.importer.ExtractedPromptData
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

class HybridParserImpl : IHybridParser {

    override fun analyzeAndExtract(htmlContent: String): ExtractedPromptData? {
        // Мы парсим не весь HTML, а только фрагмент контента поста,
        // поэтому оборачиваем его в body для корректной работы Jsoup.
        val postElement = Jsoup.parse("<body>$htmlContent</body>").body()
        
        // --- 1. Извлекаем все структурные блоки ---
        val spoilers = postElement.select("div.post-block.spoil")
        val attachmentLink = postElement.selectFirst("a.attach-file[href*=.txt]")

        // --- 2. Эвристика: если нет ни спойлеров, ни вложений, это, скорее всего, не промпт ---
        if (spoilers.isEmpty() && attachmentLink == null) {
            return null
        }

        var title = ""
        var description = ""
        var content = ""

        // --- 3. Логика принятия решений на основе структуры ---
        if (attachmentLink != null) {
            title = attachmentLink.text().removeSuffix(".txt").trim()
            content = "[Содержимое из файла: ${attachmentLink.text()}]"
            description = cleanHtmlToText(postElement)
        } else if (spoilers.isNotEmpty()) {
            val firstSpoiler = spoilers.first()!!
            
            title = firstSpoiler.selectFirst(".block-title")?.text()?.replace("ПРОМПТ №\\d+".toRegex(), "")?.trim() ?: ""
            content = cleanHtmlToText(firstSpoiler.selectFirst(".block-body"))

            // --- ИСПРАВЛЕННАЯ ЛОГИКА ИЗВЛЕЧЕНИЯ ОПИСАНИЯ ---
            val descriptionParts = mutableListOf<String>()
            var currentNode = firstSpoiler.previousSibling()
            while (currentNode != null) {
                when (currentNode) {
                    is TextNode -> descriptionParts.add(currentNode.text())
                    is Element -> descriptionParts.add(cleanHtmlToText(currentNode))
                }
                currentNode = currentNode.previousSibling()
            }
            val preSpoilerDescription = descriptionParts.reversed().joinToString("\n").trim()
            
            val secondSpoilerContent = if (spoilers.size > 1) {
                cleanHtmlToText(spoilers[1].selectFirst(".block-body"))
            } else ""

            description = (preSpoilerDescription + "\n\n" + secondSpoilerContent).trim()

            if (title.isBlank()) {
                title = description.lines().firstOrNull { it.trim().length in 5..100 } ?: ""
            }
        }

        if (content.isBlank()) return null

        return ExtractedPromptData(
            title = title,
            description = description,
            content = content
        )
    }
}