package com.arny.aiprompts.data.parser

import com.arny.aiprompts.domain.model.SelectorConfig
import org.jsoup.nodes.Element
import kotlin.text.Regex

class ConfigurableExtractor(private val config: Map<String, SelectorConfig>) {

    fun extract(element: Element, key: String): String? {
        // Получаем конфигурацию для нужного нам ключа (например, "authorName")
        val selectorConfig = config[key] ?: return null

        // --- ИСПРАВЛЕНИЕ ЗДЕСЬ ---
        // Используем selectorConfig, а не несуществующий selector
        val targetElement = element.selectFirst(selectorConfig.selector) ?: return null

        // Получаем "сырое" значение - либо из атрибута, либо текст элемента
        val rawValue = if (selectorConfig.attribute != null) {
            targetElement.attr(selectorConfig.attribute)
        } else {
            targetElement.text()
        }

        // Применяем регулярное выражение, если оно есть
        return if (selectorConfig.regex != null) {
            val regex = Regex(selectorConfig.regex)
            val match = regex.find(rawValue)

            if (match != null) {
                // Безопасно извлекаем группу 1, если она есть, иначе группу 0
                if (match.groups.size > 1) {
                    match.groupValues[1]
                } else {
                    match.value
                }
            } else {
                null
            }
        } else {
            // Если регулярного выражения нет, возвращаем сырое значение
            rawValue
        }
    }
}