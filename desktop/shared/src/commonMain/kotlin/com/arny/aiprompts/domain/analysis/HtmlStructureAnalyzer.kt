package com.arny.aiprompts.domain.analysis

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Analyzes HTML structure of 4pda forum posts.
 * Helps understand post structure for correct parsing.
 */
class HtmlStructureAnalyzer {

    /**
     * Result of HTML structure analysis.
     */
    data class AnalysisResult(
        val isValid: Boolean,
        val postId: String?,
        val structure: PostStructure,
        val issues: List<StructureIssue>,
        val suggestions: List<String>
    )

    /**
     * Parsed structure of a forum post.
     */
    data class PostStructure(
        val hasPostContainer: Boolean,
        val hasDataPost: Boolean,
        val postId: String?,
        val authorInfo: AuthorInfo?,
        val contentBlocks: List<ContentBlock>,
        val spoilerCount: Int,
        val quoteCount: Int,
        val linkCount: Int,
        val imageCount: Int,
        val estimatedContentLength: Int
    )

    /**
     * Author information from the post.
     */
    data class AuthorInfo(
        val name: String?,
        val id: String?,
        val profileUrl: String?
    )

    /**
     * A block of content within the post.
     */
    data class ContentBlock(
        val type: BlockType,
        val html: String,
        val text: String,
        val position: Int
    )

    /**
     * Types of content blocks.
     */
    enum class BlockType {
        TEXT,
        SPOILER,
        QUOTE,
        IMAGE,
        LINK,
        ATTACHMENT,
        UNKNOWN
    }

    /**
     * Issue found during analysis.
     */
    data class StructureIssue(
        val severity: IssueSeverity,
        val code: String,
        val message: String
    )

    /**
     * Severity levels for issues.
     */
    enum class IssueSeverity {
        INFO,
        WARNING,
        ERROR
    }

    /**
     * Analyze HTML structure and return detailed report.
     */
    fun analyze(htmlContent: String): AnalysisResult {
        val issues = mutableListOf<StructureIssue>()
        val suggestions = mutableListOf<String>()

        val document = Jsoup.parse(htmlContent)
        val body = document.body() ?: return AnalysisResult(
            isValid = false,
            postId = null,
            structure = PostStructure(
                hasPostContainer = false,
                hasDataPost = false,
                postId = null,
                authorInfo = null,
                contentBlocks = emptyList(),
                spoilerCount = 0,
                quoteCount = 0,
                linkCount = 0,
                imageCount = 0,
                estimatedContentLength = 0
            ),
            issues = listOf(StructureIssue(IssueSeverity.ERROR, "NO_BODY", "HTML body is empty")),
            suggestions = listOf("Check if HTML is valid")
        )

        // Find post container
        val postElement = findPostElement(body)
        
        if (postElement == null) {
            issues.add(StructureIssue(
                IssueSeverity.ERROR,
                "NO_POST_ELEMENT",
                "Could not find post element with data-post attribute"
            ))
            suggestions.add("Check if this is a valid 4pda forum post")
            
            return AnalysisResult(
                isValid = false,
                postId = null,
                structure = parseFallbackStructure(body),
                issues = issues,
                suggestions = suggestions
            )
        }

        // Extract post ID
        val postId = postElement.attr("data-post")
        val hasDataPost = postId.isNotBlank()

        if (!hasDataPost) {
            issues.add(StructureIssue(
                IssueSeverity.WARNING,
                "NO_POST_ID",
                "Post element does not have data-post attribute"
            ))
        }

        // Extract author info
        val authorInfo = extractAuthorInfo(postElement)

        // Extract content blocks
        val contentBlocks = extractContentBlocks(postElement)

        // Count elements
        val spoilerCount = postElement.select("div.post-block.spoil").size
        val quoteCount = postElement.select("div.post-block.quote").size
        val linkCount = postElement.select("a[href]").size
        val imageCount = postElement.select("img").size
        val estimatedContentLength = postElement.selectFirst("div.postcolor")?.text()?.length ?: 0

        // Analyze content quality
        if (estimatedContentLength < 50) {
            issues.add(StructureIssue(
                IssueSeverity.WARNING,
                "SHORT_CONTENT",
                "Content length ($estimatedContentLength chars) is less than 50"
            ))
        }

        // Check for spoiler structure
        if (spoilerCount > 0) {
            suggestions.add("Post has $spoilerCount spoiler(s) - prompts are likely inside")
            
            val firstSpoiler = postElement.selectFirst("div.post-block.spoil")
            val spoilerTitle = firstSpoiler?.selectFirst(".block-title")?.text()
            if (spoilerTitle != null) {
                suggestions.add("First spoiler title: '$spoilerTitle'")
            }
        }

        // Check for likely prompt indicators
        val postText = postElement.text().lowercase()
        val promptIndicators = detectPromptIndicators(postText)
        if (promptIndicators.isNotEmpty()) {
            suggestions.add("Possible prompt indicators found: ${promptIndicators.joinToString(", ")}")
        }

        return AnalysisResult(
            isValid = true,
            postId = postId,
            structure = PostStructure(
                hasPostContainer = true,
                hasDataPost = hasDataPost,
                postId = postId,
                authorInfo = authorInfo,
                contentBlocks = contentBlocks,
                spoilerCount = spoilerCount,
                quoteCount = quoteCount,
                linkCount = linkCount,
                imageCount = imageCount,
                estimatedContentLength = estimatedContentLength
            ),
            issues = issues,
            suggestions = suggestions
        )
    }

    /**
     * Find the main post element.
     */
    private fun findPostElement(body: Element): Element? {
        // Try different selectors
        return body.selectFirst("div.post[data-post]")
            ?: body.selectFirst("div[id^='post_']")
            ?: body.selectFirst("div.post")
    }

    /**
     * Extract author information from post.
     */
    private fun extractAuthorInfo(post: Element): AuthorInfo? {
        val authorLink = post.selectFirst("a[href*='showuser']")
        val authorName = authorLink?.text()?.trim()
        val authorId = authorLink?.attr("href")?.let { extractUserId(it) }
        
        return if (authorName != null || authorId != null) {
            AuthorInfo(
                name = authorName,
                id = authorId,
                profileUrl = authorLink?.attr("abs:href")
            )
        } else null
    }

    /**
     * Extract user ID from profile URL.
     */
    private fun extractUserId(url: String): String? {
        val regex = Regex("""showuser=(\d+)""")
        return regex.find(url)?.groupValues?.get(1)
    }

    /**
     * Extract content blocks from post.
     */
    private fun extractContentBlocks(post: Element): List<ContentBlock> {
        val blocks = mutableListOf<ContentBlock>()
        val postColor = post.selectFirst("div.postcolor") ?: post
        
        var position = 0
        
        // Process spoilers
        postColor.select("div.post-block.spoil").forEach { spoiler ->
            val title = spoiler.selectFirst(".block-title")?.text()?.trim() ?: ""
            val body = spoiler.selectFirst(".block-body")?.html() ?: ""
            val text = spoiler.selectFirst(".block-body")?.text() ?: ""
            
            blocks.add(ContentBlock(
                type = BlockType.SPOILER,
                html = body,
                text = text,
                position = position++
            ))
        }

        // Process quotes
        postColor.select("div.post-block.quote").forEach { quote ->
            val html = quote.html()
            blocks.add(ContentBlock(
                type = BlockType.QUOTE,
                html = html,
                text = Jsoup.parse(html).text(),
                position = position++
            ))
        }

        // Process remaining text
        val remainingText = getRemainingText(postColor, blocks.map { it.html })
        if (remainingText.isNotBlank()) {
            blocks.add(ContentBlock(
                type = BlockType.TEXT,
                html = remainingText,
                text = Jsoup.parse(remainingText).text(),
                position = position++
            ))
        }

        return blocks
    }

    /**
     * Get remaining text that hasn't been captured by blocks.
     */
    private fun getRemainingText(container: Element, capturedHtml: List<String>): String {
        // This is a simplified version - in real implementation would compare DOM
        return container.text()
    }

    /**
     * Detect indicators that this post might contain a prompt.
     */
    private fun detectPromptIndicators(text: String): List<String> {
        val indicators = mutableListOf<String>()
        
        when {
            text.contains("промпт") || text.contains("prompt") -> 
                indicators.add("'промпт'/'prompt' keyword")
            text.contains("ты ") || text.contains("вы ") -> 
                indicators.add("role-based instruction ('ты'/'вы')")
            text.contains("####") || text.contains("## ") -> 
                indicators.add("markdown headers")
            text.contains("```") -> 
                indicators.add("code blocks")
            text.contains("задача:") || text.contains("задание:") -> 
                indicators.add("task specification")
        }
        
        return indicators
    }

    /**
     * Parse fallback structure when post element not found.
     */
    private fun parseFallbackStructure(body: Element): PostStructure {
        return PostStructure(
            hasPostContainer = false,
            hasDataPost = false,
            postId = null,
            authorInfo = null,
            contentBlocks = emptyList(),
            spoilerCount = body.select("div.post-block.spoil").size,
            quoteCount = body.select("div.post-block.quote").size,
            linkCount = body.select("a[href]").size,
            imageCount = body.select("img").size,
            estimatedContentLength = body.text().length
        )
    }
}

/**
 * Simple reporter for HtmlStructureAnalyzer results.
 */
object StructureReporter {

    private const val LINE_WIDTH = 80

    fun print(result: HtmlStructureAnalyzer.AnalysisResult) {
        println("=".repeat(LINE_WIDTH))
        println("HTML STRUCTURE ANALYSIS")
        println("=".repeat(LINE_WIDTH))

        println("\n[VALIDITY]")
        println("Valid: ${result.isValid}")
        println("Post ID: ${result.postId ?: "N/A"}")

        println("\n[STRUCTURE]")
        val s = result.structure
        println("  Has post container: ${s.hasPostContainer}")
        println("  Has data-post: ${s.hasDataPost}")
        println("  Spoilers: ${s.spoilerCount}")
        println("  Quotes: ${s.quoteCount}")
        println("  Links: ${s.linkCount}")
        println("  Images: ${s.imageCount}")
        println("  Content length: ${s.estimatedContentLength} chars")

        s.authorInfo?.let { author ->
            println("\n[AUTHOR]")
            println("  Name: ${author.name ?: "N/A"}")
            println("  ID: ${author.id ?: "N/A"}")
        }

        println("\n[CONTENT BLOCKS]")
        s.contentBlocks.forEachIndexed { idx, block ->
            println("  ${idx + 1}. ${block.type}")
            println("     Text preview: ${block.text.take(100)}...")
        }

        if (result.issues.isNotEmpty()) {
            println("\n[ISSUES]")
            result.issues.forEach { issue ->
                println("  [${issue.severity}] ${issue.code}: ${issue.message}")
            }
        }

        if (result.suggestions.isNotEmpty()) {
            println("\n[SUGGESTIONS]")
            result.suggestions.forEach { suggestion ->
                println("  • $suggestion")
            }
        }

        println("\n" + "=".repeat(LINE_WIDTH))
    }

    fun toMarkdown(result: HtmlStructureAnalyzer.AnalysisResult): String {
        return buildString {
            appendLine("# HTML Structure Analysis")
            appendLine()
            appendLine("## Overview")
            appendLine("- Valid: `${result.isValid}`")
            appendLine("- Post ID: `${result.postId ?: "N/A"}`")
            appendLine()
            appendLine("## Structure")
            appendLine("- Spoilers: `${result.structure.spoilerCount}`")
            appendLine("- Quotes: `${result.structure.quoteCount}`")
            appendLine("- Links: `${result.structure.linkCount}`")
            appendLine("- Content length: `${result.structure.estimatedContentLength}` chars")
            appendLine()
            
            result.suggestions.forEach { suggestion ->
                appendLine("- $suggestion")
            }
        }
    }
}
