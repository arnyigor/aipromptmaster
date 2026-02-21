package com.arny.aiprompts.presentation.ui.scraper

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arny.aiprompts.domain.model.Author
import com.arny.aiprompts.domain.model.PromptData
import com.arny.aiprompts.domain.repositories.SyncResult
import com.arny.aiprompts.domain.strings.StringHolder
import com.arny.aiprompts.domain.usecase.ProcessScrapedPostsUseCase
import com.benasher44.uuid.uuid4
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

// Extension to convert StringHolder to plain string (non-Compose)
private fun StringHolder.toPlainString(): String = when (this) {
    is StringHolder.Text -> value ?: ""
    is StringHolder.Resource -> "Resource"
    is StringHolder.Formatted -> "Formatted"
    is StringHolder.Plural -> "Plural"
}

/**
 * Fake ScraperComponent implementation for Compose preview.
 */
private class FakeScraperComponent : ScraperComponent {
    private val dummyPrompt = PromptData(
        id = uuid4().toString(),
        sourceId = "",
        title = "Prompt Example",
        description = "Краткое описание промпта.",
        variants = emptyList(),
        author = Author(name = "Anonymous", id = "1212"),
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )

    // ---------- 2. List for preview ----------
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

    // Initialize state immediately - preview doesn't launch coroutines.
    private val _state = MutableStateFlow(
        ScraperState(
            pagesToScrape = "25",
            logs = listOf(
                "Готов к запуску.",
                "Загрузка страниц…",
                "Сохранено 3 HTML‑файла."
            ),
            savedHtmlFiles = listOf(
                "sample1.html",
                "sample2.html"
            ),
            parsedPrompts = parsedPromptsPreview,
            lastSavedJsonFiles = listOf("prompts.json"),
            inProgress = false,
            preScrapeCheckResult = null,
            pipelineStage = PipelineStage.IDLE,
            pipelineProgress = 0f,
            pipelineLogs = emptyList(),
            pipelineResult = PipelineExecutionResult(
                success = true,
                totalProcessed = 50,
                newPrompts = 45,
                skippedDuplicates = 5,
                missingPages = 0,
                errors = 0,
                outputFiles = listOf("prompts/business.json", "prompts/creative.json"),
                durationMs = 5000,
                categoryBreakdown = mapOf("Business" to 20, "Creative" to 25)
            ),
            categoryFiles = listOf(
                CategoryFileInfo("Business", "prompts/business.json", 20),
                CategoryFileInfo("Creative", "prompts/creative.json", 25)
            ),
            lastSyncTime = "09.02.2024 15:30",
            localPromptsCount = 12,
            syncedPromptsCount = 45,
            isPreviewMode = false,
            previewPrompts = parsedPromptsPreview,
            currentPreviewIndex = 0,
            acceptedCount = 0,
            skippedCount = 0,
            indexLinks = emptyList(),
            showOriginalHtml = false,
            currentHtmlContent = null
        )
    )

    override val state: StateFlow<ScraperState> get() = _state

    // Handler methods remain empty - not needed in preview.
    override fun onPagesChanged(pages: String) {}
    override fun onStartScrapingClicked() {}
    override fun onParseAndSaveClicked() {}
    override fun onRunAnalyzerPipelineClicked() {}
    override fun onOpenDirectoryClicked() {}
    override fun onNavigateToImporterClicked() {}
    override fun onBackClicked() {}
    override fun onOverwriteConfirmed() {}
    override fun onContinueConfirmed() {}
    override fun onDialogDismissed() {}
    override fun onPreviewImportClicked() {}
    override fun onImportFileSelectionChanged(filePath: String, selected: Boolean) {}
    override fun onConfirmImportClicked() {}
    override fun onCancelImportClicked() {}
    override fun onDismissImportResults() {}

    override fun onPromptPreviewClicked(prompt: PromptData) {}
    override fun onAcceptPrompt() {}
    override fun onSkipPrompt() {}
    override fun onNextPrompt() {}
    override fun onPrevPrompt() {}
    override fun onClosePreview() {}
    override fun onToggleHtmlView() {}

    override suspend fun getPromptsStats(): PromptsStats {
        return PromptsStats(localCount = 12, syncedCount = 45)
    }

    override suspend fun syncWithRemote(): SyncResult {
        // For preview, return success with dummy prompts
        return SyncResult.Success(emptyList())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScraperScreen(
    component: ScraperComponent,
) {
    val state by component.state.collectAsState()
    val logListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var isSyncing by remember { mutableStateOf(false) }

    // Auto-scroll logs
    LaunchedEffect(state.logs.size) {
        if (state.logs.isNotEmpty()) {
            logListState.animateScrollToItem(state.logs.size - 1)
        }
    }

    // Check if pipeline is running
    val isPipelineRunning = state.pipelineStage != PipelineStage.IDLE &&
            state.pipelineStage != PipelineStage.COMPLETED &&
            state.pipelineStage != PipelineStage.ERROR

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Скрапер") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets.systemBars
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // --- SYNC STATUS PANEL ---
            if (state.lastSyncTime != null || state.localPromptsCount > 0 || state.syncedPromptsCount > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Статус синхронизации",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Локальных: ${state.localPromptsCount}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "Синхронизированных: ${state.syncedPromptsCount}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Column {
                                Text(
                                    text = "Последняя синхронизация:",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = state.lastSyncTime ?: "ещё не было",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (state.lastSyncTime == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // --- CONTROL PANEL ---
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
                    enabled = state.savedHtmlFiles.isNotEmpty() && !state.inProgress,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    if (state.inProgress) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Анализирую...")
                    } else {
                        Icon(Icons.Default.Analytics, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Запустить Анализ")
                    }
                }

                // --- Processing Stats Panel (after Parse button) ---
                state.processingResult?.let { result ->
                    Spacer(Modifier.height(8.dp))
                    ProcessingStatsView(result = result)
                }

                // --- NEW: Analyzer Pipeline Button ---
                Button(
                    onClick = component::onRunAnalyzerPipelineClicked,
                    enabled = state.savedHtmlFiles.isNotEmpty() && !isPipelineRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Анализ и экспорт")
                }

                // --- NEW: Sync Button ---
                Button(
                    onClick = {
                        scope.launch {
                            isSyncing = true
                            try {
                                val result = component.syncWithRemote()
                                when (result) {
                                    is com.arny.aiprompts.domain.repositories.SyncResult.Success -> {
                                        val message = if (result.prompts.isNotEmpty()) {
                                            "Синхронизировано: ${result.prompts.size} промптов"
                                        } else {
                                            "Новых промптов нет"
                                        }
                                        snackbarHostState.showSnackbar(message)
                                    }
                                    is com.arny.aiprompts.domain.repositories.SyncResult.Error -> {
                                        snackbarHostState.showSnackbar("Ошибка: ${result.message.toPlainString()}")
                                    }
                                    com.arny.aiprompts.domain.repositories.SyncResult.TooSoon -> {
                                        snackbarHostState.showSnackbar("Синхронизация возможна только раз в 10 минут")
                                    }
                                }
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("Ошибка синхронизации: ${e.message ?: "Неизвестная ошибка"}")
                            } finally {
                                isSyncing = false
                            }
                        }
                    },
                    enabled = !isSyncing && !state.inProgress && !isPipelineRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Синхронизировать")
                    }
                }

                Spacer(Modifier.weight(1f))

                // --- NEW: Preview Import Button ---
                Button(
                    onClick = component::onPreviewImportClicked,
                    enabled = !state.inProgress && !isPipelineRunning,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Предпросмотр и импорт")
                }

                Button(
                    onClick = component::onNavigateToImporterClicked,
                    enabled = state.savedHtmlFiles.isNotEmpty()
                ) {
                    Text("Перейти к Ассистенту")
                }
            }

            // --- NEW: Pipeline Progress Panel ---
            AnimatedVisibility(visible = isPipelineRunning || state.pipelineStage == PipelineStage.COMPLETED) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when (state.pipelineStage) {
                            PipelineStage.ERROR -> MaterialTheme.colorScheme.errorContainer
                            PipelineStage.COMPLETED -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = when (state.pipelineStage) {
                                    PipelineStage.LOADING_INDEX -> "Загрузка индекса..."
                                    PipelineStage.MAPPING_FILES -> "Сопоставление файлов..."
                                    PipelineStage.DEDUPLICATING -> "Дедупликация..."
                                    PipelineStage.PARSING -> "Парсинг страниц..."
                                    PipelineStage.EXPORTING -> "Экспорт по категориям..."
                                    PipelineStage.COMPLETED -> "Готово!"
                                    PipelineStage.ERROR -> "Ошибка"
                                    PipelineStage.IDLE -> "Готов"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )

                            if (isPipelineRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }

                        if (state.pipelineStage == PipelineStage.PARSING && state.pipelineTotalPosts > 0) {
                            LinearProgressIndicator(
                                progress = { state.pipelineProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                            )
                            Text(
                                text = "${state.pipelineCurrentPost} (${(state.pipelineProgress * 100).toInt()}%)",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        // Pipeline logs
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                        ) {
                            items(state.pipelineLogs.takeLast(10)) { log ->
                                Text(
                                    text = log,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            // --- NEW: Pipeline Results Panel ---
            AnimatedVisibility(visible = state.pipelineResult != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Результаты Pipeline:",
                            style = MaterialTheme.typography.titleMedium
                        )

                        state.pipelineResult?.let { result ->
                            Spacer(Modifier.height(8.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                StatItem("Всего обработано", result.totalProcessed.toString())
                                StatItem("Новых промптов", result.newPrompts.toString())
                                StatItem("Дубликатов", result.skippedDuplicates.toString())
                                StatItem("Ошибок", result.errors.toString())
                                StatItem("Время", "${result.durationMs / 1000}с")
                            }

                            if (result.categoryBreakdown.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "По категориям:",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    result.categoryBreakdown.forEach { (category, count) ->
                                        AssistChip(
                                            onClick = { },
                                            label = { Text("$category: $count") }
                                        )
                                    }
                                }
                            }

                            if (result.outputFiles.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Экспортированные файлы:",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                LazyColumn(
                                    modifier = Modifier.height(80.dp)
                                ) {
                                    items(result.outputFiles) { file ->
                                        Text(
                                            text = "• ${File(file).name}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- IMPORT PANEL ---
            AnimatedVisibility(
                visible = state.availableImportFiles.isNotEmpty() ||
                          state.isImporting ||
                          state.importResult != null
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Импорт в prompts/{category}/",
                                style = MaterialTheme.typography.titleMedium
                            )

                            if (state.isImporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }

                        // Import progress
                        if (state.isImporting && state.importProgress > 0) {
                            LinearProgressIndicator(
                                progress = { state.importProgress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Available files list
                        if (state.availableImportFiles.isNotEmpty() && !state.isImporting) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 150.dp)
                            ) {
                                // Group by category
                                state.availableImportFiles.groupBy { it.category }.forEach { (category, files) ->
                                    item {
                                        Text(
                                            text = category,
                                            style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    }
                                    items(files) { file ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(start = 8.dp)
                                        ) {
                                            Checkbox(
                                                checked = file.filePath in state.selectedImportFiles,
                                                onCheckedChange = {
                                                    component.onImportFileSelectionChanged(file.filePath, it)
                                                }
                                            )
                                            Text(
                                                text = "${file.fileName}: ${file.promptCount} промптов",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Import result
                        state.importResult?.let { result ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (result.success) {
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.errorContainer
                                    }
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (result.success) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Text("Импорт завершен успешно!", style = MaterialTheme.typography.labelMedium)
                                        } else {
                                            Text("Импорт завершен с ошибками", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Text(
                                            text = "Импортировано: ${result.importedCount}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            text = "Пропущено: ${result.skippedCount}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            text = "Ошибок: ${result.errors.size}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }

                                    if (result.categoryBreakdown.isNotEmpty()) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.padding(top = 4.dp)
                                        ) {
                                            result.categoryBreakdown.forEach { (category, count) ->
                                                AssistChip(
                                                    onClick = { },
                                                    label = { Text("$category: $count") },
                                                    modifier = Modifier.height(24.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (result.errors.isNotEmpty()) {
                                        LazyColumn(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 60.dp)
                                        ) {
                                            items(result.errors.take(5)) { error ->
                                                Text(
                                                    text = "• $error",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                            if (result.errors.size > 5) {
                                                item {
                                                    Text(
                                                        text = "... и еще ${result.errors.size - 5} ошибок",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Import buttons
                        if (!state.isImporting) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = component::onConfirmImportClicked,
                                    enabled = state.selectedImportFiles.isNotEmpty()
                                ) {
                                    Text("Импортировать выбранные")
                                }

                                if (state.importResult != null) {
                                    OutlinedButton(
                                        onClick = {
                                            // Can't modify state directly from Compose
                                            // The user can click Preview Import again to reset
                                        }
                                    ) {
                                        Text("Скрыть результат")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- RESULTS PANELS ---
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Logs column
                LazyColumn(modifier = Modifier.weight(1f), state = logListState) {
                    items(state.logs) { log ->
                        Text(log, style = MaterialTheme.typography.bodySmall)
                    }
                }

                // HTML files column
                LazyColumn(modifier = Modifier.weight(1f)) {
                    item {
                        Text(
                            "Сохраненные HTML (${state.savedHtmlFiles.size} шт.):",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    items(state.savedHtmlFiles) { file ->
                        Text("• ${java.io.File(file).name}")
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
                            "• ${java.io.File(file).name}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Parsed prompts column
                LazyColumn(modifier = Modifier.weight(1f)) {
                    item {
                        Text(
                            "Спарсенные промпты (${state.parsedPrompts.size}):",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    items(state.parsedPrompts) { prompt ->
                        ClickableText(
                            text = AnnotatedString("• ${prompt.title}"),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (state.parsedPrompts.indexOf(prompt) == state.currentPreviewIndex) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            ),
                            onClick = { component.onPromptPreviewClicked(prompt) },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    // --- PROMPT PREVIEW DIALOG ---
    if (state.isPreviewMode && state.previewPrompts.isNotEmpty()) {
        val currentPrompt = state.previewPrompts.getOrNull(state.currentPreviewIndex)
        if (currentPrompt != null) {
            PromptPreviewDialog(
                prompt = currentPrompt,
                currentIndex = state.currentPreviewIndex,
                totalCount = state.previewPrompts.size,
                acceptedCount = state.acceptedCount,
                skippedCount = state.skippedCount,
                hasPrev = state.currentPreviewIndex > 0,
                hasNext = state.currentPreviewIndex < state.previewPrompts.size - 1,
                showOriginalHtml = state.showOriginalHtml,
                htmlContent = state.currentHtmlContent,
                onAccept = component::onAcceptPrompt,
                onSkip = component::onSkipPrompt,
                onPrev = component::onPrevPrompt,
                onNext = component::onNextPrompt,
                onToggleHtmlView = component::onToggleHtmlView,
                onClose = component::onClosePreview
            )
        }
    }

    // --- DIALOG ---
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

/**
 * Small stat item for pipeline results.
 */
@Composable
private fun StatItem(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/**
 * Statistics view for ProcessScrapedPostsUseCase.ProcessingResult.
 * Shows detailed breakdown: total, duplicates, low quality, errors, and new prompts.
 */
@Composable
fun ProcessingStatsView(result: ProcessScrapedPostsUseCase.ProcessingResult) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(
            text = "Результаты анализа:",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))

        StatRow("Всего обработано:", "${result.totalInput}", FontWeight.Normal)
        StatRow("Пропущено (дубли):", "${result.alreadyExists}", FontWeight.Normal, MaterialTheme.colorScheme.secondary)
        StatRow("Отсеяно (низкое качество):", "${result.lowQuality}", FontWeight.Normal, MaterialTheme.colorScheme.secondary)
        StatRow("Ошибки парсинга:", "${result.parseErrors}", FontWeight.Normal, MaterialTheme.colorScheme.error)

        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

        StatRow(
            "Готово к импорту:",
            "+${result.success}",
            FontWeight.Bold,
            MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    fontWeight: FontWeight = FontWeight.Normal,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = fontWeight)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontWeight = fontWeight
        )
    }
}

/**
 * Section card for 3-stage workflow UI.
 */
@Composable
fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            content()
        }
    }
}

/**
 * Log console widget for displaying scraper logs.
 * Wrapped in SelectionContainer to allow copying logs.
 */
@Composable
fun LogConsole(logs: List<String>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.8f))
    ) {
        SelectionContainer {
            LazyColumn(
                modifier = Modifier.padding(8.dp),
                reverseLayout = true // Newest logs at bottom
            ) {
                items(logs.reversed()) { log ->
                    Text(
                        text = log,
                        style = MaterialTheme.typography.labelSmall,
                        color = androidx.compose.ui.graphics.Color.Green,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
