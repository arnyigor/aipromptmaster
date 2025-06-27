package com.arny.aipromptmaster.presentation.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.aipromptmaster.domain.interactors.ILLMInteractor
import com.arny.aipromptmaster.domain.models.LLMModel
import com.arny.aipromptmaster.domain.results.DataResult
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel @AssistedInject constructor(
    private val interactor: ILLMInteractor,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ChatUIState>(ChatUIState.Initial)
    val uiState: StateFlow<ChatUIState> = _uiState.asStateFlow()

    private val messages = mutableListOf<AiChatMessage>()

    private val _modelsState = MutableStateFlow<DataResult<List<LLMModel>>>(DataResult.Loading)
    val modelsState: StateFlow<DataResult<List<LLMModel>>> = _modelsState.asStateFlow()

    init {
        loadModels()
    }

    private fun loadModels() {
        viewModelScope.launch {
            interactor.getModels()
                .collect { result ->
                    _modelsState.value = result
                }
        }
    }

    fun sendMessage(model: String, message: String) {
        val userMessage = AiChatMessage(
            id = UUID.randomUUID().toString(),
            content = message,
            type = AiChatMessageType.USER
        )
        messages.add(userMessage)
        updateState()

        val assistantMessage = AiChatMessage(
            id = UUID.randomUUID().toString(),
            content = "",
            type = AiChatMessageType.ASSISTANT
        )
        messages.add(assistantMessage)
        updateState()

        viewModelScope.launch {
            interactor.sendMessage(model, message)
                .onEach { result ->
                    when (result) {
                        is DataResult.Success -> {
                            val lastIndex = messages.lastIndex
                            messages[lastIndex] = messages[lastIndex].copy(
                                content = messages[lastIndex].content + result.data
                            )
                            updateState()
                        }

                        is DataResult.Error -> {
                            val lastIndex = messages.lastIndex
                            messages[lastIndex] = messages[lastIndex].copy(
                                type = AiChatMessageType.ERROR,
                                content = result.exception?.message ?: "Unknown error"
                            )
                            updateState()
                        }

                        is DataResult.Loading -> {
                            val lastIndex = messages.lastIndex
                            messages[lastIndex] = messages[lastIndex].copy(
                                type = AiChatMessageType.LOADING
                            )
                            updateState()
                        }
                    }
                }
                .catch { e ->
                    val lastIndex = messages.lastIndex
                    messages[lastIndex] = messages[lastIndex].copy(
                        type = AiChatMessageType.ERROR,
                        content = e.message ?: "Unknown error"
                    )
                    updateState()
                }
                .collect()
        }
    }

    private fun updateState() {
        _uiState.value = ChatUIState.Content(
            messages = messages.toList(),
            isLoading = messages.any { it.type == AiChatMessageType.LOADING },
            error = messages.firstOrNull { it.type == AiChatMessageType.ERROR }?.content?.let {
                Exception(it)
            }
        )
    }
}