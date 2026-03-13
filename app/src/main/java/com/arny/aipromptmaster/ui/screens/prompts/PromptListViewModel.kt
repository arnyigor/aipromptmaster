package com.arny.aipromptmaster.ui.screens.prompts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.aipromptmaster.domain.interactors.IPromptsInteractor
import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.models.SyncStatus
import com.arny.aipromptmaster.domain.models.strings.StringHolder
import com.arny.aipromptmaster.domain.repositories.IPromptSynchronizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 1. STATE: Данные, которые нужны для отрисовки UI (кроме списка Paging)
data class PromptListUiState(
    val query: String = "",
    val prompts: List<Prompt> = emptyList(),
    val availableCategories: List<String> = emptyList(), // Список всех категорий из БД
    val selectedCategory: String? = null,                // Текущая выбранная категория (null = все)
    val availableTags: List<String> = emptyList(),       // Список всех тегов из БД
    val selectedTags: Set<String> = emptySet(),          // Выбранные теги
    val isFiltersExpanded: Boolean = false,
    val isSyncing: Boolean = false,
    val syncErrorMessage: StringHolder? = null
)

// 2. INTENT: Действия пользователя
sealed interface PromptListIntent {
    data class SearchQueryChanged(val query: String) : PromptListIntent
    data class SelectCategory(val category: String?) : PromptListIntent
    data class ToggleTag(val tag: String) : PromptListIntent
    data object ToggleFiltersVisibility : PromptListIntent
    data object ClearSearchAndFilters : PromptListIntent
    data object RetrySync : PromptListIntent
    data class OnPromptClick(val id: String) : PromptListIntent
    data class ToggleFavorite(val id: String, val currentIsFavorite: Boolean) : PromptListIntent
    data class CopyText(val id: String) : PromptListIntent
    data class RemovePending(val id: String) : PromptListIntent
    data class RemovePrompt(val id: String) : PromptListIntent
}

// 3. EFFECT: Одноразовые события (Навигация, Тосты)
sealed interface PromptListEffect {
    data class NavigateToDetails(val promptId: String) : PromptListEffect
    data class ShowToast(val message: StringHolder) : PromptListEffect
    data class Copy(val text: String, val label: String) : PromptListEffect
    data class ShowDeleteConfirmation(val promptId: String) : PromptListEffect
}

class PromptListViewModel(
    navScreen: String,
    private val interactor: IPromptsInteractor,
    private val promptSynchronizer: IPromptSynchronizer
) : ViewModel() {

    // --- 1. UI STATE ---
    // Храним состояние фильтров отдельно
    private val _filtersState = MutableStateFlow(PromptListUiState())

    val uiState: StateFlow<PromptListUiState> = combine(
        _filtersState,
        interactor.observeAllPrompts(),
        promptSynchronizer.status
    ) { filters, allPrompts, syncStatus ->
        calculateState(filters, allPrompts, syncStatus)
    }.stateIn(viewModelScope, SharingStarted.Lazily, PromptListUiState())

    private fun getSortedPrompts(filteredPrompts: List<Prompt>): List<Prompt> {
        val sorted = filteredPrompts.sortedWith(
            compareByDescending<Prompt> { it.isLocal }     // Сначала локальные
                .thenByDescending { it.isFavorite }        // Потом избранные
                .thenByDescending { it.modifiedAt }        // Потом свежие
        )
        return sorted
    }

    private fun getFilteredPrompts(
        allPrompts: List<Prompt>,
        filters: PromptListUiState
    ): List<Prompt> {
        val filteredPrompts = allPrompts.filter { prompt ->
            // Фильтр по строке поиска (title или description)
            val matchesQuery = filters.query.isBlank() ||
                    prompt.title.contains(filters.query, ignoreCase = true) ||
                    (prompt.description?.contains(filters.query, ignoreCase = true) == true)

            // Фильтр по категории (если выбрана)
            val matchesCategory = filters.selectedCategory == null ||
                    prompt.category == filters.selectedCategory // Убедись, что поле category есть в Prompt

            // Фильтр по тегам (если выбраны) - должны содержать ВСЕ выбранные теги
            val matchesTags = filters.selectedTags.isEmpty() ||
                    prompt.tags.containsAll(filters.selectedTags)

            matchesQuery && matchesCategory && matchesTags
        }
        return filteredPrompts
    }

    // --- 2. EFFECTS ---
    private val _effect = Channel<PromptListEffect>()
    val effect = _effect.receiveAsFlow()

    init {
        loadFilters()
        viewModelScope.launch { promptSynchronizer.synchronize() }
    }

    // --- 3. INTENT PROCESSING ---
    fun processIntent(intent: PromptListIntent) {
        when (intent) {
            is PromptListIntent.SearchQueryChanged -> {
                _filtersState.update { it.copy(query = intent.query) }
            }

            is PromptListIntent.SelectCategory -> {
                _filtersState.update { state ->
                    val newCategory =
                        if (state.selectedCategory == intent.category) null else intent.category
                    state.copy(selectedCategory = newCategory)
                }
            }

            is PromptListIntent.ToggleTag -> {
                _filtersState.update { state ->
                    val newTags = if (state.selectedTags.contains(intent.tag)) {
                        state.selectedTags - intent.tag
                    } else {
                        state.selectedTags + intent.tag
                    }
                    state.copy(selectedTags = newTags)
                }
            }

            is PromptListIntent.ToggleFiltersVisibility -> {
                _filtersState.update { it.copy(isFiltersExpanded = !it.isFiltersExpanded) }
            }

            is PromptListIntent.ClearSearchAndFilters -> {
                _filtersState.update {
                    it.copy(
                        query = "",
                        selectedTags = emptySet(),
                        selectedCategory = null
                    )
                }
            }

            is PromptListIntent.RetrySync -> {
                viewModelScope.launch { promptSynchronizer.synchronize() }
            }

            is PromptListIntent.OnPromptClick -> {
                sendEffect(PromptListEffect.NavigateToDetails(intent.id))
            }

            is PromptListIntent.ToggleFavorite -> {
                viewModelScope.launch {
                    try {
                        interactor.toggleFavorite(intent.id)
                    } catch (e: Exception) {
                        sendEffect(PromptListEffect.ShowToast(StringHolder.Text("Ошибка")))
                    }
                }
            }

            is PromptListIntent.RemovePrompt -> {
                viewModelScope.launch {
                    try {
                        interactor.deletePrompt(intent.id)
                        sendEffect(PromptListEffect.ShowToast(StringHolder.Text("Удалено")))
                    } catch (e: Exception) {
                        sendEffect(PromptListEffect.ShowToast(StringHolder.Text("Ошибка удаления:" + e.localizedMessage)))
                    }
                }
            }

            is PromptListIntent.RemovePending -> {
                sendEffect(PromptListEffect.ShowDeleteConfirmation(intent.id))
            }

            is PromptListIntent.CopyText -> {
                viewModelScope.launch {
                    try {
                        val prompt = interactor.getPrompt(intent.id)
                        if (prompt != null) {
                            val content = prompt.content
                            val copy = buildString {
                                content.ru.takeIf { it.isNotBlank() }?.let {
                                    append("RU: $it\n")
                                }
                                content.en.takeIf { it.isNotBlank() }?.let {
                                    append("RU: $it\n")
                                }
                            }
                            sendEffect(PromptListEffect.Copy(copy, "Скопировано"))
                            sendEffect(
                                PromptListEffect.ShowToast(StringHolder.Text("Скопировано"))
                            )
                        } else {
                            sendEffect(
                                PromptListEffect.ShowToast(StringHolder.Text("У промпта не т"))
                            )
                        }
                    } catch (e: Exception) {
                        sendEffect(
                            PromptListEffect.ShowToast(
                                StringHolder.Text("Ошибка:${e.localizedMessage}")
                            )
                        )
                    }
                }
            }
        }
    }

    private fun loadFilters() {
        viewModelScope.launch {
            val categories = interactor.getUniqueCategories()
            val tags = interactor.getUniqueTags()
            _filtersState.update { it.copy(availableCategories = categories, availableTags = tags) }
        }
    }

    private fun sendEffect(effect: PromptListEffect) {
        viewModelScope.launch { _effect.send(effect) }
    }

    private suspend fun calculateState(
        filters: PromptListUiState,
        allPrompts: List<Prompt>,
        syncStatus: SyncStatus
    ): PromptListUiState = withContext(Dispatchers.Default) {
        val sorted = getSortedPrompts(getFilteredPrompts(allPrompts, filters))
        filters.copy(
            prompts = sorted,
            isSyncing = syncStatus is SyncStatus.InProgress,
            syncErrorMessage = if (syncStatus is SyncStatus.Error) StringHolder.Text("Sync Error") else null
        )
    }
}
