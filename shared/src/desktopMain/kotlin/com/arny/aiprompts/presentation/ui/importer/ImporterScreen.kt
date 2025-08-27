package com.arny.aiprompts.presentation.ui.importer

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arny.aiprompts.data.parser.BlockType
import com.arny.aiprompts.data.parser.ParsedPostBlock
import com.arny.aiprompts.data.parser.PostStructureParser
import com.arny.aiprompts.domain.model.FileAttachment
import com.arny.aiprompts.domain.model.FileType
import com.arny.aiprompts.domain.model.RawPostData
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImporterScreen(component: ImporterComponent) {
    val state by component.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Ассистент Импорта")
                        Text(
                            "${state.filteredPosts.size} из ${state.rawPosts.size} постов",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = component::onBackClicked) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    // Кнопка превью
                    IconButton(onClick = component::onTogglePreview) {
                        Icon(
                            if (state.showPreview) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (state.showPreview) "Скрыть превью" else "Показать превью"
                        )
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = remember { SnackbarHostState() }) {
                // Обработка ошибок и успехов
                state.error?.let {
                    Snackbar(
                        action = {
                            TextButton(onClick = component::onDismissError) {
                                Text("OK")
                            }
                        }
                    ) {
                        Text(it)
                    }
                }
                state.successMessage?.let {
                    Snackbar(
                        action = {
                            TextButton(onClick = component::onDismissSuccess) {
                                Text("OK")
                            }
                        }
                    ) {
                        Text(it)
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Прогресс бар
            if (state.isLoading || state.progress.stepProgress > 0f) {
                LinearProgressIndicator(
                    progress = { state.progress.stepProgress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "${state.progress.currentStep} - ${state.progress.processedItems}/${state.progress.totalItems}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Основной контент
            if (state.isLoading && state.rawPosts.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Загрузка и анализ файлов...")
                    }
                }
            } else {
                ImprovedImporterLayout(state, component)
            }
        }
    }
}

// --- УЛУЧШЕННЫЙ LAYOUT ---
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImprovedImporterLayout(state: ImporterState, component: ImporterComponent) {
    Row(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Левая панель - Список постов с фильтрами
        PostListWithFiltersPanel(
            modifier = Modifier.weight(1f),
            state = state,
            component = component
        )

        // Центральная панель - Редактор
        EditorPanel(
            modifier = Modifier.weight(2f),
            state = state,
            component = component
        )

        // Правая панель - Действия и превью
        SidePanel(
            modifier = Modifier.width(300.dp),
            state = state,
            component = component
        )
    }
}

// --- СПИСОК ПОСТОВ С ФИЛЬТРАМИ ---
@Composable
private fun PostListWithFiltersPanel(
    modifier: Modifier,
    state: ImporterState,
    component: ImporterComponent
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Заголовок с количеством
        Text(
            "Найденные посты (${state.filteredPosts.size}/${state.rawPosts.size})",
            style = MaterialTheme.typography.titleMedium
        )

        // Панель фильтров
        FilterPanel(state, component)

        // Список постов
        LazyColumn(
            modifier = Modifier.fillMaxHeight()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(state.filteredPosts, key = { it.postId }) { post ->
                PostListItem(
                    post = post,
                    isSelected = state.selectedPostId == post.postId,
                    isReadyToImport = post.postId in state.postsToImport,
                    isExpanded = post.postId in state.expandedPostIds,
                    editedData = state.editedData[post.postId],
                    state = state,
                    component = component,
                    onClick = { component.onPostClicked(post.postId) },
                    onToggleImport = { component.onTogglePostForImport(post.postId, it) },
                    onToggleExpansion = { component.onTogglePostExpansion(post.postId) }
                )
            }
        }
    }
}

// --- ПАНЕЛЬ ФИЛЬТРОВ ---
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterPanel(state: ImporterState, component: ImporterComponent) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Фильтры", style = MaterialTheme.typography.titleSmall)

            // Поиск
            OutlinedTextField(
                value = state.filters.searchQuery,
                onValueChange = component::onSearchQueryChanged,
                label = { Text("Поиск по постам") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, "Поиск") }
            )

            // Переключатели фильтров
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.filters.showOnlyReady,
                    onClick = {
                        component.onFilterChanged(state.filters.copy(showOnlyReady = !state.filters.showOnlyReady))
                    },
                    label = { Text("Готовые") }
                )
                FilterChip(
                    selected = state.filters.showOnlyLikelyPrompts,
                    onClick = {
                        component.onFilterChanged(state.filters.copy(showOnlyLikelyPrompts = !state.filters.showOnlyLikelyPrompts))
                    },
                    label = { Text("Промпты") }
                )
            }
        }
    }
}

// --- ЭЛЕМЕНТ СПИСКА ПОСТОВ ---
@Composable
private fun PostListItem(
    post: RawPostData,
    isSelected: Boolean,
    isReadyToImport: Boolean,
    isExpanded: Boolean,
    editedData: EditedPostData?,
    state: ImporterState,
    component: ImporterComponent,
    onClick: () -> Unit,
    onToggleImport: (Boolean) -> Unit,
    onToggleExpansion: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Чекбокс для импорта
                Checkbox(
                    checked = isReadyToImport,
                    onCheckedChange = onToggleImport
                )

                // Статус иконки
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (editedData?.content?.isNotBlank() == true) {
                        Icon(
                            Icons.Default.CheckCircle,
                            "Данные готовы",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    } else if (post.attachments.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Attachment,
                                "Есть вложения (${post.attachments.size})",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                "${post.attachments.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(start = 2.dp)
                            )
                        }
                    } else if (post.isLikelyPrompt) {
                        Icon(
                            Icons.AutoMirrored.Filled.HelpOutline,
                            "Вероятно промпт",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Информация о посте
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        post.author.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                    Text(
                        post.postId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                // Кнопка развертывания
                IconButton(onClick = onToggleExpansion, modifier = Modifier.size(24.dp)) {
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        "Развернуть/свернуть"
                    )
                }
            }

            // Развернутый контент
            if (isExpanded) {
                Spacer(Modifier.height(8.dp))
                SelectionContainer {
                    Text(
                        text = formatTextWithMarkdown(post.fullHtmlContent.take(200)) + if (post.fullHtmlContent.length > 200) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight
                    )
                }

                // Вложения
                if (post.attachments.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Attachment,
                                    "Вложения",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "Файлы (${post.attachments.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            AttachmentList(
                                attachments = post.attachments,
                                postId = post.postId,
                                downloadedFiles = state.downloadedFiles,
                                expandedFileIds = state.expandedFileIds,
                                onDownloadFile = { url, filename -> component.onDownloadFile(url, filename) },
                                onPreviewFile = { url, filename -> component.onPreviewFile(url, filename) },
                                onOpenFileInSystem = { url, filename -> component.onOpenFileInSystem(url, filename) },
                                onToggleExpansion = { postId, attachmentUrl ->
                                    component.onToggleFileExpansion(
                                        postId,
                                        attachmentUrl
                                    )
                                }
                            )
                        }
                    }
                }

                // Действия с блоками
                if (editedData != null) {
                    Spacer(Modifier.height(8.dp))
                    ActionButtonsRow(post.fullHtmlContent, component)
                }

                // Кнопка открытия поста в браузере
                if (post.postUrl != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { component.onOpenPostInBrowser(post.postUrl) },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.OpenInNew,
                            "Открыть пост в браузере",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Открыть оригинальный пост", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

// --- ЦЕНТРАЛЬНАЯ ПАНЕЛЬ РЕДАКТОРА ---
@Composable
private fun EditorPanel(
    modifier: Modifier,
    state: ImporterState,
    component: ImporterComponent
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Заголовок панели
        Text("Редактор промпта", style = MaterialTheme.typography.titleMedium)

        if (state.currentEditedData == null) {
            // Заглушка когда ничего не выбрано
            EmptyEditorState()
        } else {
            // Редактор с вкладками
            EditorWithTabs(state, component)
        }
    }
}

// --- ЗАГЛУШКА РЕДАКТОРА ---
@Composable
private fun EmptyEditorState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Edit,
                "Редактор",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Выберите пост из списка для редактирования",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// --- РЕДАКТОР С ВКЛАДКАМИ ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorWithTabs(state: ImporterState, component: ImporterComponent) {
    val editedData = state.currentEditedData ?: return

    val tabs = listOf("Превью", "Структура", "Промпт")
    var selectedTabIndex by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTabIndex) {
            0 -> PreviewTab(editedData, state, component)
            1 -> StructureEditorTab(state, component)
            2 -> BasicEditorTab(state, editedData, component)
        }
    }
}

// --- СПИСОК ВЛОЖЕНИЙ ---
@Composable
private fun AttachmentList(
    attachments: List<FileAttachment>,
    postId: String,
    downloadedFiles: Map<String, DownloadedFile>,
    expandedFileIds: Set<String>,
    onDownloadFile: (String, String) -> Unit,
    onPreviewFile: (String, String) -> Unit,
    onOpenFileInSystem: (String, String) -> Unit,
    onToggleExpansion: (String, String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Вложения (${attachments.size})",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )

        attachments.forEach { attachment ->
            AttachmentItem(
                attachment = attachment,
                postId = postId,
                downloadedFile = downloadedFiles[attachment.url],
                isExpanded = "$postId:${attachment.url}" in expandedFileIds,
                onDownloadFile = onDownloadFile,
                onPreviewFile = onPreviewFile,
                onOpenFileInSystem = onOpenFileInSystem,
                onToggleExpansion = onToggleExpansion
            )
        }
    }
}

// --- ЭЛЕМЕНТ ВЛОЖЕНИЯ ---
@Composable
private fun AttachmentItem(
    attachment: FileAttachment,
    postId: String,
    downloadedFile: DownloadedFile?,
    isExpanded: Boolean,
    onDownloadFile: (String, String) -> Unit,
    onPreviewFile: (String, String) -> Unit,
    onOpenFileInSystem: (String, String) -> Unit,
    onToggleExpansion: (String, String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Иконка типа файла
                FileTypeIcon(attachment.fileType)

                // Информация о файле
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        attachment.filename,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            attachment.fileType.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (attachment.fileSize != null) {
                            Text(
                                attachment.fileSize,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Статус загрузки
                DownloadStatusIndicator(
                    downloadedFile = downloadedFile,
                    onDownload = { onDownloadFile(attachment.url, attachment.filename) },
                    onPreview = { onPreviewFile(attachment.url, attachment.filename) },
                    onOpenInSystem = { onOpenFileInSystem(attachment.url, attachment.filename) }
                )

                // Кнопка развертывания (для текстовых файлов)
                if (attachment.fileType == FileType.TEXT && downloadedFile?.state == DownloadState.DOWNLOADED) {
                    IconButton(
                        onClick = { onToggleExpansion(postId, attachment.url) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            "Развернуть содержимое файла"
                        )
                    }
                }
            }

            // Развернутое содержимое текстового файла
            if (isExpanded && attachment.fileType == FileType.TEXT && downloadedFile?.content != null) {
                Spacer(Modifier.height(8.dp))
                SelectionContainer {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            downloadedFile.content!!,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

// --- НОРМАЛИЗАЦИЯ ТЕКСТА ---
private fun normalizeText(text: String): String {
    return text
        // Обрабатываем экранированные переводы строк
        .replace("\\n", "\n")
        .replace("\\r", "")
        // Нормализуем множественные переводы строк (заменяем 3+ на 2)
        .replace(Regex("\\n{3,}"), "\n\n")
        // Убираем лишние пробелы в начале и конце строк, но сохраняем пустые строки
        .lines()
        .map { it.trimEnd() }
        .joinToString("\n")
        .trim()
}

// --- ФОРМАТИРОВАНИЕ ТЕКСТА С МАРКДАУН ---
private fun formatTextWithMarkdown(text: String): String {
    return normalizeText(text)
        // Добавляем переводы строк перед заголовками
        .replace(Regex("(?<!\\n)\\n#"), "\n\n#")
        .replace(Regex("(?<!\\n)\\n\\*\\*"), "\n\n**")
        // Простая обработка Markdown
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "[$1]") // Жирный текст -> в скобках
        .replace(Regex("\\*(.+?)\\*"), "$1") // Курсив -> обычный текст
        .replace(Regex("# (.+)"), "$1") // Заголовки -> обычный текст
        .replace(Regex("- (.+)"), "• $1") // Списки
}

// --- РАСШИРЯЕМЫЙ ТЕКСТ С ПРЕВЬЮ ---
@Composable
private fun ExpandableText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    maxLinesCollapsed: Int = 3,
    showExpandedButton: Boolean = true
) {
    var isExpanded by remember { mutableStateOf(false) }
    var needsExpansion by remember(text) {
        mutableStateOf(text.lines().size > maxLinesCollapsed || text.length > 200)
    }

    val formattedText = remember(text) { formatTextWithMarkdown(text) }

    Column(modifier = modifier) {
        Text(
            text = formattedText,
            style = style,
            maxLines = if (isExpanded || !needsExpansion) Int.MAX_VALUE else maxLinesCollapsed,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            lineHeight = style.lineHeight
        )

        if (needsExpansion && showExpandedButton) {
            TextButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = if (isExpanded) "Свернуть" else "Показать полностью",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// --- ВЫПАДАЮЩИЙ СПИСОК КАТЕГОРИЙ ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    selectedCategory: String,
    availableCategories: List<String>,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedCategory,
            onValueChange = {},
            readOnly = true,
            label = { Text("Категория") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier.menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            availableCategories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category) },
                    onClick = {
                        onCategorySelected(category)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

// --- ИКОНКА ТИПА ФАЙЛА ---
@Composable
private fun FileTypeIcon(fileType: FileType) {
    val icon = when (fileType) {
        FileType.TEXT -> Icons.Default.Description
        FileType.IMAGE -> Icons.Default.Image
        FileType.DOCUMENT -> Icons.Default.Article
        FileType.ARCHIVE -> Icons.Default.Archive
        FileType.OTHER -> Icons.Default.InsertDriveFile
    }

    val color = when (fileType) {
        FileType.TEXT -> MaterialTheme.colorScheme.primary
        FileType.IMAGE -> MaterialTheme.colorScheme.secondary
        FileType.DOCUMENT -> MaterialTheme.colorScheme.tertiary
        FileType.ARCHIVE -> MaterialTheme.colorScheme.error
        FileType.OTHER -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Icon(
        icon,
        contentDescription = "Тип файла: ${fileType.name}",
        tint = color,
        modifier = Modifier.size(20.dp)
    )
}

// --- ИНДИКАТОР СТАТУСА ЗАГРУЗКИ ---
@Composable
private fun DownloadStatusIndicator(
    downloadedFile: DownloadedFile?,
    onDownload: () -> Unit,
    onPreview: () -> Unit,
    onOpenInSystem: () -> Unit
) {
    when (downloadedFile?.state) {
        null, DownloadState.NOT_DOWNLOADED -> {
            OutlinedButton(
                onClick = onDownload,
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(
                    Icons.Default.Download,
                    "Скачать файл",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Скачать", style = MaterialTheme.typography.labelSmall)
            }
        }

        DownloadState.DOWNLOADING -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    "Загрузка...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        DownloadState.DOWNLOADED -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedButton(
                    onClick = onPreview,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Default.Visibility,
                        "Просмотреть файл",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Просмотр", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = onOpenInSystem,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Default.OpenInNew,
                        "Открыть в системе",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Открыть", style = MaterialTheme.typography.labelSmall)
                }

                Icon(
                    Icons.Default.CheckCircle,
                    "Файл загружен",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        DownloadState.ERROR -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedButton(
                    onClick = onDownload,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        "Повторить загрузку",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Повторить", style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    downloadedFile.error ?: "Ошибка загрузки",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2
                )
            }
        }
    }
}

// --- ОСНОВНАЯ ВКЛАДКА РЕДАКТОРА ---
@Composable
private fun BasicEditorTab(state: ImporterState, editedData: EditedPostData, component: ImporterComponent) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Заголовок
        OutlinedTextField(
            value = editedData.title,
            onValueChange = { newTitle ->
                component.onEditDataChanged(editedData.copy(title = newTitle))
            },
            label = { Text("Заголовок промпта") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = { Text("Краткое название промпта") }
        )

        // --- ФОРМАТИРОВАНИЕ ДАТЫ ---
        @Composable
        fun formatDate(instant: Instant): String {
            return try {
                val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
                "${localDateTime.dayOfMonth.toString().padStart(2, '0')}.${
                    localDateTime.monthNumber.toString().padStart(2, '0')
                }.${localDateTime.year} ${localDateTime.hour.toString().padStart(2, '0')}:${
                    localDateTime.minute.toString().padStart(2, '0')
                }"
            } catch (e: Exception) {
                instant.toString().substringBefore('T')
            }
        }

        // Описание
        OutlinedTextField(
            value = editedData.description,
            onValueChange = { newDescription ->
                component.onEditDataChanged(editedData.copy(description = newDescription))
            },
            label = { Text("Описание") },
            modifier = Modifier.fillMaxWidth().height(120.dp),
            supportingText = { Text("Подробное описание промпта и его назначения") },
            minLines = 2,
            maxLines = 5,
            singleLine = false
        )

        // Категория и теги
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryDropdown(
                selectedCategory = editedData.category,
                availableCategories = state.availableCategories,
                onCategorySelected = { newCategory ->
                    component.onEditDataChanged(editedData.copy(category = newCategory))
                },
                modifier = Modifier.weight(1f)
            )
        }

        // Даты поста
        val selectedPost = state.selectedPost
        if (selectedPost != null) {
            Spacer(Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Информация о посте", style = MaterialTheme.typography.titleSmall)

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column {
                            Text("Дата создания", style = MaterialTheme.typography.labelMedium)
                            Text(
                                formatDate(selectedPost.date),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        selectedPost.updatedDate?.let { updatedDate ->
                            Column {
                                Text("Дата обновления", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    formatDate(updatedDate),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Контент промпта
        OutlinedTextField(
            value = editedData.content,
            onValueChange = { newContent ->
                component.onEditDataChanged(editedData.copy(content = newContent))
            },
            label = { Text("Контент промпта") },
            modifier = Modifier.fillMaxWidth().weight(1f),
            supportingText = { Text("Основной текст промпта") },
            minLines = 3,
            maxLines = 20,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default.copy(
                imeAction = androidx.compose.ui.text.input.ImeAction.Default
            ),
            singleLine = false
        )
    }
}

// --- ВКЛАДКА СТРУКТУРЫ ---
@Composable
private fun StructureEditorTab(state: ImporterState, component: ImporterComponent) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Структура поста", style = MaterialTheme.typography.titleSmall)

        val selectedPost = state.selectedPost
        if (selectedPost != null) {
            val blocks = remember(selectedPost.fullHtmlContent, selectedPost.attachments) {
                PostStructureParser().parse(selectedPost.fullHtmlContent, selectedPost.attachments)
            }

            if (blocks.isEmpty()) {
                Text("Не удалось разобрать структуру поста")
            } else {
                blocks.forEach { block ->
                    when (block.type) {
                        BlockType.TEXT -> InteractiveTextBlock(block.content, component)
                        BlockType.SPOILER -> InteractiveSpoilerBlock(block, component)
                        BlockType.QUOTE -> InteractiveQuoteBlock(block.content, component)
                        BlockType.ATTACHMENT -> InteractiveAttachmentBlock(block, state, component)
                    }
                }
            }
        }
    }
}

// --- ВКЛАДКА ПРЕВЬЮ ---
@Composable
private fun PreviewTab(editedData: EditedPostData, state: ImporterState, component: ImporterComponent) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Превью промпта", style = MaterialTheme.typography.titleSmall)
            Icon(
                Icons.Default.ContentCopy,
                "Текст можно выделять и копировать",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Текст можно выделять и копировать",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Кнопка открытия поста в браузере
        val selectedPost = state.selectedPost
        if (selectedPost?.postUrl != null) {
            OutlinedButton(
                onClick = { component.onOpenPostInBrowser(selectedPost.postUrl) },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    Icons.Default.OpenInNew,
                    "Открыть пост в браузере",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Открыть оригинальный пост", style = MaterialTheme.typography.labelMedium)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            SelectionContainer {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        editedData.title,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    if (editedData.description.isNotBlank()) {
                        SelectionContainer {
                            Text(
                                formatTextWithMarkdown(editedData.description),
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                            )
                        }
                    }
                    if (editedData.content.isNotBlank()) {
                        SelectionContainer {
                            Text(
                                formatTextWithMarkdown(editedData.content),
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- ИНТЕРАКТИВНЫЕ БЛОКИ ДЛЯ СТРУКТУРЫ ---
@Composable
private fun InteractiveTextBlock(text: String, component: ImporterComponent) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Текст", style = MaterialTheme.typography.labelMedium)
                ActionButtonsRow(text, component)
            }
            SelectionContainer {
                SelectionContainer {
                    ExpandableText(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                        maxLinesCollapsed = 3
                    )
                }
            }
        }
    }
}

@Composable
private fun InteractiveSpoilerBlock(block: ParsedPostBlock, component: ImporterComponent) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Кликабельная область - вся строка заголовка
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 4.dp) // Добавляем немного padding для лучшего клика
            ) {
                Text("Спойлер", style = MaterialTheme.typography.labelMedium)
                Text(
                    block.title ?: "Без заголовка",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                // Иконка теперь просто индикатор, без кнопки
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    "Развернуть",
                    modifier = Modifier.size(20.dp)
                )
            }

            // Кнопки действий - размещаем отдельно, чтобы не перехватывать клик
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                ActionButtonsRow(block.content, component)
            }

            if (isExpanded) {
                SelectionContainer {
                    Text(
                        text = formatTextWithMarkdown(block.content),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
                    )
                }
            } else {
                Text(
                    "Нажмите для просмотра...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun InteractiveQuoteBlock(text: String, component: ImporterComponent) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Цитата", style = MaterialTheme.typography.labelMedium)
                ActionButtonsRow(text, component)
            }
            SelectionContainer {
                Text(
                    text = formatTextWithMarkdown(text),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun InteractiveAttachmentBlock(block: ParsedPostBlock, state: ImporterState, component: ImporterComponent) {
    val attachment = block.attachment ?: return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Вложение", style = MaterialTheme.typography.labelMedium)
                FileTypeIcon(attachment.fileType)
                Text(
                    attachment.filename,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    "${attachment.fileType.name} • ${attachment.fileSize ?: "Размер неизвестен"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Кнопки управления файлом
            DownloadStatusIndicator(
                downloadedFile = state.downloadedFiles[attachment.url],
                onDownload = { component.onDownloadFile(attachment.url, attachment.filename) },
                onPreview = { component.onPreviewFile(attachment.url, attachment.filename) },
                onOpenInSystem = { component.onOpenFileInSystem(attachment.url, attachment.filename) }
            )
        }
    }
}

// --- КНОПКИ ДЕЙСТВИЙ ---
@Composable
private fun ActionButtonsRow(text: String, component: ImporterComponent?) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ActionButtonWithText(
            icon = Icons.Default.TextFields,
            text = "Заголовок",
            tooltip = "Назначить как заголовок промпта"
        ) {
            component?.onBlockActionClicked(text, BlockActionTarget.TITLE)
        }
        ActionButtonWithText(
            icon = Icons.Default.Description,
            text = "Описание",
            tooltip = "Добавить в описание промпта"
        ) {
            component?.onBlockActionClicked(text, BlockActionTarget.DESCRIPTION)
        }
        ActionButtonWithText(
            icon = Icons.Default.ContentPaste,
            text = "Контент",
            tooltip = "Добавить в контент промпта"
        ) {
            component?.onBlockActionClicked(text, BlockActionTarget.CONTENT)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionButtonWithText(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tooltip: String,
    onClick: () -> Unit
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(tooltip)
            }
        },
        state = rememberTooltipState()
    ) {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.height(32.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                icon,
                contentDescription = tooltip,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

// --- БОКОВАЯ ПАНЕЛЬ ---
@Composable
private fun SidePanel(
    modifier: Modifier,
    state: ImporterState,
    component: ImporterComponent
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Статистика
        StatisticsCard(state)

        // Действия
        ActionsCard(state, component)

        // Валидация
        if (state.hasValidationErrors) {
            ValidationErrorsCard(state)
        }
    }
}

// --- КАРТОЧКА СТАТИСТИКИ ---
@Composable
private fun StatisticsCard(state: ImporterState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Статистика", style = MaterialTheme.typography.titleSmall)

            StatisticRow("Всего постов", state.rawPosts.size.toString())
            StatisticRow("Отфильтровано", state.filteredPosts.size.toString())
            StatisticRow("Готово к импорту", state.readyToImportCount.toString())

            if (state.postsToImport.isNotEmpty()) {
                val completionPercentage = (state.postsToImport.size.toFloat() / state.filteredPosts.size * 100).toInt()
                LinearProgressIndicator(
                    progress = { completionPercentage / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("$completionPercentage% готово", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// --- СТРОКА СТАТИСТИКИ ---
@Composable
private fun StatisticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

// --- КАРТОЧКА ДЕЙСТВИЙ ---
@Composable
private fun ActionsCard(state: ImporterState, component: ImporterComponent) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Действия", style = MaterialTheme.typography.titleSmall)

            // Навигация между постами
            if (state.selectedPostId != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = component::onSelectPreviousPost,
                        modifier = Modifier.weight(1f),
                        enabled = !state.isLoading
                    ) {
                        Icon(Icons.Outlined.ChevronLeft, "Предыдущий пост")
                        Spacer(Modifier.width(4.dp))
                        Text("Предыдущий")
                    }

                    OutlinedButton(
                        onClick = component::onSelectNextPost,
                        modifier = Modifier.weight(1f),
                        enabled = !state.isLoading
                    ) {
                        Text("Следующий")
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Outlined.ChevronRight, "Следующий пост")
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Кнопки управления постом
                Button(
                    onClick = component::onSaveAndSelectNextClicked,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading
                ) {
                    Text("Сохранить и следующий")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = component::onSaveAndSelectPreviousClicked,
                        modifier = Modifier.weight(1f),
                        enabled = !state.isLoading
                    ) {
                        Text("Сохранить и предыдущий")
                    }

                    OutlinedButton(
                        onClick = component::onSkipPostClicked,
                        modifier = Modifier.weight(1f),
                        enabled = !state.isLoading
                    ) {
                        Text("Пропустить")
                    }
                }
            }

            Divider()

            // Финальные действия
            Button(
                onClick = component::onImportClicked,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canGenerateJson
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                } else {
                    Text("Сгенерировать JSON (${state.readyToImportCount})")
                }
            }
        }
    }
}

// --- КАРТОЧКА ОШИБОК ВАЛИДАЦИИ ---
@Composable
private fun ValidationErrorsCard(state: ImporterState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ошибки валидации", style = MaterialTheme.typography.titleSmall)

            state.validationErrors.forEach { (field, error) ->
                Text(
                    "$field: $error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}