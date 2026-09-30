package com.arny.aipromptmaster.ui.screens.edit


import com.arny.aipromptmaster.domain.ImprovePromptUseCase
import com.arny.aipromptmaster.domain.ImprovementRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

enum class PromptLanguage { RU, EN }

data class PromptImprovementState(
    val visible: Boolean = false,
    val language: PromptLanguage = PromptLanguage.RU,
    val source: String = "",
    val model: String = "",
    val instructions: String = "",
    val temperature: Float = 0.7f,
    val maxTokens: String = "1024",
    val stream: Boolean = true,
    val result: String = "",
    val running: Boolean = false,
    val complete: Boolean = false,
    val error: String? = null,
    val firstResponseMs: Long? = null,
    val elapsedMs: Long? = null
) {
    val canApply: Boolean get() = complete && !running && error == null && result.isNotBlank()
}

sealed interface PromptImprovementAction {
    data class Language(val value: PromptLanguage) : PromptImprovementAction
    data class Source(val value: String) : PromptImprovementAction
    data class Model(val value: String) : PromptImprovementAction
    data class Instructions(val value: String) : PromptImprovementAction
    data class Temperature(val value: Float) : PromptImprovementAction
    data class MaxTokens(val value: String) : PromptImprovementAction
    data class Stream(val value: Boolean) : PromptImprovementAction
    data class Result(val value: String) : PromptImprovementAction
    data object Generate : PromptImprovementAction
    data object Cancel : PromptImprovementAction
    data object Apply : PromptImprovementAction
    data object Dismiss : PromptImprovementAction
}

/** Owns the preview. Applying updates a draft through a callback; never saves a prompt. */
class PromptImprovementController(
    private val useCase: ImprovePromptUseCase,
    private val scope: CoroutineScope,
    private val onApply: (PromptLanguage, String) -> Unit
) {
    private val _state = MutableStateFlow(PromptImprovementState())
    val state = _state.asStateFlow()
    private var contentRu = ""
    private var contentEn = ""
    private var generation = 0
    private var job: Job? = null

    fun open(ru: String, en: String) {
        cancel()
        contentRu = ru; contentEn = en
        val language = if (ru.isBlank()) PromptLanguage.EN else PromptLanguage.RU
        _state.value = PromptImprovementState(visible = true, language = language, source = content(language))
        val token = generation
        scope.launch {
            runCatching { useCase.selectedModel() }.onSuccess { model ->
                if (token == generation) _state.update { if (it.model.isBlank()) it.copy(model = model) else it }
            }
        }
    }

    fun onAction(action: PromptImprovementAction) {
        when (action) {
            PromptImprovementAction.Generate -> generate()
            PromptImprovementAction.Cancel -> { cancel(); _state.update { it.copy(running = false, complete = false, error = "Генерация отменена") } }
            PromptImprovementAction.Dismiss -> { cancel(); _state.update { it.copy(visible = false, running = false, complete = false) } }
            PromptImprovementAction.Apply -> {
                val current = _state.value
                if (current.canApply) {
                    onApply(current.language, current.result)
                    _state.update { it.copy(visible = false) }
                }
            }
            else -> if (!_state.value.running) _state.update {
                when (action) {
                    is PromptImprovementAction.Language -> it.copy(language = action.value, source = content(action.value), result = "", complete = false, error = null)
                    is PromptImprovementAction.Source -> it.copy(source = action.value, result = "", complete = false, error = null)
                    is PromptImprovementAction.Model -> it.copy(model = action.value)
                    is PromptImprovementAction.Instructions -> it.copy(instructions = action.value)
                    is PromptImprovementAction.Temperature -> it.copy(temperature = action.value)
                    is PromptImprovementAction.MaxTokens -> it.copy(maxTokens = action.value)
                    is PromptImprovementAction.Stream -> it.copy(stream = action.value)
                    is PromptImprovementAction.Result -> it.copy(result = action.value)
                    else -> it
                }
            }
        }
    }

    private fun content(language: PromptLanguage): String = when (language) {
        PromptLanguage.RU -> contentRu
        PromptLanguage.EN -> contentEn
    }

    private fun cancel() { generation++; job?.cancel(); job = null }

    private fun generate() {
        if (_state.value.running) return
        cancel()
        val token = generation
        val current = _state.value
        _state.update { it.copy(running = true, complete = false, result = "", error = null, firstResponseMs = null, elapsedMs = null) }
        job = scope.launch {
            val start = TimeSource.Monotonic.markNow()
            try {
                val request = ImprovementRequest(current.source, current.model.trim(), current.instructions,
                    current.temperature.toDouble(), current.maxTokens.toIntOrNull() ?: 0, current.stream)
                useCase(request).collect { text ->
                    if (token == generation) _state.update {
                        it.copy(result = text, firstResponseMs = it.firstResponseMs ?: if (text.isNotBlank()) start.elapsedNow().inWholeMilliseconds else null)
                    }
                }
                if (token == generation) _state.update { it.copy(complete = true) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (token == generation) _state.update { it.copy(error = error.message ?: "Не удалось улучшить промпт", complete = false) }
            } finally {
                if (token == generation) _state.update { it.copy(running = false, elapsedMs = start.elapsedNow().inWholeMilliseconds) }
            }
        }
    }
}

