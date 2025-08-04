package com.arny.aipromptmaster.presentation.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.aipromptmaster.domain.interactors.ISettingsInteractor
import com.arny.aipromptmaster.domain.models.strings.StringHolder
import com.arny.aipromptmaster.domain.models.strings.toErrorHolder
import com.arny.aipromptmaster.presentation.R
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel @AssistedInject constructor(
    private val settingsInteractor: ISettingsInteractor
) : ViewModel() {
    private val _uiState = MutableStateFlow<SettingsUIState>(SettingsUIState.Idle)
    val uiState: StateFlow<SettingsUIState> = _uiState

    fun saveApiKey(apiKey: String?) {
        viewModelScope.launch {
            if (apiKey.isNullOrBlank()) {
                _uiState.value = SettingsUIState.Error(StringHolder.Resource(R.string.error_empty_string))
            } else {
                try {
                    settingsInteractor.saveApiKey(apiKey)
                    _uiState.value = SettingsUIState.Success(apiKey)
                } catch (e: Exception) {
                    _uiState.value = SettingsUIState.Error(e.toErrorHolder())
                }
            }
        }
    }

    fun loadApiKey() {
        viewModelScope.launch {
            try {
                val apiKey = settingsInteractor.getApiKey()
                _uiState.value = SettingsUIState.Success(apiKey)
            } catch (e: Exception) {
                _uiState.value = SettingsUIState.Error(e.toErrorHolder())
            }
        }
    }
}