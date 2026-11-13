package com.arny.aiprompts.domain.analysis

import kotlinx.coroutines.flow.Flow

/**
 * Interface for the analyzer pipeline that processes prompts from scraped pages.
 * This abstraction allows different implementations for different platforms.
 */
interface IAnalyzerPipeline {

    /**
     * Run the analysis pipeline with progress updates.
     * @return Flow emitting progress updates
     */
    fun runPipelineFlow(): Flow<AnalyzerPipelineProgress>

    /**
     * Get current pipeline statistics.
     */
    fun getStats(): AnalyzerStats
}

/**
 * Progress update during pipeline execution.
 */
sealed class AnalyzerPipelineProgress {
    data class Loading(val message: String) : AnalyzerPipelineProgress()
    data class Mapping(val message: String) : AnalyzerPipelineProgress()
    data class Deduplicating(val message: String) : AnalyzerPipelineProgress()
    data class Parsing(val current: Int, val total: Int, val postId: String) : AnalyzerPipelineProgress()
    data class Exporting(val message: String) : AnalyzerPipelineProgress()
    data class Completed(val result: AnalyzerPipelineResult) : AnalyzerPipelineProgress()
    data class Error(val message: String) : AnalyzerPipelineProgress()
}

/**
 * Final result of pipeline execution.
 */
data class AnalyzerPipelineResult(
    val success: Boolean,
    val totalProcessed: Int,
    val newPrompts: Int,
    val skippedDuplicates: Int,
    val missingPages: Int,
    val errors: Int,
    val outputFiles: List<String>,
    val durationMs: Long
)

/**
 * Pipeline statistics.
 */
data class AnalyzerStats(
    val scrapedPages: Int,
    val processedPrompts: Int,
    val exportedPrompts: Int,
    val exportedCategories: Int,
    val outputDir: String
)
