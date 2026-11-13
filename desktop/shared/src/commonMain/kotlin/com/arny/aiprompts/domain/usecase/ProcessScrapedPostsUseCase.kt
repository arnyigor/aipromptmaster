package com.arny.aiprompts.domain.usecase

import com.arny.aiprompts.domain.model.*
import com.benasher44.uuid.uuid4
import com.arny.aiprompts.utils.Logger
import kotlin.time.ExperimentalTime

/**
 * Main pipeline UseCase for processing scraped posts from 4pda.
 * Combines extraction, categorization, and quality filtering.
 * Now with idempotency support - skips posts that already exist.
 */
@OptIn(ExperimentalTime::class)
class ProcessScrapedPostsUseCase(
    private val extractPromptData: ExtractPromptDataUseCase,
    private val categorizeUseCase: AutoCategorizeUseCase
) {

    companion object {
        private const val MIN_CONTENT_LENGTH = 50
    }

    /**
     * Result of processing scraped posts.
     * Contains detailed statistics for user feedback.
     */
    data class ProcessingResult(
        val totalInput: Int,
        val alreadyExists: Int,
        val parseErrors: Int,
        val lowQuality: Int,
        val success: Int,
        val prompts: List<PromptData>,
        val errorLogs: List<String>
    )

    /**
     * Process raw posts through the complete pipeline with idempotency support.
     *
     * @param rawPosts Список сырых постов из HTML
     * @param existingSourceIds Набор ID постов, которые УЖЕ есть в базе (чтобы пропустить)
     */
    operator fun invoke(
        rawPosts: List<RawPostData>,
        existingSourceIds: Set<String> = emptySet()
    ): ProcessingResult {
        val errors = mutableListOf<String>()
        val prompts = mutableListOf<PromptData>()

        var alreadyExistsCount = 0
        var parseErrorsCount = 0
        var lowQualityCount = 0

        Logger.i("ProcessPosts", "Start processing ${rawPosts.size} posts. Existing IDs: ${existingSourceIds.size}")

        for (rawPost in rawPosts) {
            // 1. Idempotency check - skip if already exists
            if (rawPost.postId in existingSourceIds) {
                alreadyExistsCount++
                continue
            }

            // 2. Skip non-prompt posts (just discussion, not a prompt)
            if (!rawPost.isLikelyPrompt && rawPost.attachments.isEmpty()) {
                continue
            }

            try {
                // 3. Extract structured data (safe extraction)
                val extracted = try {
                    extractPromptData(rawPost)
                } catch (e: Exception) {
                    parseErrorsCount++
                    errors.add("Ошибка парсинга поста ${rawPost.postId}: ${e.message}")
                    null
                }

                if (extracted == null) {
                    // extractPromptData returned null - not a valid prompt structure
                    continue
                }

                // 4. Quality filter
                if (!extracted.isHighQuality && rawPost.attachments.isEmpty()) {
                    lowQualityCount++
                    // Not an error, just low quality content filtered out
                    continue
                }

                // 5. Auto-categorize
                val categoryResult = categorizeUseCase(extracted.contentRu)

                // 6. Build final PromptData
                val promptData = PromptData(
                    id = uuid4().toString(),
                    sourceId = extracted.sourceId,
                    title = extracted.title.ifBlank { "Prompt ${extracted.sourceId}" },
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
                // Global catch for unexpected errors - don't break the pipeline
                parseErrorsCount++
                errors.add("Критическая ошибка на посте ${rawPost.postId}: ${e.message}")
                Logger.e(e, "ProcessPosts")
            }
        }

        Logger.i("ProcessPosts", "Finished. New: ${prompts.size}, Exists: $alreadyExistsCount, Errors: $parseErrorsCount")

        return ProcessingResult(
            totalInput = rawPosts.size,
            alreadyExists = alreadyExistsCount,
            parseErrors = parseErrorsCount,
            lowQuality = lowQualityCount,
            success = prompts.size,
            prompts = prompts,
            errorLogs = errors
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