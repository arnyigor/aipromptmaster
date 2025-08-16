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
        _state.update { it.copy(pagesToScrape = pages) }
    }


    override fun onStartScrapingClicked() {
        // 1. Получаем список страниц с помощью нашего парсера
        val pagesToScrape = PageStringParser.parse(_state.value.pagesToScrape)

        // 2. Проверяем, что список не пуст
        if (pagesToScrape.isEmpty()) {
            // Опционально: можно показать ошибку пользователю
            // _state.update { it.copy(error = "Некорректный ввод страниц") }
            return
        }

        scope.launch{
            // 3. Передаем список страниц для проверки
            // Важно: вашему `webScraper` может понадобиться адаптация.
            // Вместо общего количества страниц, ему теперь нужен конкретный список.
            val checkResult = webScraper.checkExistingFiles(pagesToScrape)

            if (checkResult.existingFileCount > 0 && checkResult.missingPages.isNotEmpty()) {
                _state.update { it.copy(preScrapeCheckResult = checkResult) }
            } else {
                // 4. Запускаем скрапинг для нужных страниц (или только недостающих)
                startScraping(checkResult.missingPages) // Или startScraping(pagesToScrape), в зависимости от вашей логики
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
        // 1. Получаем список страниц из _state
        val pagesToScrapeString = _state.value.pagesToScrape // Это строка, которую ввел пользователь

        // 2. Парсим строку в список номеров страниц
        val pagesToScrape = PageStringParser.parse(pagesToScrapeString)

        // 3. Очищаем результат предпроверки (чтобы пользователь видел прогресс)
        _state.update { it.copy(preScrapeCheckResult = null) }

        // 4.  Запускаем скрапинг для выбранных страниц.
        //  Важно: убедитесь, что метод startScraping принимает List<Int>
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