package com.arny.aiprompts.presentation.ui.scraper

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.arny.aiprompts.domain.analysis.AnalyzerPipelineProgress
import com.arny.aiprompts.domain.analysis.IAnalyzerPipeline
import com.arny.aiprompts.domain.interfaces.IPromptsRepository
import com.arny.aiprompts.domain.interfaces.IWebScraper
import com.arny.aiprompts.domain.model.PromptData
import com.arny.aiprompts.domain.repositories.IPromptSynchronizer
import com.arny.aiprompts.domain.repositories.SyncResult
import com.arny.aiprompts.domain.strings.StringHolder
import com.arny.aiprompts.domain.usecase.ParseRawPostsUseCase
import com.arny.aiprompts.domain.usecase.ProcessScrapedPostsUseCase
import com.arny.aiprompts.domain.usecase.ScrapeWebsiteUseCase
import com.arny.aiprompts.domain.usecase.ScraperResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Updated ScraperComponent with improved parsing pipeline.
 * Uses ProcessScrapedPostsUseCase for automatic extraction and categorization.
 */
class DefaultScraperComponent(
    componentContext: ComponentContext,
    private val scrapeUseCase: ScrapeWebsiteUseCase,
    private val webScraper: IWebScraper,
    private val parseRawPostsUseCase: ParseRawPostsUseCase,
    private val processScrapedPostsUseCase: ProcessScrapedPostsUseCase,
    private val analyzerPipeline: IAnalyzerPipeline,
    private val promptSynchronizer: IPromptSynchronizer,
    private val promptsRepository: IPromptsRepository,
    private val onNavigateToImporter: (files: List<String>) -> Unit,
    private val onBack: () -> Unit
) : ScraperComponent, ComponentContext by componentContext {

    private val _state = MutableStateFlow(ScraperState())
    override val state: StateFlow<ScraperState> = _state.asStateFlow()

    private val scope = coroutineScope()

    init {
        scope.launch(Dispatchers.IO) {
            _state.update { it.copy(savedHtmlFiles = webScraper.getExistingScrapedFiles()) }
            updateSyncStats()
        }
    }

    private suspend fun updateSyncStats() {
        try {
            val allPrompts = promptsRepository.getAllPrompts().first()
            val localCount = allPrompts.count { it.isLocal }
            val syncedCount = allPrompts.count { !it.isLocal }
            val lastSyncTimestamp = promptSynchronizer.getLastSyncTime()
            val lastSyncString = if (lastSyncTimestamp > 0) {
                val sdf = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
                sdf.format(java.util.Date(lastSyncTimestamp))
            } else {
                null
            }

            _state.update { it.copy(
                localPromptsCount = localCount,
                syncedPromptsCount = syncedCount,
                lastSyncTime = lastSyncString
            )}
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onPagesChanged(pages: String) {
        _state.update { it.copy(pagesToScrape = pages) }
    }

    override fun onStartScrapingClicked() {
        val pagesToScrape = PageStringParser.parse(_state.value.pagesToScrape)

        if (pagesToScrape.isEmpty()) {
            return
        }

        scope.launch {
            val checkResult = webScraper.checkExistingFiles(pagesToScrape)

            if (checkResult.existingFileCount > 0 && checkResult.missingPages.isNotEmpty()) {
                _state.update { it.copy(preScrapeCheckResult = checkResult) }
            } else {
                startScraping(checkResult.missingPages)
            }
        }
    }

    /**
     * Updated parsing with new pipeline:
     * HTML → RawPostData → ExtractPromptDataUseCase → AutoCategorizeUseCase → PromptData
     */
    override fun onParseAndSaveClicked() {
        scope.launch {
            val currentState = _state.value
            _state.update {
                it.copy(
                    inProgress = true,
                    lastSavedJsonFiles = emptyList(),
                    parsedPrompts = emptyList(),
                    processingResult = null
                )
            }
            addLog("--- НАЧИНАЮ ПАРСИНГ С НОВЫМ PIPELINE ---")

            val filesToParse = currentState.savedHtmlFiles
            if (filesToParse.isEmpty()) {
                addLog("Нет файлов для парсинга.")
                _state.update { it.copy(inProgress = false) }
                return@launch
            }

            // Step 1: Parse HTML files to RawPostData
            addLog("Шаг 1: Парсинг HTML файлов...")
            val parsingResults = coroutineScope {
                filesToParse.map { filePath ->
                    async(Dispatchers.IO) { parseRawPostsUseCase(java.io.File(filePath)) }
                }.awaitAll()
            }

            val allRawPosts = parsingResults.flatMap { it.getOrElse { emptyList() } }
            addLog("Найдено ${allRawPosts.size} постов для обработки")

            // Step 2: Process through new pipeline (extraction + categorization)
            addLog("Шаг 2: Извлечение данных и категоризация...")
            val result = processScrapedPostsUseCase(allRawPosts)

            addLog("--- РЕЗУЛЬТАТЫ ОБРАБОТКИ ---")
            addLog("Всего постов: ${result.totalPosts}")
            addLog("Извлечено: ${result.extractedCount}")
            addLog("Качественных (>50 символов): ${result.qualityCount}")
            addLog("С категорией: ${result.categorizedCount}")
            addLog("Итого для сохранения: ${result.prompts.size}")

            if (result.errors.isNotEmpty()) {
                addLog("Ошибки (${result.errors.size}):")
                result.errors.take(5).forEach { addLog("  - $it") }
            }

            _state.update { it.copy(
                parsedPrompts = result.prompts,
                processingResult = result,
                inProgress = false
            ) }
        }
    }

    /**
     * Runs the new analyzer pipeline with category-based export.
     * Uses Flow for progress updates.
     */
    override fun onRunAnalyzerPipelineClicked() {
        scope.launch {
            _state.update {
                it.copy(
                    pipelineStage = PipelineStage.LOADING_INDEX,
                    pipelineProgress = 0f,
                    pipelineLogs = emptyList(),
                    pipelineResult = null,
                    categoryFiles = emptyList()
                )
            }
            addLog("--- ЗАПУСК ANALYZER PIPELINE ---")

            analyzerPipeline.runPipelineFlow()
                .catch { e ->
                    addLog("Ошибка pipeline: ${e.message}")
                    _state.update {
                        it.copy(
                            pipelineStage = PipelineStage.ERROR,
                            pipelineLogs = it.pipelineLogs + "Ошибка: ${e.message}"
                        )
                    }
                }
                .collect { progress ->
                    when (progress) {
                        is AnalyzerPipelineProgress.Loading -> {
                            val newLogs = _state.value.pipelineLogs + progress.message
                            _state.update {
                                it.copy(
                                    pipelineStage = PipelineStage.LOADING_INDEX,
                                    pipelineLogs = newLogs.takeLast(100)
                                )
                            }
                            addLog(progress.message)
                        }
                        is AnalyzerPipelineProgress.Mapping -> {
                            val newLogs = _state.value.pipelineLogs + progress.message
                            _state.update {
                                it.copy(
                                    pipelineStage = PipelineStage.MAPPING_FILES,
                                    pipelineLogs = newLogs.takeLast(100)
                                )
                            }
                        }
                        is AnalyzerPipelineProgress.Deduplicating -> {
                            val newLogs = _state.value.pipelineLogs + progress.message
                            _state.update {
                                it.copy(
                                    pipelineStage = PipelineStage.DEDUPLICATING,
                                    pipelineLogs = newLogs.takeLast(100)
                                )
                            }
                        }
                        is AnalyzerPipelineProgress.Parsing -> {
                            val progressPercent = if (progress.total > 0) {
                                progress.current.toFloat() / progress.total
                            } else 0f
                            _state.update {
                                it.copy(
                                    pipelineStage = PipelineStage.PARSING,
                                    pipelineProgress = progressPercent,
                                    pipelineCurrentPost = progress.postId,
                                    pipelineTotalPosts = progress.total
                                )
                            }
                        }
                        is AnalyzerPipelineProgress.Exporting -> {
                            val newLogs = _state.value.pipelineLogs + progress.message
                            _state.update {
                                it.copy(
                                    pipelineStage = PipelineStage.EXPORTING,
                                    pipelineLogs = newLogs.takeLast(100)
                                )
                            }
                        }
                        is AnalyzerPipelineProgress.Completed -> {
                            val result = progress.result
                            val categoryBreakdown = calculateCategoryBreakdown(result.outputFiles)

                            _state.update {
                                it.copy(
                                    pipelineStage = PipelineStage.COMPLETED,
                                    pipelineProgress = 1f,
                                    pipelineResult = PipelineExecutionResult(
                                        success = result.success,
                                        totalProcessed = result.totalProcessed,
                                        newPrompts = result.newPrompts,
                                        skippedDuplicates = result.skippedDuplicates,
                                        missingPages = result.missingPages,
                                        errors = result.errors,
                                        outputFiles = result.outputFiles,
                                        durationMs = result.durationMs,
                                        categoryBreakdown = categoryBreakdown
                                    ),
                                    categoryFiles = result.outputFiles.map { file ->
                                        CategoryFileInfo(
                                            categoryName = extractCategoryName(file),
                                            filePath = file,
                                            promptCount = 0 // Will be updated after counting
                                        )
                                    }
                                )
                            }
                            addLog("Pipeline завершен: ${result.newPrompts} новых промптов")
                            
                            // Обновляем статистику синхронизации после успешного завершения
                            scope.launch(Dispatchers.IO) {
                                updateSyncStats()
                            }
                        }
                        is AnalyzerPipelineProgress.Error -> {
                            val newLogs = _state.value.pipelineLogs + "ERROR: ${progress.message}"
                            _state.update {
                                it.copy(
                                    pipelineStage = PipelineStage.ERROR,
                                    pipelineLogs = newLogs.takeLast(100)
                                )
                            }
                            addLog("ОШИБКА: ${progress.message}")
                        }
                    }
                }
        }
    }

    /**
     * Calculate category breakdown from exported files.
     */
    private fun calculateCategoryBreakdown(outputFiles: List<String>): Map<String, Int> {
        val breakdown = mutableMapOf<String, Int>()
        outputFiles.forEach { filePath ->
            val categoryName = extractCategoryName(filePath)
            breakdown[categoryName] = breakdown.getOrDefault(categoryName, 0) + 1
        }
        return breakdown
    }

    /**
     * Extract category name from file path.
     */
    private fun extractCategoryName(filePath: String): String {
        val file = java.io.File(filePath)
        return file.nameWithoutExtension
            .replace("_", " ")
            .replaceFirstChar { it.titlecase() }
    }

    override fun onOpenDirectoryClicked() {
        try { webScraper.openSaveDirectory() } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onNavigateToImporterClicked() {
        onNavigateToImporter(_state.value.savedHtmlFiles)
    }

    override fun onBackClicked() {
        onBack()
    }

    override fun onDialogDismissed() {
        _state.update { it.copy(preScrapeCheckResult = null) }
    }

    override fun onOverwriteConfirmed() {
        val pagesToScrapeString = _state.value.pagesToScrape
        val pagesToScrape = PageStringParser.parse(pagesToScrapeString)
        _state.update { it.copy(preScrapeCheckResult = null) }
        startScraping(pagesToScrape)
    }

    override fun onContinueConfirmed() {
        val missingPages = _state.value.preScrapeCheckResult?.missingPages ?: emptyList()
        _state.update { it.copy(preScrapeCheckResult = null) }
        startScraping(missingPages)
    }

    private fun startScraping(pages: List<Int>) {
        scope.launch {
            _state.update { it.copy(inProgress = true) }
            addLog("Запуск скрапинга для ${pages.size} страниц...")

            scrapeUseCase("https://4pda.to/forum/index.php?showtopic=1109539", pages)
                .onEach { result ->
                    when (result) {
                        is ScraperResult.InProgress -> addLog(result.message)
                        is ScraperResult.Success -> {
                            val updatedFiles = withContext(Dispatchers.IO) {
                                webScraper.getExistingScrapedFiles()
                            }
                            _state.update { it.copy(savedHtmlFiles = updatedFiles) }
                            addLog("--- Скрапинг ЗАВЕРШЕН ---")
                        }
                        is ScraperResult.Error -> addLog("--- ОШИБКА: ${result.errorMessage} ---")
                    }
                }
                .onCompletion { _state.update { it.copy(inProgress = false) } }
                .collect()
        }
    }

    private fun addLog(message: String) {
        _state.update { it.copy(logs = (it.logs + message).takeLast(100)) }
    }

    override suspend fun getPromptsStats(): PromptsStats {
        return try {
            val allPrompts = promptsRepository.getAllPrompts().first()
            val localCount = allPrompts.count { it.isLocal }
            val syncedCount = allPrompts.count { !it.isLocal }
            PromptsStats(localCount, syncedCount)
        } catch (e: Exception) {
            e.printStackTrace()
            PromptsStats(0, 0)
        }
    }

    override suspend fun syncWithRemote(): SyncResult {
        return try {
            val result = promptSynchronizer.synchronize(ignoreCooldown = true)
            // После синхронизации обновляем статистику
            updateSyncStats()
            result
        } catch (e: Exception) {
            e.printStackTrace()
            SyncResult.Error(StringHolder.Text(e.message ?: "Неизвестная ошибка"))
        }
    }
}