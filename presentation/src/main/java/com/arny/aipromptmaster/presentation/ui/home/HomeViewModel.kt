package com.arny.aipromptmaster.presentation.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.aipromptmaster.domain.interactors.IPromptsInteractor
import dagger.assisted.AssistedInject
import kotlinx.coroutines.launch

class HomeViewModel @AssistedInject constructor(
    private val interactor: IPromptsInteractor,
) : ViewModel() {
    init {
        loadPrompts()
    }

    private fun loadPrompts() {
        viewModelScope.launch {
            interactor
        }
    }
}