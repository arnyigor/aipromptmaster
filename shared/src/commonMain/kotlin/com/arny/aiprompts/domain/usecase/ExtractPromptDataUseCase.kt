package com.arny.aiprompts.domain.usecase

import com.arny.aiprompts.domain.model.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * UseCase for extracting structured prompt data from raw 4pda posts.
 * Implements improved parsing logic for title, description, and content.
 */
@OptIn(ExperimentalTime::class)
class ExtractPromptDataUseCase {

    companion object {
        private const val MIN_CONTENT_LENGTH = 50
        private val TITLE_REGEX = Regex("""ПРОМПТ\s*[#№]?\s*\d*[:\-]?\s*(.+?)$""", RegexOption.IGNORE_CASE)
        private val ENGLISH_WORDS = setOf(
            "the", "be", "to", "of", "and", "a", "in", "that", "have", "i",
            "it", "for", "not", "on", "with", "he", "as", "you", "do", "at",
            "this", "but", "his", "by", "from", "they", "we", "say", "her", "she"
        )
    }

    data class ExtractedPromptData(
        val title: String,
        val description: String,
        val contentRu: String,
        val contentEn: String?,
        val author: Author,
        val postDate: Instant,
        val sourceId: String,
        val sourceUrl: String?,
        val isHighQuality: Boolean,
        val hasEnglishContent: Boolean
    )

    @OptIn(ExperimentalTime::class)
    operator fun invoke(rawPost: RawPostData): ExtractedPromptData? {
        // Parse HTML content
        val document = Jsoup.parse(rawPost.fullHtmlContent)
        val body = document.body()

        // Find spoilers (prompts are typically in spoilers on 4pda)
        val spoilers = body.select("div.post-block.spoil")
        if (spoilers.isEmpty()) {
            // Try alternative parsing for non-spoiler posts
            return parseNonSpoilerPost(rawPost, body)
        }

        // First spoiler usually contains the main prompt
        val firstSpoiler = spoilers.firstOrNull()
            ?: return parseNonSpoilerPost(rawPost, body)
        
        val spoilerTitle = firstSpoiler.selectFirst(".block-title")?.text()?.trim() ?: ""
        val spoilerContent = extractSpoilerContent(firstSpoiler)

        // Extract title from spoiler title or generate from content
        val title = extractTitle(spoilerTitle, spoilerContent)

        // Extract description from text before first spoiler
        val description = extractDescription(body, firstSpoiler)

        // Separate Russian and English content
        val (contentRu, contentEn) = separateLanguages(spoilerContent)

        // Check quality
        val isHighQuality = contentRu.length >= MIN_CONTENT_LENGTH

        return ExtractedPromptData(
            title = title,
            description = description,
            contentRu = contentRu,
            contentEn = contentEn?.takeIf { it.length >= MIN_CONTENT_LENGTH },
            author = rawPost.author,
            postDate = rawPost.date,
            sourceId = rawPost.postId,
            sourceUrl = rawPost.postUrl,
            isHighQuality = isHighQuality,
            hasEnglishContent = contentEn != null && contentEn.length > 20
        )
    }

    private fun extractSpoilerContent(spoiler: Element): String {
        val bodyElement = spoiler.selectFirst(".block-body")
        return cleanHtmlToText(bodyElement?.html() ?: "")
    }

    private fun extractTitle(spoilerTitle: String, content: String): String {
        // Try to extract from "ПРОМПТ #123: Title" format
        val match = TITLE_REGEX.find(spoilerTitle)
        if (match != null) {
            return match.groupValues[1].trim()
        }

        // If spoiler title is meaningful (not just "Промпт"), use it
        if (spoilerTitle.length > 5 && !spoilerTitle.lowercase().contains("промпт")) {
            return spoilerTitle.trim()
        }

        // Generate title from first sentence of content
        val firstSentence = content.split(".", "?", "!")
            .firstOrNull { it.trim().length in 10..100 }
            ?.trim()

        return firstSentence ?: "Промпт от ${content.take(30)}..."
    }

    private fun extractDescription(body: Element, firstSpoiler: Element): String {
        // Get all text before the first spoiler
        val descriptionBuilder = StringBuilder()
        var foundSpoiler = false

        for (element in body.children()) {
            if (element == firstSpoiler || element.selectFirst("div.post-block.spoil") == firstSpoiler) {
                foundSpoiler = true
                break
            }
            if (!foundSpoiler) {
                val text = cleanHtmlToText(element.html())
                if (text.isNotBlank()) {
                    descriptionBuilder.append(text).append(" ")
                }
            }
        }

        var description = descriptionBuilder.toString().trim()

        // If no description found before spoiler, take first paragraph of content
        if (description.isBlank()) {
            description = firstSpoiler.selectFirst(".block-body")
                ?.text()
                ?.split("\n\n", "<br><br>")
                ?.firstOrNull { it.length in 20..300 }
                ?.take(200)
                ?: ""
        }

        return description
    }

    private fun separateLanguages(content: String): Pair<String, String?> {
        // Split by common bilingual separators
        val separators = listOf(
            "--- English ---", "--- Английский ---",
            "English:", "Английский:",
            "EN:", "RU:",
            "[en]", "[EN]"
        )

        for (separator in separators) {
            val parts = content.split(separator, ignoreCase = true)
            if (parts.size >= 2) {
                val ruPart = parts[0].trim()
                val enPart = parts[1].trim()
                return Pair(ruPart, enPart)
            }
        }

        // Try to detect if content is mixed
        val paragraphs = content.split("\n\n", "<br><br>", "\r\n\r\n")
        val ruParagraphs = mutableListOf<String>()
        val enParagraphs = mutableListOf<String>()

        for (paragraph in paragraphs) {
            val cleanText = paragraph.trim()
            if (cleanText.isBlank()) continue

            val words = cleanText.lowercase().split(Regex("\\s+"))
            val englishWordCount = words.count { it in ENGLISH_WORDS }
            val totalWords = words.size

            // If more than 30% common English words, consider it English
            if (totalWords > 5 && englishWordCount.toFloat() / totalWords > 0.3f) {
                enParagraphs.add(cleanText)
            } else {
                ruParagraphs.add(cleanText)
            }
        }

        return if (enParagraphs.isNotEmpty()) {
            Pair(ruParagraphs.joinToString("\n\n"), enParagraphs.joinToString("\n\n"))
        } else {
            Pair(content, null)
        }
    }

    private fun parseNonSpoilerPost(rawPost: RawPostData, body: Element): ExtractedPromptData? {
        val text = cleanHtmlToText(body.html())
        if (text.length < MIN_CONTENT_LENGTH) return null

        val paragraphs = text.split("\n\n", "<br><br>")
        val title = paragraphs.firstOrNull { it.length in 10..100 }?.take(80) ?: "Промпт ${rawPost.postId}"
        val description = paragraphs.getOrNull(1)?.take(200) ?: text.take(200)
        val content = text

        return ExtractedPromptData(
            title = title,
            description = description,
            contentRu = content,
            contentEn = null,
            author = rawPost.author,
            postDate = rawPost.date,
            sourceId = rawPost.postId,
            sourceUrl = rawPost.postUrl,
            isHighQuality = content.length >= MIN_CONTENT_LENGTH,
            hasEnglishContent = false
        )
    }

    private fun cleanHtmlToText(html: String?): String {
        if (html.isNullOrBlank()) return ""
        return Jsoup.parse(html).text()
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}