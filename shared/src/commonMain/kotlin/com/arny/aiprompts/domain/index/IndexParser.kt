package com.arny.aiprompts.domain.index

import com.arny.aiprompts.domain.index.model.IndexLink
import com.arny.aiprompts.domain.index.model.IndexParseResult
import com.arny.aiprompts.domain.index.model.ParsedIndex
import com.arny.aiprompts.domain.index.model.PostLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Parses the index (table of contents) from the first page of 4pda topic.
 * Extracts links to individual prompt posts from spoilers.
 */
class IndexParser {

    companion object {
        /** Regex to extract post ID from 4pda URLs */
        private val POST_ID_REGEX = Regex("""[?&]p=(\d+)""")
        
        /** Regex to extract topic ID from URL */
        private val TOPIC_ID_REGEX = Regex("""showtopic=(\d+)""")
        
        /** Base URL for 4pda forum */
        private const val BASE_URL = "https://4pda.to/forum"
    }

    /**
     * Parse index from HTML content.
     * Extracts all links from spoilers in the first post.
     */
    suspend fun parseIndex(htmlContent: String, sourceUrl: String): IndexParseResult = 
        withContext(Dispatchers.Default) {
            try {
                val document = Jsoup.parse(htmlContent)
                val topicId = extractTopicId(sourceUrl) ?: "unknown"
                
                // Find the first post (original post with index)
                // 4pda uses table.ipbtable[data-post] for posts
                val firstPost = document.selectFirst("table.ipbtable[data-post]")
                    ?: document.selectFirst("div.post[data-post]")
                    ?: return@withContext IndexParseResult.Error("First post not found in HTML")
                
                // Extract all spoilers from the first post
                // 4pda uses div.post-block.spoil or .block-title structure
                val spoilers = firstPost.select("div.post-block.spoil")
                    .ifEmpty { firstPost.select("div.spoiler") }
                    .ifEmpty { 
                        // Try to find any block-title that looks like a spoiler
                        firstPost.select(".block-title").mapNotNull { title ->
                            val parent = title.parent() ?: return@mapNotNull null
                            if (parent.hasClass("post-block") ||
                                parent.hasClass("spoil") ||
                                parent.hasClass("spoiler") ||
                                parent.className().contains("spoil")) {
                                parent
                            } else null
                        }
                    }
                
                if (spoilers.isEmpty()) {
                    return@withContext IndexParseResult.Error("No spoilers found in first post")
                }

                val allLinks = mutableListOf<IndexLink>()
                val currentTime = 0L // TODO: Use proper time source
                
                spoilers.forEachIndexed { index, spoiler ->
                    val spoilerTitle = spoiler.selectFirst(".block-title")?.text()?.trim()
                        ?: spoiler.selectFirst("[class*=block-title]")?.text()?.trim()
                        ?: spoiler.selectFirst("a[name*='Spoil']")?.attr("name")
                    
                    val category = extractCategory(spoiler, spoilerTitle)
                    
                    // Find all links in this spoiler
                    val links = spoiler.select("a[href]")
                    
                    links.forEach { linkElement ->
                        val href = linkElement.attr("href")
                        val fullUrl = resolveUrl(href)
                        
                        // Extract post ID from URL
                        val postId = extractPostId(fullUrl)
                        
                        if (postId != null && isPromptLink(fullUrl)) {
                            allLinks.add(IndexLink(
                                postId = postId,
                                originalUrl = fullUrl,
                                spoilerTitle = spoilerTitle,
                                category = category,
                                parsedAt = currentTime
                            ))
                        }
                    }
                }

                // Remove duplicates by postId
                val uniqueLinks = allLinks.distinctBy { it.postId }
                
                if (uniqueLinks.isEmpty()) {
                    return@withContext IndexParseResult.Error("No valid prompt links found in spoilers")
                }

                val parsedIndex = ParsedIndex(
                    topicId = topicId,
                    sourceUrl = sourceUrl,
                    links = uniqueLinks,
                    parsedAt = currentTime
                )

                IndexParseResult.Success(parsedIndex)
                
            } catch (e: Exception) {
                IndexParseResult.Error("Failed to parse index: ${e.message}", e)
            }
        }

    /**
     * Update parsed index with page locations after resolving redirects.
     */
    fun updateWithPageLocations(index: ParsedIndex, locations: List<PostLocation>): ParsedIndex {
        val linksWithPages = index.links.map { link ->
            val location = locations.find { it.postId == link.postId }
            link.copy(pageOffset = location?.pageOffset)
        }
        
        // Group by page offset
        val linksByPage = linksWithPages
            .filter { it.pageOffset != null }
            .groupBy { it.pageOffset!! }
        
        return index.copy(
            links = linksWithPages,
            linksByPage = linksByPage
        )
    }

    /**
     * Extract post ID from 4pda URL.
     * Example: https://4pda.to/forum/index.php?showtopic=1109539&view=findpost&p=138452835
     * Returns: 138452835
     */
    private fun extractPostId(url: String): String? {
        val match = POST_ID_REGEX.find(url)
        return match?.groupValues?.get(1)
    }

    /**
     * Extract topic ID from URL.
     */
    private fun extractTopicId(url: String): String? {
        val match = TOPIC_ID_REGEX.find(url)
        return match?.groupValues?.get(1)
    }

    /**
     * Check if URL is a valid prompt post link.
     */
    private fun isPromptLink(url: String): Boolean {
        // Must be 4pda.to domain
        if (!url.contains("4pda.to")) return false
        
        // Must have post ID
        if (extractPostId(url) == null) return false
        
        // Should be a findpost link or anchor link
        return url.contains("view=findpost") || url.contains("#entry")
    }

    /**
     * Resolve relative URL to absolute.
     */
    private fun resolveUrl(href: String): String {
        return when {
            href.startsWith("http") -> href
            href.startsWith("/") -> "https://4pda.to$href"
            else -> "$BASE_URL/$href"
        }
    }

    /**
     * Extract category name from spoiler structure.
     */
    private fun extractCategory(spoiler: Element, spoilerTitle: String?): String? {
        // Try to find parent category spoiler
        val parent = spoiler.parent()
        
        // If this is nested spoiler, try to get parent title
        if (parent != null) {
            val parentSpoiler = parent.selectFirst("div.post-block.spoil")
            if (parentSpoiler != null) {
                return parentSpoiler.selectFirst(".block-title")?.text()?.trim()
            }
        }
        
        // Use spoiler title as category if it looks like category
        if (spoilerTitle != null && 
            (spoilerTitle.contains("Промпт", ignoreCase = true) ||
             spoilerTitle.contains("Категория", ignoreCase = true) ||
             spoilerTitle.contains("Раздел", ignoreCase = true))) {
            return spoilerTitle
        }
        
        return null
    }

    /**
     * Get statistics about parsed index.
     */
    fun getIndexStats(index: ParsedIndex): IndexStats {
        val totalLinks = index.links.size
        val withPages = index.links.count { it.pageOffset != null }
        val byCategory = index.links.groupingBy { it.category ?: "Unknown" }.eachCount()
        
        return IndexStats(
            totalLinks = totalLinks,
            linksWithResolvedPages = withPages,
            linksWithoutPages = totalLinks - withPages,
            uniqueCategories = byCategory.keys.size,
            linksByCategory = byCategory
        )
    }

    data class IndexStats(
        val totalLinks: Int,
        val linksWithResolvedPages: Int,
        val linksWithoutPages: Int,
        val uniqueCategories: Int,
        val linksByCategory: Map<String, Int>
    )
}