package com.arny.aipromptmaster.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.aipromptmaster.domain.models.ModelsFilter
import com.arny.aipromptmaster.domain.models.SortType
import com.arny.aipromptmaster.domain.models.strings.StringHolder
import com.arny.aipromptmaster.domain.repositories.ISettingsRepository
import com.arny.aipromptmaster.domain.repositories.ModelRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

sealed interface ModelsEffect {
    data class FavoriteToggled(val id: String, val added: Boolean) : ModelsEffect
    data class ShowMessage(val message: StringHolder, val isError: Boolean) : ModelsEffect
    data class AvailabilityCheckComplete(
        val availableCount: Int,
        val totalCount: Int,
        val bestModelId: String?,
        val bestRating: Float
    ) : ModelsEffect
}

@OptIn(FlowPreview::class)
class ModelsViewModel(
    private val repository: ModelRepository,
    private val settingsRepository: ISettingsRepository
) : ViewModel() {

    // 1. Source of Truth для фильтров
    private val _filter = MutableStateFlow(ModelsFilter())

    // 2. Состояние проверки доступности
    private val _isChecking = MutableStateFlow(false)
    private val _checkingProgress = MutableStateFlow(0f)
    private val _checkedCount = MutableStateFlow(0)
    private val _totalToCheck = MutableStateFlow(0)
    private var availabilityCheckJob: Job? = null
    private var isCheckCancelled = false

    // 3. SharedFlow для разовых эффектов
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

    // 4. Реактивный UI State
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
                    val modelsWithRating = models
                    val bestModel = modelsWithRating
                        .filter { it.rating != null && it.rating > 0 }
                        .maxByOrNull { it.rating ?: 0f }

                    ModelsUiState(
                        models = modelsWithRating,
                        isLoading = false,
                        filter = currentFilter,
                        error = null,
                        isCheckingAvailability = _isChecking.value,
                        checkingProgress = _checkingProgress.value,
                        checkedCount = _checkedCount.value,
                        totalToCheck = _totalToCheck.value,
                        availableCount = modelsWithRating.count { it.isAvailable == true },
                        totalCheckedCount = modelsWithRating.count { it.isAvailable != null },
                        bestRatedModel = bestModel
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
        setupFavoriteToggle()
    }

    private fun setupFavoriteToggle() {
        favoriteToggleFlow
            .debounce(300)
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
            is ModelsEvent.ToggleAvailableOnly -> _filter.update { it.copy(isAvailableOnly = !it.isAvailableOnly) }
            is ModelsEvent.ChangeSort -> _filter.update { it.copy(sortType = event.type) }
            is ModelsEvent.ToggleModelFavorite -> {
                viewModelScope.launch {
                    favoriteToggleFlow.emit(event.id)
                }
            }

            is ModelsEvent.SelectModel -> selectModel(event.id)
            ModelsEvent.Refresh -> refreshModels()
            ModelsEvent.CheckFreeModelsAvailability -> checkFreeModelsAvailability()
            is ModelsEvent.CheckModelAvailability -> checkModelAvailability(event.id)
            ModelsEvent.CancelAvailabilityCheck -> cancelAvailabilityCheck()
        }
    }

    private fun selectModel(modelId: String) {
        viewModelScope.launch {
            try {
                repository.selectModel(modelId)
                _effects.emit(
                    ModelsEffect.ShowMessage(
                        StringHolder.Text("Модель выбрана"),
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

    /**
     * Проверяет доступность всех free моделей.
     */
    private fun checkFreeModelsAvailability() {
        val apiKey = settingsRepository.getApiKey()
        if (apiKey.isNullOrBlank()) {
            viewModelScope.launch {
                _effects.emit(
                    ModelsEffect.ShowMessage(
                        StringHolder.Text("API ключ не настроен. Укажите ключ в настройках."),
                        isError = true
                    )
                )
            }
            return
        }

        availabilityCheckJob?.cancel()
        isCheckCancelled = false
        _checkedCount.value = 0
        _checkingProgress.value = 0f
        _isChecking.value = true

        availabilityCheckJob = viewModelScope.launch {
            try {
                val availabilityFlow = repository.checkFreeModelsAvailability { _, checked, total ->
                    if (isCheckCancelled) return@checkFreeModelsAvailability
                    _checkedCount.value = checked
                    _totalToCheck.value = total
                    _checkingProgress.value = if (total > 0) checked.toFloat() / total else 0f
                }

                var availableCount = 0
                var totalCount = 0
                var bestModelId: String? = null
                var bestRating = 0f

                availabilityFlow.collect { (modelId, isAvailable) ->
                    if (isCheckCancelled) return@collect
                    totalCount++
                    if (isAvailable) availableCount++
                    _checkedCount.value = totalCount
                    _checkingProgress.value = if (_totalToCheck.value > 0) {
                        totalCount.toFloat() / _totalToCheck.value
                    } else {
                        0f
                    }
                }

                _isChecking.value = false
                _totalToCheck.value = 0
                _checkedCount.value = 0
                _checkingProgress.value = 0f

                if (!isCheckCancelled) {
                    // После проверки обновляем UI и получаем лучшую модель
                    _filter.value = _filter.value.copy()

                    // Даем время UI обновиться
                    delay(200)

                    // Находим лучшую модель по рейтингу
                    val currentState = uiState.value
                    val best = currentState.models
                        .filter { it.rating != null && it.rating > 0 }
                        .maxByOrNull { it.rating ?: 0f }

                    bestModelId = best?.id
                    bestRating = best?.rating ?: 0f

                    _effects.emit(
                        ModelsEffect.AvailabilityCheckComplete(
                            availableCount = availableCount,
                            totalCount = totalCount,
                            bestModelId = bestModelId,
                            bestRating = bestRating
                        )
                    )
                }
            } catch (e: Exception) {
                _isChecking.value = false
                _totalToCheck.value = 0
                _checkedCount.value = 0
                _checkingProgress.value = 0f
                _effects.emit(
                    ModelsEffect.ShowMessage(
                        StringHolder.Text("Ошибка проверки: ${e.localizedMessage}"),
                        isError = true
                    )
                )
            }
        }
    }

    /**
     * Отмена проверки доступности моделей.
     */
    private fun cancelAvailabilityCheck() {
        isCheckCancelled = true
        availabilityCheckJob?.cancel()
        _isChecking.value = false
        _totalToCheck.value = 0
        _checkedCount.value = 0
        _checkingProgress.value = 0f
        viewModelScope.launch {
            _effects.emit(
                ModelsEffect.ShowMessage(
                    StringHolder.Text("Проверка отменена"),
                    isError = false
                )
            )
        }
    }

    /**
     * Проверяет доступность конкретной модели.
     */
    private fun checkModelAvailability(modelId: String) {
        // Проверяем наличие API ключа перед началом проверки
        val apiKey = settingsRepository.getApiKey()
        if (apiKey.isNullOrBlank()) {
            viewModelScope.launch {
                _effects.emit(
                    ModelsEffect.ShowMessage(
                        StringHolder.Text("API ключ не настроен. Укажите ключ в настройках."),
                        isError = true
                    )
                )
            }
            return
        }
        
        viewModelScope.launch {
            try {
                val isAvailable = repository.checkModelAvailability(modelId)
                val message = if (isAvailable) "Модель доступна" else "Модель недоступна"
                _effects.emit(
                    ModelsEffect.ShowMessage(
                        StringHolder.Text(message),
                        isError = !isAvailable
                    )
                )
                // Принудительно обновляем UI перезапуском Flow
                _filter.value = _filter.value.copy()
            } catch (e: Exception) {
                _effects.emit(
                    ModelsEffect.ShowMessage(
                        StringHolder.Text("Ошибка проверки: ${e.localizedMessage}"),
                        isError = true
                    )
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        availabilityCheckJob?.cancel()
    }
}
