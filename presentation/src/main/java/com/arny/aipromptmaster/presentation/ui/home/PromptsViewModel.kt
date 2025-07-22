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
import com.arny.aipromptmaster.domain.interactors.ISettingsInteractor
import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.models.PromptsSortData
import com.arny.aipromptmaster.domain.repositories.SyncResult
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.utils.strings.ResourceString
import com.arny.aipromptmaster.presentation.utils.strings.SimpleString
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
    private val _currentSortDataState = MutableStateFlow<CurrentFilters?>(null)

    private val _uiState = MutableStateFlow<PromptsUiState>(PromptsUiState.Initial)
    val uiState = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<PromptsUiEvent>()
    val event = _event.asSharedFlow()

    private val _searchState = MutableStateFlow(SearchState())
    val searchState = _searchState.asStateFlow()

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
                        currentFilters = CurrentFilters(
                            category = _currentSortDataState.value?.category.orEmpty(),
                            tags = _currentSortDataState.value?.tags.orEmpty(),
                        ),
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

    fun applyFilters(
        category: String? = searchState.value.category,
        status: String? = searchState.value.status,
        tags: List<String> = searchState.value.tags,
    ) {
        _searchState.update { currentState ->
            currentState.copy(
                category = category,
                status = when (status) {
                    "favorite" -> "favorite"
                    else -> null
                },
                tags = tags
            )
        }
    }

    fun resetSearchAndFilters() {
        _searchState.value = SearchState()
    }

    fun synchronize() {
        viewModelScope.launch {
            _event.emit(PromptsUiEvent.SyncInProgress) // Используем event-поток
            try {
                val result = interactor.synchronize()
                Log.i(this::class.java.simpleName, "synchronize: result: $result")
                when (result) {
                    is SyncResult.Success -> {
                        _event.emit(PromptsUiEvent.SyncSuccess(result.updatedPrompts.size))
                        // После успешной синхронизации PagingDataAdapter сам должен будет обновиться

                        _searchState.value = SearchState()
                    }

                    is SyncResult.Error -> {
                        _event.emit(PromptsUiEvent.SyncError)
                        _event.emit(
                            PromptsUiEvent.ShowError(
                                ResourceString(
                                    R.string.sync_error,
                                    result.message
                                )
                            )
                        )
                    }

                    is SyncResult.Conflicts -> {
                        _event.emit(PromptsUiEvent.SyncConflicts(result.conflicts))
                    }
                }
            } catch (e: Exception) {
                handleError(e)
                _event.emit(PromptsUiEvent.SyncError)
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
        _event.emit(PromptsUiEvent.ShowError(SimpleString(error.message ?: "Unknown error")))
    }

    fun updateFavorite(promptId: String?) {
        viewModelScope.launch {
            try {
                if (!promptId.isNullOrBlank()) {
                    _event.emit(PromptsUiEvent.PromptUpdated)
                }
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    fun applyFiltersFromDialog(category: String?, tags: List<String>) {
        _currentSortDataState.value = CurrentFilters(category, tags)
        applyFilters(
            category = category,
            status = searchState.value.status,
            tags = tags
        )
    }
}