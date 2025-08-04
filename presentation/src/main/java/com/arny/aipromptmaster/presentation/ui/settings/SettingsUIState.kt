package com.arny.aipromptmaster.presentation.ui.settings

import com.arny.aipromptmaster.domain.models.strings.StringHolder

sealed class SettingsUIState {
    object Idle : SettingsUIState()
    data class Success(val apiKey: String? = null) : SettingsUIState()
    data class Error(val stringHolder: StringHolder?) : SettingsUIState()
}