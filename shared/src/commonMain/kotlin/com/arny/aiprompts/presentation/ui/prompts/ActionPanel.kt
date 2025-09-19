package com.arny.aiprompts.presentation.ui.prompts

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arny.aiprompts.BuildsConfig

@Composable
fun ActionPanel(
    modifier: Modifier = Modifier,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
    onScraperNavigate: () -> Unit,
    onLLMNavigate: () -> Unit
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxHeight().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("Добавить промпт") }
            OutlinedButton(onClick = onLLMNavigate, modifier = Modifier.fillMaxWidth()) {
                Text("LLM Чат")
            }
            Spacer(Modifier.weight(1f)) // Этот Spacer отодвигает кнопку настроек вниз
            if (BuildsConfig.DEBUG) {
                OutlinedButton(onClick = onScraperNavigate, modifier = Modifier.fillMaxWidth()) {
                    Text("Скрапер / Импорт")
                }
            }
            OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Настройки")
            }

        }
    }
}
