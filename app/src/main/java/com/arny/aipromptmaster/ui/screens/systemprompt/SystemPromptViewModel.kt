package com.arny.aipromptmaster.ui.screens.systemprompt

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.aipromptmaster.domain.interactors.ILLMInteractor
import com.arny.aipromptmaster.ui.navigation.AppBarAction
import com.arny.aipromptmaster.ui.navigation.ScreenConfig
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Intent – пользовательское действие, которое инициирует ViewModel.
 */
sealed class SystemPromptIntent {
    /** Изменение текста системного промпта. */
    data class UpdateText(val text: String) : SystemPromptIntent()

    /** Пользователь нажал «Сохранить». */
    object SaveClicked : SystemPromptIntent()

    /** Пользователь нажал на переход к списку промптов. */
    object NavToPromptsClicked : SystemPromptIntent()
}

/**
 * Effect – одноразовое событие, которое UI должно обработать (например,
 * навигация, показать snackbar и т.д.).
 */
sealed class SystemPromptEffect {
    /** Навигация к списку промптов */
    data object NavigateToPrompts : SystemPromptEffect()

    /** Показ toast, показываем и success и error, можно будет разделить */
    data class ShowToast(val message: String) : SystemPromptEffect()
}

class SystemPromptViewModel(
    private val conversationId: String,
    private val interactor: ILLMInteractor
) : ViewModel() {

    private val _screenConfig = MutableStateFlow(
        ScreenConfig(
            title = "Chat",
            showBackButton = true,
            actions = listOf(
                AppBarAction(Icons.Default.Save, "Сохранить") { onSaveClicked() },
                AppBarAction(
                    Icons.Default.Search,
                    "Перейти к списку промптов"
                ) { onNavigateToPromptsClicked() },
            )
        )
    )
    val screenConfig = _screenConfig.asStateFlow()

    private val _systemPrompt = MutableStateFlow("")
    val systemPrompt: StateFlow<String> get() = _systemPrompt.asStateFlow()

    private val _intentChannel = Channel<SystemPromptIntent>(capacity = Channel.CONFLATED)
    val intentFlow: Flow<SystemPromptIntent> get() = _intentChannel.receiveAsFlow()

    private val _effectFlow = MutableSharedFlow<SystemPromptEffect>(replay = 0)
    val effectFlow: SharedFlow<SystemPromptEffect> get() = _effectFlow.asSharedFlow()

    init {
        viewModelScope.launch {
            try {
                val prompt = interactor.getSystemPrompt(conversationId)
                _systemPrompt.value = prompt.orEmpty()
            } catch (e: Exception) {
                _effectFlow.emit(SystemPromptEffect.ShowToast(e.message.orEmpty()))
            }
        }
        viewModelScope.launch {
            intentFlow.collect { handleIntent(it) }
        }
    }

    /* ---------- Методы для UI (обёртки над каналом) ---------- */

    fun onNavigateToPromptsClicked() =
        sendIntent(SystemPromptIntent.NavToPromptsClicked)

    fun onTextChanged(text: String) = sendIntent(SystemPromptIntent.UpdateText(text))

    fun onSaveClicked() = sendIntent(SystemPromptIntent.SaveClicked)

    private suspend fun handleIntent(intent: SystemPromptIntent) {
        when (intent) {
            is SystemPromptIntent.UpdateText -> _systemPrompt.value = intent.text

            is SystemPromptIntent.SaveClicked -> processSave()

            is SystemPromptIntent.NavToPromptsClicked -> {
                _effectFlow.emit(SystemPromptEffect.NavigateToPrompts)
            }
        }
    }

    /** Основная бизнес‑логика сохранения. */
    private suspend fun processSave() {
        val prompt = _systemPrompt.value.trim()
        try {
            interactor.setSystemPrompt(conversationId, prompt)
            _effectFlow.emit(SystemPromptEffect.ShowToast("Системный промпт сохранен"))
        } catch (e: Exception) {
            // Любая ошибка – показываем сообщение
            _effectFlow.emit(SystemPromptEffect.ShowToast("Не удалось сохранить: ${e.message}"))
        }
    }

    private fun sendIntent(intent: SystemPromptIntent) = viewModelScope.launch {
        _intentChannel.send(intent)
    }
}
