package com.arny.aiprompts.presentation.ui.importer

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.arny.aiprompts.domain.interfaces.IHybridParser
import com.arny.aiprompts.domain.model.PromptData
import com.arny.aiprompts.domain.model.PromptVariant
import com.arny.aiprompts.domain.model.RawPostData
import com.arny.aiprompts.domain.usecase.ParseRawPostsUseCase
import com.arny.aiprompts.domain.usecase.SavePromptsAsFilesUseCase
import com.arny.aiprompts.presentation.ui.importer.DownloadState
import com.arny.aiprompts.presentation.ui.importer.DownloadedFile
import com.benasher44.uuid.uuid4
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class DefaultImporterComponent(
    componentContext: ComponentContext,
    private val filesToImport: List<File>,
    private val parseRawPostsUseCase: ParseRawPostsUseCase,
    private val savePromptsAsFilesUseCase: SavePromptsAsFilesUseCase,
    private val hybridParser: IHybridParser,
    private val httpClient: HttpClient,
    private val onBack: () -> Unit
) : ImporterComponent, ComponentContext by componentContext {

    private val _state = MutableStateFlow(ImporterState(sourceHtmlFiles = filesToImport))
    override val state = _state.asStateFlow()
    private val scope = coroutineScope()

    // История для undo/redo
    private val stateHistory = mutableListOf<ImporterState>()
    private var historyIndex = -1

    init {
        loadAndParseFiles()
    }

    private fun loadAndParseFiles() {
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            updateProgress(ImportStep.LOADING_FILES, 0f, "Подготовка файлов", filesToImport.size)

            try {
                // 1. Парсим все файлы с прогрессом
                val allPostsWithDuplicates = mutableListOf<RawPostData>()
                filesToImport.forEachIndexed { index, file ->
                    updateProgress(
                        ImportStep.LOADING_FILES,
                        (index + 1).toFloat() / filesToImport.size,
                        file.name,
                        filesToImport.size,
                        index + 1
                    )

                    val posts = parseRawPostsUseCase(file).getOrElse { error ->
                        _state.update { s -> s.copy(error = "Ошибка парсинга ${file.name}: ${error.message}") }
                        emptyList()
                    }
                    allPostsWithDuplicates.addAll(posts)
                }

                updateProgress(ImportStep.LOADING_FILES, 1f, "Удаление дубликатов")

                // 2. Удаление дубликатов
                val allPosts = allPostsWithDuplicates
                    .groupBy { it.postId }
                    .map { it.value.first() }

                updateProgress(ImportStep.LOADING_FILES, 1f, "Сортировка постов")

                // 3. Сортировка
                val sortedPosts = allPosts.sortedWith(
                    compareByDescending<RawPostData> { it.attachments.isNotEmpty() }
                        .thenByDescending { it.isLikelyPrompt }
                        .thenByDescending { it.fullHtmlContent.length }
                )

                val firstPostToSelect = sortedPosts.firstOrNull()

                _state.update {
                    it.copy(
                        isLoading = false,
                        rawPosts = sortedPosts,
                        selectedPostId = firstPostToSelect?.postId,
                        progress = ImportProgress() // Сброс прогресса
                    )
                }

                if (firstPostToSelect != null) {
                    ensureAndPrefillEditedData(firstPostToSelect)
                }

                _state.update {
                    it.copy(successMessage = "Успешно загружено ${sortedPosts.size} постов")
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Критическая ошибка загрузки: ${e.message}",
                        progress = ImportProgress()
                    )
                }
            }
        }
    }

    override fun onPostClicked(postId: String) {
        val selectedPost = _state.value.rawPosts.find { it.postId == postId } ?: return
        _state.update { it.copy(selectedPostId = postId) }
        ensureAndPrefillEditedData(selectedPost)
    }

    override fun onEditDataChanged(editedData: EditedPostData) {
        val postId = _state.value.selectedPostId ?: return
        saveStateToHistory()
        _state.update {
            val newEditedData = it.editedData + (postId to editedData)
            it.copy(editedData = newEditedData)
        }
    }

    override fun onBlockActionClicked(text: String, target: BlockActionTarget) {
        val postId = _state.value.selectedPostId ?: return
        val currentEditedData = _state.value.editedData[postId] ?: return

        val newEditedData = when (target) {
            BlockActionTarget.TITLE -> currentEditedData.copy(title = text.lines().firstOrNull()?.trim() ?: "")
            BlockActionTarget.DESCRIPTION -> currentEditedData.copy(description = if (currentEditedData.description.isBlank()) text else "${currentEditedData.description}\n\n$text")
            BlockActionTarget.CONTENT -> currentEditedData.copy(content = if (currentEditedData.content.isBlank()) text else "${currentEditedData.content}\n\n$text")
        }
        _state.update {
            it.copy(editedData = it.editedData + (postId to newEditedData))
        }
    }

    override fun onSkipPostClicked() {
        // TODO: Добавить ID в список пропущенных
        selectNextUnprocessedPost()
    }

    override fun onSaveAndSelectNextClicked() {
        val postId = _state.value.selectedPostId ?: return
        onTogglePostForImport(postId, true)
        selectNextUnprocessedPost()
    }

    override fun onSaveAndSelectPreviousClicked() {
        val postId = _state.value.selectedPostId ?: return
        onTogglePostForImport(postId, true)
        selectPreviousUnprocessedPost()
    }

    override fun onSelectNextPost() {
        selectNextPost()
    }

    override fun onSelectPreviousPost() {
        selectPreviousPost()
    }

    override fun onTogglePostForImport(postId: String, isChecked: Boolean) {
        val currentSet = _state.value.postsToImport.toMutableSet()
        if (isChecked) currentSet.add(postId) else currentSet.remove(postId)
        _state.update { it.copy(postsToImport = currentSet) }
    }

    override fun onBackClicked() {
        onBack()
    }

    override fun onVariantSelected(variant: PromptVariantData) {
        val postId = _state.value.selectedPostId ?: return
        val currentEditedData = _state.value.editedData[postId] ?: return

        // Когда пользователь кликает на вариант, мы просто меняем основной контент в черновике
        val newEditedData = currentEditedData.copy(content = variant.content)
        _state.update {
            it.copy(editedData = it.editedData + (postId to newEditedData))
        }
    }

    override fun onImportClicked() {
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            updateProgress(ImportStep.GENERATING_JSON, 0f, "Валидация данных")

            try {
                val postsToImport = _state.value.postsToImport
                val validationErrors = mutableMapOf<String, String>()

                // Валидация всех постов
                postsToImport.forEachIndexed { index, postId ->
                    updateProgress(
                        ImportStep.GENERATING_JSON,
                        (index + 1).toFloat() / (postsToImport.size * 2),
                        "Валидация: ${index + 1}/${postsToImport.size}"
                    )

                    if (!validateEditedData(postId)) {
                        validationErrors[postId] = "Ошибки валидации"
                    }
                }

                if (validationErrors.isNotEmpty()) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = "Найдены ошибки валидации в ${validationErrors.size} постах",
                            progress = ImportProgress()
                        )
                    }
                    return@launch
                }

                updateProgress(ImportStep.GENERATING_JSON, 0.5f, "Генерация JSON файлов")

                // Генерация финальных промптов
                val finalPrompts = postsToImport.mapIndexedNotNull { index, postId ->
                    updateProgress(
                        ImportStep.GENERATING_JSON,
                        0.5f + (index + 1).toFloat() / (postsToImport.size * 2),
                        "Создание промпта: ${index + 1}/${postsToImport.size}"
                    )

                    val rawPost = _state.value.rawPosts.find { it.postId == postId }
                    val editedData = _state.value.editedData[postId]
                    if (rawPost != null && editedData != null) {
                        PromptData(
                            id = uuid4().toString(),
                            sourceId = rawPost.postId,
                            title = editedData.title.ifBlank { "Prompt ${rawPost.postId}" },
                            description = editedData.description,
                            variants = listOf(PromptVariant(content = editedData.content)),
                            author = rawPost.author,
                            createdAt = rawPost.date.toEpochMilliseconds(),
                            updatedAt = rawPost.date.toEpochMilliseconds(),
                            category = editedData.category,
                            tags = editedData.tags
                        )
                    } else null
                }

                updateProgress(ImportStep.GENERATING_JSON, 1f, "Сохранение файлов")

                savePromptsAsFilesUseCase(finalPrompts)
                    .onSuccess { savedFiles ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                successMessage = "Успешно сохранено ${savedFiles.size} промптов",
                                progress = ImportProgress()
                            )
                        }
                        // Не закрываем экран сразу, даем пользователю увидеть результат
                    }
                    .onFailure { error ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                error = "Ошибка сохранения: ${error.message}",
                                progress = ImportProgress()
                            )
                        }
                    }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Критическая ошибка: ${e.message}",
                        progress = ImportProgress()
                    )
                }
            }
        }
    }

    private fun ensureAndPrefillEditedData(post: RawPostData) {
        if (!_state.value.editedData.containsKey(post.postId)) {
            val extractedData = hybridParser.analyzeAndExtract(post.fullHtmlContent)

            val newEditedData = extractedData ?: EditedPostData(
                title = "Промпт от ${post.author.name} (${post.postId})",
                description = post.fullHtmlContent
                    .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                    .replace(Regex("<.*?>"), "")
                    .trim(),
                content = ""
            )
            _state.update {
                it.copy(editedData = it.editedData + (post.postId to newEditedData))
            }
        }
    }

    private fun selectNextUnprocessedPost() {
        val currentState = _state.value
        val processedIds = currentState.postsToImport // + пропущенные ID в будущем
        val currentIndex = currentState.rawPosts.indexOfFirst { it.postId == currentState.selectedPostId }

        val nextPost = currentState.rawPosts
            .drop(currentIndex + 1) // Ищем только после текущего
            .firstOrNull { it.postId !in processedIds }
            ?: currentState.rawPosts.firstOrNull { it.postId !in processedIds } // Если не нашли, ищем с начала

        if (nextPost != null) {
            onPostClicked(nextPost.postId)
        } else {
            _state.update { it.copy(selectedPostId = null) }
        }
    }

    private fun selectPreviousUnprocessedPost() {
        val currentState = _state.value
        val processedIds = currentState.postsToImport // + пропущенные ID в будущем
        val currentIndex = currentState.rawPosts.indexOfFirst { it.postId == currentState.selectedPostId }

        val previousPost = currentState.rawPosts
            .take(currentIndex) // Ищем только до текущего
            .lastOrNull { it.postId !in processedIds }
            ?: currentState.rawPosts.lastOrNull { it.postId !in processedIds } // Если не нашли, ищем с конца

        if (previousPost != null) {
            onPostClicked(previousPost.postId)
        } else {
            _state.update { it.copy(selectedPostId = null) }
        }
    }

    private fun selectNextPost() {
        val currentState = _state.value
        val currentIndex = currentState.rawPosts.indexOfFirst { it.postId == currentState.selectedPostId }

        val nextPost = if (currentIndex >= 0 && currentIndex < currentState.rawPosts.size - 1) {
            currentState.rawPosts[currentIndex + 1]
        } else {
            currentState.rawPosts.firstOrNull() // Зацикливаемся на начало
        }

        if (nextPost != null) {
            onPostClicked(nextPost.postId)
        }
    }

    private fun selectPreviousPost() {
        val currentState = _state.value
        val currentIndex = currentState.rawPosts.indexOfFirst { it.postId == currentState.selectedPostId }

        val previousPost = if (currentIndex > 0) {
            currentState.rawPosts[currentIndex - 1]
        } else {
            currentState.rawPosts.lastOrNull() // Зацикливаемся на конец
        }

        if (previousPost != null) {
            onPostClicked(previousPost.postId)
        }
    }

    // --- НОВЫЕ МЕТОДЫ ИНТЕРФЕЙСА ---

    override fun onSearchQueryChanged(query: String) {
        saveStateToHistory()
        _state.update {
            it.copy(filters = it.filters.copy(searchQuery = query))
        }
    }

    override fun onFilterChanged(filter: PostFilters) {
        saveStateToHistory()
        _state.update {
            it.copy(filters = filter)
        }
    }

    override fun onGroupingChanged(grouping: PostGrouping) {
        saveStateToHistory()
        _state.update {
            it.copy(grouping = grouping)
        }
    }

    override fun onTogglePreview() {
        _state.update {
            it.copy(showPreview = !it.showPreview)
        }
    }

    override fun onTogglePostExpansion(postId: String) {
        val currentExpanded = _state.value.expandedPostIds
        val newExpanded = if (postId in currentExpanded) {
            currentExpanded - postId
        } else {
            currentExpanded + postId
        }
        _state.update {
            it.copy(expandedPostIds = newExpanded)
        }
    }

    // Вспомогательная функция для декодирования URL с кириллицей
    private fun decodeUrlWithCyrillic(url: String): String {
        return try {
            // Сначала пробуем декодировать как UTF-8
            URLDecoder.decode(url, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            try {
                // Если не получилось, пробуем Windows-1251
                URLDecoder.decode(url, "Windows-1251")
            } catch (e2: Exception) {
                // Если ничего не получилось, возвращаем оригинальный URL
                url
            }
        }
    }

    override fun onDownloadFile(attachmentUrl: String, filename: String) {
        scope.launch {
            // Логируем начало загрузки
            println("🔄 Начинаем загрузку файла: $filename")
            println("📥 Оригинальный URL: $attachmentUrl")

            // Пробуем декодировать URL с кириллицей
            val decodedUrl = decodeUrlWithCyrillic(attachmentUrl)
            println("📥 Декодированный URL: $decodedUrl")

            // Обновляем состояние - начинаем загрузку
            _state.update {
                it.copy(
                    downloadedFiles = it.downloadedFiles + (attachmentUrl to DownloadedFile(
                        url = attachmentUrl,
                        filename = filename,
                        state = DownloadState.DOWNLOADING
                    ))
                )
            }

            try {
                // Реализуем загрузку файла через HttpClient
                println("🌐 Отправляем HTTP запрос...")
                val response = httpClient.get(decodedUrl)

                println("📊 HTTP статус: ${response.status.value} ${response.status.description}")

                if (response.status.value == 200) {
                    println("✅ HTTP ответ успешен, читаем данные...")

                    // Используем современный API вместо deprecated readRawBytes()
                    val bytes = response.body<ByteArray>()

                    println("📏 Размер файла: ${bytes.size} байт")

                    // Создаем временный файл
                    val tempDir = File(System.getProperty("java.io.tmpdir"), "aiprompts_downloads")
                    if (!tempDir.exists()) {
                        tempDir.mkdirs()
                        println("📁 Создана директория: ${tempDir.absolutePath}")
                    }
                    val tempFile = File(tempDir, filename)
                    tempFile.writeBytes(bytes)

                    println("💾 Файл сохранен: ${tempFile.absolutePath}")

                    // Определяем тип файла и читаем содержимое для текстовых файлов
                    val fileType = filename.substringAfterLast(".", "").lowercase()
                    val content = if (fileType in listOf("txt", "md", "json", "xml", "html")) {
                        val textContent = String(bytes, Charsets.UTF_8)
                        println("📄 Текстовый файл прочитан (${textContent.length} символов)")
                        textContent
                    } else {
                        println("🖼️ Бинарный файл, содержимое не читаем")
                        null // Для бинарных файлов не читаем содержимое
                    }

                    _state.update {
                        it.copy(
                            downloadedFiles = it.downloadedFiles + (attachmentUrl to DownloadedFile(
                                url = attachmentUrl,
                                filename = filename,
                                state = DownloadState.DOWNLOADED,
                                content = content,
                                localPath = tempFile.absolutePath
                            ))
                        )
                    }

                    println("✅ Файл '$filename' успешно загружен и сохранен")
                    _state.update {
                        it.copy(successMessage = "Файл '$filename' успешно загружен (${bytes.size} байт)")
                    }
                } else {
                    val errorMsg = "HTTP ${response.status.value}: ${response.status.description}"
                    println("❌ HTTP ошибка: $errorMsg")

                    // Для 404 ошибки показываем более понятное сообщение
                    val userFriendlyError = when (response.status.value) {
                        404 -> "Файл не найден на сервере (404). Возможно, ссылка устарела или файл был удален."
                        403 -> "Доступ к файлу запрещен (403). Возможно, требуется авторизация."
                        500 -> "Ошибка сервера (500). Попробуйте позже."
                        else -> "Ошибка загрузки: ${response.status.description}"
                    }

                    throw Exception("$errorMsg\n$userFriendlyError")
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Неизвестная ошибка загрузки"
                println("💥 Ошибка загрузки: $errorMsg")
                e.printStackTrace()

                _state.update {
                    it.copy(
                        downloadedFiles = it.downloadedFiles + (attachmentUrl to DownloadedFile(
                            url = attachmentUrl,
                            filename = filename,
                            state = DownloadState.ERROR,
                            error = errorMsg
                        )),
                        error = "Не удалось загрузить '$filename': $errorMsg"
                    )
                }
            }
        }
    }

    override fun onPreviewFile(attachmentUrl: String, filename: String) {
        val downloadedFile = _state.value.downloadedFiles[attachmentUrl]
        if (downloadedFile?.state == DownloadState.DOWNLOADED && downloadedFile.content != null) {
            // Показываем содержимое файла в сообщении
            _state.update {
                it.copy(successMessage = "Содержимое файла '$filename':\n\n${downloadedFile.content}")
            }
        } else if (downloadedFile?.state == DownloadState.DOWNLOADED && downloadedFile.content == null) {
            // Для бинарных файлов показываем информацию о файле
            _state.update {
                it.copy(successMessage = "Файл '$filename' загружен (${File(downloadedFile.localPath ?: "").length()} байт)")
            }
        } else {
            // Если файл не загружен, сначала загружаем его
            onDownloadFile(attachmentUrl, filename)
        }
    }

    override fun onOpenFileInSystem(attachmentUrl: String, filename: String) {
        val downloadedFile = _state.value.downloadedFiles[attachmentUrl]
        if (downloadedFile?.state == DownloadState.DOWNLOADED && downloadedFile.localPath != null) {
            try {
                val file = File(downloadedFile.localPath)
                if (file.exists()) {
                    // Используем Java Desktop API для открытия файла
                    if (Desktop.isDesktopSupported()) {
                        val desktop = Desktop.getDesktop()
                        if (desktop.isSupported(Desktop.Action.OPEN)) {
                            desktop.open(file)
                            _state.update {
                                it.copy(successMessage = "Файл '$filename' открыт в системном просмотрщике")
                            }
                        } else {
                            throw Exception("Открытие файлов не поддерживается на этой платформе")
                        }
                    } else {
                        throw Exception("Desktop API не поддерживается на этой платформе")
                    }
                } else {
                    throw Exception("Файл не найден: ${downloadedFile.localPath}")
                }
            } catch (e: IOException) {
                _state.update {
                    it.copy(error = "Не удалось открыть файл '$filename': ${e.message}")
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(error = "Ошибка открытия файла '$filename': ${e.message}")
                }
            }
        } else {
            _state.update {
                it.copy(error = "Файл '$filename' не загружен или путь недоступен")
            }
        }
    }

    override fun onToggleFileExpansion(postId: String, attachmentUrl: String) {
        val fileId = "$postId:$attachmentUrl"
        val currentExpanded = _state.value.expandedFileIds
        val newExpanded = if (fileId in currentExpanded) {
            currentExpanded - fileId
        } else {
            currentExpanded + fileId
        }
        _state.update {
            it.copy(expandedFileIds = newExpanded)
        }
    }

    override fun onOpenPostInBrowser(postUrl: String) {
        try {
            if (Desktop.isDesktopSupported()) {
                val desktop = Desktop.getDesktop()
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(java.net.URI(postUrl))
                    _state.update {
                        it.copy(successMessage = "Открываем пост в браузере...")
                    }
                } else {
                    throw Exception("Открытие ссылок не поддерживается на этой платформе")
                }
            } else {
                throw Exception("Desktop API не поддерживается на этой платформе")
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(error = "Не удалось открыть ссылку: ${e.message}")
            }
        }
    }

    override fun onDismissError() {
        _state.update {
            it.copy(error = null)
        }
    }

    override fun onDismissSuccess() {
        _state.update {
            it.copy(successMessage = null)
        }
    }

    override fun validateEditedData(postId: String): Boolean {
        val editedData = _state.value.editedData[postId] ?: return false
        val errors = mutableMapOf<String, String>()

        if (editedData.title.isBlank()) {
            errors["Заголовок"] = "Заголовок не может быть пустым"
        }

        if (editedData.content.isBlank()) {
            errors["Контент"] = "Контент промпта не может быть пустым"
        }

        if (editedData.category.isBlank()) {
            errors["Категория"] = "Категория не может быть пустой"
        }

        _state.update {
            it.copy(validationErrors = it.validationErrors + (postId to errors.toMap()))
        }

        return errors.isEmpty()
    }

    override fun getValidationErrors(postId: String): Map<String, Map<String, String>> {
        return _state.value.validationErrors
    }

    // --- ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ---

    private fun saveStateToHistory() {
        val currentState = _state.value
        // Очищаем историю после текущего индекса при новом изменении
        if (historyIndex < stateHistory.size - 1) {
            stateHistory.subList(historyIndex + 1, stateHistory.size).clear()
        }
        stateHistory.add(currentState.copy())
        historyIndex = stateHistory.size - 1

        // Ограничиваем размер истории
        if (stateHistory.size > 50) {
            stateHistory.removeAt(0)
            historyIndex--
        }
    }

    private fun updateProgress(step: ImportStep, progress: Float, currentItem: String = "", totalItems: Int = 0, processedItems: Int = 0) {
        _state.update {
            it.copy(
                progress = ImportProgress(
                    currentStep = step,
                    stepProgress = progress,
                    currentItem = currentItem,
                    totalItems = totalItems,
                    processedItems = processedItems
                )
            )
        }
    }
}