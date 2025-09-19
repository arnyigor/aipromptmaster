package com.arny.aiprompts.presentation.features.llm

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.arny.aiprompts.domain.interactors.ILLMInteractor
import com.arny.aiprompts.data.model.LlmModel
import com.arny.aiprompts.results.DataResult
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// Интерфейс компонента
interface LlmComponent {
    val uiState: StateFlow<LlmUiState>

    fun onPromptChanged(newPrompt: String)
    fun onModelSelected(modelId: String)
    fun onGenerateClicked()
    fun refreshModels()
}