@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)

package com.arny.aiprompts.presentation.ui.llm

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.arny.aiprompts.data.model.ChatMessageRole
import com.arny.aiprompts.presentation.features.llm.LlmComponent
import com.arny.aiprompts.presentation.features.llm.LlmUiState
import com.arny.aiprompts.presentation.features.llm.ModelCategory
import com.arny.aiprompts.presentation.features.llm.ModelSortOrder
import com.arny.aiprompts.presentation.ui.llm.components.*
import com.arny.aiprompts.presentation.ui.llm.theme.*
import com.arny.aiprompts.results.DataResult
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime

/**
 * Главный экран LLM чата.
 * Адаптивный layout: desktop (3 панели) / mobile (адаптивный).
 *
 * @param component Компонент для управления логикой
 */
@Composable
fun LlmScreen(component: LlmComponent) {
    val uiState by component.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Показываем ошибки
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "OK",
                duration = SnackbarDuration.Short
            )
            component.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            // Keep at least 360 dp for the conversation after both supporting panes.
            val isWideScreen = maxWidth >= 1080.dp

            if (isWideScreen) {
                // Desktop layout: Sidebar | Chat | Parameters
                DesktopLayout(
                    uiState = uiState,
                    component = component
                )
            } else {
                // Mobile layout
                MobileLayout(
                    uiState = uiState,
                    component = component
                )
            }
        }
    }

    // Диалог выбора модели
    uiState.editingMessageId?.let { id ->
        AlertDialog(
            onDismissRequest = component::onDismissEditMessage,
            title = { Text("Редактировать сообщение") },
            text = { Column {
                OutlinedTextField(uiState.editingText, component::onEditDraftChanged,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp), minLines = 3, maxLines = 10)
                Text("Последующие ответы будут заменены новым ответом.", style = MaterialTheme.typography.bodySmall)
            } },
            confirmButton = { TextButton(enabled = uiState.editingText.isNotBlank(), onClick = {
                component.onEditMessage(id, uiState.editingText)
            }) { Text("Сохранить и отправить") } },
            dismissButton = { TextButton(onClick = component::onDismissEditMessage) { Text("Отмена") } },
        )
    }
    if (uiState.showModelDialog) {
        ModelSelectionDialog(
            uiState = uiState,
            onModelSelected = component::onModelSelected,
            onSearchQueryChanged = component::onSearchQueryChanged,
            onCategorySelected = component::onCategorySelected,
            onSortOrderSelected = component::onSortOrderSelected,
            onRefresh = component::refreshModels,
            onDismiss = component::toggleModelDialog
        )
    }
}

// ==================== Desktop Layout ====================

@Composable
private fun DesktopLayout(
    uiState: LlmUiState,
    component: LlmComponent
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        ChatHeader(
            selectedModel = uiState.selectedModel?.name,
            onModelClick = component::toggleModelDialog
        )

        HorizontalDivider()

        // Main content
        Row(modifier = Modifier.weight(1f)) {
            // Sidebar (чаты)
            AnimatedVisibility(visible = uiState.showChatList) {
                ChatSidebar(
                    sessions = uiState.chatSessions,
                    selectedSessionId = uiState.selectedChatId,
                    onSessionSelected = component::onChatSessionSelected,
                    onNewChat = component::onCreateNewChatSession,
                    onDeleteSession = component::onDeleteChatSession,
                    onRenameSession = component::onRenameChatSession,
                    onArchiveSession = component::onArchiveChatSession,
                    modifier = Modifier.width(280.dp)
                )
            }

            // Toggle sidebar button
            IconButton(onClick = component::toggleChatList) {
                Icon(
                    imageVector = if (uiState.showChatList) 
                        Icons.Default.ChevronLeft else Icons.Default.ChevronRight,
                    contentDescription = "Toggle sidebar"
                )
            }

            VerticalDivider(modifier = Modifier.fillMaxHeight())

            // Chat area
            ChatArea(
                uiState = uiState,
                component = component,
                modifier = Modifier.weight(1f)
            )

            VerticalDivider(modifier = Modifier.fillMaxHeight())

            // Parameters panel
            IconButton(onClick = component::toggleParameters) {
                Icon(
                    imageVector = if (uiState.showParameters) 
                        Icons.Default.ChevronRight else Icons.Default.ChevronLeft,
                    contentDescription = "Toggle parameters"
                )
            }

            AnimatedVisibility(visible = uiState.showParameters) {
                ParametersPanel(
                    session = uiState.currentSession ?: com.arny.aiprompts.data.model.ChatSession("draft", "Новый чат", uiState.newChatSystemPrompt, uiState.newChatSettings, 0, 0),
                    selectedModel = uiState.selectedModel,
                    onSettingsChanged = component::onChatSettingsChanged,
                    onSystemPromptChanged = component::onSystemPromptChanged,
                    modifier = Modifier.width(320.dp)
                )
            }
        }
    }
}

// ==================== Mobile Layout ====================

@Composable
private fun MobileLayout(
    uiState: LlmUiState,
    component: LlmComponent
) {
val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showParams by remember { mutableStateOf(false) }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                ChatSidebar(
                    sessions = uiState.chatSessions, selectedSessionId = uiState.selectedChatId,
                    onSessionSelected = { component.onChatSessionSelected(it); scope.launch { drawerState.close() } },
                    onNewChat = { component.onCreateNewChatSession(); scope.launch { drawerState.close() } },
                    onDeleteSession = component::onDeleteChatSession, onRenameSession = component::onRenameChatSession,
                    onArchiveSession = component::onArchiveChatSession, modifier = Modifier.width(300.dp),
                )
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            MobileHeader(selectedModel = uiState.selectedModel?.name,
                chatTitle = uiState.currentSession?.name ?: "Новый чат",
                onMenuClick = { scope.launch { drawerState.open() } },
                onModelClick = component::toggleModelDialog, onSettingsClick = { showParams = true })
            HorizontalDivider()
            ChatArea(uiState, component, Modifier.weight(1f))
        }
    }
    if (showParams) {
        ModalBottomSheet(onDismissRequest = { showParams = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            ParametersPanel(
                session = uiState.currentSession ?: com.arny.aiprompts.data.model.ChatSession("draft", "Новый чат", uiState.newChatSystemPrompt, uiState.newChatSettings, 0, 0),
                selectedModel = uiState.selectedModel,
                onSettingsChanged = component::onChatSettingsChanged, onSystemPromptChanged = component::onSystemPromptChanged,
                onDismiss = { showParams = false }, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f),
            )
        }
    }
}

// ==================== Chat Area ====================

@Composable
private fun ChatArea(
    uiState: LlmUiState,
    component: LlmComponent,
    modifier: Modifier = Modifier
) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    Column(modifier = modifier.fillMaxHeight()) {
        // Messages
        MessagesList(
            messages = if (uiState.searchHistoryQuery.isBlank()) uiState.messages
                else uiState.messages.filter { it.content.contains(uiState.searchHistoryQuery, ignoreCase = true) },
            isLoading = uiState.isLoadingMessages,
            onRetry = component::onRetryMessage,
            onEdit = { id, _ -> component.onBeginEditMessage(id) },
            onCopy = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(it)) },
            modifier = Modifier.weight(1f)
        )

        HorizontalDivider()

        // Input
        com.arny.sharedui.ChatComposer(
            value = uiState.prompt,
            onValueChange = component::onPromptChanged,
            onSend = component::onStreamingGenerateClicked,
            onCancel = component::onCancelGenerating,
            generating = uiState.isGenerating,
            canSend = uiState.canSendMessage,
            modifier = Modifier.fillMaxWidth(),
            sendOnEnter = true,
            onAttach = { com.arny.aiprompts.platform.pickChatAttachments(component::onAttachmentsAdded, component::onAttachmentError) },
            attachments = {
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.attachments, key = { it.uri }) { file ->
                        InputChip(selected = true, onClick = { component.onAttachmentRemoved(file.uri) },
                            enabled = !uiState.isGenerating, label = { Text(file.fileName, maxLines = 1) },
                            trailingIcon = { Icon(Icons.Default.Close, "Удалить вложение") })
                    }
                }
            },
            onClear = {
                component.onPromptChanged("")
                uiState.attachments.forEach { component.onAttachmentRemoved(it.uri) }
            },
        )
    }
}

@Composable
private fun MessagesList(
    messages: List<com.arny.aiprompts.data.model.ChatMessage>,
    isLoading: Boolean,
    onRetry: (String) -> Unit,
    onEdit: (String, String) -> Unit,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Автоскролл к последнему сообщению
    LaunchedEffect(messages.lastOrNull()?.id, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) {
            val nearBottom = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= messages.lastIndex - 1 } ?: true
            if (nearBottom && !listState.isScrollInProgress) {
                withFrameNanos { }
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    Box(modifier = modifier) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (messages.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Начните диалог",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = messages,
                    key = { it.id }
                ) { message ->
                    ChatMessageCard(
                        message = message,
                        isLast = message == messages.last(),
                        onRetry = { onRetry(message.id) },
                        onEdit = { onEdit(message.id, "") },
                        onCopy = { onCopy(message.content) }
                    )
                }
            }
        }
    }
}

// ==================== Headers ====================

@Composable
private fun ChatHeader(
    selectedModel: String?,
    onModelClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Чаты",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // Кнопка выбора модели
        Button(
            onClick = onModelClick,
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(selectedModel ?: "Выбрать модель")
        }
    }
}

@Composable
private fun MobileHeader(
    selectedModel: String?,
    chatTitle: String,
    onMenuClick: () -> Unit,
    onModelClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onMenuClick) {
            Icon(Icons.Default.Menu, contentDescription = "Меню")
        }

        Text(
            text = chatTitle,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Row {
            IconButton(onClick = onModelClick) {
                Icon(Icons.Default.SmartToy, contentDescription = "Модель")
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Настройки")
            }
        }
    }
}

// ==================== Model Selection Dialog ====================

@Composable
internal fun ModelSelectionDialog(
    uiState: LlmUiState,
    onModelSelected: (String) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onCategorySelected: (ModelCategory) -> Unit,
    onSortOrderSelected: (ModelSortOrder) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val compact = maxWidth < 600.dp
        Surface(
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(if (compact) 1f else 0.9f)
                .fillMaxHeight(if (compact) 1f else 0.9f),
            shape = RoundedCornerShape(if (compact) 0.dp else 16.dp),
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Выберите модель",
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                com.arny.sharedui.ModelPickerControls(
                    query = uiState.searchQuery,
                    filters = ModelCategory.entries.map { com.arny.sharedui.ModelFilterOption(it.name, it.displayName, uiState.selectedCategory == it) },
                    sortOptions = ModelSortOrder.entries.map { com.arny.sharedui.ModelSortOption(it.name, it.displayName) },
                    selectedSortId = uiState.selectedSortOrder.name,
                    onQueryChange = onSearchQueryChanged,
                    onFilterClick = { onCategorySelected(ModelCategory.valueOf(it)) },
                    onSortClick = { onSortOrderSelected(ModelSortOrder.valueOf(it)) },
                )
                Spacer(Modifier.height(16.dp))
                // Models list
                Box(modifier = Modifier.weight(1f)) {
                    when (val result = uiState.modelsResult) {
                        is DataResult.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        is DataResult.Success -> {
                            val models = uiState.displayModels
                            if (models.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Модели не найдены")
                                }
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(models, key = { it.id }) { model ->
                                        ModelListItem(
                                            model = model,
                                            isSelected = model.isSelected,
                                            onClick = {
                                                onModelSelected(model.id)
                                                onDismiss()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        is DataResult.Error -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Ошибка загрузки",
                                    color = MaterialTheme.colorScheme.error
                                )
                                TextButton(onClick = onRefresh) {
                                    Text("Повторить")
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun ModelListItem(
    model: com.arny.aiprompts.data.model.LlmModel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    com.arny.sharedui.ModelRow(
        name = model.name,
        description = model.description,
        selected = isSelected,
        onSelect = onClick,
        details = {
            model.contextLength?.let {
                Text("Контекст: $it токенов", style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}
