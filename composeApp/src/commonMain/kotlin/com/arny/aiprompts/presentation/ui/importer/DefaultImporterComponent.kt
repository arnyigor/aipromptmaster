package com.arny.aiprompts.presentation.ui.importer

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.arny.aiprompts.domain.interfaces.IHybridParser
import com.arny.aiprompts.domain.model.PromptData
import com.arny.aiprompts.domain.model.PromptVariant
import com.arny.aiprompts.domain.model.RawPostData
import com.arny.aiprompts.domain.usecase.ParseRawPostsUseCase
import com.arny.aiprompts.domain.usecase.SavePromptsAsFilesUseCase
import com.benasher44.uuid.uuid4
import io.ktor.client.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class DefaultImporterComponent(
    componentContext: ComponentContext,
    private val filesToImport: List<File>,
    private val parseRawPostsUseCase: ParseRawPostsUseCase,
    private val savePromptsAsFilesUseCase: SavePromptsAsFilesUseCase,
    private val hybridParser: IHybridParser,
    private val httpClient: HttpClient, // Внедряем Ktor Client
    private val onBack: () -> Unit
) : ImporterComponent, ComponentContext by componentContext {

    private val _state = MutableStateFlow(ImporterState(sourceHtmlFiles = filesToImport))
    override val state = _state.asStateFlow()
    private val scope = coroutineScope()

    init {
        loadAndParseFiles()
    }

    private fun loadAndParseFiles() {
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            // 1. Парсим все файлы и получаем список, который МОЖЕТ содержать дубликаты
            val allPostsWithDuplicates = filesToImport.flatMap { file ->
                parseRawPostsUseCase(file).getOrElse { error ->
                    _state.update { s -> s.copy(error = "Ошибка парсинга ${file.name}: ${error.message}") }
                    emptyList()
                }
            }

            // 2. Используем groupBy и first() для удаления дубликатов по postId,
            // оставляя только первое вхождение каждого поста.
            val allPosts = allPostsWithDuplicates
                .groupBy { it.postId }
                .map { it.value.first() }

            // 3. Дальнейшая логика сортировки и обновления state работает уже с уникальным списком
            val sortedPosts = allPosts.sortedWith(
                compareByDescending<RawPostData> { it.fileAttachmentUrl != null }
                    .thenByDescending { it.isLikelyPrompt }
                    .thenByDescending { it.fullHtmlContent.length }
            )

            val firstPostToSelect = sortedPosts.firstOrNull()

            _state.update {
                it.copy(
                    isLoading = false,
                    rawPosts = sortedPosts,
                    selectedPostId = firstPostToSelect?.postId
                )
            }
            if (firstPostToSelect != null) {
                ensureAndPrefillEditedData(firstPostToSelect)
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
            val finalPrompts = _state.value.postsToImport.mapNotNull { postId ->
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
            savePromptsAsFilesUseCase(finalPrompts)
                .onSuccess {
                    println("Успешно сохранено ${it.size} файлов.")
                    onBack()
                }
                .onFailure { error ->
                    _state.update { s -> s.copy(isLoading = false, error = "Ошибка сохранения: ${error.message}") }
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
}