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
import com.arny.aiprompts.domain.usecase.ImportParsedPromptsUseCase
import com.arny.aiprompts.domain.usecase.ParseRawPostsUseCase
import com.arny.aiprompts.domain.usecase.ProcessScrapedPostsUseCase
import com.arny.aiprompts.domain.usecase.ScrapeWebsiteUseCase
import com.arny.aiprompts.domain.usecase.ScraperResult
import com.arny.aiprompts.data.model.*
import com.benasher44.uuid.uuid4
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

/**
 * Updated ScraperComponent with improved parsing pipeline and import functionality.
 * Uses ProcessScrapedPostsUseCase for automatic extraction and categorization.
 */
class DefaultScraperComponent(
    componentContext: ComponentContext,
    private val scrapeUseCase: ScrapeWebsiteUseCase,
    private val webScraper: IWebScraper,
    private val parseRawPostsUseCase: ParseRawPostsUseCase,
    private val processScrapedPostsUseCase: ProcessScrapedPostsUseCase,
    private val analyzerPipeline: IAnalyzerPipeline,
    private val importParsedPromptsUseCase: ImportParsedPromptsUseCase,
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
        addLog("Pages to scrape: $pagesToScrape")

        if (pagesToScrape.isEmpty()) {
            addLog("ERROR: No pages specified!")
            return
        }

        scope.launch {
            val checkResult = webScraper.checkExistingFiles(pagesToScrape)
            addLog("Check result: existing=${checkResult.existingFileCount}, missing=${checkResult.missingPages}")

            if (checkResult.existingFileCount > 0 && checkResult.missingPages.isNotEmpty()) {
                addLog("Showing dialog - some files exist, some missing")
                _state.update { it.copy(preScrapeCheckResult = checkResult) }
            } else {
                addLog("Starting scraping for pages: ${checkResult.missingPages}")
                startScraping(checkResult.missingPages)
            }
        }
    }

    /**
     * Updated parsing with new pipeline and idempotency:
     * HTML → RawPostData → ExtractPromptDataUseCase → AutoCategorizeUseCase → PromptData
     * Now skips posts that already exist in the database.
     * NEW: Filters to only include prompts from index links (first page spoilers).
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

            // Get index links for filtering (from first page spoilers)
            val indexPostIds = currentState.indexLinks.map { it.postId }.toSet()
            if (indexPostIds.isNotEmpty()) {
                addLog("Фильтрация по списку из первой страницы: ${indexPostIds.size} промптов")
            }

            // Step 1: Get existing source IDs from database for idempotency
            addLog("Шаг 1: Загрузка существующих промптов...")
            val allExistingPrompts = promptsRepository.getAllPrompts().first()
            val existingSourceIds = allExistingPrompts
                .mapNotNull { it.metadata.source?.takeIf { s -> s.isNotEmpty() } }
                .toSet()

            // Also check pending candidates from current session (not yet saved)
            val pendingImportIds = currentState.parsedPrompts.mapNotNull { it.sourceId }.toSet()

            val totalIgnoreSet = existingSourceIds + pendingImportIds

            addLog("ℹ️ В базе уже есть ${existingSourceIds.size} промптов. Игнорирую дубликаты.")

            // Step 2: Parse HTML files to RawPostData
            addLog("Шаг 2: Парсинг HTML файлов...")
            val parsingResults = coroutineScope {
                filesToParse.map { filePath ->
                    async(Dispatchers.IO) { parseRawPostsUseCase(java.io.File(filePath)) }
                }.awaitAll()
            }

            val allRawPosts = parsingResults.flatMap { it.getOrElse { emptyList() } }
            
            // NEW: Filter by index postIds if available
            val filteredRawPosts = if (indexPostIds.isNotEmpty()) {
                val filtered = allRawPosts.filter { it.postId in indexPostIds }
                addLog("После фильтрации по индексу: ${filtered.size} постов (из ${allRawPosts.size})")
                filtered
            } else {
                allRawPosts
            }
            
            addLog("Найдено ${filteredRawPosts.size} постов для обработки (включая дубли и мусор)")

            // Step 3: Process through new pipeline (extraction + categorization + deduplication)
            addLog("Шаг 3: Извлечение данных, категоризация и дедупликация...")

            // NEW: Pass indexPostIds for filtering
            val result = processScrapedPostsUseCase(
                rawPosts = filteredRawPosts,
                existingSourceIds = totalIgnoreSet
            )

            addLog("--- РЕЗУЛЬТАТЫ ОБРАБОТКИ ---")
            addLog("Всего постов: ${result.totalInput}")
            addLog("Пропущено (уже есть): ${result.alreadyExists}")
            addLog("Отсеяно (низкое качество): ${result.lowQuality}")
            addLog("Ошибок парсинга: ${result.parseErrors}")
            addLog("Итого НОВЫХ для сохранения: ${result.success}")

            if (result.errorLogs.isNotEmpty()) {
                addLog("Детали ошибок (${result.errorLogs.size}):")
                result.errorLogs.take(5).forEach { addLog("  - $it") }
            }

            // Add NEW prompts to existing candidates (don't replace!)
            _state.update { it.copy(
                parsedPrompts = it.parsedPrompts + result.prompts,
                processingResult = result,
                inProgress = false
            ) }

            if (result.success > 0) {
                addLog("✅ Добавлено ${result.success} новых промптов (всего: ${_state.value.parsedPrompts.size})")
            }
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
            addLog("Запуск скрапинга для ${pages.size} страниц: ${pages.joinToString()}")

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
                            
                            // Parse index from first page after scraping
                            if (pages.contains(1)) {
                                addLog("Parsing index from first page...")
                                val indexLinks = withContext(Dispatchers.IO) {
                                    webScraper.parseIndexFromFirstPage()
                                }
                                if (indexLinks.isNotEmpty()) {
                                    val categories = indexLinks.groupBy { it.category }.keys
                                    addLog("Found ${indexLinks.size} links from spoilers. Categories: ${categories.joinToString()}")
                                    _state.update { it.copy(indexLinks = indexLinks) }
                                }
                            }
                        }
                        is ScraperResult.Error -> addLog("--- ERROR: ${result.errorMessage} ---")
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

    // ========== IMPORT METHODS ==========

    /**
      * Preview import - load available files from parsed_prompts directory
      * or use already parsed prompts from state.
      */
    override fun onPreviewImportClicked() {
        scope.launch {
            // First, check if we already have parsed prompts from pipeline
            val existingPrompts = _state.value.parsedPrompts
            if (existingPrompts.isNotEmpty()) {
                addLog("--- ПРЕВЬЮ СУЩЕСТВУЮЩИХ ПРОМПТОВ ---")
                addLog("Найдено ${existingPrompts.size} спарсенных промптов")

                _state.update {
                    it.copy(
                        isPreviewMode = true,
                        previewPrompts = existingPrompts,
                        currentPreviewIndex = 0,
                        acceptedCount = 0,
                        skippedCount = 0,
                        isImporting = false
                    )
                }
                addLog("Нажмите на промпт в списке для просмотра и принятия/пропуска.")
                return@launch
            }

            // Fall back to looking for files
            _state.update {
                it.copy(
                    isImporting = true,
                    importProgress = 0f,
                    importResult = null,
                    importError = null
                )
            }
            addLog("--- ПОИСК ФАЙЛОВ ДЛЯ ИМПОРТА ---")

            val availableFiles = importParsedPromptsUseCase.getAvailableFilesList()

            if (availableFiles.isEmpty()) {
                addLog("Файлы для импорта не найдены.")
                addLog("Сначала запустите 'Анализ и экспорт' для создания файлов.")
                _state.update {
                    it.copy(
                        isImporting = false,
                        availableImportFiles = emptyList()
                    )
                }
                return@launch
            }

            addLog("Найдено ${availableFiles.size} файлов для импорта:")

            // Log breakdown by category
            val byCategory = availableFiles.groupBy { it.category }
            byCategory.forEach { (category, files) ->
                val totalPrompts = files.sumOf { it.promptCount }
                addLog("  - $category: ${files.size} файлов, $totalPrompts промптов")
            }

            _state.update {
                it.copy(
                    isImporting = false,
                    availableImportFiles = availableFiles,
                    selectedImportFiles = availableFiles.map { it.filePath }.toSet()
                )
            }

            addLog("Выберите файлы для импорта и нажмите 'Импортировать'.")
        }
    }

    /**
     * Toggle file selection for import.
     */
    override fun onImportFileSelectionChanged(filePath: String, selected: Boolean) {
        _state.update { state ->
            val newSelection = if (selected) {
                state.selectedImportFiles + filePath
            } else {
                state.selectedImportFiles - filePath
            }
            state.copy(selectedImportFiles = newSelection)
        }
    }

    /**
     * Confirm import - start importing selected files.
     */
    override fun onConfirmImportClicked() {
        val selectedFiles = _state.value.selectedImportFiles.toList()
        if (selectedFiles.isEmpty()) {
            addLog("Не выбрано ни одного файла для импорта.")
            return
        }

        scope.launch {
            _state.update {
                it.copy(
                    isImporting = true,
                    importProgress = 0f,
                    importResult = null,
                    importError = null
                )
            }
            addLog("--- ИМПОРТ ВЫБРАННЫХ ФАЙЛОВ ---")
            addLog("Файлов для импорта: ${selectedFiles.size}")

            var totalImported = 0
            var totalSkipped = 0
            val errors = mutableListOf<String>()
            val categoryBreakdown = mutableMapOf<String, Int>()

            val totalFiles = selectedFiles.size

            selectedFiles.forEachIndexed { index, filePath ->
                val file = java.io.File(filePath)
                _state.update {
                    it.copy(importProgress = (index + 1).toFloat() / totalFiles)
                }

                try {
                    importParsedPromptsUseCase.importFiles(listOf(filePath)).collect { progress ->
                        if (progress.currentPrompts > 0) {
                            addLog("  ${file.name}: ${progress.currentPrompts} промптов")
                            totalImported += progress.currentPrompts
                        }
                    }
                    addLog("  ${file.name}: обработка завершена")

                    // Count category
                    val category = file.nameWithoutExtension
                    categoryBreakdown[category] = categoryBreakdown.getOrDefault(category, 0) + 1

                } catch (e: Exception) {
                    errors.add("Ошибка импорта файла ${file.name}: ${e.message}")
                    addLog("ОШИБКА: ${e.message}")
                }
            }

            // Refresh repository after import to pick up new prompts
            promptsRepository.invalidateSortDataCache()
            updateSyncStats()

            val importResult = ImportParsedPromptsUseCase.ImportResult(
                success = errors.isEmpty(),
                totalFiles = selectedFiles.size,
                totalPrompts = totalImported + totalSkipped,
                importedCount = totalImported,
                skippedCount = totalSkipped,
                errors = errors,
                categoryBreakdown = categoryBreakdown
            )

            _state.update {
                it.copy(
                    isImporting = false,
                    importProgress = 1f,
                    importResult = importResult,
                    // Clear selections after successful import
                    selectedImportFiles = emptySet()
                )
            }

            addLog("--- РЕЗУЛЬТАТЫ ИМПОРТА ---")
            addLog("Импортировано: $totalImported промптов")
            addLog("Пропущено: $totalSkipped")
            addLog("Ошибок: ${errors.size}")

            if (errors.isNotEmpty()) {
                errors.forEach { error ->
                    addLog("  - $error")
                }
            }

            if (totalImported > 0) {
                addLog("Файлы сохранены в prompts/{category}/")
                addLog("Обновлено в базе данных.")
            }
        }
    }

    /**
     * Cancel import operation.
     */
    override fun onCancelImportClicked() {
        _state.update {
            it.copy(
                isImporting = false,
                importProgress = 0f,
                selectedImportFiles = emptySet()
            )
        }
        addLog("Импорт отменен.")
    }

    /**
      * Dismiss import results after user has viewed them.
      * Clears the results panel while keeping selected files.
      */
    override fun onDismissImportResults() {
        _state.update {
            it.copy(
                importResult = null,
                importError = null
            )
        }
    }

    // ========== PREVIEW MODE HANDLERS ==========

    /**
     * Open preview mode for a single prompt.
     */
    override fun onPromptPreviewClicked(prompt: PromptData) {
        val prompts = _state.value.parsedPrompts
        val index = prompts.indexOfFirst { it.id == prompt.id }.coerceAtLeast(0)

        _state.update {
            it.copy(
                isPreviewMode = true,
                previewPrompts = prompts,
                currentPreviewIndex = index,
                acceptedCount = 0,
                skippedCount = 0
            )
        }
        addLog("Превью: ${prompt.title}")
    }

    /**
     * Accept current prompt and save to prompts/{category}/.
     */
    override fun onAcceptPrompt() {
        val state = _state.value
        val prompt = state.previewPrompts.getOrNull(state.currentPreviewIndex) ?: return

        scope.launch {
            try {
                val saved = savePromptToCollection(prompt)
                if (saved) {
                    addLog("✅ Принят: ${prompt.title}")
                    _state.update { it.copy(acceptedCount = it.acceptedCount + 1) }
                    // Move to next
                    moveToNextPrompt()
                } else {
                    addLog("❌ Ошибка сохранения: ${prompt.title}")
                }
            } catch (e: Exception) {
                addLog("❌ Ошибка: ${e.message}")
            }
        }
    }

    /**
     * Skip current prompt without saving.
     */
    override fun onSkipPrompt() {
        val prompt = _state.value.previewPrompts.getOrNull(_state.value.currentPreviewIndex) ?: return
        addLog("⏭ Пропущен: ${prompt.title}")
        _state.update { it.copy(skippedCount = it.skippedCount + 1) }
        moveToNextPrompt()
    }

    /**
     * Move to next prompt in preview mode.
     */
    override fun onNextPrompt() {
        moveToNextPrompt()
    }

    /**
     * Move to previous prompt in preview mode.
     */
    override fun onPrevPrompt() {
        val newIndex = (_state.value.currentPreviewIndex - 1).coerceAtLeast(0)
        _state.update { it.copy(currentPreviewIndex = newIndex) }
    }

    /**
     * Close preview mode.
     */
    override fun onClosePreview() {
        val state = _state.value
        val summary = buildString {
            if (state.acceptedCount > 0) append("Принято: ${state.acceptedCount}")
            if (state.skippedCount > 0) {
                if (isNotEmpty()) append(", ")
                append("Пропущено: ${state.skippedCount}")
            }
        }
        if (summary.isNotEmpty()) {
            addLog("Итого: $summary")
        }
        _state.update {
            it.copy(
                isPreviewMode = false,
                previewPrompts = emptyList(),
                currentPreviewIndex = 0,
                acceptedCount = 0,
                skippedCount = 0,
                showOriginalHtml = false,
                currentHtmlContent = null
            )
        }
    }

    /**
     * Toggle between parsed content and original HTML content.
     */
    override fun onToggleHtmlView() {
        scope.launch {
            val state = _state.value
            val currentPrompt = state.previewPrompts.getOrNull(state.currentPreviewIndex) ?: return@launch
            
            val newShowOriginal = !state.showOriginalHtml
            
            if (newShowOriginal) {
                // Load original HTML content
                addLog("Загрузка оригинального HTML контента...")
                val htmlContent = withContext(Dispatchers.IO) {
                    webScraper.getPromptContentFromHtml(currentPrompt.sourceId)
                }
                _state.update {
                    it.copy(
                        showOriginalHtml = true,
                        currentHtmlContent = htmlContent
                    )
                }
                if (htmlContent != null) {
                    addLog("Загружен HTML контент (${htmlContent.length} символов)")
                } else {
                    addLog("Не удалось загрузить HTML контент")
                }
            } else {
                _state.update {
                    it.copy(
                        showOriginalHtml = false,
                        currentHtmlContent = null
                    )
                }
                addLog("Показан спарсенный контент")
            }
        }
    }

    /**
     * Move to next prompt or close preview mode if at end.
     */
    private fun moveToNextPrompt() {
        val state = _state.value
        val nextIndex = state.currentPreviewIndex + 1
        if (nextIndex >= state.previewPrompts.size) {
            // End of list - show summary and close
            val summary = "Готово! Принято: ${state.acceptedCount}, Пропущено: ${state.skippedCount}"
            addLog(summary)
            _state.update {
                it.copy(
                    isPreviewMode = false,
                    previewPrompts = emptyList(),
                    currentPreviewIndex = 0
                )
            }
        } else {
            _state.update { it.copy(currentPreviewIndex = nextIndex) }
        }
    }

    /**
     * Save a single prompt to prompts/{category}/ directory.
     * Returns true if saved successfully.
     */
    private suspend fun savePromptToCollection(prompt: PromptData): Boolean {
        return try {
            // Get category from tags or use "general"
            val category = prompt.tags.firstOrNull()?.lowercase() ?: "general"

            // Create category directory
            val projectRoot = File(System.getProperty("user.home"), "aiprompts/prompts")
            val categoryDir = File(projectRoot, category)
            if (!categoryDir.exists()) {
                categoryDir.mkdirs()
            }

            // Generate new UUID
            val newId = com.benasher44.uuid.uuid4().toString()
            val timestamp = java.time.Instant.now().toString()

            // Convert PromptData to PromptJson format
            val promptJson = convertToPromptJson(prompt, newId, timestamp)

                // Save to file
                val outputFile = File(categoryDir, "$newId.json")
                kotlinx.serialization.json.Json {
                    prettyPrint = true
                    ignoreUnknownKeys = true
                }.encodeToString(
                    com.arny.aiprompts.data.model.PromptJson.serializer(),
                    promptJson
                ).let { jsonContent ->
                    outputFile.writeText(jsonContent)
                }

                // Refresh repository to pick up new prompt
                promptsRepository.invalidateSortDataCache()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Convert domain PromptData to data layer PromptJson.
     */
    private fun convertToPromptJson(
        prompt: PromptData,
        newId: String,
        timestamp: String
    ): com.arny.aiprompts.data.model.PromptJson {
        return com.arny.aiprompts.data.model.PromptJson(
            id = newId,
            sourceId = prompt.id,
            title = prompt.title,
            version = "1.0.0",
            status = "active",
            isLocal = true,
            isFavorite = false,
            description = prompt.description ?: "",
            content = mapOf(
                "ru" to (prompt.variants.firstOrNull()?.content ?: ""),
                "en" to ""
            ),
            compatibleModels = emptyList(),
            category = prompt.tags.firstOrNull()?.lowercase() ?: "general",
            tags = prompt.tags,
            variables = emptyList(),
            metadata = com.arny.aiprompts.data.model.PromptMetadata(
                author = com.arny.aiprompts.domain.model.Author(
                    id = prompt.author?.id ?: "",
                    name = prompt.author?.name ?: "4pda User"
                ),
                source = "",
                notes = "Imported from scraper"
            ),
            rating = com.arny.aiprompts.data.model.Rating(),
            promptVariants = prompt.variants.map { variant ->
                com.arny.aiprompts.data.model.PromptVariant(
                    variantId = com.arny.aiprompts.data.model.VariantId(
                        type = variant.type,
                        id = uuid4().toString()
                    ),
                    content = com.arny.aiprompts.data.model.PromptContentMap(
                        ru = variant.content,
                        en = ""
                    ),
                    priority = 1
                )
            },
            createdAt = timestamp,
            updatedAt = timestamp
        )
    }
}