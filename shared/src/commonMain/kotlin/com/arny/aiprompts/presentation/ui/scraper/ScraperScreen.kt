package com.arny.aiprompts.presentation.ui.scraper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arny.aiprompts.domain.model.Author
import com.arny.aiprompts.domain.model.PromptData
import com.benasher44.uuid.uuid4
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import kotlin.uuid.Uuid

/**
 * Фиктивная реализация ScraperComponent, пригодная для Compose‑preview.
 */
private class FakeScraperComponent : ScraperComponent {
    private val dummyPrompt = PromptData(
        id = uuid4().toString(),
        sourceId = "",
        title = "Prompt Example",
        description  = "Краткое описание промпта.",
        variants = emptyList(),
        author = Author(name = "Anonymous", id = "1212"),
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )

    // ---------- 2. Список для preview ----------
    val parsedPromptsPreview = listOf(
        dummyPrompt.copy(
            id = "1",
            title = "Prompt One",
            description = "Краткое описание первого промпта."
        ),
        dummyPrompt.copy(
            id = "2",
            title = "Prompt Two",
            description = "Описание второго примера. Можно добавить более длинный текст, чтобы проверить разметку."
        )
    )

    // Инициализируем состояние сразу – preview не запускает корутины.
    private val _state = MutableStateFlow(
        ScraperState(
            pagesToScrape = "25",
            logs = listOf(
                "Готов к запуску.",
                "Загрузка страниц…",
                "Сохранено 3 HTML‑файла."
            ),
            savedHtmlFiles = listOf(
                File("sample1.html"),
                File("sample2.html")
            ),
            parsedPrompts = parsedPromptsPreview,
            lastSavedJsonFiles = listOf(File("prompts.json")),
            inProgress = false,
            preScrapeCheckResult = null // при необходимости можно задать объект PreScrapeCheck
        )
    )

    override val state: StateFlow<ScraperState> get() = _state

    // Методы‑обработчики оставляем пустыми – они не нужны в preview.
    override fun onPagesChanged(pages: String) {}
    override fun onStartScrapingClicked() {}
    override fun onParseAndSaveClicked() {}
    override fun onOpenDirectoryClicked() {}
    override fun onNavigateToImporterClicked() {}
    override fun onBackClicked() {}
    override fun onOverwriteConfirmed() {}
    override fun onContinueConfirmed() {}
    override fun onDialogDismissed() {}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScraperScreen(
    component: ScraperComponent,
) {
    val state by component.state.collectAsState()
    val logListState = rememberLazyListState()

    // Автопрокрутка логов
    LaunchedEffect(state.logs.size) {
        if (state.logs.isNotEmpty()) {
            logListState.animateScrollToItem(state.logs.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Скрапер") }
            )
        },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets.systemBars
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // --- ПАНЕЛЬ УПРАВЛЕНИЯ ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.pagesToScrape,
                    onValueChange = component::onPagesChanged,
                    modifier = Modifier.width(180.dp),
                    label = { Text("Страницы для сканирования") },
                    placeholder = { Text("Напр: 1-10, 15, 22") },
                    singleLine = true,
                    supportingText = { Text("Укажите номера или диапазоны") }
                )

                Button(
                    onClick = component::onStartScrapingClicked,
                    enabled = !state.inProgress && state.pagesToScrape.isNotBlank()
                ) {
                    Text("Запустить скрапер")
                }

                IconButton(
                    onClick = component::onOpenDirectoryClicked,
                    enabled = !state.inProgress
                ) {
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

                Button(
                    onClick = component::onNavigateToImporterClicked,
                    enabled = state.savedHtmlFiles.isNotEmpty()
                ) {
                    Text("Перейти к Ассистенту")
                }
            }

            // --- ПАНЕЛИ РЕЗУЛЬТАТОВ ---
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LazyColumn(modifier = Modifier.weight(1f), state = logListState) {
                    items(state.logs) { log ->
                        Text(log, style = MaterialTheme.typography.bodySmall)
                    }
                }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    item {
                        Text(
                            "Сохраненные HTML (${state.savedHtmlFiles.size} шт.):",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    items(state.savedHtmlFiles) { file ->
                        Text("• ${file.name}")
                    }
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
    }

    // --- ДИАЛОГ ---
    if (state.preScrapeCheckResult != null) {
        val checkResult = state.preScrapeCheckResult!!
        AlertDialog(
            onDismissRequest = component::onDialogDismissed,
            title = { Text("Обнаружены пропуски") },
            text = {
                Text(
                    "Найдено ${checkResult.existingFileCount} из ${state.pagesToScrape} страниц.\n" +
                            "Отсутствуют страницы: ${
                                checkResult.missingPages.map { it + 1 }.joinToString()
                            }. \n\n" +
                            "Что вы хотите сделать?"
                )
            },
            confirmButton = {
                if (checkResult.missingPages.isNotEmpty()) {
                    Button(onClick = component::onContinueConfirmed) {
                        Text("Докачать (${checkResult.missingPages.size})")
                    }
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = component::onOverwriteConfirmed) {
                        Text("Перезаписать все")
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = component::onDialogDismissed) {
                        Text("Отмена")
                    }
                }
            }
        )
    }
}
