package com.arny.aipromptmaster.presentation.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.aipromptmaster.domain.interactors.ISettingsInteractor
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.utils.strings.ResourceString
import com.arny.aipromptmaster.presentation.utils.strings.SimpleString
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
                _uiState.value = SettingsUIState.Error(ResourceString(R.string.error_empty_string))
            } else {
                try {
                    settingsInteractor.saveApiKey(apiKey)
                    _uiState.value = SettingsUIState.Success(apiKey)
                } catch (e: Exception) {
                    _uiState.value = SettingsUIState.Error(SimpleString(e.message))
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
                _uiState.value = SettingsUIState.Error(SimpleString(e.message))
            }
        }
    }
}