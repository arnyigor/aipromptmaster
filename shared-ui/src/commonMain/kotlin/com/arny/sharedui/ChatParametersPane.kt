package com.arny.sharedui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SystemPromptEditor(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value, onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text("Системный промпт") },
        placeholder = { Text("Задайте роль и правила ответа…") },
        minLines = 4, maxLines = 12,
        trailingIcon = {
            if (value.isNotEmpty()) IconButton(onClick = { onValueChange("") }) {
                Icon(Icons.Default.Close, "Очистить системный промпт")
            }
        },
    )
}

/** State comes from the platform owner; the pane never keeps a second copy of the prompt. */
@Composable
fun ChatParametersPane(
    systemPrompt: String,
    onSystemPromptChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    modelInfo: @Composable () -> Unit = {},
    generation: @Composable () -> Unit = {},
    statistics: @Composable () -> Unit = {},
) {
    Surface(modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.surface) {
    Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text("Параметры чата", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (onDismiss != null) IconButton(onDismiss) { Icon(Icons.Default.Close, "Закрыть параметры") }
        }
        HorizontalDivider()
        modelInfo()
        SystemPromptEditor(systemPrompt, onSystemPromptChanged)
        generation()
        statistics()
    }
    }
}
