package com.arny.aipromptmaster.presentation.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.arny.aipromptmaster.domain.interactors.IPromptsInteractor
import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.models.SyncStatus
import com.arny.aipromptmaster.domain.repositories.IPromptSynchronizer
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.utils.strings.IWrappedString
import com.arny.aipromptmaster.presentation.utils.strings.ResourceString
import com.arny.aipromptmaster.presentation.utils.strings.SimpleString
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel @AssistedInject constructor(
    private val interactor: IPromptsInteractor,
) : ViewModel() {
    private val _error = MutableSharedFlow<IWrappedString?>()
    val error = _error.asSharedFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.None)
    val syncStatus = _syncStatus.asStateFlow()

    private val _searchState = MutableStateFlow(SearchState())
    private val searchTrigger = MutableSharedFlow<Unit>()
    private val searchAction = MutableStateFlow<UiAction.Search?>(null)

    val promptsFlow: Flow<PagingData<Prompt>> = listOf(
        searchTrigger.map { UiAction.Refresh },
        searchAction.filterNotNull()
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
                is UiAction.Refresh -> { /* refresh current search */ }
            }
            interactor.getPromptsPaginated(
                query = _searchState.value.query,
                category = _searchState.value.category,
                status = _searchState.value.status,
                tags = _searchState.value.tags
            )
        }
        .flowOn(Dispatchers.IO)
        .onEach { _isLoading.value = false }
        .cachedIn(viewModelScope)

    fun search(
        query: String = "",
        category: String? = null,
        status: String? = null,
        tags: List<String> = emptyList()
    ) {
        viewModelScope.launch {
            searchAction.emit(UiAction.Search(query, category, status, tags))
        }
    }

    fun refresh() {
        viewModelScope.launch {
            searchTrigger.emit(Unit)
        }
    }

    fun synchronize() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _syncStatus.value = SyncStatus.InProgress
                when (val result = interactor.synchronize()) {
                    is IPromptSynchronizer.SyncResult.Success -> {
                        _syncStatus.value = SyncStatus.Success(result.updatedPrompts.size)
                        refresh()
                    }
                    is IPromptSynchronizer.SyncResult.Error -> {
                        _syncStatus.value = SyncStatus.Error
                        _error.emit(SimpleString(result.message))
                    }
                    is IPromptSynchronizer.SyncResult.Conflicts -> {
                        _syncStatus.value = SyncStatus.Conflicts(result.conflicts)
                        _error.emit(ResourceString(R.string.sync_conflicts_found))
                    }
                }
            } catch (e: Exception) {
                handleError(e)
                _syncStatus.value = SyncStatus.Error
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deletePrompt(promptId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                interactor.deletePrompt(promptId)
                refresh()
            } catch (e: Exception) {
                handleError(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun handleError(error: Throwable) {
        _error.emit(SimpleString(error.message ?: "Unknown error"))
    }

}