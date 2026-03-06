package com.arny.aiprompts.presentation.ui.scraperwizard

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.arny.aiprompts.domain.analysis.CategoryTagMapper
import com.arny.aiprompts.domain.analysis.PromptPageParser
import com.arny.aiprompts.domain.index.IndexParser
import com.arny.aiprompts.domain.index.model.IndexParseResult
import com.arny.aiprompts.domain.index.model.PostLocation
import com.arny.aiprompts.domain.interfaces.FileDataSource
import com.arny.aiprompts.domain.interfaces.IPromptsRepository
import com.arny.aiprompts.domain.interfaces.IWebScraper
import com.arny.aiprompts.domain.model.Author
import com.arny.aiprompts.domain.model.PromptData
import com.arny.aiprompts.domain.model.PromptVariant
import com.arny.aiprompts.domain.repositories.IPromptSynchronizer
import com.arny.aiprompts.domain.usecase.ImportParsedPromptsUseCase
import com.arny.aiprompts.domain.usecase.SavePromptsAsFilesUseCase
import com.arny.aiprompts.domain.usecase.ScrapeWebsiteUseCase
import com.arny.aiprompts.domain.usecase.ScraperResult
import com.arny.aiprompts.presentation.ui.scraper.PageStringParser
import com.benasher44.uuid.uuid4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * Мастер скрапинга с правильной логикой:
 *
 * 1. PAGE_INPUT — скачать первую страницу (если нет), показать статус, ввод диапазона
 * 2. RESOLVING — парсим индекс из стр.1, определяем нужные страницы, скачиваем,
 *    ищем посты по postId в скачанных HTML
 * 3. ANALYSIS — парсим ТОЛЬКО посты по ссылкам из индекса, с названиями из индекса
 * 4. IMPORT — выбор и импорт промптов с полным превью
 */
class DefaultScraperWizardComponent(
    componentContext: ComponentContext,
    private val scrapeUseCase: ScrapeWebsiteUseCase,
    private val webScraper: IWebScraper,
    private val promptSynchronizer: IPromptSynchronizer,
    private val promptsRepository: IPromptsRepository,
    private val fileDataSource: FileDataSource,
    private val onBack: () -> Unit
) : ScraperWizardComponent, ComponentContext by componentContext {

    private val scope = coroutineScope()
    private val _state = MutableStateFlow(ScraperWizardState())
    override val state: StateFlow<ScraperWizardState> = _state.asStateFlow()

    private val indexParser = IndexParser()
    private val pageParser = PromptPageParser()
    private val savePromptsAsFilesUseCase = SavePromptsAsFilesUseCase(fileDataSource)

    private val topicUrl = "https://4pda.to/forum/index.php?showtopic=1109539"

    private var resolveJob: Job? = null
    private var analysisJob: Job? = null

    init {
        // При открытии: проверяем, есть ли уже страница 1 и другие файлы
        scope.launch(Dispatchers.IO) {
            checkPage1AndExistingFiles()
        }
    }

    /**
     * Проверяет наличие page_1.html и других файлов при старте.
     */
    private suspend fun checkPage1AndExistingFiles() {
        try {
            val saveDir = webScraper.getSaveDirectory()
            val page1 = File(saveDir, "page_1.html")
            val allFiles = webScraper.getExistingScrapedFiles()
            val pageNumbers = allFiles.mapNotNull { filePath ->
                File(filePath).name
                    .removePrefix("page_")
                    .removeSuffix(".html")
                    .toIntOrNull()
            }.sorted()

            if (page1.exists()) {
                val checkResult = webScraper.checkExistingFiles(pageNumbers)
                _state.update {
                    it.copy(
                        page1Exists = true,
                        pagesInput = if (pageNumbers.size > 1) pageNumbers.joinToString(", ") else "",
                        pageCheckResult = checkResult,
                        pagesToDownload = emptyList()
                    )
                }
                addLog("Первая страница найдена. Скачано всего ${allFiles.size} страниц.")
                addLog("Нажмите 'Далее' для парсинга индекса.")
            } else {
                addLog("Первая страница не найдена. Нажмите 'Скачать стр. 1' для начала.")
            }
        } catch (e: Exception) {
            addLog("Ошибка при проверке файлов: ${e.message}")
        }
    }

    // ========== Step 1: PAGE_INPUT ==========

    override fun onPagesInputChanged(input: String) {
        _state.update { it.copy(pagesInput = input) }
    }

    override fun onCheckPagesClicked() {
        addLog("Button clicked: onCheckPagesClicked")
        // Здесь: скачиваем первую страницу если её нет
        scope.launch(Dispatchers.IO) {
            val saveDir = webScraper.getSaveDirectory()
            val page1 = File(saveDir, "page_1.html")

            if (!page1.exists()) {
                addLog("Скачиваю первую страницу...")
                _state.update { it.copy(isDownloading = true) }
                try {
                    downloadPagesAndWait(listOf(1))
                } finally {
                    _state.update { it.copy(isDownloading = false) }
                }
            }

            if (page1.exists()) {
                _state.update { it.copy(page1Exists = true) }
                addLog("Первая страница готова.")
            } else {
                addError(WizardStep.PAGE_INPUT, "Не удалось скачать первую страницу")
            }

            // Обновляем список существующих файлов
            val allFiles = webScraper.getExistingScrapedFiles()
            val pageNumbers = allFiles.mapNotNull { filePath ->
                File(filePath).name.removePrefix("page_").removeSuffix(".html").toIntOrNull()
            }.sorted()
            if (pageNumbers.isNotEmpty()) {
                val checkResult = webScraper.checkExistingFiles(pageNumbers)
                _state.update { it.copy(pageCheckResult = checkResult) }
            }
        }
    }

    // ========== Step 2: RESOLVING ==========

    override fun onStartResolving() {
        resolveJob?.cancel()
        resolveJob = scope.launch(Dispatchers.IO) {
            _state.update {
                it.copy(
                    currentStep = WizardStep.RESOLVING,
                    resolvingProgress = ResolvingProgress(isRunning = true),
                    logs = emptyList()
                )
            }

            try {
                // 1. Парсим индекс из первой страницы
                val saveDir = webScraper.getSaveDirectory()
                val firstPageFile = File(saveDir, "page_1.html")

                if (!firstPageFile.exists()) {
                    addError(WizardStep.RESOLVING, "Первая страница не найдена. Скачайте её на шаге 1.")
                    _state.update { it.copy(resolvingProgress = ResolvingProgress()) }
                    return@launch
                }

                addLog("Парсинг индекса из первой страницы...")
                val html = firstPageFile.readText()
                val parseResult = indexParser.parseIndex(html, topicUrl)

                val index = when (parseResult) {
                    is IndexParseResult.Success -> {
                        _state.update { it.copy(parsedIndex = parseResult.index) }
                        addLog("Найдено ${parseResult.index.links.size} ссылок на промпты")
                        val categories = parseResult.index.links.groupBy { it.category ?: "Без категории" }
                        categories.forEach { (cat, links) ->
                            addLog("  $cat: ${links.size} промптов")
                        }
                        parseResult.index
                    }

                    is IndexParseResult.Cached -> {
                        _state.update { it.copy(parsedIndex = parseResult.index) }
                        addLog("Загружен кэшированный индекс: ${parseResult.index.links.size} ссылок")
                        parseResult.index
                    }

                    is IndexParseResult.Error -> {
                        addError(WizardStep.RESOLVING, "Ошибка парсинга индекса: ${parseResult.message}")
                        _state.update { it.copy(resolvingProgress = ResolvingProgress()) }
                        return@launch
                    }
                }

                // 2. Ищем postId во всех скачанных HTML файлах
                addLog("Поиск постов в скачанных HTML файлах...")

                val allHtmlFiles = webScraper.getExistingScrapedFiles()
                    .map { File(it) }
                    .filter { it.exists() }

                val totalLinks = index.links.size
                val locations = mutableListOf<PostLocation>()
                val notFoundPostIds = mutableListOf<String>()

                index.links.forEachIndexed { idx, link ->
                    _state.update { state ->
                        state.copy(
                            resolvingProgress = state.resolvingProgress.copy(
                                resolved = idx + 1,
                                totalLinks = totalLinks,
                                currentPostId = link.postId
                            )
                        )
                    }

                    val location = findPostInLocalFiles(link.postId, allHtmlFiles)
                    if (location != null) {
                        locations.add(location)
                    } else {
                        notFoundPostIds.add(link.postId)
                    }
                }

                addLog("Найдено ${locations.size} из $totalLinks постов в скачанных файлах")

                // 3. Если есть ненайденные посты — определяем, какие страницы нужны
                if (notFoundPostIds.isNotEmpty()) {
                    addLog("Не найдено ${notFoundPostIds.size} постов. Попробуем скачать дополнительные страницы.")

                    // Дополнительно: пользователь мог указать диапазон
                    val userPages = PageStringParser.parse(_state.value.pagesInput)
                    val existingPages = allHtmlFiles.mapNotNull { f ->
                        f.name.removePrefix("page_").removeSuffix(".html").toIntOrNull()
                    }.toSet()

                    val pagesToDownload = userPages.filter { it !in existingPages }

                    if (pagesToDownload.isNotEmpty()) {
                        addLog("Скачиваю ${pagesToDownload.size} дополнительных страниц: ${pagesToDownload.joinToString()}")
                        _state.update { it.copy(isDownloading = true) }
                        try {
                            downloadPagesAndWait(pagesToDownload)
                        } finally {
                            _state.update { it.copy(isDownloading = false) }
                        }

                        // Повторный поиск после загрузки
                        val updatedHtmlFiles = webScraper.getExistingScrapedFiles()
                            .map { File(it) }
                            .filter { it.exists() }

                        var newFound = 0
                        notFoundPostIds.toList().forEach { postId ->
                            val loc = findPostInLocalFiles(postId, updatedHtmlFiles)
                            if (loc != null) {
                                locations.add(loc)
                                notFoundPostIds.remove(postId)
                                newFound++
                            }
                        }
                        if (newFound > 0) {
                            addLog("Дополнительно найдено $newFound постов после загрузки")
                        }
                    }
                }

                _state.update { state ->
                    state.copy(
                        postLocations = locations,
                        resolvingProgress = ResolvingProgress(
                            totalLinks = totalLinks,
                            resolved = locations.size,
                            isRunning = false
                        )
                    )
                }

                val stillMissing = totalLinks - locations.size
                addLog("Итого: найдено ${locations.size} постов" +
                        if (stillMissing > 0) ", не найдено: $stillMissing" else "")
                addLog("Нажмите 'Далее' для анализа промптов.")

            } catch (e: Exception) {
                addError(WizardStep.RESOLVING, "Ошибка при разрешении", e.message)
                _state.update { it.copy(resolvingProgress = ResolvingProgress()) }
            }
        }
    }

    /**
     * Ищет пост по postId в локально сохранённых HTML файлах.
     */
    private fun findPostInLocalFiles(postId: String, htmlFiles: List<File>): PostLocation? {
        val patterns = listOf(
            "data-post=\"$postId\"",
            "id=\"entry$postId\"",
            "id=\"entry-$postId\"",
        )

        for (file in htmlFiles) {
            try {
                val content = file.readText()
                if (patterns.any { content.contains(it) }) {
                    val pageNum = file.name
                        .removePrefix("page_")
                        .removeSuffix(".html")
                        .toIntOrNull() ?: continue
                    val pageOffset = (pageNum - 1) * 20
                    return PostLocation(
                        postId = postId,
                        pageOffset = pageOffset,
                        directUrl = "$topicUrl&st=$pageOffset#entry$postId"
                    )
                }
            } catch (_: Exception) {
                // Игнорируем битые файлы
            }
        }
        return null
    }

    // ========== Step 3: ANALYSIS ==========

    override fun onStartAnalysisClicked() {
        analysisJob?.cancel()
        analysisJob = scope.launch(Dispatchers.IO) {
            val currentState = _state.value
            val index = currentState.parsedIndex

            if (index == null) {
                addError(WizardStep.ANALYSIS, "Индекс не загружен. Вернитесь на шаг 'Разрешение'.")
                return@launch
            }

            val locationsMap = currentState.postLocations.associateBy { it.postId }

            _state.update {
                it.copy(
                    analysisProgress = AnalysisProgress(
                        totalPrompts = index.links.size,
                        isRunning = true
                    ),
                    indexPrompts = emptyList(),
                    selectedPrompts = emptySet(),
                    logs = emptyList()
                )
            }

            addLog("Извлечение промптов ТОЛЬКО из ссылок индекса (${index.links.size} шт.)")

            val prompts = mutableListOf<PromptData>()
            var processed = 0
            var errors = 0
            var skippedDuplicates = 0
            var notFound = 0

            // Получаем существующие sourceId для дедупликации
            val existingPrompts = promptsRepository.getAllPrompts().first()
            val existingSourceIds = existingPrompts.mapNotNull {
                it.metadata.source?.takeIf { s -> s.isNotEmpty() }
            }.toSet()

            val saveDir = webScraper.getSaveDirectory()

            for (link in index.links) {
                // Проверяем дубликаты
                if (link.postId in existingSourceIds) {
                    skippedDuplicates++
                    processed++
                    updateAnalysisProgress(processed, prompts.size, errors)
                    continue
                }

                // Ищем расположение поста
                val location = locationsMap[link.postId]
                if (location == null) {
                    notFound++
                    processed++
                    updateAnalysisProgress(processed, prompts.size, errors)
                    continue
                }

                val pageNum = location.pageOffset / 20 + 1
                val htmlFile = File(saveDir, "page_$pageNum.html")

                if (!htmlFile.exists()) {
                    errors++
                    processed++
                    updateAnalysisProgress(processed, prompts.size, errors)
                    continue
                }

                // Парсим пост по postId
                val parseResult = pageParser.parsePage(htmlFile, link.postId)
                if (!parseResult.success) {
                    errors++
                    processed++
                    updateAnalysisProgress(processed, prompts.size, errors)
                    continue
                }

                // Используем название из индекса (spoilerTitle) как основное
                val promptData = convertToPromptData(link, parseResult)
                prompts.add(promptData)

                processed++
                updateAnalysisProgress(processed, prompts.size, errors)

                if (processed % 20 == 0) {
                    addLog("  Обработано $processed/${index.links.size}")
                }
            }

            _state.update { state ->
                state.copy(
                    indexPrompts = prompts,
                    analysisProgress = AnalysisProgress(
                        totalPrompts = index.links.size,
                        processed = processed,
                        newPrompts = prompts.size,
                        duplicates = skippedDuplicates,
                        errors = errors,
                        isRunning = false
                    ),
                    selectedPrompts = prompts.map { it.id }.toSet()
                )
            }

            addLog("Анализ завершён.")
            addLog("Новых: ${prompts.size}, дубликатов: $skippedDuplicates, не найдено: $notFound, ошибок: $errors")
        }
    }

    private fun updateAnalysisProgress(processed: Int, newPrompts: Int, errors: Int) {
        _state.update { state ->
            state.copy(
                analysisProgress = state.analysisProgress.copy(
                    processed = processed,
                    newPrompts = newPrompts,
                    errors = errors
                )
            )
        }
    }

    /**
     * Название берётся из индекса (spoilerTitle), контент — из парсера.
     */
    private fun convertToPromptData(
        link: com.arny.aiprompts.domain.index.model.IndexLink,
        parseResult: PromptPageParser.ParseResult
    ): PromptData {
        val category = CategoryTagMapper.mapToAppCategory(link.category ?: "")
        val tags = CategoryTagMapper.getTagsWithAutoDetect(link.category ?: "", parseResult.cleanContent ?: "")

        // Название: приоритет — из индекса (spoilerTitle), затем из парсера, затем fallback
        val title = link.spoilerTitle?.takeIf { it.isNotBlank() }
            ?: parseResult.promptTitle
            ?: "Prompt #${link.postId}"

        val timestamp = System.currentTimeMillis()

        return PromptData(
            id = uuid4().toString(),
            sourceId = link.postId,
            title = title,
            description = parseResult.cleanContent?.take(300) ?: "",
            variants = listOf(PromptVariant(content = parseResult.promptContent ?: "")),
            author = Author(name = "4pda User", id = ""),
            createdAt = timestamp,
            updatedAt = timestamp,
            tags = tags,
            category = category,
            source = link.originalUrl
        )
    }

    // ========== Step 4: IMPORT ==========

    override fun onTogglePromptSelection(promptId: String, selected: Boolean) {
        _state.update { state ->
            val newSet = if (selected) state.selectedPrompts + promptId else state.selectedPrompts - promptId
            state.copy(selectedPrompts = newSet)
        }
    }

    override fun onSelectAllPrompts(selected: Boolean) {
        _state.update { state ->
            val allIds = state.indexPrompts.map { it.id }.toSet()
            state.copy(selectedPrompts = if (selected) allIds else emptySet())
        }
    }

    override fun onImportSelectedClicked() {
        val selectedIds = _state.value.selectedPrompts
        if (selectedIds.isEmpty()) {
            addError(WizardStep.IMPORT, "Не выбрано ни одного промпта для импорта")
            return
        }

        val promptsToImport = _state.value.indexPrompts.filter { it.id in selectedIds }

        scope.launch(Dispatchers.IO) {
            _state.update { it.copy(analysisProgress = it.analysisProgress.copy(isRunning = true)) }

            val result = savePromptsAsFilesUseCase(promptsToImport)

            result.fold(
                onSuccess = { files ->
                    val categoryBreakdown = files.groupBy {
                        it.parentFile?.name ?: "unknown"
                    }.mapValues { it.value.size }

                    val importResult = ImportParsedPromptsUseCase.ImportResult(
                        success = true,
                        totalFiles = files.size,
                        totalPrompts = files.size,
                        importedCount = files.size,
                        skippedCount = 0,
                        errors = emptyList(),
                        categoryBreakdown = categoryBreakdown
                    )

                    _state.update { state ->
                        state.copy(
                            importResult = importResult,
                            analysisProgress = state.analysisProgress.copy(isRunning = false),
                            currentStep = WizardStep.IMPORT
                        )
                    }
                    addLog("Импортировано ${files.size} промптов")

                    promptSynchronizer.loadLocalPrompts()
                    addLog("Промпты загружены в приложение")
                    promptsRepository.invalidateSortDataCache()
                },
                onFailure = { e ->
                    addError(WizardStep.IMPORT, "Ошибка при импорте", e.message)
                    _state.update { it.copy(analysisProgress = it.analysisProgress.copy(isRunning = false)) }
                }
            )
        }
    }

    // ========== Preview ==========

    override fun onPromptPreviewClicked(prompt: PromptData) {
        addLog("Предпросмотр: ${prompt.title}")
    }

    // ========== Navigation ==========

    override fun onBackToPreviousStep() {
        _state.update { state ->
            val prevStep = when (state.currentStep) {
                WizardStep.PAGE_INPUT -> WizardStep.PAGE_INPUT
                WizardStep.RESOLVING -> WizardStep.PAGE_INPUT
                WizardStep.ANALYSIS -> WizardStep.RESOLVING
                WizardStep.IMPORT -> WizardStep.ANALYSIS
            }
            state.copy(currentStep = prevStep)
        }
    }

    override fun onNextStep() {
        val currentState = _state.value
        when (currentState.currentStep) {
            WizardStep.PAGE_INPUT -> {
                // Переход к RESOLVING: парсинг индекса + поиск постов
                onStartResolving()
            }

            WizardStep.RESOLVING -> {
                if (!currentState.resolvingProgress.isRunning && currentState.parsedIndex != null) {
                    _state.update { it.copy(currentStep = WizardStep.ANALYSIS) }
                }
            }

            WizardStep.ANALYSIS -> {
                if (!currentState.analysisProgress.isRunning && currentState.indexPrompts.isNotEmpty()) {
                    _state.update { it.copy(currentStep = WizardStep.IMPORT) }
                }
            }

            WizardStep.IMPORT -> { /* финальный шаг */ }
        }
    }

    override fun onFinish() {
        onBack()
    }

    // ========== Error & Utils ==========

    override fun onCopyErrorsToClipboard() {
        val errorsText = _state.value.errorMessages.joinToString("\n") {
            "[${it.step}] ${it.message}${it.details?.let { d -> " - $d" } ?: ""}"
        }
        copyToClipboard(errorsText)
    }

    override fun onDismissError(error: ScraperError) {
        _state.update { state -> state.copy(errorMessages = state.errorMessages - error) }
    }

    private suspend fun downloadPagesAndWait(pages: List<Int>) {
        addLog("=== DOWNLOAD START ===")
        addLog("Pages: $pages")
        
        try {
            scrapeUseCase(topicUrl, pages).collect { result ->
                addLog(">>> $result")
                when (result) {
                    is ScraperResult.InProgress -> addLog(result.message)
                    is ScraperResult.Success -> addLog("Загружено ${result.files.size} страниц")
                    is ScraperResult.Error -> addLog("Ошибка: ${result.errorMessage}")
                }
            }
            addLog("=== DOWNLOAD DONE ===")
        } catch (e: Exception) {
            addLog("EXCEPTION: ${e.message}")
            addLog("Type: ${e.javaClass.name}")
            e.printStackTrace()
        }
    }

    private fun addError(step: WizardStep, message: String, details: String? = null) {
        val error = ScraperError(step, message, details)
        _state.update { state -> state.copy(errorMessages = state.errorMessages + error) }
        addLog("ОШИБКА: $message${details?.let { ": $it" } ?: ""}")
    }

    private fun addLog(message: String) {
        _state.update { state -> state.copy(logs = (state.logs + message).takeLast(200)) }
    }

    private fun copyToClipboard(text: String) {
        try {
            val selection = java.awt.datatransfer.StringSelection(text)
            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(selection, selection)
        } catch (e: Exception) {
            addError(WizardStep.PAGE_INPUT, "Не удалось скопировать в буфер обмена", e.message)
        }
    }
}
