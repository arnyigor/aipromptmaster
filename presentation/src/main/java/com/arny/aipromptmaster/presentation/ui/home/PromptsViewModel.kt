package com.arny.aipromptmaster.presentation.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.arny.aipromptmaster.domain.R
import com.arny.aipromptmaster.domain.interactors.IPromptsInteractor
import com.arny.aipromptmaster.domain.interactors.ISettingsInteractor
import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.models.PromptsSortData
import com.arny.aipromptmaster.domain.models.strings.StringHolder
import com.arny.aipromptmaster.domain.repositories.SyncResult
import dagger.assisted.AssistedInject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PromptsViewModel @AssistedInject constructor(
    private val interactor: IPromptsInteractor,
    private val settingsInteractor: ISettingsInteractor
) : ViewModel() {

    // Используем SharedFlow для одноразовых событий (показать Toast/Snackbar)
    private val _feedbackResult = MutableSharedFlow<Result<Unit>>()
    val feedbackResult = _feedbackResult.asSharedFlow()

    private val _sortDataState = MutableStateFlow<PromptsSortData?>(null)
    val sortDataState = _sortDataState.asStateFlow()

    private val _uiState = MutableStateFlow<PromptsUiState>(PromptsUiState.Initial)
    val uiState = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<PromptsUiEvent>()
    val event = _event.asSharedFlow()

    private val _searchState = MutableStateFlow(SearchState())
    val searchState = _searchState.asStateFlow()

    private val _customFiltersState =
        MutableStateFlow(CurrentFilters(category = null, tags = emptyList()))

    init {
        loadSortData()
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val promptsFlow: Flow<PagingData<Prompt>> = _searchState
        .debounce(350)
        .flatMapLatest { state ->
            createPager(state).flow
        }
        .cachedIn(viewModelScope)

    private fun loadSortData() {
        viewModelScope.launch {
            try {
                _sortDataState.value = interactor.getPromptsSortData()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendFeedback(content: String) {
        viewModelScope.launch {
            val result = settingsInteractor.sendFeedback(content)
            _feedbackResult.emit(result)
        }
    }

    fun onSortButtonClicked() {
        viewModelScope.launch {
            val availableFilters = _sortDataState.value
            if (availableFilters != null) {
                _event.emit(
                    PromptsUiEvent.OpenSortScreenEvent(
                        sortData = SortData(
                            categories = availableFilters.categories,
                            tags = availableFilters.tags,
                        ),
                        currentFilters = _customFiltersState.value
                    )
                )
            }
        }
    }

    fun handleLoadStates(loadStates: CombinedLoadStates, itemCount: Int) {
        val isLoading = loadStates.refresh is LoadState.Loading
        val isError = loadStates.refresh is LoadState.Error
        val isEmpty = loadStates.refresh is LoadState.NotLoading && itemCount == 0

        _uiState.value = when {
            isError -> PromptsUiState.Error((loadStates.refresh as LoadState.Error).error)
            isLoading -> PromptsUiState.Loading
            isEmpty -> PromptsUiState.Empty
            else -> PromptsUiState.Content
        }
    }

    private fun createPager(searchState: SearchState): Pager<Int, Prompt> = Pager(
        config = PagingConfig(
            pageSize = IPromptsInteractor.DEFAULT_PAGE_SIZE,
            enablePlaceholders = false,
            initialLoadSize = IPromptsInteractor.DEFAULT_PAGE_SIZE
        ),
        pagingSourceFactory = {
            PromptPagingSource(
                interactor = interactor,
                query = searchState.query,
                category = searchState.category,
                status = searchState.status,
                tags = searchState.tags
            )
        }
    )

    fun search(query: String) {
        _searchState.update { it.copy(query = query) }
    }

    fun removeFilter(filter: String) {
        // Обновляем оба состояния
        val newCustomFilters = _customFiltersState.value.let {
            if (it.category == filter) {
                it.copy(category = null)
            } else {
                it.copy(tags = it.tags.filterNot { t -> t == filter })
            }
        }
        _customFiltersState.value = newCustomFilters

        _searchState.update { currentState ->
            if (currentState.category == filter) {
                currentState.copy(category = null)
            } else {
                currentState.copy(tags = currentState.tags.filterNot { it == filter })
            }
        }
    }

    fun resetSearchAndFilters() {
        _customFiltersState.value = CurrentFilters(null, emptyList())
        _searchState.value = SearchState()
    }

    fun synchronize() {
        viewModelScope.launch {
            _event.emit(PromptsUiEvent.SyncInProgress) // Используем event-поток
            try {
                val result = interactor.synchronize()
                when (result) {
                    is SyncResult.Success -> {
                        _event.emit(PromptsUiEvent.SyncSuccess(result.updatedPrompts.size))
                        _event.emit(PromptsUiEvent.PromptUpdated)
                    }

                    is SyncResult.Error -> {
                        _event.emit(PromptsUiEvent.ShowError(result.stringHolder))
                    }

                    SyncResult.TooSoon ->{
                        _event.emit(
                            PromptsUiEvent.ShowInfoMessage(
                                StringHolder.Resource(R.string.sync_is_up_to_date)
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                handleError(e) // Ваш существующий обработчик ошибок
            } finally {
                // Добавляем finally, чтобы всегда скрывать прогресс
                _event.emit(PromptsUiEvent.SyncFinished)
            }
        }
    }

    fun deletePrompt(promptId: String) {
        viewModelScope.launch {
            try {
                interactor.deletePrompt(promptId)
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    fun toggleFavorite(promptId: String) {
        viewModelScope.launch {
            try {
                interactor.toggleFavorite(promptId)
                _event.emit(PromptsUiEvent.PromptUpdated)
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    private suspend fun handleError(error: Throwable) {
        error.printStackTrace()
        _event.emit(
            PromptsUiEvent.ShowError(
                error.message
                    ?.takeIf { it.isNotBlank() }
                    ?.let(StringHolder::Text)
                    ?: StringHolder.Resource(R.string.system_error)
            )
        )
    }

    fun omPromptUpdated(promptUpdated: Boolean) {
        viewModelScope.launch {
            try {
                if (promptUpdated) {
                    _event.emit(PromptsUiEvent.PromptUpdated)
                }
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    fun setStatusFilter(newStatus: String?) {
        // При любом выборе статического фильтра ("Все" или "Избранное")
        // мы должны полностью сбросить состояние кастомных фильтров.
        _customFiltersState.value = CurrentFilters(category = null, tags = emptyList())

        // А затем обновить поисковое состояние, которое также сбросит кастомные фильтры
        // и установит нужный статус.
        _searchState.update {
            it.copy(
                status = newStatus,
                category = null,
                tags = emptyList()
            )
        }
    }

    fun applyFiltersFromDialog(category: String?, tags: List<String>) {
        _customFiltersState.value = CurrentFilters(category, tags)

        _searchState.update { currentState ->
            val newState = currentState.copy(
                category = category,
                tags = tags,
                status = null
            )
            newState // Возвращаем новый стейт
        }
    }
}