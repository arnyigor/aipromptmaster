package com.arny.aipromptmaster.presentation.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.aipromptmaster.domain.interactors.ILLMInteractor
import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.domain.results.DataResult
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ModelsViewModel @AssistedInject constructor(
    private val llmInteractor: ILLMInteractor,
) : ViewModel() {

    val uiState: StateFlow<DataResult<List<LlmModel>>> = llmInteractor.getModels()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = DataResult.Loading
        )


    fun selectModel(clickedModelId: String) {
        viewModelScope.launch {
            llmInteractor.toggleModelSelection(clickedModelId)
        }
    }
}
