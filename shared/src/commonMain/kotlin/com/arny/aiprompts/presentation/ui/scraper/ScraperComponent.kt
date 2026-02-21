package com.arny.aiprompts.presentation.ui.scraper

import com.arny.aiprompts.domain.analysis.AnalyzerPipelineProgress
import com.arny.aiprompts.domain.analysis.AnalyzerPipelineResult
import com.arny.aiprompts.domain.analysis.AnalyzerStats
import com.arny.aiprompts.domain.analysis.IAnalyzerPipeline
import com.arny.aiprompts.domain.index.model.IndexLink
import com.arny.aiprompts.domain.interfaces.PreScrapeCheck
import com.arny.aiprompts.domain.model.PromptData
import com.arny.aiprompts.domain.repositories.SyncResult
import com.arny.aiprompts.domain.usecase.ImportParsedPromptsUseCase
import com.arny.aiprompts.domain.usecase.ProcessScrapedPostsUseCase
import kotlinx.coroutines.flow.StateFlow

/**
 * Статистика промптов по типу хранения.
 */
data class PromptsStats(
    val localCount: Int,
    val syncedCount: Int
)

interface ScraperComponent {
    val state: StateFlow<ScraperState>

    // События от UI
    fun onPagesChanged(pages: String)
    fun onStartScrapingClicked()
    fun onParseAndSaveClicked()
    fun onRunAnalyzerPipelineClicked()
    fun onOpenDirectoryClicked()
    fun onNavigateToImporterClicked()
    fun onBackClicked()

    // --- Import events ---
    fun onPreviewImportClicked()
    fun onImportFileSelectionChanged(filePath: String, selected: Boolean)
    fun onConfirmImportClicked()
    fun onCancelImportClicked()
    fun onDismissImportResults()

    // --- Preview Mode Events ---
    fun onPromptPreviewClicked(prompt: PromptData)
    fun onAcceptPrompt()
    fun onSkipPrompt()
    fun onNextPrompt()
    fun onPrevPrompt()
    fun onClosePreview()

    // --- Toggle between parsed and original HTML content ---
    fun onToggleHtmlView()

    // События от диалога
    fun onOverwriteConfirmed()
    fun onContinueConfirmed()
    fun onDialogDismissed()

    // Статистика промптов
    suspend fun getPromptsStats(): PromptsStats

    // Синхронизация с удалённым источником
    suspend fun syncWithRemote(): SyncResult
}

/**
 * Represents the state of the analyzer pipeline.
 */
enum class PipelineStage {
    IDLE,
    LOADING_INDEX,
    MAPPING_FILES,
    DEDUPLICATING,
    PARSING,
    EXPORTING,
    COMPLETED,
    ERROR
}

/**
 * Represents the result of running the analyzer pipeline.
 */
data class PipelineExecutionResult(
    val success: Boolean,
    val totalProcessed: Int,
    val newPrompts: Int,
    val skippedDuplicates: Int,
    val missingPages: Int,
    val errors: Int,
    val outputFiles: List<String>,
    val durationMs: Long,
    val categoryBreakdown: Map<String, Int> = emptyMap()
)

/**
 * Data class representing the complete state of the scraper screen (ScraperScreen).
 * Single source of truth for UI.
 *
 * @property pagesToScrape String value from "Number of pages" input field.
 * @property logs List of messages for log panel display.
 * @property savedHtmlFiles List of downloaded HTML files (paths), found on disk.
 * @property parsedPrompts List of prompts extracted from HTML after parsing.
 * @property lastSavedJsonFiles List of JSON files created in last save session.
 * @property inProgress Flag indicating a long-running operation (scraping or parsing).
 * @property preScrapeCheckResult Result of pre-processing file check. If not null, dialog is shown.
 * @property processingResult Processing result with detailed statistics.
 *
 * @property pipelineStage Current stage of the analyzer pipeline.
 * @property pipelineProgress Current progress (0-100).
 * @property pipelineCurrentPost Current post being processed.
 * @property pipelineTotalPosts Total posts to process.
 * @property pipelineLogs Pipeline-specific logs for detailed progress display.
 * @property pipelineResult Result of the last pipeline execution.
 * @property categoryFiles List of exported category files with prompt counts.
 *
 * @property availableImportFiles List of available import files from parsed_prompts.
 * @property selectedImportFiles Set of selected file paths for import.
 * @property importProgress Current import progress (0-1).
 * @property importResult Result of the last import operation.
 * @property isImporting Flag indicating import operation is in progress.
 *
 * @property indexLinks Links parsed from first page spoilers (for filtering).
 * @property showOriginalHtml In preview mode: show original HTML content vs parsed content.
 * @property currentHtmlContent In preview mode: original HTML content from the post.
 */
 data class ScraperState(
    // --- Input field state ---
    val pagesToScrape: String = "10",

    // --- Data for display ---
    val logs: List<String> = listOf("Готов к запуску."),
    val savedHtmlFiles: List<String> = emptyList(),
    val parsedPrompts: List<PromptData> = emptyList(),
    val lastSavedJsonFiles: List<String> = emptyList(),

    // --- UI state ---
    val inProgress: Boolean = false,
    val preScrapeCheckResult: PreScrapeCheck? = null,
    val processingResult: ProcessScrapedPostsUseCase.ProcessingResult? = null,

    // --- Analyzer Pipeline state ---
    val pipelineStage: PipelineStage = PipelineStage.IDLE,
    val pipelineProgress: Float = 0f,
    val pipelineCurrentPost: String = "",
    val pipelineTotalPosts: Int = 0,
    val pipelineLogs: List<String> = emptyList(),
    val pipelineResult: PipelineExecutionResult? = null,
    val categoryFiles: List<CategoryFileInfo> = emptyList(),

    // --- Import Panel state ---
    val availableImportFiles: List<ImportParsedPromptsUseCase.ImportFileInfo> = emptyList(),
    val selectedImportFiles: Set<String> = emptySet(),
    val importProgress: Float = 0f,
    val importResult: ImportParsedPromptsUseCase.ImportResult? = null,
    val isImporting: Boolean = false,
    val importError: String? = null,

    // --- Sync Statistics ---
    val lastSyncTime: String? = null,
    val localPromptsCount: Int = 0,
    val syncedPromptsCount: Int = 0,

    // --- Preview Mode State ---
    val isPreviewMode: Boolean = false,
    val previewPrompts: List<PromptData> = emptyList(),
    val currentPreviewIndex: Int = 0,
    val acceptedCount: Int = 0,
    val skippedCount: Int = 0,

    // --- Index-based filtering (new) ---
    val indexLinks: List<IndexLink> = emptyList(),
    val showOriginalHtml: Boolean = false,
    val currentHtmlContent: String? = null
)

/**
 * Information about an exported category file.
 */
data class CategoryFileInfo(
    val categoryName: String,
    val filePath: String,
    val promptCount: Int
)