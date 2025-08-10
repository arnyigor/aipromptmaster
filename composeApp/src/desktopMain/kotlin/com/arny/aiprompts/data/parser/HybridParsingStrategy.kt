package com.arny.aiprompts.data.parser

import com.arny.aiprompts.presentation.ui.importer.EditedPostData
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node

/**
 * Гибридная стратегия, которая пытается извлечь структурированные данные.
 * Если не получается, возвращает null, сигнализируя, что это, вероятно, не промпт.
 */
class HybridParsingStrategy {

    fun extract(postElement: Element): EditedPostData? {
        val contentBody = postElement.selectFirst("div.postcolor") ?: return null

        // --- 1. Проверяем самые сильные признаки промпта ---
        val spoilers = contentBody.select("div.post-block.spoil")
        val hasTxtAttachment = postElement.selectFirst("a.attach-file[href*=.txt]") != null

        // Если нет ни спойлеров, ни .txt файла, скорее всего, это не промпт
        if (spoilers.isEmpty() && !hasTxtAttachment) {
            return null
        }

        // --- 2. Извлекаем данные ---
        val title: String

        val firstSpoiler = spoilers.firstOrNull()

        // Собираем все узлы до первого спойлера
        val descriptionNodes = mutableListOf<Node>()
        var prev = firstSpoiler?.previousSibling()
        while (prev != null) {
            descriptionNodes.add(0, prev.clone())
            prev = prev.previousSibling()
        }

        val descriptionHtml = Element("div")
        descriptionNodes.forEach { descriptionHtml.appendChild(it) }

        val description = cleanHtmlToText(descriptionHtml)
        val content = cleanHtmlToText(firstSpoiler?.selectFirst(".block-body"))

        // Простая эвристика для заголовка
        title = firstSpoiler?.selectFirst(".block-title")?.text()
            ?.trim()
            ?: description.lines().firstOrNull()?.take(80)
                    ?: ""

        // Если после всех усилий контент пуст, это не промпт
        if (content.isBlank()) return null

        return EditedPostData(
            title = title,
            description = description,
            content = content
        )
    }
}