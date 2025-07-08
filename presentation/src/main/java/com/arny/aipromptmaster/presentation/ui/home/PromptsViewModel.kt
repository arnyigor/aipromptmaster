package com.arny.aipromptmaster.presentation.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.arny.aipromptmaster.domain.interactors.IPromptsInteractor
import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.models.PromptsSortData
import com.arny.aipromptmaster.domain.repositories.SyncResult
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.utils.strings.IWrappedString
import com.arny.aipromptmaster.presentation.utils.strings.ResourceString
import com.arny.aipromptmaster.presentation.utils.strings.SimpleString
import dagger.assisted.AssistedInject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

class PromptsViewModel @AssistedInject constructor(
    private val interactor: IPromptsInteractor,
) : ViewModel() {
    private val _error = MutableSharedFlow<IWrappedString>()
    val error = _error.asSharedFlow()

    private val _sortDataState = MutableStateFlow<PromptsSortData?>(null)
    private val _currentSortDataState = MutableStateFlow<PromptsSortData?>(null)

    private val _eventChannel = MutableSharedFlow<PromptsUiEvents>()
    val eventChannel: SharedFlow<PromptsUiEvents> = _eventChannel.asSharedFlow()

    private val _uiState = MutableStateFlow<PromptsUiState>(PromptsUiState.Initial)
    val uiState = _uiState.asStateFlow()

    private val _searchState = MutableStateFlow(SearchState())
    private val searchTrigger = MutableSharedFlow<Unit>()
    private var queryString: String = ""
    private val trigger = MutableSharedFlow<Unit>()
    private val actionStateFlow = MutableSharedFlow<UiAction>()

    init {
        loadSortData()
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val promptsFlow: Flow<PagingData<Prompt>> = listOf(
        trigger.map { UiAction.Refresh },
        actionStateFlow
            .distinctUntilChanged()
            .debounce(350)
    )
        .merge()
        .onStart { emit(UiAction.Refresh) }
        .flatMapLatest { action ->
            when (action) {
                is UiAction.Search -> {
                    _searchState.value = _searchState.value.copy(
                        query = action.query,
                        category = action.category,
                        status = action.status,
                        tags = action.tags
                    )
                }

                is UiAction.Refresh -> {}
            }
            createPager(_searchState.value).flow
        }
        .cachedIn(viewModelScope)

    private fun loadSortData() {
        viewModelScope.launch {
            try {
                // Загружаем данные и помещаем их в StateFlow
                _sortDataState.value = interactor.getPromptsSortData()
            } catch (e: Exception) {
                // Обработка ошибки, если нужна
                // Например, показать Toast или записать в лог
            }
        }
    }

    fun onSortButtonClicked() {
        val availableFilters = _sortDataState.value
        if (availableFilters != null) {
            val event = PromptsUiEvents.OpenSortScreenEvent(
                sortData = SortData(availableFilters.categories, availableFilters.tags),
                currentFilters = CurrentFilters(
                    _currentSortDataState.value?.categories.orEmpty(),
                    _currentSortDataState.value?.tags.orEmpty()
                ),
            )
            viewModelScope.launch {
                _eventChannel.emit(event)
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

    private fun createPager(searchState: SearchState): Pager<Int, Prompt> {
        return Pager(
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
    }

    fun search(
        query: String = "",
        category: String? = null,
        status: String? = null,
        tags: List<String> = emptyList()
    ) {
        viewModelScope.launch {
            actionStateFlow.emit(
                UiAction.Search(
                    query = query,
                    category = category,
                    status = when (status) {
                        "favorite" -> "favorite"
                        else -> null
                    },
                    tags = tags
                )
            )
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            searchTrigger.emit(Unit)
        }
    }

    fun synchronize() {
        viewModelScope.launch {
            _uiState.value = PromptsUiState.SyncInProgress
            try {
                when (val result = interactor.synchronize()) {
                    is SyncResult.Success -> {
                        _uiState.value = PromptsUiState.SyncSuccess(result.updatedPrompts.size)
                        loadPrompts(resetAll = true)
                    }

                    is SyncResult.Error -> {
                        Log.e(
                            PromptsViewModel::class.java.simpleName,
                            "synchronize: Error:${result.message}"
                        )
                        _uiState.value = PromptsUiState.SyncError
                        _error.tryEmit(ResourceString(R.string.sync_error, result.message))
                    }

                    is SyncResult.Conflicts -> {
                        _uiState.value = PromptsUiState.SyncConflicts(result.conflicts)
                    }
                }
            } catch (e: Exception) {
                handleError(e)
                _uiState.value = PromptsUiState.SyncError
            }
        }
    }

    fun deletePrompt(promptId: String) {
        viewModelScope.launch {
            try {
                interactor.deletePrompt(promptId)
                refresh()
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    fun toggleFavorite(promptId: String) {
        viewModelScope.launch {
            try {
                val prompt = interactor.getPromptById(promptId)
                if (prompt != null) {
                    val updatedPrompt = prompt.copy(isFavorite = !prompt.isFavorite)
                    interactor.updatePrompt(updatedPrompt)
                    refresh()
                }
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    private suspend fun handleError(error: Throwable) {
        _error.emit(SimpleString(error.message ?: "Unknown error"))
    }

    fun loadPrompts(
        query: String = "",
        resetAll: Boolean = false
    ) {
        viewModelScope.launch {
            queryString = query
            when {
                resetAll -> {
                    _searchState.value = SearchState()
                    actionStateFlow.emit(UiAction.Search())
                }

                else -> {
                    actionStateFlow.emit(
                        UiAction.Search(
                            query = query,
                            category = _searchState.value.category,
                            status = _searchState.value.status,
                            tags = _searchState.value.tags
                        )
                    )
                }
            }
        }
    }

    fun applyFilters(categories: List<String>, tags: List<String>) {
        _currentSortDataState.value = PromptsSortData(categories, tags)
    }
}