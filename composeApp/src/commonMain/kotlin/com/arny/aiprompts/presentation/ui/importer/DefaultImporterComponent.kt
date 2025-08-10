package com.arny.aiprompts.presentation.ui.importer


import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.arny.aiprompts.domain.model.PromptData
import com.arny.aiprompts.domain.model.PromptVariant
import com.arny.aiprompts.domain.usecase.ParseRawPostsUseCase
import com.arny.aiprompts.domain.usecase.SavePromptsAsFilesUseCase
import com.benasher44.uuid.uuid4
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import java.io.File

class DefaultImporterComponent(
    componentContext: ComponentContext,
    private val filesToImport: List<File>,
    private val parseRawPostsUseCase: ParseRawPostsUseCase,
    private val savePromptsAsFilesUseCase: SavePromptsAsFilesUseCase,
    private val onBack: () -> Unit // Коллбэк для возврата назад
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
            val allPosts = filesToImport.flatMap { file ->
                parseRawPostsUseCase(file).getOrElse { error ->
                    _state.update { s -> s.copy(error = "Ошибка парсинга ${file.name}: ${error.message}") }
                    emptyList()
                }
            }

            val firstLikelyPost = allPosts.firstOrNull { it.isLikelyPrompt }

            _state.update {
                it.copy(
                    isLoading = false,
                    rawPosts = allPosts,
                    // Автоматически выбираем первый "вероятный" промпт, если он есть
                    selectedPostId = firstLikelyPost?.postId
                )
            }
            // Если пост был выбран автоматически, сразу предзаполняем поля
            if (firstLikelyPost != null) {
                prefillEditableFields(firstLikelyPost)
            }
        }
    }

    override fun onPostClicked(postId: String) {
        val selectedPost = _state.value.rawPosts.find { it.postId == postId } ?: return
        _state.update { it.copy(selectedPostId = postId) }
        prefillEditableFields(selectedPost)
    }

    private fun prefillEditableFields(post: com.arny.aiprompts.domain.model.RawPostData) {
        // Очищаем HTML и предзаполняем поля
        val cleanContent = Jsoup.parse(post.fullHtmlContent).text()
        _state.update {
            it.copy(
                editableTitle = "Промпт от ${post.author.name} (${post.postId})",
                editableDescription = cleanContent.take(250),
                editableContent = cleanContent
            )
        }
    }

    override fun onTogglePostForImport(postId: String, isChecked: Boolean) {
        val currentSet = _state.value.postsToImport.toMutableSet()
        if (isChecked) currentSet.add(postId) else currentSet.remove(postId)
        _state.update { it.copy(postsToImport = currentSet) }
    }

    override fun onTitleChanged(newTitle: String) { _state.update { it.copy(editableTitle = newTitle) } }
    override fun onDescriptionChanged(newDescription: String) { _state.update { it.copy(editableDescription = newDescription) } }
    override fun onContentChanged(newContent: String) { _state.update { it.copy(editableContent = newContent) } }

    override fun onImportClicked() {
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            // Собираем финальные данные из отмеченных постов и отредактированных полей
            // ВАЖНО: Сейчас мы берем отредактированные поля только для ОДНОГО выбранного поста.
            // В будущем это нужно будет расширить, чтобы хранить отредактированные данные для каждого поста.
            // Для MVP это упрощение приемлемо.
            val finalPrompts = _state.value.postsToImport.mapNotNull { postId ->
                _state.value.rawPosts.find { it.postId == postId }?.let { rawPost ->
                    PromptData(
                        id = uuid4().toString(),
                        sourceId = rawPost.postId,
                        title = _state.value.editableTitle,
                        description = _state.value.editableDescription,
                        variants = listOf(PromptVariant(content = _state.value.editableContent)),
                        author = rawPost.author,
                        createdAt = rawPost.date.toEpochMilliseconds(),
                        updatedAt = rawPost.date.toEpochMilliseconds()
                    )
                }
            }

            savePromptsAsFilesUseCase(finalPrompts)
                .onSuccess {
                    println("Успешно сохранено ${it.size} файлов.")
                    onBack() // Возвращаемся на предыдущий экран
                }
                .onFailure { error ->
                    _state.update { s -> s.copy(isLoading = false, error = "Ошибка сохранения: ${error.message}") }
                }
        }
    }

    override fun onBackClicked() {
        onBack()
    }
}