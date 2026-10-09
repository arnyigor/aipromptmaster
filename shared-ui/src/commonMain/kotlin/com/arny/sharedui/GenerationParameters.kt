package com.arny.sharedui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

data class GenerationParametersUi(val temperature: Float, val maxTokens: Int, val topP: Float, val contextWindow: Int)

@Composable
fun GenerationParameters(state: GenerationParametersUi, onChanged: (GenerationParametersUi) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Генерация", style = MaterialTheme.typography.titleSmall)
        GenerationSlider("Температура", state.temperature, 0f..2f) { onChanged(state.copy(temperature = it)) }
        GenerationIntField("Лимит токенов", state.maxTokens, 1..8192) { onChanged(state.copy(maxTokens = it)) }
        GenerationSlider("Top P", state.topP, 0f..1f) { onChanged(state.copy(topP = it)) }
        GenerationIntField("Сообщений в контексте", state.contextWindow, 1..50) { onChanged(state.copy(contextWindow = it)) }
    }
}

@Composable
private fun GenerationSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChanged: (Float) -> Unit) {
    Column {
        Text("$label: ${(value * 100).roundToInt() / 100f}", style = MaterialTheme.typography.bodyMedium)
        Slider(value.coerceIn(range), onChanged, valueRange = range, modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable
private fun GenerationIntField(label: String, value: Int, range: IntRange, onChanged: (Int) -> Unit) {
    OutlinedTextField(value.toString(), { input -> input.toIntOrNull()?.takeIf { it in range }?.let(onChanged) },
        label = { Text(label) }, supportingText = { Text("${range.first}–${range.last}") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
}
