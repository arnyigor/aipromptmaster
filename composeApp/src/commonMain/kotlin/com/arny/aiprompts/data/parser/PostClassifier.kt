package com.arny.aiprompts.data.parser

import com.arny.aiprompts.domain.interfaces.LLMService
import com.arny.aiprompts.domain.model.PostType
import org.jsoup.nodes.Element

// Пока без LLM, только правила
class PostClassifier(
    private val llmService: LLMService
) {
    suspend fun classify(postElement: Element): PostType {
        val textContent = postElement.select("div.postcolor").text().lowercase()
        val title = postElement.selectFirst("div.post-block.spoil .block-title")?.text()?.lowercase() ?: ""

        // Правила с высоким приоритетом
        if (postElement.selectFirst("a.attach-file[href*=.txt]") != null) {
            return PostType.FILE_ATTACHMENT
        }
        if (textContent.contains("jailbreak") || title.contains("джейлбрейк")) {
            return PostType.JAILBREAK
        }
        if (textContent.startsWith("промпт №") || title.contains("промпт №")) {
            return PostType.STANDARD_PROMPT
        }
        if (textContent.contains("шаблон промпта") || title.contains("шаблон")) {
            return PostType.TEMPLATE_PROMPT
        }
        if (textContent.contains("мета-промпт") || title.contains("мета-промпт")) {
            return PostType.META_PROMPT
        }

        // Если правила не сработали, обращаемся к LLM
        val llmResult = llmService.classifyPost(textContent)
        return llmResult.getOrDefault(PostType.DISCUSSION)
    }
}