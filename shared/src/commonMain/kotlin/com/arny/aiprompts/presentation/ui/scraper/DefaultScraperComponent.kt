package com.arny.aiprompts.presentation.ui.scraper

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.arny.aiprompts.data.mappers.toPromptData
import com.arny.aiprompts.data.scraper.WebScraper
import com.arny.aiprompts.domain.usecase.ParseRawPostsUseCase
import com.arny.aiprompts.domain.usecase.SavePromptsAsFilesUseCase
import com.arny.aiprompts.domain.usecase.ScrapeWebsiteUseCase
import com.arny.aiprompts.domain.usecase.ScraperResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

class DefaultScraperComponent(
    componentContext: ComponentContext,
    private val scrapeUseCase: ScrapeWebsiteUseCase,
    private val webScraper: WebScraper,
    private val parseRawPostsUseCase: ParseRawPostsUseCase,
    private val savePromptsAsFilesUseCase: SavePromptsAsFilesUseCase,
    private val onNavigateToImporter: (files: List<File>) -> Unit
) : ScraperComponent, ComponentContext by componentContext {

    private val _state = MutableStateFlow(ScraperState())
    override val state: StateFlow<ScraperState> = _state.asStateFlow()

    private val scope = coroutineScope()

    init {
        scope.launch(Dispatchers.IO) {
            _state.update { it.copy(savedHtmlFiles = webScraper.getExistingScrapedFiles()) }
        }
    }

    override fun onPagesChanged(pages: String) {
        _state.update { it.copy(pagesToScrape = pages.filter { c -> c.isDigit() }) }
    }

    override fun onStartScrapingClicked() {
        val totalPages = _state.value.pagesToScrape.toIntOrNull() ?: 0
        if (totalPages <= 0) return

        scope.launch(Dispatchers.IO) {
            val checkResult = webScraper.checkExistingFiles(totalPages)
            if (checkResult.existingFileCount > 0 && checkResult.missingPages.isNotEmpty()) {
                _state.update { it.copy(preScrapeCheckResult = checkResult) }
            } else {
                startScraping((0 until totalPages).toList())
            }
        }
    }

    override fun onParseAndSaveClicked() {
        scope.launch {
            val currentState = _state.value
            _state.update { it.copy(inProgress = true, lastSavedJsonFiles = emptyList(), parsedPrompts = emptyList()) }
            addLog("--- НАЧИНАЮ ПАРСИНГ ВСЕХ ФАЙЛОВ ---")

            val filesToParse = currentState.savedHtmlFiles
            if (filesToParse.isEmpty()) {
                addLog("Нет файлов для парсинга.")
                _state.update { it.copy(inProgress = false) }
                return@launch
            }

            val parsingResults = coroutineScope {
                filesToParse.map { file ->
                    async(Dispatchers.IO) { parseRawPostsUseCase(file) }
                }.awaitAll()
            }

            val allRawPosts = parsingResults.flatMap { it.getOrElse { emptyList() } }
            val finalPromptsToSave = allRawPosts.map { it.toPromptData() }

            addLog("--- ПАРСИНГ ЗАВЕРШЕН --- Найдено ${finalPromptsToSave.size} промптов.")
            _state.update { it.copy(parsedPrompts = finalPromptsToSave) }

            if (finalPromptsToSave.isNotEmpty()) {
                addLog("Сохранение в JSON файлы...")
                savePromptsAsFilesUseCase(finalPromptsToSave)
                    .onSuccess { savedFiles ->
                        _state.update { it.copy(lastSavedJsonFiles = savedFiles) }
                        addLog("Успешно сохранено ${savedFiles.size} файлов.")
                    }
                    .onFailure { addLog("Ошибка сохранения: ${it.message}") }
            }
            _state.update { it.copy(inProgress = false) }
        }
    }

    override fun onOpenDirectoryClicked() { try { webScraper.openSaveDirectory() } catch (e: Exception) { e.printStackTrace() } }
    override fun onNavigateToImporterClicked() { onNavigateToImporter(_state.value.savedHtmlFiles) }
    override fun onDialogDismissed() { _state.update { it.copy(preScrapeCheckResult = null) } }

    override fun onOverwriteConfirmed() {
        val totalPages = _state.value.pagesToScrape.toIntOrNull() ?: 0
        _state.update { it.copy(preScrapeCheckResult = null) }
        startScraping((0 until totalPages).toList())
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
                            val updatedFiles = withContext(Dispatchers.IO) { webScraper.getExistingScrapedFiles() }
                            _state.update { it.copy(savedHtmlFiles = updatedFiles) }
                            addLog("--- Скрапинг ЗАВЕРШЕН ---")
                        }
                        is ScraperResult.Error -> addLog("--- ОШИБКА скрапинга: ${result.errorMessage} ---")
                    }
                }
                .onCompletion { _state.update { it.copy(inProgress = false) } }
                .collect()
        }
    }

    private fun addLog(message: String) {
        _state.update { it.copy(logs = (it.logs + message).takeLast(100)) } // Ограничиваем лог
    }
}