package com.arny.aiprompts.presentation.ui.scraper

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ScraperScreen(component: ScraperComponent) {
    val state by component.state.collectAsState()
    val logListState = rememberLazyListState()
    // Автопрокрутка логов
    LaunchedEffect(state.logs.size) {
        if (state.logs.isNotEmpty()) {
            logListState.animateScrollToItem(state.logs.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // --- ПАНЕЛЬ УПРАВЛЕНИЯ ---
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.pagesToScrape,
                onValueChange = component::onPagesChanged,
                label = { Text("Количество страниц") },
                modifier = Modifier.width(180.dp)
            )
            Button(
                onClick = component::onStartScrapingClicked,
                enabled = !state.inProgress && (state.pagesToScrape.toIntOrNull() ?: 0) > 0
            ) { Text("Запустить скрапер") }

            IconButton(onClick = component::onOpenDirectoryClicked, enabled = !state.inProgress) {
                Icon(Icons.Default.FolderOpen, "Открыть директорию")
            }

            if (state.inProgress) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }

            Button(
                onClick = component::onParseAndSaveClicked,
                enabled = state.savedHtmlFiles.isNotEmpty() && !state.inProgress
            ) {
                Text("Парсить и сохранить")
            }

            Spacer(Modifier.weight(1f))

            Button(onClick = component::onNavigateToImporterClicked, enabled = state.savedHtmlFiles.isNotEmpty()) {
                Text("Перейти к Ассистенту")
            }
        }

        // --- ПАНЕЛИ РЕЗУЛЬТАТОВ ---
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LazyColumn(modifier = Modifier.weight(1f), state = logListState) {
                items(state.logs) { log -> Text(log, style = MaterialTheme.typography.bodySmall) }
            }
            LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                    Text(
                        "Сохраненные HTML (${state.savedHtmlFiles.size} шт.):",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                items(state.savedHtmlFiles) { file -> Text("• ${file.name}") }
            }
            LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                    Text(
                        "Спарсенные промпты (${state.parsedPrompts.size}):",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                items(state.parsedPrompts) { prompt ->
                    Text(
                        "• ${prompt.title}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
                item {
                    Text(
                        "Сохраненные JSON (${state.lastSavedJsonFiles.size}):",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                items(state.lastSavedJsonFiles) { file ->
                    Text(
                        "• ${file.name}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    // --- ДИАЛОГ ---
    if (state.preScrapeCheckResult != null) {
        val checkResult = state.preScrapeCheckResult!!
        AlertDialog(
            onDismissRequest = component::onDialogDismissed,
            title = { Text("Обнаружены пропуски") },
            text = {
                Text(
                    "Найдено ${checkResult.existingFileCount} из ${state.pagesToScrape} страниц.\nОтсутствуют страницы: ${
                        checkResult.missingPages.map { it + 1 }.joinToString()
                    }. \n\nЧто вы хотите сделать?"
                )
            },
            confirmButton = {
                if (checkResult.missingPages.isNotEmpty()) {
                    Button(onClick = component::onContinueConfirmed) { Text("Докачать (${checkResult.missingPages.size})") }
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = component::onOverwriteConfirmed) { Text("Перезаписать все") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = component::onDialogDismissed) { Text("Отмена") }
                }
            }
        )
    }
}