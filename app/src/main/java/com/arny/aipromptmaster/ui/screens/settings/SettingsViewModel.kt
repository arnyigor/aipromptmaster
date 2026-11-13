package com.arny.aipromptmaster.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.aipromptmaster.domain.interactors.ISettingsInteractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Состояние экрана.
 */
data class SettingsUiState(
    // API‑ключ
    val apiKey: String = "",
    val isSaving: Boolean = false,

    // Фидбек
    val feedbackText: String = "",
    val isSendingFeedback: Boolean = false,

    // Общий пользовательский вывод (успех/ошибка)
    val message: String? = null,

    // Синхронизация каталога (отдельный раздел настроек)
    val syncRunning: Boolean = false,
    val syncSummary: String? = null,
    val syncStage: String? = null,
    val syncProgress: Float = 0f,
)

class SettingsViewModel constructor(
    private val interactor: ISettingsInteractor,
    private val synchronizer: com.arny.aipromptmaster.domain.repositories.IPromptSynchronizer? = null,
) : ViewModel() {

    // ---------- StateFlow ----------
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> get() = _state.asStateFlow()

    init {
        loadSavedApiKey()
    }

    /** UI‑ввод API‑ключа */
    fun onApiKeyChanged(newKey: String) =
        _state.update { it.copy(apiKey = newKey, message = null) }

    /** Сохранить ключ в репозитории */
    fun saveApiKey() = viewModelScope.launch {
        val trimmed = _state.value.apiKey.trim()
        if (trimmed.isEmpty()) return@launch

        _state.update { it.copy(isSaving = true, message = null) }
        try {
            interactor.saveApiKey(trimmed)
            _state.update { it.copy(isSaving = false, message = "Ключ сохранён") }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    isSaving = false,
                    message = e.localizedMessage ?: "Не удалось сохранить ключ"
                )
            }
        }
    }

    /** UI‑ввод текста фидбека */
    fun onFeedbackChanged(text: String) =
        _state.update { it.copy(feedbackText = text, message = null) }

    /** Отправить фидбек через репозиторий */
    fun sendFeedback() = viewModelScope.launch {
        val content = _state.value.feedbackText.trim()
        if (content.isEmpty()) return@launch

        _state.update { it.copy(isSendingFeedback = true, message = null) }
        try {
            interactor.sendFeedback(content)
            _state.update {
                it.copy(
                    isSendingFeedback = false,
                    feedbackText = "", // очищаем поле
                    message = "Фидбек отправлен"
                )
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    isSendingFeedback = false,
                    message = e.localizedMessage ?: "Ошибка отправки фидбека"
                )
            }
        }
    }

    /** Очистить сообщение после показа Snackbar */
    fun clearMessage() =
        _state.update { it.copy(message = null) }

    private fun loadSavedApiKey() = viewModelScope.launch {
        interactor.getApiKey()?.let { key ->
            _state.update { it.copy(apiKey = key) }
        }
    }

    /* ----------------------- Синхронизация каталога ------------------------ */

    /** Запускает синхронизацию каталога и отражает прогресс/результат в состоянии. */
    fun syncCatalog() {
        val sync = synchronizer ?: return
        if (_state.value.syncRunning) return
        viewModelScope.launch {
            _state.update { it.copy(syncRunning = true, syncSummary = null, syncStage = null) }
            val stageWatcher = launch {
                sync.stage.collect { stage ->
                    val label = when (stage) {
                        com.arny.aipromptmaster.domain.models.SyncStage.IDLE -> null
                        com.arny.aipromptmaster.domain.models.SyncStage.CHECKING -> "Проверка…"
                        com.arny.aipromptmaster.domain.models.SyncStage.DOWNLOADING -> "Загрузка каталога…"
                        com.arny.aipromptmaster.domain.models.SyncStage.VALIDATING -> "Проверка контрольных сумм…"
                        com.arny.aipromptmaster.domain.models.SyncStage.IMPORTING -> "Импорт…"
                        com.arny.aipromptmaster.domain.models.SyncStage.DONE -> null
                    }
                    _state.update { it.copy(syncStage = label) }
                }
            }
            val progressWatcher = launch {
                sync.progress.collect { p -> _state.update { it.copy(syncProgress = p) } }
            }
            val result = try {
                sync.synchronize()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                "Ошибка синхронизации: ${error.localizedMessage}"
            }
            stageWatcher.cancel()
            progressWatcher.cancel()
            val summary = when (result) {
                is String -> result
                else -> when (val status = sync.status.value) {
                    is com.arny.aipromptmaster.domain.models.SyncStatus.Success ->
                        "Каталог обновлён: ${status.updatedCount} промптов"
                    is com.arny.aipromptmaster.domain.models.SyncStatus.Cooldown ->
                        "Недавно синхронизировано, попробуйте позже"
                    is com.arny.aipromptmaster.domain.models.SyncStatus.Error ->
                        "Ошибка: ${status.error.localizedMessage}"
                    else -> "Синхронизация завершена"
                }
            }
            _state.update { it.copy(syncRunning = false, syncSummary = summary, syncStage = null, syncProgress = 0f) }
        }
    }
}
