package com.arny.aiprompts.presentation.ui.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@Composable
fun PromptImprovementDialog(state: PromptImprovementState, onAction: (PromptImprovementAction) -> Unit) {
val shared = com.arny.sharedui.ImprovementUi(state.language.name, state.source, state.result, state.model,
        state.instructions, state.temperature, state.maxTokens, state.stream, state.running, state.canApply,
        state.error, state.firstResponseMs, state.elapsedMs)
    val dispatch: (com.arny.sharedui.ImprovementUiAction) -> Unit = { action ->
        onAction(when (action) {
            is com.arny.sharedui.ImprovementUiAction.Language -> PromptImprovementAction.Language(PromptLanguage.valueOf(action.value))
            is com.arny.sharedui.ImprovementUiAction.Source -> PromptImprovementAction.Source(action.value)
            is com.arny.sharedui.ImprovementUiAction.Result -> PromptImprovementAction.Result(action.value)
            is com.arny.sharedui.ImprovementUiAction.Model -> PromptImprovementAction.Model(action.value)
            is com.arny.sharedui.ImprovementUiAction.Instructions -> PromptImprovementAction.Instructions(action.value)
            is com.arny.sharedui.ImprovementUiAction.Temperature -> PromptImprovementAction.Temperature(action.value)
            is com.arny.sharedui.ImprovementUiAction.MaxTokens -> PromptImprovementAction.MaxTokens(action.value)
            is com.arny.sharedui.ImprovementUiAction.Stream -> PromptImprovementAction.Stream(action.value)
            com.arny.sharedui.ImprovementUiAction.Generate -> PromptImprovementAction.Generate
            com.arny.sharedui.ImprovementUiAction.Cancel -> PromptImprovementAction.Cancel
            com.arny.sharedui.ImprovementUiAction.Apply -> PromptImprovementAction.Apply
            com.arny.sharedui.ImprovementUiAction.Dismiss -> PromptImprovementAction.Dismiss
        })
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = { onAction(PromptImprovementAction.Dismiss) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth(0.95f).fillMaxHeight(0.90f), shape = MaterialTheme.shapes.large) {
            com.arny.sharedui.PromptImprovementPane(shared, dispatch, Modifier.fillMaxSize())
        }
    }
}
