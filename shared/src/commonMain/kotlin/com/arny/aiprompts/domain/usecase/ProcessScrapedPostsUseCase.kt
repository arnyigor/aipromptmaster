package com.arny.aiprompts.domain.usecase

import com.arny.aiprompts.domain.model.*
import com.benasher44.uuid.uuid4
import kotlin.time.ExperimentalTime

/**
 * Main pipeline UseCase for processing scraped posts from 4pda.
 * Combines extraction, categorization, and quality filtering.
 */
@OptIn(ExperimentalTime::class)
class ProcessScrapedPostsUseCase(
    private val extractPromptData: ExtractPromptDataUseCase,
    private val categorizeUseCase: AutoCategorizeUseCase
) {

    companion object {
        private const val MIN_CONTENT_LENGTH = 50
    }

    data class ProcessingResult(
        val totalPosts: Int,
        val extractedCount: Int,
        val qualityCount: Int,
        val categorizedCount: Int,
        val prompts: List<PromptData>,
        val errors: List<String>
    )

    /**
     * Process raw posts through the complete pipeline.
     */
    operator fun invoke(rawPosts: List<RawPostData>): ProcessingResult {
        val errors = mutableListOf<String>()
        val prompts = mutableListOf<PromptData>()

        var extractedCount = 0
        var qualityCount = 0

        for (rawPost in rawPosts) {
            try {
                // Step 1: Skip non-prompt posts
                if (!rawPost.isLikelyPrompt) {
                    continue
                }

                // Step 2: Extract structured data
                val extracted = extractPromptData(rawPost)
                if (extracted == null) {
                    errors.add("Failed to extract from post ${rawPost.postId}")
                    continue
                }
                extractedCount++

                // Step 3: Quality filter (min 50 chars)
                if (!extracted.isHighQuality) {
                    errors.add("Post ${rawPost.postId} skipped: content too short (${extracted.contentRu.length} chars)")
                    continue
                }
                qualityCount++

                // Step 4: Auto-categorize
                val categoryResult = categorizeUseCase(extracted.contentRu)

                // Step 5: Build final PromptData
                val promptData = PromptData(
                    id = uuid4().toString(),
                    sourceId = extracted.sourceId,
                    title = extracted.title,
                    description = extracted.description,
                    variants = buildVariants(extracted),
                    author = extracted.author,
                    createdAt = extracted.postDate.toEpochMilliseconds(),
                    updatedAt = java.lang.System.currentTimeMillis(),
                    category = categoryResult.category,
                    tags = extractTags(extracted),
                    source = "4pda.to"
                )

                prompts.add(promptData)

            } catch (e: Exception) {
                errors.add("Error processing post ${rawPost.postId}: ${e.message}")
            }
        }

        return ProcessingResult(
            totalPosts = rawPosts.size,
            extractedCount = extractedCount,
            qualityCount = qualityCount,
            categorizedCount = prompts.count { it.category != "imported" },
            prompts = prompts,
            errors = errors
        )
    }

    private fun buildVariants(extracted: ExtractPromptDataUseCase.ExtractedPromptData): List<PromptVariant> {
        val variants = mutableListOf<PromptVariant>()
        
        // Russian variant
        variants.add(PromptVariant(
            type = "ru",
            content = extracted.contentRu
        ))

        // English variant if available
        extracted.contentEn?.let { enContent ->
            if (enContent.length >= MIN_CONTENT_LENGTH) {
                variants.add(PromptVariant(
                    type = "en",
                    content = enContent
                ))
            }
        }

        return variants
    }

    private fun extractTags(extracted: ExtractPromptDataUseCase.ExtractedPromptData): List<String> {
        val tags = mutableListOf<String>()
        val text = (extracted.contentRu + " " + extracted.description).lowercase()

        // Auto-tagging based on content
        when {
            text.contains("gpt") || text.contains("chatgpt") -> tags.add("gpt")
            text.contains("claude") -> tags.add("claude")
            text.contains("image") || text.contains("нарисуй") || text.contains("изображение") -> tags.add("image")
            text.contains("code") || text.contains("код") || text.contains("функция") -> tags.add("code")
            text.contains("role") || text.contains("роль") -> tags.add("roleplay")
            text.contains("translate") || text.contains("перевод") -> tags.add("translation")
        }

        return tags
    }
}