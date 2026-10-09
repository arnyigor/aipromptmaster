package com.arny.aiprompts.domain.index.model

import kotlinx.serialization.Serializable

/**
 * Represents a single link found in the index (table of contents) post.
 * Contains information about where to find the actual prompt post.
 */
@Serializable
data class IndexLink(
    /** Post ID from the link (p=XXXXX parameter) */
    val postId: String,
    
    /** Original URL as found in the spoiler */
    val originalUrl: String,
    
    /** Title of the spoiler containing this link */
    val spoilerTitle: String? = null,
    
    /** Category or section name (from parent spoiler structure) */
    val category: String? = null,
    
    /** Calculated page location (st parameter) after resolving redirect */
    val pageOffset: Int? = null,
    
    /** Timestamp when this link was parsed */
    val parsedAt: Long = 0L
)

/**
 * Complete parsed index from the first page.
 */
@Serializable
data class ParsedIndex(
    /** Topic ID (from URL) */
    val topicId: String,
    
    /** Source URL of the index page */
    val sourceUrl: String,
    
    /** All unique links found in spoilers */
    val links: List<IndexLink>,
    
    /** Grouped links by their calculated page offset */
    val linksByPage: Map<Int, List<IndexLink>> = emptyMap(),
    
    /** Timestamp when index was parsed */
    val parsedAt: Long = 0L
) {
    /** Whether this cached index is still valid */
    fun isValid(ttlHours: Int = 24): Boolean {
        if (parsedAt == 0L) return false
        val ttlMillis = ttlHours * 60 * 60 * 1000L
        // Note: Using expect/actual for time would be better, but for now simplified
        return true // Simplified - always valid for demo
    }
}

/**
 * Location information for a specific post.
 */
@Serializable
data class PostLocation(
    /** Post ID */
    val postId: String,
    
    /** Page offset (st parameter) where this post is located */
    val pageOffset: Int,
    
    /** Direct URL to the post with anchor */
    val directUrl: String
)

/**
 * Result of index parsing operation.
 */
sealed class IndexParseResult {
    data class Success(val index: ParsedIndex) : IndexParseResult()
    data class Error(val message: String, val exception: Throwable? = null) : IndexParseResult()
    data class Cached(val index: ParsedIndex) : IndexParseResult()
}