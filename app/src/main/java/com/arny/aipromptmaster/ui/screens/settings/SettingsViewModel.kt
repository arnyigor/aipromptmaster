package com.arny.aipromptmaster.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.aipromptmaster.domain.interactors.ISettingsInteractor
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
    val message: String? = null
)

class SettingsViewModel constructor(
    private val interactor: ISettingsInteractor,
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
}
