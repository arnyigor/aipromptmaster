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
import com.arny.aipromptmaster.domain.models.SyncConflict
import com.arny.aipromptmaster.domain.repositories.SyncResult
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.utils.strings.IWrappedString
import com.arny.aipromptmaster.presentation.utils.strings.ResourceString
import com.arny.aipromptmaster.presentation.utils.strings.SimpleString
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

class HomeViewModel @AssistedInject constructor(
    private val interactor: IPromptsInteractor,
) : ViewModel() {
    private val _error = MutableSharedFlow<IWrappedString>()
    val error = _error.asSharedFlow()

    private val _uiState = MutableStateFlow<UiState>(UiState.Initial)
    val uiState = _uiState.asStateFlow()

    private val _searchState = MutableStateFlow(SearchState())
    private val searchTrigger = MutableSharedFlow<Unit>()

    private var queryString: String = ""
    private val trigger = MutableSharedFlow<Unit>()
    private val actionStateFlow = MutableSharedFlow<UiAction>()

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

    fun handleLoadStates(loadStates: CombinedLoadStates, itemCount: Int) {
        val isLoading = loadStates.refresh is LoadState.Loading
        val isError = loadStates.refresh is LoadState.Error
        val isEmpty = loadStates.refresh is LoadState.NotLoading && itemCount == 0

        _uiState.value = when {
            isError -> UiState.Error((loadStates.refresh as LoadState.Error).error)
            isLoading -> UiState.Loading
            isEmpty -> UiState.Empty
            else -> UiState.Content
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

    fun refresh() {
        viewModelScope.launch {
            searchTrigger.emit(Unit)
        }
    }

    fun synchronize() {
        viewModelScope.launch {
            _uiState.value = UiState.SyncInProgress
            try {
                when (val result = interactor.synchronize()) {
                    is SyncResult.Success -> {
                        _uiState.value = UiState.SyncSuccess(result.updatedPrompts.size)
                        loadPrompts(resetAll = true)
                    }

                    is SyncResult.Error -> {
                        Log.e(HomeViewModel::class.java.simpleName, "synchronize: Error:${result.message}")
                        _uiState.value = UiState.SyncError
                        _error.tryEmit(ResourceString(R.string.sync_error, result.message))
                    }

                    is SyncResult.Conflicts -> {
                        _uiState.value = UiState.SyncConflicts(result.conflicts)
                    }
                }
            } catch (e: Exception) {
                handleError(e)
                _uiState.value = UiState.SyncError
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

    sealed class UiState {
        object Initial : UiState()
        object Loading : UiState()
        object Content : UiState()
        object Empty : UiState()
        data class Error(val error: Throwable) : UiState()

        // Sync states
        object SyncInProgress : UiState()
        object SyncError : UiState()
        data class SyncSuccess(val updatedCount: Int) : UiState()
        data class SyncConflicts(val conflicts: List<SyncConflict>) : UiState()
    }

    sealed class UiAction {
        data class Search(
            val query: String = "",
            val category: String? = null,
            val status: String? = null,
            val tags: List<String> = emptyList()
        ) : UiAction()

        object Refresh : UiAction()
    }

    data class SearchState(
        val query: String = "",
        val category: String? = null,
        val status: String? = null,
        val tags: List<String> = emptyList()
    )
}