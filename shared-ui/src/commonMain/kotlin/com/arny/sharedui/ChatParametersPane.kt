package com.arny.sharedui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

@Composable
fun SystemPromptEditor(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value, onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text("Системный промпт") },
        placeholder = { Text("Задайте роль и правила ответа…") },
        minLines = 3, maxLines = 10,
        shape = MaterialTheme.shapes.medium,
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
    Surface(modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
    Column(Modifier.fillMaxHeight()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Параметры чата", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (onDismiss != null) IconButton(onDismiss) { Icon(Icons.Default.Close, "Закрыть параметры") }
        }
        HorizontalDivider()
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
        modelInfo()
        SystemPromptEditor(systemPrompt, onSystemPromptChanged)
        generation()
        statistics()
        }
    }
    }
}
