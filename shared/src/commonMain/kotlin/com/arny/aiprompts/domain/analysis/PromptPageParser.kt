package com.arny.aiprompts.domain.analysis

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.File

/**
 * Parses individual prompt pages from 4pda forum HTML.
 * Extracts prompt content, metadata, and attachments from posts.
 */
class PromptPageParser {

    companion object {
        /** Pattern to extract prompt number from text */
        private val PROMPT_NUMBER_PATTERN = Regex("""[Пп]РОМПТ\s*[№#]\s*(\d+)""")
        
        /** BBCode and HTML tags to remove */
        private val CLEANUP_PATTERNS = listOf(
            Regex("""\[/?[^\]]+\]"""),  // BBCode tags
            Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE),  // <br> tags
            Regex("""&nbsp;"""),  // HTML entities
            Regex("""\s+""")  // Multiple spaces
        )
    }

    /**
     * Result of parsing a prompt page.
     */
    data class ParseResult(
        val success: Boolean,
        val postId: String?,
        val promptTitle: String?,
        val promptContent: String?,
        val cleanContent: String?,
        val attachments: List<Attachment>,
        val error: String? = null
    )

    /**
     * Attachment reference (file in ЗАКРЕП).
     */
    data class Attachment(
        val name: String,
        val url: String?,
        val type: AttachmentType = AttachmentType.UNKNOWN
    )

    enum class AttachmentType {
        UNKNOWN,
        TEXT,      // .txt, .md
        CODE,      // .py, .js, .json, etc.
        IMAGE,     // .jpg, .png, etc.
        LINK       // Link to external resource
    }

    /**
     * Parse a prompt page from HTML file.
     * 
     * HTML Structure on 4pda:
     * ```
     * <div class="post" data-post="ID">
     *   <div class="postcolor" id="post-ID">
     *     <div class="post-block spoil">
     *       <div class="block-title">ПРОМПТ № ХХХ - НАЗВАНИЕ</div>
     *       <div class="block-body">
     *         [B]ПРОМПТ № ХХХ[/B]
     *         НАЗВАНИЕ (НАЗНАЧЕНИЕ)
     *         ФАЙЛЫ В ЗАКРЕП
     *         ТЕЛО ПРОМПТА
     *       </div>
     *     </div>
     *   </div>
     * </div>
     * ```
     *
     * @param htmlFile The HTML file to parse
     * @param targetPostId The post ID to look for (from index)
     * @return ParseResult with extracted prompt data
     */
    suspend fun parsePage(htmlFile: File, targetPostId: String): ParseResult = 
        withContext(Dispatchers.IO) {
            try {
                if (!htmlFile.exists()) {
                    return@withContext ParseResult(
                        success = false,
                        postId = null,
                        promptTitle = null,
                        promptContent = null,
                        cleanContent = null,
                        attachments = emptyList(),
                        error = "File not found: ${htmlFile.absolutePath}"
                    )
                }

                val html = htmlFile.readText(Charsets.UTF_8)
                parseHtml(html, targetPostId)
                
            } catch (e: Exception) {
                ParseResult(
                    success = false,
                    postId = null,
                    promptTitle = null,
                    promptContent = null,
                    cleanContent = null,
                    attachments = emptyList(),
                    error = "Failed to parse ${htmlFile.name}: ${e.message}"
                )
            }
        }

    /**
     * Parse prompt from HTML string.
     */
    suspend fun parseHtml(html: String, targetPostId: String): ParseResult = 
        withContext(Dispatchers.Default) {
            try {
                val document = Jsoup.parse(html)

                // 1. Ищем таблицу поста
                val table = document.selectFirst("table.ipbtable[data-post=\"$targetPostId\"]")
                    ?: document.selectFirst("div.post[data-post=\"$targetPostId\"]")
                    ?: return@withContext ParseResult(false, targetPostId, null, null, null, emptyList(), "Post $targetPostId not found")

                // 2. Ищем КОНКРЕТНУЮ ячейку с контентом (ID обычно post-main-XXXXXX)
                // Если нет ID, ищем ячейку с классом post1 или post2
                val targetPost = table.selectFirst("td[id^=post-main-]")
                    ?: table.selectFirst(".post1, .post2")
                    ?: table.selectFirst("div.postcolor") // Fallback

                if (targetPost == null) {
                    return@withContext ParseResult(false, targetPostId, null, null, null, emptyList(), "Content cell not found")
                }

                // Find prompt blocks within the post
                val promptBlock = findPromptBlock(targetPost, targetPostId) ?: return@withContext ParseResult(
                    success = false,
                    postId = targetPostId,
                    promptTitle = null,
                    promptContent = null,
                    cleanContent = null,
                    attachments = emptyList(),
                    error = "Prompt block not found in post $targetPostId"
                )

                // Extract data
                val title = extractTitle(promptBlock, targetPostId)
                val content = extractContent(promptBlock)
                val cleanContent = cleanContent(content)
                val attachments = extractAttachments(promptBlock)

                ParseResult(
                    success = true,
                    postId = targetPostId,
                    promptTitle = title,
                    promptContent = content,
                    cleanContent = cleanContent,
                    attachments = attachments,
                    error = null
                )
                
            } catch (e: Exception) {
                ParseResult(
                    success = false,
                    postId = targetPostId,
                    promptTitle = null,
                    promptContent = null,
                    cleanContent = null,
                    attachments = emptyList(),
                    error = "Parse error: ${e.message}"
                )
            }
        }

    /**
     * Find the prompt block within a post.
     * Looks for div.post-block.spoil containing ПРОМПТ
     */
    private fun findPromptBlock(post: Element, postId: String): Element? {
        val postColor = post.selectFirst(".postcolor") ?: return null

        // 1. Пытаемся найти спойлер, даже если заголовок пустой или не содержит "ПРОМПТ"
        // Иногда промпт спрятан просто в спойлере без спец. заголовка
        val spoilers = postColor.select("div.post-block.spoil")

        // Если есть спойлеры, берем первый (обычно там контент)
        if (spoilers.isNotEmpty()) {
            return spoilers.first()
        }

        // 2. Если спойлеров нет, проверяем, содержит ли пост ключевые слова (ПРОМПТ, PROMPT)
        val text = postColor.text()
        if (text.contains("ПРОМПТ", ignoreCase = true) || text.contains("PROMPT", ignoreCase = true)) {
            return postColor
        }
//        println("DEBUG: Failed to find content in post $postId. HTML: ${post.html().take(300)}")
        return null
    }

    /**
     * Extract prompt title from block title or content.
     */
    private fun extractTitle(block: Element, postId: String): String? {
        // Try block-title first
        val blockTitle = block.selectFirst(".block-title")?.text()?.trim()
        if (!blockTitle.isNullOrBlank()) {
            // Clean up title (remove number prefix)
            val cleaned = blockTitle.replaceFirst(PROMPT_NUMBER_PATTERN, "").trim()
            if (cleaned.isNotBlank()) {
                return cleaned
            }
        }
        
        // Try to find title in content
        val content = block.text()
        val match = PROMPT_NUMBER_PATTERN.find(content)
        if (match != null) {
            // Extract title after prompt number
            val afterNumber = content.substringAfter(match.value, "")
            val title = afterNumber
                .substringBefore("\n")
                .substringBefore("[")
                .trim()
            if (title.isNotBlank()) {
                return title
            }
        }
        
        // Fallback to postId
        return "Prompt #$postId"
    }

    /**
     * Extract prompt content from block body.
     */
    private fun extractContent(block: Element): String {
        // Если это спойлер, берем его тело
        if (block.hasClass("post-block")) {
            val body = block.selectFirst(".block-body")
            if (body != null) {
                return body.html()
                    .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
                    .trim()
            }
        }
        val blockBody = block.selectFirst(".block-body, .block-content, .content") 
            ?: block
        
        // Get HTML content
        var content = blockBody.html()
        
        // Clean up but keep structure
        content = content
            .replace(Regex("""&nbsp;"""), " ")
            .replace(Regex("""<br\s*/?>"""), "\n")
            .replace(Regex("""\n{3,}"""), "\n\n")
        
        return content.trim()
    }

    /**
     * Clean HTML content to plain text.
     */
    private fun cleanContent(html: String?): String? {
        if (html.isNullOrBlank()) return null
        
        var text: String = html
        
        // Remove all HTML and BBCode tags
        CLEANUP_PATTERNS.forEach { pattern ->
            text = pattern.replace(text, " ")
        }
        
        // Clean up whitespace
        text = text
            .replace(Regex("""\s+"""), " ")
            .trim()
        
        return text.ifBlank { null }
    }

    /**
     * Extract attachment references from block body.
     * Looks for "ФАЙЛЫ В ЗАКРЕП" section.
     */
    private fun extractAttachments(block: Element): List<Attachment> {
        val attachments = mutableListOf<Attachment>()
        
        val blockText = block.text()
        
        // Check for "ФАЙЛЫ В ЗАКРЕП" section
        if (!blockText.contains("ФАЙЛЫ В ЗАКРЕП", ignoreCase = true)) {
            return emptyList()
        }
        
        // Find attachment links
        val links = block.select("a[href]")
        
        for (link in links) {
            val href = link.attr("href")
            val text = link.text().trim()
            
            if (text.isBlank()) continue
            
            val attachment = Attachment(
                name = text,
                url = href,
                type = determineAttachmentType(href, text)
            )
            attachments.add(attachment)
        }
        
        return attachments
    }

    /**
     * Determine attachment type from URL and name.
     */
    private fun determineAttachmentType(url: String?, name: String): AttachmentType {
        if (url == null) return AttachmentType.UNKNOWN
        
        val lowerUrl = url.lowercase()
        val lowerName = name.lowercase()
        
        return when {
            // Links to external resources
            url.startsWith("http") -> {
                when {
                    lowerUrl.contains("pastebin") || 
                    lowerUrl.contains("gist") ||
                    lowerUrl.contains("github") -> AttachmentType.LINK
                    else -> AttachmentType.LINK
                }
            }
            // File extensions
            lowerName.endsWith(".txt") || lowerName.endsWith(".md") -> AttachmentType.TEXT
            lowerName.endsWith(".py") || 
            lowerName.endsWith(".js") ||
            lowerName.endsWith(".json") ||
            lowerName.endsWith(".java") ||
            lowerName.endsWith(".cpp") -> AttachmentType.CODE
            lowerName.endsWith(".jpg") || 
            lowerName.endsWith(".jpeg") ||
            lowerName.endsWith(".png") ||
            lowerName.endsWith(".gif") -> AttachmentType.IMAGE
            else -> AttachmentType.UNKNOWN
        }
    }

    /**
     * Extract prompt number from text.
     */
    fun extractPromptNumber(text: String): String? {
        val match = PROMPT_NUMBER_PATTERN.find(text)
        return match?.groupValues?.get(1)
    }
}
