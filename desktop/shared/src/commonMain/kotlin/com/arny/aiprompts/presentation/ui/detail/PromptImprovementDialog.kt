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
    AlertDialog(
        onDismissRequest = { onAction(PromptImprovementAction.Dismiss) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth(0.95f),
        title = { Text("Улучшить промпт") },
        text = {
            Column(Modifier.heightIn(max = 600.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Используется подключение из настроек LLM. Применение изменит черновик; сохранение выполняется отдельно.")
                Row {
                    PromptLanguage.entries.forEach { language ->
                        TextButton(onClick = { onAction(PromptImprovementAction.Language(language)) }, enabled = !state.running && language != state.language) { Text(language.name) }
                    }
                    Text("Выбран: ${state.language.name}")
                }
                if (state.running) LinearProgressIndicator(Modifier.fillMaxWidth())
                OutlinedTextField(state.result, { onAction(PromptImprovementAction.Result(it)) }, label = { Text("Предпросмотр результата") }, readOnly = state.running, minLines = 3, maxLines = 10, modifier = Modifier.fillMaxWidth())
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.firstResponseMs?.let { Text("Первый ответ: $it мс") }
                state.elapsedMs?.let { Text("Время: $it мс") }
                if (state.running) {
                    TextButton(onClick = { onAction(PromptImprovementAction.Cancel) }) { Text("Остановить") }
                } else {
                    Button(onClick = { onAction(PromptImprovementAction.Generate) }, enabled = state.source.isNotBlank() && state.model.isNotBlank()) { Text("Улучшить") }
                }
                OutlinedTextField(state.model, { onAction(PromptImprovementAction.Model(it)) }, label = { Text("ID модели") }, enabled = !state.running, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(state.instructions, { onAction(PromptImprovementAction.Instructions(it)) }, label = { Text("Что улучшить (необязательно)") }, enabled = !state.running, modifier = Modifier.fillMaxWidth())
                Text("Температура: ${state.temperature}")
                Slider(state.temperature, { onAction(PromptImprovementAction.Temperature(it)) }, valueRange = 0f..2f, enabled = !state.running)
                OutlinedTextField(state.maxTokens, { onAction(PromptImprovementAction.MaxTokens(it)) }, label = { Text("Лимит токенов (1–16384)") }, enabled = !state.running, singleLine = true)
                Row {
                    Checkbox(state.stream, { onAction(PromptImprovementAction.Stream(it)) }, enabled = !state.running)
                    Text("Показывать ответ постепенно")
                }
                OutlinedTextField(state.source, { onAction(PromptImprovementAction.Source(it)) }, label = { Text("Исходный промпт") }, enabled = !state.running, minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth())

            }
        },
        confirmButton = { Button(onClick = { onAction(PromptImprovementAction.Apply) }, enabled = state.canApply) { Text("Применить к черновику") } },
        dismissButton = { TextButton(onClick = { onAction(PromptImprovementAction.Dismiss) }) { Text("Закрыть") } }
    )
}
