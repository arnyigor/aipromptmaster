package com.arny.aipromptmaster.ui.screens.edit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.arny.aipromptmaster.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptImprovementScreen(state: PromptImprovementState, onAction: (PromptImprovementAction) -> Unit) {
    Dialog(onDismissRequest = { onAction(PromptImprovementAction.Dismiss) }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        val lightBars = MaterialTheme.colorScheme.surface.luminance() > 0.5f
        SideEffect {
            window?.let { WindowCompat.getInsetsController(it, it.decorView).apply {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            } }
        }
        Scaffold(
            modifier = Modifier.fillMaxSize().imePadding(),
            topBar = { TopAppBar(title = { Text(stringResource(R.string.improve_title)) }, navigationIcon = {
                TextButton(onClick = { onAction(PromptImprovementAction.Dismiss) }) { Text(stringResource(R.string.improve_close)) }
            }) },
            bottomBar = {
                Surface(shadowElevation = 4.dp) {
                    Button(onClick = { onAction(PromptImprovementAction.Apply) }, enabled = state.canApply,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) { Text(stringResource(R.string.improve_apply)) }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.improve_draft_hint))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PromptLanguage.entries.forEach { language -> FilterChip(selected = state.language == language, onClick = { onAction(PromptImprovementAction.Language(language)) }, enabled = !state.running, label = { Text(language.name) }) }
                }
                if (state.running) LinearProgressIndicator(Modifier.fillMaxWidth())
                OutlinedTextField(state.result, { onAction(PromptImprovementAction.Result(it)) }, label = { Text(stringResource(R.string.improve_preview)) }, readOnly = state.running, minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth())
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.running) OutlinedButton(onClick = { onAction(PromptImprovementAction.Cancel) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.improve_stop)) }
                else Button(onClick = { onAction(PromptImprovementAction.Generate) }, enabled = state.source.isNotBlank() && state.model.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.improve_generate)) }
                OutlinedTextField(state.model, { onAction(PromptImprovementAction.Model(it)) }, label = { Text(stringResource(R.string.improve_model)) }, enabled = !state.running, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.improve_provider_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(state.source, { onAction(PromptImprovementAction.Source(it)) }, label = { Text(stringResource(R.string.improve_source)) }, enabled = !state.running, minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(state.instructions, { onAction(PromptImprovementAction.Instructions(it)) }, label = { Text(stringResource(R.string.improve_instructions)) }, enabled = !state.running, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.improve_temperature, state.temperature.toString()))
                Slider(state.temperature, { onAction(PromptImprovementAction.Temperature(it)) }, enabled = !state.running, valueRange = 0f..2f)
                OutlinedTextField(state.maxTokens, { onAction(PromptImprovementAction.MaxTokens(it)) }, label = { Text(stringResource(R.string.improve_tokens)) }, enabled = !state.running, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row { Checkbox(state.stream, { onAction(PromptImprovementAction.Stream(it)) }, enabled = !state.running); Text(stringResource(R.string.improve_stream)) }
            }
        }
    }
}
