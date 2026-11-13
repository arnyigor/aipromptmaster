package com.arny.aiprompts.data.parser

import com.arny.aiprompts.domain.interfaces.IHybridParser
import com.arny.aiprompts.presentation.ui.importer.EditedPostData
import com.arny.aiprompts.presentation.ui.importer.PromptVariantData
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

class HybridParserImpl : IHybridParser {

    override fun analyzeAndExtract(htmlContent: String): EditedPostData? {
        val doc = Jsoup.parseBodyFragment(htmlContent)
        val contentBody = doc.selectFirst("div.postcolor") ?: doc.body()

        // --- 1. Чистка от мусора (изображения, подписи) ---
        contentBody.select(".edit").remove() // Удаляем надпись "Сообщение отредактировал"
        contentBody.select("div.post-block.spoil").forEach { spoiler ->
            val title = spoiler.selectFirst(".block-title")?.text().orEmpty()
            if (title.contains("Прикрепленные изображения", true)) {
                spoiler.remove()
            }
        }

        // --- 2. Умный поиск вариантов (с учетом вложенности) ---
        // Ищем только спойлеры, которые НЕ содержат внутри других спойлеров.
        // Это позволяет игнорировать "Сборники" и брать только конечные "Листья"-промпты.
        val allSpoilers = contentBody.select("div.post-block.spoil")
        val leafSpoilers = allSpoilers.filter { it.select("div.post-block.spoil").isEmpty() }

        val variants = mutableListOf<PromptVariantData>()

        if (leafSpoilers.isNotEmpty()) {
            // СТРАТЕГИЯ А: Есть спойлеры -> берем их как варианты
            leafSpoilers.forEach { spoiler ->
                val variantTitle = spoiler.selectFirst(".block-title")?.text()?.trim() ?: "Вариант"
                // Берем тело спойлера
                val bodyElement = spoiler.selectFirst(".block-body") ?: return@forEach
                val variantContent = parseHtmlToPromptText(bodyElement)

                if (variantContent.isNotBlank()) {
                    variants.add(PromptVariantData(title = variantTitle, content = variantContent))
                }
            }
        } else {
            // СТРАТЕГИЯ Б: Нет спойлеров -> весь пост это один промпт
            // Ищем блоки кода, так как промпты часто там
            val codeBlocks = contentBody.select("div.code-box")
            if (codeBlocks.isNotEmpty()) {
                codeBlocks.forEachIndexed { index, element ->
                    val codeText = parseHtmlToPromptText(element.selectFirst(".code-body") ?: element)
                    variants.add(PromptVariantData(title = "Code Block ${index + 1}", content = codeText))
                }
            } else {
                // Если нет ни спойлеров, ни кода -> берем весь текст поста
                val fullText = parseHtmlToPromptText(contentBody)
                if (fullText.length > 20) { // Фильтр от совсем коротких сообщений "Спасибо"
                    variants.add(PromptVariantData(title = "General Content", content = fullText))
                }
            }
        }

        if (variants.isEmpty()) return null

        // --- 3. Описание (все что осталось снаружи вариантов) ---
        val descriptionElement = contentBody.clone()
        // Удаляем из клона все найденные leaf-спойлеры, чтобы получить чистый контекст/описание
        // (Если пост был без спойлеров, description будет дублировать content, можно очистить)
        if (leafSpoilers.isNotEmpty()) {
            descriptionElement.select("div.post-block.spoil").filter { it.select("div.post-block.spoil").isEmpty() }.forEach { it.remove() }
        } else {
            descriptionElement.empty() // Если мы забрали весь текст как вариант, описание пустое
        }
        val description = parseHtmlToPromptText(descriptionElement)

        // --- 4. Заголовок ---
        val firstVariantTitle = variants.firstOrNull()?.title ?: ""
        val cleanTitle = firstVariantTitle
            .replace("(?i)ПРОМПТ( №\\s*\\d+)?".toRegex(), "") // Удаляем "ПРОМПТ №1"
            .replace("(?i)Спойлер".toRegex(), "")
            .trim()
            .takeIf { it.isNotEmpty() }
            ?: description.lines().firstOrNull { it.isNotBlank() }?.take(50)
            ?: "New Prompt"

        return EditedPostData(
            title = cleanTitle,
            description = description,
            content = variants.first().content,
            variants = variants
        )
    }

    // Важная функция для сохранения переносов строк
    private fun parseHtmlToPromptText(element: Element): String {
        val sb = StringBuilder()
        element.childNodes().forEach { node ->
            when (node) {
                is TextNode -> sb.append(node.text())
                is Element -> {
                    if (node.tagName() == "br") sb.append("\n")
                    else if (node.tagName() == "p" || node.tagName() == "div") {
                        sb.append("\n").append(parseHtmlToPromptText(node)).append("\n")
                    } else {
                        sb.append(parseHtmlToPromptText(node))
                    }
                }
            }
        }
        return sb.toString().trim()
    }
}
