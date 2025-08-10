package com.arny.aiprompts.data.parser

import com.arny.aiprompts.domain.interfaces.IHybridParser
import com.arny.aiprompts.presentation.ui.importer.EditedPostData
import com.arny.aiprompts.presentation.ui.importer.PromptVariantData
import org.jsoup.Jsoup

class HybridParserImpl : IHybridParser {

    override fun analyzeAndExtract(htmlContent: String): EditedPostData? {
        val postElement = Jsoup.parse("<body>$htmlContent</body>").body()
        val contentBody = postElement.selectFirst("div.postcolor") ?: return null

        // --- 1. Находим все спойлеры, игнорируя те, что с изображениями ---
        val allSpoilers = contentBody.select("div.post-block.spoil")
        val promptSpoilers = allSpoilers
            .filterNot { it.selectFirst(".block-title")?.text()?.contains("Прикрепленные изображения", true) == true }

        // --- НОВАЯ ЛОГИКА ИЗВЛЕЧЕНИЯ ВАРИАНТОВ ---
        val variants = promptSpoilers.mapNotNull { spoiler ->
            val variantTitle = spoiler.selectFirst(".block-title")?.text()?.trim() ?: "Вариант"
            val variantContent = cleanHtmlToText(spoiler.selectFirst(".block-body"))
            if (variantContent.isNotBlank()) {
                PromptVariantData(title = variantTitle, content = variantContent)
            } else {
                null
            }
        }

        // Если вариантов нет, это не промпт
        if (variants.isEmpty()) return null

        // --- 4. Извлекаем ОПИСАНИЕ (все, что находится ВНЕ спойлеров) ---
        val descriptionElement = contentBody.clone()
        // Удаляем из клона все спойлеры, чтобы остался только текст описания
        descriptionElement.select("div.post-block.spoil").remove()
        val description = cleanHtmlToText(descriptionElement)

        // --- 5. Извлекаем ЗАГОЛОВОК по приоритетам ---
        val title = // Приоритет 1: Заголовок первого спойлера
            promptSpoilers.first().selectFirst(".block-title")?.text()
                ?.replace("ПРОМПТ №?\\d+?".toRegex(), "")?.trim()
                // Приоритет 2: Первая осмысленная строка описания
                ?: description.lines().firstOrNull { it.trim().length in 5..100 }
                // Приоритет 3: Fallback
                ?: ""

        return EditedPostData(
            title = title,
            description = description,
            content = variants.first().content, // Основной контент - из первого варианта
            variants = variants
        )
    }
}
