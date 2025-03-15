package com.arny.aipromptmaster.presentation.ui.history

import androidx.lifecycle.ViewModel
import com.arny.aipromptmaster.presentation.utils.strings.IWrappedString
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class HistoryViewModel @AssistedInject constructor(
) : ViewModel() {

    private val _error = MutableSharedFlow<IWrappedString?>()
    val error = _error.asSharedFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    init {
    }

    companion object {
    }
}