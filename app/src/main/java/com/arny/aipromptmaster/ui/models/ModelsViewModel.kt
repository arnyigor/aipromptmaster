package com.arny.aipromptmaster.ui.models

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.aipromptmaster.domain.models.ModelsFilter
import com.arny.aipromptmaster.domain.models.strings.StringHolder
import com.arny.aipromptmaster.domain.repositories.ModelRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

sealed interface ModelsEffect {
    data class FavoriteToggled(val id: String, val added: Boolean) : ModelsEffect
    data class ShowMessage(val message: StringHolder, val isError: Boolean) : ModelsEffect
}

@OptIn(FlowPreview::class)
class ModelsViewModel(
    private val repository: ModelRepository
) : ViewModel() {

    // 1. Source of Truth для фильтров
    private val _filter = MutableStateFlow(ModelsFilter())

    // 2. SharedFlow для разовых эффектов
    private val _effects = MutableSharedFlow<ModelsEffect>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val effects: Flow<ModelsEffect> = _effects.asSharedFlow()

    private val favoriteToggleFlow = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // 3. Реактивный UI State
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val uiState: StateFlow<ModelsUiState> = _filter
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { currentFilter ->
            repository.getModels(currentFilter)
                .onEach { models ->
                    Timber.tag("ModelsVM").d("Filter: $currentFilter, models count: ${models.size}")
                }
                .map { models ->
                    ModelsUiState(
                        models = models,
                        isLoading = false,
                        filter = currentFilter,
                        error = null
                    )
                }
                .catch { exception ->
                    emit(
                        ModelsUiState(
                            models = emptyList(),
                            isLoading = false,
                            filter = currentFilter,
                            error = exception.message
                        )
                    )
                }
                .onStart { emit(ModelsUiState(isLoading = true, filter = currentFilter)) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = 5000,
                replayExpirationMillis = 0
            ),
            initialValue = ModelsUiState(isLoading = true)
        )

    init {
        refreshModels()
        favoriteToggleFlow
            .debounce(300) // Защита от двойных кликов
            .onEach { modelId ->
                try {
                    val newState = repository.toggleFavorite(modelId)
                    _effects.emit(ModelsEffect.FavoriteToggled(modelId, newState))
                } catch (e: Throwable) {
                    _effects.emit(
                        ModelsEffect.ShowMessage(
                            StringHolder.Text(e.localizedMessage ?: "Unknown error"),
                            isError = true
                        )
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: ModelsEvent) {
        when (event) {
            is ModelsEvent.Search -> _filter.update { it.copy(query = event.query) }
            is ModelsEvent.ToggleFree -> _filter.update { it.copy(isFreeOnly = !it.isFreeOnly) }
            is ModelsEvent.ToggleFavoritesOnly -> _filter.update { it.copy(isFavoritesOnly = !it.isFavoritesOnly) }
            is ModelsEvent.ChangeSort -> _filter.update { it.copy(sortType = event.type) }
            is ModelsEvent.ToggleModelFavorite -> {
                viewModelScope.launch {
                    favoriteToggleFlow.emit(event.id)
                }
            }

            is ModelsEvent.SelectModel -> {
                viewModelScope.launch {
                    try {
                        repository.selectModel(event.id)
                        _effects.emit(
                            ModelsEffect.ShowMessage(
                                StringHolder.Text("Модель ${event.id} выбрана"),
                                isError = false
                            )
                        )
                    } catch (e: Exception) {
                        _effects.emit(
                            ModelsEffect.ShowMessage(
                                StringHolder.Text(e.localizedMessage ?: "Unknown error"),
                                isError = true
                            )
                        )
                    }
                }
            }

            ModelsEvent.Refresh -> refreshModels()
        }
    }

    private fun refreshModels() {
        viewModelScope.launch {
            try {
                val result = repository.refreshModels()
                result.onFailure { e ->
                    _effects.emit(
                        ModelsEffect.ShowMessage(
                            StringHolder.Text("Refresh failed: ${e.message}"),
                            isError = true
                        )
                    )
                }
            } catch (e: Exception) {
                _effects.emit(
                    ModelsEffect.ShowMessage(
                        StringHolder.Text(e.localizedMessage ?: "Unknown error"),
                        isError = true
                    )
                )
            }
        }
    }
}
