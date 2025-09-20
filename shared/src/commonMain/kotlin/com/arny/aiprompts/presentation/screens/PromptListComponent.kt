package com.arny.aiprompts.presentation.screens

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.arny.aiprompts.domain.errors.DomainError
import com.arny.aiprompts.domain.model.Prompt
import com.arny.aiprompts.domain.usecase.GetPromptsUseCase
import com.arny.aiprompts.domain.usecase.ImportJsonUseCase
import com.arny.aiprompts.domain.usecase.ToggleFavoriteUseCase
import com.arny.aiprompts.domain.usecase.DeletePromptUseCase
import com.benasher44.uuid.uuid4
import com.arny.aiprompts.presentation.ui.prompts.PromptsListState
import com.arny.aiprompts.presentation.ui.prompts.SortOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

interface PromptListComponent {
    val state: StateFlow<PromptsListState>

    fun onRefresh()

    // --- Обновленные и новые методы для UI ---
    fun onPromptClicked(id: String)
    fun onFavoriteClicked(id: String)
    fun onSearchQueryChanged(query: String)
    fun onCategoryChanged(category: String)
    fun onTagsChanged(tags: List<String>)
    fun onSortOrderChanged(sortOrder: SortOrder)
    fun onFavoritesToggleChanged(isFavoritesOnly: Boolean)
    fun onSortDirectionToggle()

    // Методы для правой панели действий
    fun onAddPromptClicked()
    fun onEditPromptClicked()
    fun onDeletePromptClicked()

    fun onMoreMenuToggle(isVisible: Boolean)
    fun onSettingsClicked()
    fun onNavigateToScraperClicked()
    fun onNavigateToLLMClicked()
}

class DefaultPromptListComponent(
    componentContext: ComponentContext,
    private val getPromptsUseCase: GetPromptsUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val importJsonUseCase: ImportJsonUseCase,
    private val deletePromptUseCase: DeletePromptUseCase,
    private val onNavigateToDetails: (promptId: String) -> Unit,
    private val onNavigateToScraper: () -> Unit,
    private val onNavigateToLLM: () -> Unit,
) : PromptListComponent, ComponentContext by componentContext {

    private companion object {
        const val PAGE_SIZE = 20
    }

    private val _state = MutableStateFlow(PromptsListState(isLoading = true))
    override val state: StateFlow<PromptsListState> = _state.asStateFlow()

    private val scope = coroutineScope()

    init {
        // Подписываемся на Flow из репозитория, который теперь будет обновляться
        observePrompts()

        // Запускаем первоначальный импорт
        onRefresh()
    }

    private fun observePrompts() {
        scope.launch {
            getPromptsUseCase.getPromptsFlow() // Используем ваш существующий метод
                .collect { result ->
                    result.onSuccess { prompts ->
                        _state.update { it.copy(allPrompts = prompts, isLoading = false) }
                        applyFiltersAndSorting() // Применяем фильтры к новым данным
                    }.onFailure { error ->
                        _state.update { it.copy(error = error.message, isLoading = false) }
                    }
                }
        }
    }

    override fun onPromptClicked(id: String) {
        // Обновляем ID выбранного элемента для правой панели
        _state.update { it.copy(selectedPromptId = id) }
        // Можно и сразу навигацию делать, если на desktop не нужна правая панель
        onNavigateToDetails(id)
    }

    override fun onFavoriteClicked(id: String) {
        scope.launch {
            toggleFavoriteUseCase(id)
        }
    }

    override fun onCategoryChanged(category: String) {
        _state.update { it.copy(selectedCategory = category) }
        applyFiltersAndSorting()
    }

    override fun onTagsChanged(tags: List<String>) {
        _state.update { it.copy(selectedTags = tags) }
        applyFiltersAndSorting()
    }

    override fun onSortDirectionToggle() {
        _state.update { it.copy(isSortAscending = !it.isSortAscending) }
        applyFiltersAndSorting() // <-- Важно переприменить сортировку
    }

    override fun onSortOrderChanged(sortOrder: SortOrder) {
        _state.update { it.copy(selectedSortOrder = sortOrder) }
        applyFiltersAndSorting()
    }

    override fun onFavoritesToggleChanged(isFavoritesOnly: Boolean) {
        _state.update { it.copy(isFavoritesOnly = isFavoritesOnly) }
        applyFiltersAndSorting()
    }

    override fun onAddPromptClicked() {
        // Генерируем UUID для нового промпта и открываем экран редактирования
        val newPromptId = uuid4().toString()
        onNavigateToDetails(newPromptId)
    }

    override fun onEditPromptClicked() {
        val selectedPromptId = state.value.selectedPromptId
        if (selectedPromptId != null) {
            val selectedPrompt = state.value.allPrompts.find { it.id == selectedPromptId }
            if (selectedPrompt?.isLocal == true) {
                // Навигация к экрану редактирования
                onNavigateToDetails(selectedPromptId)
            }
        }
    }

    override fun onDeletePromptClicked() {
        val promptId = state.value.selectedPromptId
        if (promptId != null) {
            val selectedPrompt = state.value.allPrompts.find { it.id == promptId }
            if (selectedPrompt?.isLocal == true) {
                scope.launch {
                    _state.update { it.copy(isDeletingPrompt = true, deleteError = null) }

                    val result = deletePromptUseCase(promptId)

                    result.onSuccess {
                        _state.update {
                            it.copy(
                                isDeletingPrompt = false,
                                selectedPromptId = null // Сбрасываем выбор
                            )
                        }
                    }.onFailure { error ->
                        _state.update {
                            it.copy(
                                isDeletingPrompt = false,
                                deleteError = error.message ?: "Failed to delete prompt"
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onMoreMenuToggle(isVisible: Boolean) {
        _state.update { it.copy(isMoreMenuVisible = isVisible) }
    }

    override fun onSettingsClicked() {

    }

    override fun onNavigateToScraperClicked() {
        onNavigateToScraper()
    }

    override fun onNavigateToLLMClicked() {
        onNavigateToLLM()
    }

    // А этот метод для полного обновления с нуля (pull-to-refresh)
    override fun onRefresh() {
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = importJsonUseCase()
            result.onSuccess { count ->
                println("Успешно импортировано $count промптов.")
                // Данные в state обновятся автоматически благодаря Flow в observePrompts()
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, error = "Ошибка импорта: ${error.message}") }
            }
        }
    }
    // Этот метод теперь будет вызываться для загрузки СЛЕДУЮЩЕЙ страницы
    fun loadNextPage() {
        val currentState = _state.value
        // Запускаем загрузку только если уже не идет загрузка и не все данные загружены
        if (currentState.isPageLoading || currentState.endReached) return

        scope.launch {
            _state.update { it.copy(isPageLoading = true) }
            loadPrompts(fromRefresh = false) // fromRefresh = false означает, что мы добавляем данные, а не заменяем
        }
    }

    /**
     * Основной метод загрузки данных.
     * @param fromRefresh true, если нужно очистить старые данные (при поиске или pull-to-refresh),
     *                    false, если нужно добавить к существующим (пагинация).
     */
    private fun loadPrompts(fromRefresh: Boolean) {
        scope.launch {
            val currentState = _state.value
            val currentPage = if (fromRefresh) 0 else currentState.page

            // Вызываем suspend-метод из UseCase, который возвращает Result
            val result = getPromptsUseCase.search(
                query = currentState.searchQuery,
                category = if (currentState.selectedCategory == "Все категории") null else currentState.selectedCategory,
                status = if (currentState.isFavoritesOnly) "favorite" else null,
                tags = emptyList(), // Добавим позже, если нужно
                offset = currentPage * PAGE_SIZE,
                limit = PAGE_SIZE
            )

            result.onSuccess { newPrompts ->
                _state.update {
                    val currentPrompts = if (fromRefresh) emptyList() else it.allPrompts
                    it.copy(
                        isLoading = false, // Основная загрузка завершена
                        isPageLoading = false, // Загрузка страницы завершена
                        // Добавляем новые промпты к старым
                        allPrompts = currentPrompts + newPrompts,
                        // Если вернулось меньше, чем мы просили, значит, данные закончились
                        endReached = newPrompts.size < PAGE_SIZE,
                        // Увеличиваем номер страницы для следующего запроса
                        page = currentPage + 1,
                        error = null
                    )
                }
                // После обновления полного списка, применяем сортировку
                applyFiltersAndSorting()

            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        isPageLoading = false,
                        // Используем наш DomainError для отображения красивого сообщения
                        error = (throwable as? DomainError)?.stringHolder?.toString() ?: "Неизвестная ошибка"
                    )
                }
            }
        }
    }

    // Также нужно обновить `onSearchQueryChanged` и другие фильтры,
    // чтобы они сбрасывали пагинацию и запускали загрузку заново.
    override fun onSearchQueryChanged(query: String) {
        _state.update { it.copy(searchQuery = query, page = 0, endReached = false) }
        loadPrompts(fromRefresh = true)
    }

    private fun applyFiltersAndSorting() {
        val currentState = _state.value
        val filteredList = currentState.allPrompts.filter { prompt ->
            val favoriteMatch = if (currentState.isFavoritesOnly) prompt.isFavorite else true
            val categoryMatch =
                currentState.selectedCategory == "Все категории" || prompt.category == currentState.selectedCategory
            val tagsMatch = if (currentState.selectedTags.isEmpty()) {
                true
            } else {
                currentState.selectedTags.all { selectedTag ->
                    prompt.tags.any { promptTag -> promptTag.equals(selectedTag, ignoreCase = true) }
                }
            }
            val queryMatch = if (currentState.searchQuery.isBlank()) {
                true
            } else {
                val query = currentState.searchQuery.trim().lowercase()
                prompt.title.lowercase().contains(query) ||
                prompt.description?.lowercase()?.contains(query) == true ||
                prompt.content?.ru?.lowercase()?.contains(query) == true ||
                prompt.content?.en?.lowercase()?.contains(query) == true ||
                prompt.tags.any { it.lowercase().contains(query) }
            }
            favoriteMatch && categoryMatch && tagsMatch && queryMatch
        }

        val sortedList = when (currentState.selectedSortOrder) {
            SortOrder.BY_FAVORITE_DESC -> filteredList.sortedWith(compareByDescending<Prompt> { it.isFavorite }.thenByDescending { it.modifiedAt })
            SortOrder.BY_NAME_ASC -> filteredList.sortedBy { it.title }
            SortOrder.BY_NAME_DESC -> filteredList.sortedByDescending { it.title }
            SortOrder.BY_DATE_DESC -> filteredList.sortedByDescending { it.modifiedAt ?: it.createdAt }
            SortOrder.BY_DATE_ASC -> filteredList.sortedBy { it.modifiedAt ?: it.createdAt }
            SortOrder.BY_POPULARITY_DESC -> filteredList.sortedByDescending { it.rating }
            SortOrder.BY_SIZE_DESC -> filteredList.sortedByDescending { (it.content?.ru?.length ?: 0) + (it.content?.en?.length ?: 0) }
            SortOrder.BY_SIZE_ASC -> filteredList.sortedBy { (it.content?.ru?.length ?: 0) + (it.content?.en?.length ?: 0) }
            SortOrder.BY_CATEGORY -> filteredList.sortedBy { it.category }
        }
        _state.update {
            it.copy(
                currentPrompts = if (it.isSortAscending) sortedList.reversed() else sortedList
            )
        }
    }
}
