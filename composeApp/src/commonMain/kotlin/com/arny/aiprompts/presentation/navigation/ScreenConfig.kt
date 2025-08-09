package com.arny.aiprompts.presentation.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface ScreenConfig {
    @Serializable
    data object PromptList : ScreenConfig
}
