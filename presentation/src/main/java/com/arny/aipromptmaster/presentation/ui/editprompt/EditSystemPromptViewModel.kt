package com.arny.aipromptmaster.presentation.ui.editprompt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.aipromptmaster.domain.interactors.ILLMInteractor
import com.arny.aipromptmaster.presentation.R
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EditSystemPromptViewModel @AssistedInject constructor(
    private val interactor: ILLMInteractor
) : ViewModel() {

    private val _uiState = MutableStateFlow<EditSystemPromptUiState>(EditSystemPromptUiState.Content)
    val uiState: StateFlow<EditSystemPromptUiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<EditSystemPromptUiEvent>()
    val event: SharedFlow<EditSystemPromptUiEvent> = _event.asSharedFlow()

    private val _currentPrompt = MutableStateFlow("")
    val currentPrompt: StateFlow<String> = _currentPrompt.asStateFlow()

    private var currentConversationId: String? = null
    private var isPromptLoaded = false  // ✅ НОВЫЙ ФЛАГ

    fun setConversationId(conversationId: String?) {
        currentConversationId = conversationId
        conversationId?.let {
            loadSystemPrompt(it)
        }
    }

    private fun loadSystemPrompt(conversationId: String) {
        viewModelScope.launch {
            try {
                _uiState.value = EditSystemPromptUiState.Loading

                // ✅ ИСПРАВЛЕНО: загружаем промпт напрямую
                val systemPrompt = interactor.getSystemPrompt(conversationId)

                if (!systemPrompt.isNullOrBlank()) {
                    _currentPrompt.value = systemPrompt
                    isPromptLoaded = true
                } else {
                    _currentPrompt.value = ""
                    isPromptLoaded = true
                }

                _uiState.value = EditSystemPromptUiState.Content
            } catch (e: Exception) {
                // В случае ошибки устанавливаем пустой промпт
                _currentPrompt.value = ""
                isPromptLoaded = true
                _uiState.value = EditSystemPromptUiState.Content
            }
        }
    }

    fun saveSystemPrompt(prompt: String) {
        viewModelScope.launch {
            try {
                _uiState.value = EditSystemPromptUiState.Loading

                // Валидация
                val validationResult = validatePrompt(prompt)
                if (validationResult != null) {
                    _event.emit(validationResult)
                    _uiState.value = EditSystemPromptUiState.Content
                    return@launch
                }

                // Получаем текущий conversationId
                val existingConversationId = currentConversationId

                if (existingConversationId != null) {
                    // ✅ Если чат существует - просто обновляем промпт
                    interactor.setSystemPrompt(existingConversationId, prompt)
                } else {
                    // ✅ Если чата нет - создаем новый с промптом
                    val newConversationId = interactor.setSystemPromptWithChatCreation(
                        conversationId = null,
                        prompt = prompt,
                        chatTitle = "Новый чат"
                    )
                    currentConversationId = newConversationId
                }

                _event.emit(EditSystemPromptUiEvent.PromptSaved)

            } catch (e: Exception) {
                _uiState.value = EditSystemPromptUiState.Error(
                    e.message ?: "Ошибка при сохранении системного промпта"
                )
            }
        }
    }

    private fun validatePrompt(prompt: String): EditSystemPromptUiEvent.ValidationError? = when {
        prompt.length > MAX_CHARACTERS -> EditSystemPromptUiEvent.ValidationError(
            "Превышено максимальное количество символов ($MAX_CHARACTERS)"
        )
        else -> null
    }


    companion object {
        private const val MAX_CHARACTERS = 2000
    }
}