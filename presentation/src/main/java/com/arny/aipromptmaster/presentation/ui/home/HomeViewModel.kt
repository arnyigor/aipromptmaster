package com.arny.aipromptmaster.presentation.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.aipromptmaster.domain.interactors.IPromptsInteractor
import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.utils.strings.IWrappedString
import com.arny.aipromptmaster.presentation.utils.strings.ResourceString
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class HomeViewModel @AssistedInject constructor(
    private val interactor: IPromptsInteractor,
) : ViewModel() {
    private val _toastError = MutableSharedFlow<IWrappedString?>()
    val error = _toastError.asSharedFlow()
    private val _prompts = MutableStateFlow<List<Prompt>>(emptyList())
    val prompts = _prompts.asStateFlow()

    init {
        loadPrompts()
    }

    private fun loadPrompts() {
        viewModelScope.launch {
            interactor.getAllPrompts()
                .flowOn(Dispatchers.IO)
                .catch { _toastError.emit(ResourceString(R.string.erro_load_prompts, it.message)) }
                .collect(::updateItems)
        }
    }

    private fun updateItems(prompts: List<Prompt>) {
        _prompts.value = prompts
    }
}