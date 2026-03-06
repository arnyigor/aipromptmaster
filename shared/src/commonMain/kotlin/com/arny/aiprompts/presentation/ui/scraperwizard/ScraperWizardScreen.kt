package com.arny.aiprompts.presentation.ui.scraperwizard

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arny.aiprompts.domain.model.PromptData
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScraperWizardScreen(
    component: ScraperWizardComponent,
    onNavigateBack: () -> Unit = {}
) {
    val state by component.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var previewPrompt by remember { mutableStateOf<PromptData?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Мастер импорта промптов") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        component.onCopyErrorsToClipboard()
                        scope.launch {
                            snackbarHostState.showSnackbar("Ошибки скопированы в буфер обмена")
                        }
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Копировать ошибки")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            StepIndicator(currentStep = state.currentStep)

            Spacer(modifier = Modifier.height(16.dp))

            Box(modifier = Modifier.weight(1f)) {
                when (state.currentStep) {
                    WizardStep.PAGE_INPUT -> PageInputStep(component, state)
                    WizardStep.RESOLVING -> ResolvingStep(component, state)
                    WizardStep.ANALYSIS -> AnalysisStep(
                        component = component,
                        state = state,
                        onPromptClick = { previewPrompt = it }
                    )

                    WizardStep.IMPORT -> ImportStep(
                        component = component,
                        state = state,
                        onPromptClick = { previewPrompt = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            NavigationButtons(component, state, onNavigateBack)
        }
    }

    // Ошибки
    LaunchedEffect(state.errorMessages) {
        state.errorMessages.lastOrNull()?.let { error ->
            snackbarHostState.showSnackbar(
                message = "${error.message}${error.details?.let { ": $it" } ?: ""}",
                actionLabel = "OK",
                duration = SnackbarDuration.Long
            )
            component.onDismissError(error)
        }
    }

    // Диалог предпросмотра с полным текстом промпта
    previewPrompt?.let { prompt ->
        PromptPreviewDialog(
            prompt = prompt,
            onDismiss = { previewPrompt = null }
        )
    }
}

@Composable
private fun StepIndicator(currentStep: WizardStep) {
    val steps = listOf(
        "Страницы" to WizardStep.PAGE_INPUT,
        "Индекс" to WizardStep.RESOLVING,
        "Анализ" to WizardStep.ANALYSIS,
        "Импорт" to WizardStep.IMPORT
    )
    val currentIndex = steps.indexOfFirst { it.second == currentStep }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        steps.forEachIndexed { index, (label, _) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = CircleShape,
                    color = when {
                        index <= currentIndex -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (index < currentIndex) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(
                                (index + 1).toString(),
                                color = if (index == currentIndex) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ========== Шаг 1: Загрузка стр.1 + ввод диапазона ==========

@Composable
private fun PageInputStep(component: ScraperWizardComponent, state: ScraperWizardState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Статус первой страницы
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (state.page1Exists) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (state.page1Exists) {
                    Text("Первая страница: загружена", style = MaterialTheme.typography.titleMedium)
                    state.pageCheckResult?.let { check ->
                        if (check.existingFileCount > 0) {
                            Text(
                                "Всего скачано страниц: ${check.existingFileCount}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                } else {
                    Text("Первая страница: не найдена", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Необходимо скачать первую страницу для получения индекса промптов",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (!state.page1Exists) {
            Button(
                onClick = component::onCheckPagesClicked,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isDownloading
            ) {
                if (state.isDownloading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Скачивание...")
                } else {
                    Text("Скачать первую страницу")
                }
            }
        }

        // Ввод дополнительных страниц
        OutlinedTextField(
            value = state.pagesInput,
            onValueChange = component::onPagesInputChanged,
            label = { Text("Диапазон страниц для скачивания") },
            placeholder = { Text("Например: 1-10, 15, 22") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = {
                Text("Укажите страницы для дополнительной загрузки (через запятую или диапазон)")
            }
        )

        if (state.isDownloading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        LogsPanel(state.logs)
    }
}

// ========== Шаг 2: Парсинг индекса + поиск постов ==========

@Composable
private fun ResolvingStep(component: ScraperWizardComponent, state: ScraperWizardState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.resolvingProgress.isRunning) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Парсинг индекса и поиск постов...", style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        progress = {
                            if (state.resolvingProgress.totalLinks > 0) {
                                state.resolvingProgress.resolved.toFloat() / state.resolvingProgress.totalLinks
                            } else 0f
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "${state.resolvingProgress.resolved} / ${state.resolvingProgress.totalLinks}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    state.resolvingProgress.currentPostId?.let { postId ->
                        Text("postId: $postId", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else if (state.parsedIndex != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Индекс загружен", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    StatRow("Ссылок в индексе", state.parsedIndex.links.size.toString())
                    StatRow("Найдено постов", state.postLocations.size.toString())
                    val notFound = state.parsedIndex.links.size - state.postLocations.size
                    if (notFound > 0) {
                        StatRow("Не найдено", notFound.toString())
                    }

                    val categories = state.parsedIndex.links.groupBy { it.category ?: "Без категории" }
                    if (categories.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Категории:", style = MaterialTheme.typography.labelMedium)
                        categories.forEach { (cat, links) ->
                            Text("  $cat: ${links.size}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Индекс не загружен", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = component::onStartResolving) {
                        Text("Запустить парсинг индекса")
                    }
                }
            }
        }

        if (state.isDownloading) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Загрузка дополнительных страниц...", style = MaterialTheme.typography.titleSmall)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        LogsPanel(state.logs)
    }
}

// ========== Шаг 3: Анализ ==========

@Composable
private fun AnalysisStep(
    component: ScraperWizardComponent,
    state: ScraperWizardState,
    onPromptClick: (PromptData) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.analysisProgress.isRunning) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Анализ промптов...", style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(
                        progress = {
                            if (state.analysisProgress.totalPrompts > 0)
                                state.analysisProgress.processed.toFloat() / state.analysisProgress.totalPrompts
                            else 0f
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${state.analysisProgress.processed} / ${state.analysisProgress.totalPrompts}")
                    Text(
                        "Найдено новых: ${state.analysisProgress.newPrompts}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Статистика
        if (state.analysisProgress.totalPrompts > 0) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Результат анализа", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    StatRow("Всего в индексе", state.analysisProgress.totalPrompts.toString())
                    StatRow("Новых промптов", state.analysisProgress.newPrompts.toString())
                    StatRow("Дубликатов", state.analysisProgress.duplicates.toString())
                    StatRow("Ошибок", state.analysisProgress.errors.toString())
                }
            }
        }

        // Список промптов с превью
        if (state.indexPrompts.isNotEmpty()) {
            Text(
                "Промпты (${state.indexPrompts.size}) — нажмите для полного просмотра",
                style = MaterialTheme.typography.titleMedium
            )
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(state.indexPrompts, key = { it.id }) { prompt ->
                    PromptPreviewItem(prompt = prompt, onClick = { onPromptClick(prompt) })
                }
            }
        } else if (!state.analysisProgress.isRunning) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Нажмите 'Запустить' для извлечения промптов из индекса")
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = component::onStartAnalysisClicked) {
                        Text("Запустить анализ")
                    }
                }
            }
        }

        LogsPanel(state.logs)
    }
}

@Composable
private fun PromptPreviewItem(prompt: PromptData, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    prompt.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            // Показываем начало контента промпта
            val contentPreview = prompt.variants.firstOrNull()?.content?.take(120)
            if (!contentPreview.isNullOrBlank()) {
                Text(
                    contentPreview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    prompt.category,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (prompt.tags.isNotEmpty()) {
                    Text(
                        prompt.tags.take(3).joinToString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ========== Шаг 4: Импорт ==========

@Composable
private fun ImportStep(
    component: ScraperWizardComponent,
    state: ScraperWizardState,
    onPromptClick: (PromptData) -> Unit
) {
    val promptsToShow = state.indexPrompts

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (promptsToShow.isEmpty()) {
            Text("Нет промптов для импорта. Вернитесь к шагу анализа.")
            return
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = state.selectedPrompts.size == promptsToShow.size && promptsToShow.isNotEmpty(),
                onCheckedChange = component::onSelectAllPrompts
            )
            Text("Выбрать все (${promptsToShow.size})")
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(promptsToShow, key = { it.id }) { prompt ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onPromptClick(prompt) }
                ) {
                    Checkbox(
                        checked = prompt.id in state.selectedPrompts,
                        onCheckedChange = { component.onTogglePromptSelection(prompt.id, it) }
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(
                            prompt.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            prompt.category,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Icon(
                        Icons.Default.Visibility,
                        contentDescription = "Просмотр",
                        modifier = Modifier.size(20.dp).padding(end = 4.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Button(
            onClick = component::onImportSelectedClicked,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.selectedPrompts.isNotEmpty() && !state.analysisProgress.isRunning
        ) {
            Text("Импортировать выбранные (${state.selectedPrompts.size})")
        }

        state.importResult?.let { result ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (result.success) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        if (result.success) "Импорт завершён" else "Импорт с ошибками",
                        style = MaterialTheme.typography.titleSmall
                    )
                    StatRow("Импортировано", result.importedCount.toString())
                    if (result.errors.isNotEmpty()) {
                        Text("Ошибки:", style = MaterialTheme.typography.bodyMedium)
                        result.errors.take(5).forEach { Text("  $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }

        LogsPanel(state.logs)
    }
}

// ========== Навигационные кнопки ==========

@Composable
private fun NavigationButtons(
    component: ScraperWizardComponent,
    state: ScraperWizardState,
    onNavigateBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        OutlinedButton(
            onClick = {
                if (state.currentStep == WizardStep.PAGE_INPUT) onNavigateBack()
                else component.onBackToPreviousStep()
            }
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Назад")
        }

        if (state.currentStep == WizardStep.IMPORT) {
            Button(onClick = component::onFinish) {
                Text("Завершить")
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.Check, contentDescription = null)
            }
        } else {
            val canProceed = when (state.currentStep) {
                WizardStep.PAGE_INPUT -> state.page1Exists && !state.isDownloading
                WizardStep.RESOLVING -> !state.resolvingProgress.isRunning && state.parsedIndex != null && !state.isDownloading
                WizardStep.ANALYSIS -> !state.analysisProgress.isRunning && state.indexPrompts.isNotEmpty()
                else -> true
            }

            Button(
                onClick = component::onNextStep,
                enabled = canProceed
            ) {
                Text("Далее")
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }
}

// ========== Диалог полного предпросмотра промпта ==========

/**
 * Clean HTML content for display purposes.
 */
private fun cleanContentForDisplay(html: String): String {
    if (html.isBlank()) return html

    // Step 1: Replace common block elements with newlines first
    var text = html
        .replace(Regex("""(?i)<br\s*/?>"""), "\n")
        .replace(Regex("""(?i)</p>"""), "\n\n")
        .replace(Regex("""(?i)</div>"""), "\n")
        .replace(Regex("""(?i)</li>"""), "\n")
        .replace(Regex("""(?i)</tr>"""), "\n")

    // Step 2: Remove all remaining HTML tags
    text = text.replace(Regex("""<[^>]+>"""), "")

    // Step 3: Clean up HTML entities
    text = text
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&rsquo;", "'")
        .replace("&lsquo;", "'")
        .replace("&ndash;", "-")
        .replace("&mdash;", "-")

    // Step 4: Clean up whitespace
    text = text
        .replace(Regex("""\n\s+\n"""), "\n\n")
        .replace(Regex("""[ \t]+"""), " ")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()

    return text
}

@Composable
private fun PromptPreviewDialog(
    prompt: PromptData,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = prompt.title,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Мета-информация
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Category,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Категория: ${prompt.category}", style = MaterialTheme.typography.bodyMedium)
                }

                if (prompt.tags.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.AutoMirrored.Filled.Label,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Теги: ${prompt.tags.joinToString()}", style = MaterialTheme.typography.bodySmall)
                    }
                }

                HorizontalDivider()

                // Полный текст промпта (очищенный от HTML)
                Text("Текст промпта:", style = MaterialTheme.typography.labelMedium)

                val rawContent = prompt.variants.firstOrNull()?.content
                if (!rawContent.isNullOrBlank()) {
                    val cleanedContent = cleanContentForDisplay(rawContent)
                    SelectionContainer {
                        Text(
                            cleanedContent,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    Text(
                        "Текст промпта не найден",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // Описание (если отличается от контента, очищенное от HTML)
                if (!prompt.description.isNullOrBlank() && prompt.description != rawContent?.take(300)) {
                    HorizontalDivider()
                    Text("Описание:", style = MaterialTheme.typography.labelMedium)
                    Text(cleanContentForDisplay(prompt.description), style = MaterialTheme.typography.bodySmall)
                }

                // Источник
                if (prompt.source.isNotBlank()) {
                    HorizontalDivider()
                    Text("Источник:", style = MaterialTheme.typography.labelMedium)
                    Text(
                        prompt.source,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}

// ========== Вспомогательные компоненты ==========


@Composable
private fun LogsPanel(logs: List<String>) {
    if (logs.isNotEmpty()) {
        val clipboardManager = LocalClipboardManager.current

        Card(
            modifier = Modifier
                .fillMaxWidth()
                // Эластичная высота: от 150dp минимум, до 350dp максимум
                .heightIn(min = 150.dp, max = 350.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column {
                // Шапка логгера с кнопкой "Копировать все"
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Системный журнал:",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    IconButton(
                        onClick = {
                            val fullLog = logs.joinToString("\n")
                            clipboardManager.setText(AnnotatedString(fullLog))
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, "Копировать лог", modifier = Modifier.size(16.dp))
                    }
                }

                HorizontalDivider()

                // Обертка для выделения текста мышью
                SelectionContainer {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            // Добавляем горизонтальный скролл для длинных StackTrace
                            .horizontalScroll(rememberScrollState())
                    ) {
                        items(logs.takeLast(100)) { log ->
                            Text(
                                text = log,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace // Моноширинный шрифт для логов
                                ),
                                // Запрещаем перенос слов, чтобы не ломать логи
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}
