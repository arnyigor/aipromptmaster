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
            val isWideScreen = maxWidth > 900.dp

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
                    session = uiState.currentSession,
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
    var showSidebar by remember { mutableStateOf(false) }
    var showParams by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            MobileHeader(
                selectedModel = uiState.selectedModel?.name,
                onMenuClick = { showSidebar = true },
                onModelClick = component::toggleModelDialog,
                onSettingsClick = { showParams = true }
            )

            HorizontalDivider()

            // Chat area
            ChatArea(
                uiState = uiState,
                component = component,
                modifier = Modifier.weight(1f)
            )
        }

        // Sidebar drawer
        if (showSidebar) {
            ModalNavigationDrawer(
                drawerState = rememberDrawerState(initialValue = DrawerValue.Open),
                drawerContent = {
                    ModalDrawerSheet {
                        ChatSidebar(
                            sessions = uiState.chatSessions,
                            selectedSessionId = uiState.selectedChatId,
                            onSessionSelected = {
                                component.onChatSessionSelected(it)
                                showSidebar = false
                            },
                            onNewChat = {
                                component.onCreateNewChatSession()
                                showSidebar = false
                            },
                            onDeleteSession = component::onDeleteChatSession,
                            onRenameSession = component::onRenameChatSession,
                            onArchiveSession = component::onArchiveChatSession,
                            modifier = Modifier.width(300.dp)
                        )
                    }
                },
                gesturesEnabled = false
            ) {
                // Empty content - just to show drawer
                Box(modifier = Modifier.fillMaxSize())
            }
        }

        // Parameters bottom sheet
        if (showParams) {
            val sheetState = rememberModalBottomSheetState()
            ModalBottomSheet(
                sheetState = sheetState,
                onDismissRequest = { showParams = false }
            ) {
                ParametersPanel(
                    session = uiState.currentSession,
                    selectedModel = uiState.selectedModel,
                    onSettingsChanged = component::onChatSettingsChanged,
                    onSystemPromptChanged = component::onSystemPromptChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 500.dp)
                )
            }
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
    Column(modifier = modifier.fillMaxHeight()) {
        // Messages
        MessagesList(
            messages = uiState.messages,
            isLoading = uiState.isLoadingMessages,
            onRetry = component::onRetryMessage,
            onEdit = component::onEditMessage,
            onCopy = { /* TODO */ },
            modifier = Modifier.weight(1f)
        )

        HorizontalDivider()

        // Input
        ChatInput(
            value = uiState.prompt,
            onValueChange = component::onPromptChanged,
            onSend = component::onStreamingGenerateClicked,
            isGenerating = uiState.isGenerating,
            canSend = uiState.canSendMessage,
            selectedModel = uiState.selectedModel?.name,
            modifier = Modifier.fillMaxWidth()
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
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            scope.launch {
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

@Composable
private fun ChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isGenerating: Boolean,
    canSend: Boolean,
    selectedModel: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.inputPanelBackground())
            .padding(16.dp)
    ) {
        // Индикатор модели
        selectedModel?.let {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(
                    text = "Модель: $it",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text("Введите сообщение... (Enter для отправки)") },
                modifier = Modifier
                    .weight(1f)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && 
                            event.key == Key.Enter &&
                            !event.isShiftPressed
                        ) {
                            if (canSend && !isGenerating) {
                                onSend()
                                true
                            } else false
                        } else false
                    },
                enabled = !isGenerating,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            if (isGenerating) {
                // Кнопка отмены
                FilledIconButton(
                    onClick = { /* TODO: cancel */ },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError
                    )
                }
            } else {
                // Кнопка отправки
                FilledIconButton(
                    onClick = onSend,
                    enabled = canSend
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Отправить"
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
            text = "AI Chat",
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
            text = "AI Chat",
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
private fun ModelSelectionDialog(
    uiState: LlmUiState,
    onModelSelected: (String) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onCategorySelected: (ModelCategory) -> Unit,
    onSortOrderSelected: (ModelSortOrder) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp),
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

                // Search
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = { Text("Поиск моделей...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Filters
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModelCategory.entries.forEach { category ->
                        FilterChip(
                            selected = uiState.selectedCategory == category,
                            onClick = { onCategorySelected(category) },
                            label = { Text(category.displayName) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Sort
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Сортировка:", style = MaterialTheme.typography.labelMedium)
                    ModelSortOrder.entries.forEach { order ->
                        FilterChip(
                            selected = uiState.selectedSortOrder == order,
                            onClick = { onSortOrderSelected(order) },
                            label = { Text(order.displayName) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

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

@Composable
private fun ModelListItem(
    model: com.arny.aiprompts.data.model.LlmModel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = model.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    model.contextLength?.let {
                        AssistChip(
                            onClick = { },
                            label = { Text("${it / 1000}K контекст") }
                        )
                    }
                }
            }
        }
    }
}
