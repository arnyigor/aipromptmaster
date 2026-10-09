package com.arny.sharedui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class ImprovementUi(
    val language: String, val source: String, val result: String, val model: String, val instructions: String,
    val temperature: Float, val maxTokens: String, val stream: Boolean, val running: Boolean,
    val canApply: Boolean, val error: String? = null, val firstResponseMs: Long? = null, val elapsedMs: Long? = null,
)

sealed interface ImprovementUiAction {
    data class Language(val value: String) : ImprovementUiAction
    data class Source(val value: String) : ImprovementUiAction
    data class Result(val value: String) : ImprovementUiAction
    data class Model(val value: String) : ImprovementUiAction
    data class Instructions(val value: String) : ImprovementUiAction
    data class Temperature(val value: Float) : ImprovementUiAction
    data class MaxTokens(val value: String) : ImprovementUiAction
    data class Stream(val value: Boolean) : ImprovementUiAction
    data object Generate : ImprovementUiAction
    data object Cancel : ImprovementUiAction
    data object Apply : ImprovementUiAction
    data object Dismiss : ImprovementUiAction
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptImprovementPane(state: ImprovementUi, onAction: (ImprovementUiAction) -> Unit, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier.imePadding(),
        topBar = { TopAppBar(title = { Text("Улучшить промпт") }, navigationIcon = {
            TextButton(onClick = { onAction(ImprovementUiAction.Dismiss) }) { Text("Закрыть") }
        }) },
        bottomBar = { Surface(shadowElevation = 4.dp) {
            Button(onClick = { onAction(ImprovementUiAction.Apply) }, enabled = state.canApply,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) { Text("Применить к черновику") }
        } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Применение изменит черновик. Сохранение выполняется отдельно.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("RU", "EN").forEach { language -> FilterChip(selected = state.language == language,
                    onClick = { onAction(ImprovementUiAction.Language(language)) }, enabled = !state.running, label = { Text(language) }) }
            }
            if (state.running) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedTextField(state.result, { onAction(ImprovementUiAction.Result(it)) }, label = { Text("Предпросмотр результата") },
                readOnly = state.running, minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.firstResponseMs?.let { Text("Первый ответ: $it мс", style = MaterialTheme.typography.bodySmall) }
            state.elapsedMs?.let { Text("Время: $it мс", style = MaterialTheme.typography.bodySmall) }
            if (state.running) OutlinedButton(onClick = { onAction(ImprovementUiAction.Cancel) }, modifier = Modifier.fillMaxWidth()) { Text("Остановить") }
            else Button(onClick = { onAction(ImprovementUiAction.Generate) }, enabled = state.source.isNotBlank() && state.model.isNotBlank(),
                modifier = Modifier.fillMaxWidth()) { Text("Улучшить") }
            OutlinedTextField(state.model, { onAction(ImprovementUiAction.Model(it)) }, label = { Text("ID модели") },
                enabled = !state.running, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Провайдер и ключ выбираются в настройках LLM.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(state.source, { onAction(ImprovementUiAction.Source(it)) }, label = { Text("Исходный промпт") },
                enabled = !state.running, minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.instructions, { onAction(ImprovementUiAction.Instructions(it)) }, label = { Text("Что улучшить (необязательно)") }, enabled = !state.running, modifier = Modifier.fillMaxWidth())
            Text("Температура: ${state.temperature}")
            Slider(state.temperature, { onAction(ImprovementUiAction.Temperature(it)) }, enabled = !state.running, valueRange = 0f..2f)
            OutlinedTextField(state.maxTokens, { onAction(ImprovementUiAction.MaxTokens(it)) }, label = { Text("Лимит токенов (1–16384)") },
                enabled = !state.running, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row { Checkbox(state.stream, { onAction(ImprovementUiAction.Stream(it)) }, enabled = !state.running); Text("Показывать ответ постепенно") }
        }
    }
}
