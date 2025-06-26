package com.arny.aipromptmaster.presentation.ui.settings

import com.arny.aipromptmaster.presentation.utils.strings.IWrappedString

sealed class SettingsUIState {
    object Idle : SettingsUIState()
    data class Success(val apiKey: String? = null) : SettingsUIState()
    data class Error(val message: IWrappedString?) : SettingsUIState()
}