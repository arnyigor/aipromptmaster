package com.arny.aiprompts.data.parser

import com.arny.aiprompts.domain.interfaces.LLMService
import com.arny.aiprompts.domain.model.PostType
import org.jsoup.nodes.Element

class PostClassifier(
    private val llmService: LLMService
) {

    private companion object {
        // Селекторы для извлечения данных из HTML
        const val CONTENT_SELECTOR = "div.postcolor"
        const val TITLE_SELECTOR = "div.post-block.spoil .block-title"
        const val TXT_ATTACHMENT_SELECTOR = "a.attach-file[href*=.txt]"

        // Пороговые значения для эвристики
        const val SHORT_POST_LENGTH_THRESHOLD = 150
        const val LINK_TEXT_RATIO_THRESHOLD = 0.5

        // Правила на основе ключевых слов. Легко расширять.
        val KEYWORD_RULES = listOf(
            Rule(PostType.JAILBREAK, "jailbreak", "джейлбрейк"),
            Rule(PostType.TEMPLATE_PROMPT, "шаблон промпта", "шаблон"),
            Rule(PostType.META_PROMPT, "мета-промпт"),
            // Правило для стандартного промпта требует особой логики (startsWith)
            // и обрабатывается отдельно.
        )
    }

    /**
     * Классифицирует пост, определяя его тип.
     * Логика работает по шагам:
     * 1. Применяются быстрые эвристические правила (например, для ссылок).
     * 2. Применяются жёсткие правила (вложения, ключевые слова).
     * 3. Если ни одно правило не сработало, используется LLM для классификации.
     */
    suspend fun classify(postElement: Element): PostType {
        val contentElement = postElement.selectFirst(CONTENT_SELECTOR) ?: return PostType.DISCUSSION
        val title = postElement.selectFirst(TITLE_SELECTOR)?.text().orEmpty()
        val content = contentElement.text()

        // Попытка классифицировать пост с помощью локальных правил и эвристик
        val ruleBasedClassification = classifyWithRulesAndHeuristics(postElement, contentElement, title, content)
        if (ruleBasedClassification != null) {
            return ruleBasedClassification
        }

        // Если правила не дали результата, обращаемся к LLM
        return llmService.classifyPost(content).getOrDefault(PostType.DISCUSSION)
    }

    private fun classifyWithRulesAndHeuristics(
        postElement: Element,
        contentElement: Element,
        title: String,
        content: String
    ): PostType? {
        // 1. Эвристика для постов, состоящих преимущественно из ссылок
        if (isExternalResource(contentElement, content)) {
            return PostType.EXTERNAL_RESOURCE
        }

        // 2. Проверка на наличие прикрепленного .txt файла
        if (postElement.selectFirst(TXT_ATTACHMENT_SELECTOR) != null) {
            return PostType.FILE_ATTACHMENT
        }

        val lowercasedContent = content.lowercase()
        val lowercasedTitle = title.lowercase()

        // 3. Правило для стандартного промпта (проверка префикса)
        if (lowercasedContent.startsWith("промпт №") || lowercasedTitle.contains("промпт №")) {
            return PostType.STANDARD_PROMPT
        }

        // 4. Поиск по списку правил на основе ключевых слов
        for (rule in KEYWORD_RULES) {
            if (rule.matches(lowercasedTitle, lowercasedContent)) {
                return rule.type
            }
        }

        return null // Ни одно правило не подошло
    }

    /**
     * Проверяет, является ли пост, скорее всего, просто набором ссылок.
     */
    private fun isExternalResource(contentElement: Element, contentText: String): Boolean {
        if (contentText.length >= SHORT_POST_LENGTH_THRESHOLD) return false

        val links = contentElement.select("a")
        if (links.isEmpty() || contentText.isBlank()) return false

        val totalLinkTextLength = links.sumOf { it.text().length }
        val linkTextRatio = totalLinkTextLength.toDouble() / contentText.length

        return linkTextRatio > LINK_TEXT_RATIO_THRESHOLD
    }

    /**
     * Вспомогательный класс для хранения правила классификации.
     */
    private data class Rule(val type: PostType, val keywords: List<String>) {
        constructor(type: PostType, vararg keywords: String) : this(type, keywords.toList())

        fun matches(title: String, content: String): Boolean {
            return keywords.any { keyword ->
                title.contains(keyword) || content.contains(keyword)
            }
        }
    }
}
