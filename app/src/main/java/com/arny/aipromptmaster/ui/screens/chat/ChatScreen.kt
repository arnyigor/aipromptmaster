package com.arny.aipromptmaster.ui.screens.chat

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Expand
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arny.aipromptmaster.domain.models.AppConstants
import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.domain.models.ChatRole
import com.arny.aipromptmaster.domain.models.FileAttachment
import com.arny.aipromptmaster.domain.models.FileAttachmentMetadata
import com.arny.aipromptmaster.domain.models.MessageState
import com.arny.aipromptmaster.domain.models.errors.DomainError
import com.arny.aipromptmaster.ui.models.TypingIndicator
import com.arny.aipromptmaster.ui.navigation.LocalTopBarManager
import com.arny.aipromptmaster.ui.theme.AIPromptMasterComposeTheme
import com.arny.aipromptmaster.ui.theme.MarkdownColorPalette
import com.arny.aipromptmaster.ui.utils.asString
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.DefaultMarkdownColors
import com.mikepenz.markdown.model.MarkdownColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

// ---------------------------------------------------------------------------
// Helper factories – вынесены, чтобы не повторять код в preview‑ах
// ---------------------------------------------------------------------------
private fun createDummyState(): ChatUiState {
    val markdownHeading = """
        # Welcome to the AI Prompt Master
        `code`
        ```bash
        git clone
        ```
        
        This is a **bold** statement, and this is *italic*.
        
        ## Features
        - Quick prompts
        - Markdown support
        - File attachments
    """.trimIndent()

    val markdownList = """
        ### Things you can do:
        1. Ask questions
        2. Share files
        3. Export chat history
    """.trimIndent()

    val bigMarkdownTable = """
| # | Feature               | Category          |
|---|-----------------------|-------------------|
| 1 | Markdown Support      | Text Formatting   |
| 2 | File Attachments      | Media Handling     |
| 3 | Streaming AI          | Interaction       |
""".trimIndent()

    return ChatUiState(
        selectedModel = null,
        isSending = false,
        isStreamingResponse = true,
        messages = listOf(
            ChatMessage(role = ChatRole.SYSTEM, content = "You are the coder!"),
            ChatMessage(role = ChatRole.ASSISTANT, content = markdownHeading),
            ChatMessage(
                role = ChatRole.USER,
                content = "Can you show me a table?",
                fileAttachment = FileAttachmentMetadata(
                    fileId = UUID.randomUUID().toString(),
                    fileName = "example.md",
                    fileExtension = ".md",
                    fileSize = 1234,
                    mimeType = "text/markdown",
                    preview = markdownHeading.take(150) + "...",
                    uploadTimestamp = System.currentTimeMillis()
                )
            ),
            ChatMessage(role = ChatRole.ASSISTANT, content = bigMarkdownTable),
            ChatMessage(role = ChatRole.ASSISTANT, content = markdownList)
        )
    )
}

private fun createAttachments(): List<FileAttachment> {
    return listOf(
        FileAttachment(
            fileName = "report",
            fileExtension = ".pdf",
            fileSize = 523_000,
            mimeType = "application/pdf",
            originalContent = "<PDF content placeholder>"
        ),
        FileAttachment(
            fileName = "image1",
            fileExtension = ".png",
            fileSize = 120_400,
            mimeType = "image/png",
            originalContent = "<PNG data placeholder>"
        )
    )
}

// ------------- 2️⃣ PREVIEW ----------------------------------------------------
@Preview(name = "Phone – Large", widthDp = 600, heightDp = 1200)
@Composable
private fun ChatScreenPreviewLight() {
    val dummyState = remember { createDummyState() }
    val attachments = remember { createAttachments() }

    AIPromptMasterComposeTheme(
        darkTheme = false
    ) {
        ChatScreen(
            uiState = dummyState,
            uiEvents = flow {
                emit(ChatUiEvent.ShowError(Exception("Error")))
            },
            attachedFiles = attachments,
        )
    }
}

@Preview(name = "Phone – Large", widthDp = 600, heightDp = 1200)
@Composable
private fun ChatScreenPreviewDark() {
    val dummyState = remember { createDummyState() }
    val attachments = remember { createAttachments() }

    AIPromptMasterComposeTheme(
        darkTheme = true
    ) {
        ChatScreen(
            uiState = dummyState,
            uiEvents = flow {
                emit(ChatUiEvent.ShowError(Exception("Error")))
            },
            attachedFiles = attachments,
        )
    }
}

// ------------- 3️⃣ ROUTE ----------------------------------------------------
@Composable
fun ChatRoute(
    viewModel: ChatViewModel,
    onModelsClick: () -> Unit,
    onSystemClick: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val inputText by viewModel.currentInputText.collectAsStateWithLifecycle()
    val attachedFiles by viewModel.attachedFiles.collectAsStateWithLifecycle()
    val estimatedTokens by viewModel.estimatedTokens.collectAsStateWithLifecycle()
    var showClearChatDialog by remember { mutableStateOf(false) }

    val topBarManager = LocalTopBarManager.current
    val screenConfig by viewModel.screenConfig.collectAsStateWithLifecycle()

    // File picker launcher для выбора файлов
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            viewModel.processFileUri(uri)
        }
    }

    DisposableEffect(screenConfig.actions) {
        topBarManager.setActions(
            key = AppConstants.CHAT_SCREEN_KEY,
            newActions = screenConfig.actions
        )
        onDispose { topBarManager.clearIfCurrent(AppConstants.CHAT_SCREEN_KEY) }
    }

    // Формируем заголовок на основе состояния модели
    val selectedModel = state.selectedModel
    val titleText = when {
        selectedModel == null -> "Выберите модель..."
        selectedModel.id.isBlank() -> "Модель не выбрана"
        else -> selectedModel.name.takeIf { it.isNotBlank() } ?: selectedModel.id
    }

    DisposableEffect(titleText) {
        topBarManager.setTitle(
            key = AppConstants.CHAT_SCREEN_KEY,
            newTitle = titleText
        )
        onDispose { }
    }

    ChatScreen(
        uiState = state,
        inputText = inputText,
        attachedFiles = attachedFiles,
        estimatedTokens = estimatedTokens,
        uiEvents = viewModel.uiEvents,
        updateInputText = viewModel::updateInputText,
        sendMessage = viewModel::sendMessage,
        clearInput = viewModel::clearInput,
        cancelCurrentRequest = viewModel::cancelCurrentRequest,
        removeAttachedFile = viewModel::removeAttachedFile,
        onAttachClick = {
            // Запускаем file picker для текстовых файлов и изображений
            filePickerLauncher.launch("*/*")
        },
        onModelsClick = onModelsClick,
        onSystemClick = onSystemClick,
        onCopyText = viewModel::onCopyText,
        onShareMessage = viewModel::onShareMessage,
        onEditMessage = viewModel::onEditMessage,
        onRetryMessage = viewModel::onRetryMessage,
        onClearChat = { showClearChatDialog = true },
        onDeleteMessage = viewModel::onDeleteMessage,
        onEditUserMessage = { id, content -> viewModel.onEditUserMessage(id, content) }
    )

    // Диалог подтверждения очистки чата
    if (showClearChatDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatDialog = false },
            title = { Text("Очистить чат") },
            text = { Text("Вы уверены, что хотите удалить всю историю сообщений? Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onRemoveChatHistory()
                        showClearChatDialog = false
                    }
                ) {
                    Text("Очистить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatDialog = false }) {
                    Text("Отмена")
                }
            })
    }
}

/**
 * Компонент отображения прикрепленного файла в сообщении чата.
 */
@Composable
fun FileAttachmentView(
    attachment: FileAttachmentMetadata,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isImage = attachment.mimeType.startsWith("image/")
    val fileIcon = if (isImage) Icons.Default.Image else Icons.Default.AttachFile

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = modifier.padding(top = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = fileIcon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (isImage) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${formatFileSize(attachment.fileSize)} • ${attachment.preview.take(50)}${if (attachment.preview.length > 50) "..." else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Форматирует размер файла в человекочитаемый вид.
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

/**
 * Полный текст ошибки, готовый к отображению в UI.
 */
private fun DomainError.fullMessage(context: Context): String {
    val main = this.stringHolder.asString(context).takeIf { it.isNotBlank() }
    val detail = when (this) {
        is DomainError.Api -> this.detailedMessage.takeIf { it.isNotBlank() }
        else -> null
    }
    val fallback = cause?.localizedMessage ?: message.orEmpty()

    return when {
        main != null && detail != null -> "$main\n$detail"
        detail != null -> detail
        main != null -> main
        else -> fallback
    }
}

// ------------- 4️⃣ SCREEN ----------------------------------------------------
@Composable
fun ChatScreen(
    uiState: ChatUiState,
    inputText: String = "",
    attachedFiles: List<FileAttachment>,
    estimatedTokens: Int = 0,
    uiEvents: Flow<ChatUiEvent> = emptyFlow(),
    onModelsClick: () -> Unit = {},
    updateInputText: (String) -> Unit = {},
    onSystemClick: (String) -> Unit = {},
    sendMessage: (String) -> Unit = {},
    clearInput: () -> Unit = {},
    cancelCurrentRequest: () -> Unit = {},
    removeAttachedFile: (String) -> Unit = {},
    onAttachClick: () -> Unit = {},
    onCopyText: (String) -> Unit = {},
    onRetryMessage: () -> Unit = {},
    onEditMessage: (String) -> Unit = {},
    onShareMessage: (String) -> Unit = {},
    onClearChat: () -> Unit = {},
    onDeleteMessage: (String) -> Unit = {},
    onEditUserMessage: (String, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var lastError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiEvents) {
        uiEvents.collect { event ->
            when (event) {
                is ChatUiEvent.ShowError -> {
                    val error = event.error
                    lastError = when (error) {
                        is DomainError -> error.fullMessage(context)
                        else -> event.error.localizedMessage
                    }
                }

                is ChatUiEvent.NavigateToSystem -> onSystemClick(event.conversationId)
                is ChatUiEvent.NavigateModels -> onModelsClick()
                is ChatUiEvent.ConfirmClearChat -> onClearChat()
                is ChatUiEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        withDismissAction = true
                    )
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                ErrorBanner(
                    error = lastError,
                    onDismiss = { lastError = null }
                )
                ChatInputArea(
                    inputValue = inputText,
                    onValueChange = updateInputText,
                    onSendClick = { sendMessage(inputText) },
                    onAttachClick = onAttachClick,
                    onCancelClick = cancelCurrentRequest,
                    isLoading = uiState.isSending,
                    isStreaming = uiState.isStreamingResponse,
                    attachedFiles = attachedFiles,
                    onRemoveFile = removeAttachedFile,
                    tokenCount = estimatedTokens,
                    clearInput = clearInput,
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            ChatContent(
                uiState = uiState,
                onCopyText = onCopyText,
                onRetryMessage = onRetryMessage,
                onEditMessage = onEditMessage,
                onShareMessage = onShareMessage,
                onDeleteMessage = onDeleteMessage,
                onEditUserMessage = onEditUserMessage
            )
        }
    }
}

// ------------- 5️⃣ CONTENT & SMART SCROLL ------------------------------------
@Composable
fun ChatContent(
    uiState: ChatUiState,
    onCopyText: (String) -> Unit = {},
    onRetryMessage: () -> Unit = {},
    onEditMessage: (String) -> Unit = {},
    onShareMessage: (String) -> Unit = {},
    onDeleteMessage: (String) -> Unit = {},
    onEditUserMessage: ((String, String) -> Unit)? = null
) {
    val messages = uiState.messages
    val listState = rememberLazyListState()
    val lastMessageId by remember(messages) {
        derivedStateOf { messages.lastOrNull()?.id }
    }

    // Отслеживаем, ушел ли пользователь от нижней части списка
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) true else {
                val lastVisibleItem = visibleItems.last()
                lastVisibleItem.index >= layoutInfo.totalItemsCount - 2
            }
        }
    }

    // ❌ АВТОСКРОЛЛ УБРАН - теперь только ручной скролл через MessageJumper

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = messages,
                key = { it.id },
                contentType = { it.role }
            ) { message ->
                val index = messages.indexOf(message)
                val isLast = index == messages.size - 1
                val isStreaming = isLast && uiState.isStreamingResponse

                MessageBubble(
                    message = message,
                    isStreaming = isStreaming,
                    isLast = isLast,
                    onCopy = { onCopyText(it) },
                    onRetry = onRetryMessage,
                    onEdit = { onEditMessage(message.content) },
                    onShare = { onShareMessage(message.content) },
                    onDelete = { onDeleteMessage(message.id) },
                    onEditUser = { id, content -> onEditUserMessage?.invoke(id, content) }
                )
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        // ✅ Джампер с авто-скрытием через 2 секунды
        val showJumperCondition by remember {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo
                if (visibleItems.isEmpty()) false else {
                    val firstVisibleItem = visibleItems.first()
                    firstVisibleItem.index > 0 || firstVisibleItem.offset < -100
                }
            }
        }

        var showJumperVisible by remember { mutableStateOf(false) }
        var lastScrollTime by remember { mutableStateOf(0L) }

        // Отслеживаем скролл и показываем джампер
        LaunchedEffect(listState.firstVisibleItemScrollOffset) {
            if (showJumperCondition) {
                showJumperVisible = true
                lastScrollTime = System.currentTimeMillis()
            }
        }

        // Авто-скрытие через 2 секунды после остановки скролла
        LaunchedEffect(showJumperVisible, lastScrollTime) {
            if (showJumperVisible) {
                delay(2000)
                val timeSinceLastScroll = System.currentTimeMillis() - lastScrollTime
                if (timeSinceLastScroll >= 2000) {
                    showJumperVisible = false
                }
            }
        }

        MessageJumper(
            visible = showJumperVisible && showJumperCondition,
            listState = listState,
            messagesCount = messages.size,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

// ------------- 6️⃣ JUMPER ----------------------------------------------------
@Composable
fun MessageJumper(
    visible: Boolean,
    listState: LazyListState,
    messagesCount: Int,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + slideInHorizontally { it },
        exit = fadeOut() + slideOutHorizontally { it }
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FloatingActionButton(
                onClick = {
                    scope.launch { listState.animateScrollToItem(0) }
                },
                modifier = Modifier.size(40.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardDoubleArrowUp,
                    contentDescription = "В начало",
                    modifier = Modifier.size(20.dp)
                )
            }

            FloatingActionButton(
                onClick = {
                    scope.launch {
                        val target = (listState.firstVisibleItemIndex - 1).coerceAtLeast(0)
                        listState.animateScrollToItem(target)
                    }
                },
                modifier = Modifier.size(40.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Вверх",
                    modifier = Modifier.size(20.dp)
                )
            }

            FloatingActionButton(
                onClick = {
                    scope.launch { listState.animateScrollToItem(listState.firstVisibleItemIndex + 1) }
                },
                modifier = Modifier.size(40.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Вниз",
                    modifier = Modifier.size(20.dp)
                )
            }

            FloatingActionButton(
                onClick = { scope.launch { listState.animateScrollToItem(messagesCount - 1) } },
                modifier = Modifier.size(40.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardDoubleArrowDown,
                    contentDescription = "В конец",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ------------- 7️⃣ BUBBLE (Оптимизированное сообщение) -----------------------
/**
 * Оптимизированный компонент пузырька сообщения.
 * ✅ Использует @Stable для стабильной рекомпозиции
 * ✅ Все производные значения кэшируются через remember
 * ✅ Поддержка действий для user-сообщений (Edit, Delete)
 * ✅ Text при стриминге, Markdown после завершения
 */
@Composable
fun MessageBubble(
    message: ChatMessage,
    isStreaming: Boolean,
    isLast: Boolean,
    onCopy: (String) -> Unit,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onEditUser: (String, String) -> Unit?
) {
    // ✅ Кэшируем все производные значения
    val isUser by remember(message.role) { derivedStateOf { message.role == ChatRole.USER } }
    val content by remember(message.content) { derivedStateOf { message.content } }
    val isSending by remember(message.state) { derivedStateOf { message.state == MessageState.SENDING } }
    // Footer показывается для всех сообщений, кроме активно стримящихся
    val showFooter by remember(isStreaming) { derivedStateOf { !isStreaming } }
    val showTypingIndicator by remember(
        content,
        isSending
    ) { derivedStateOf { content.isBlank() && isSending } }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            // Показываем modelId в header для AI-сообщений
            if (!isUser && !message.modelId.isNullOrBlank()) {
                Text(
                    text = message.modelId,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
                )
            }
            Surface(
                shape = if (isUser)
                    RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
                else
                    RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
                color = if (isUser)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceContainer,
                contentColor = if (isUser)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSurface,
                tonalElevation = if (isUser) 4.dp else 1.dp,
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (showTypingIndicator) {
                        TypingIndicator()
                    } else {
                        // ✅ Text при стриминге (нет мигания), Markdown после завершения
                        MessageContent(
                            content = content,
                            isUser = isUser,
                            isStreaming = isStreaming && isLast
                        )
                    }

                    // Отображение прикрепленного файла
                    message.fileAttachment?.let { attachment ->
                        FileAttachmentView(
                            attachment = attachment,
                            onClick = { /* TODO: Открыть полный просмотр файла */ }
                        )
                    }
                }
            }

            // ✅ Footer показывается для всех не-стриминговых сообщений
            AnimatedVisibility(
                visible = showFooter,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                if (isUser) {
                    // Footer для user-сообщений: Copy, Edit, Delete
                    UserMessageFooterActions(
                        onCopy = { onCopy(content) },
                        onEdit = { onEditUser.invoke(message.id, message.content) },
                        onDelete = onDelete
                    )
                } else {
                    // Footer для AI-сообщений: Copy, Retry, Share, Delete
                    MessageFooterActions(
                        onCopy = { onCopy(content) },
                        onRetry = onRetry,
                        onShare = onShare,
                        onDelete = onDelete
                    )
                }
            }
        }
    }
}

/**
 * Оптимизированный компонент контента сообщения.
 * ✅ Text при стриминге (нет мигания)
 * ✅ Markdown после завершения (красивая разметка)
 * ✅ Курсор стриминга работает отдельно
 */
@Composable
private fun MessageContent(
    content: String,
    isUser: Boolean,
    isStreaming: Boolean
) {
    val markdownColors = markdownColorsContent(isUser)

    SelectionContainer {
        Box(modifier = Modifier.wrapContentWidth()) {
            if (content.isNotBlank()) {
                if (isStreaming) {
                    // ✅ При стриминге показываем обычный Text (нет мигания!)
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isUser) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                } else {
                    // ✅ После завершения показываем Markdown
                    Markdown(
                        content = content,
                        colors = markdownColors,
                        typography = markdownTypography(),
                        modifier = Modifier.wrapContentWidth()
                    )
                }
            }

            // ✅ Курсор стриминга - отдельный компонент
            if (isStreaming) {
                BlinkingCursor(
                    modifier = Modifier.align(if (isUser) Alignment.BottomEnd else Alignment.BottomStart)
                )
            }
        }
    }
}

/**
 * Мигающий курсор - отдельный компонент для стриминга.
 * Не вызывает рекомпозицию всего Markdown!
 */
@Composable
private fun BlinkingCursor(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )

    Text(
        text = "▋",
        color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier
    )
}

@Composable
private fun markdownColorsContent(isUser: Boolean): MarkdownColors {
    return RichMarkdownColors(
        base = DefaultMarkdownColors(
            text = if (isUser) MarkdownColorPalette.MarkdownTextInvert else MarkdownColorPalette.MarkdownText,
            codeBackground = MarkdownColorPalette.CodeBackground,
            inlineCodeBackground = MarkdownColorPalette.CodeBackground,
            dividerColor = MarkdownColorPalette.DividerColor,
            tableBackground = Color.Transparent
        ),
        codeText = MarkdownColorPalette.CodeText,
        linkText = MarkdownColorPalette.LinkText
    )
}

@Composable
fun MessageFooterActions(
    onCopy: () -> Unit,
    onRetry: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(top = 4.dp, start = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionIcon(
            icon = Icons.Outlined.ContentCopy,
            description = "Copy",
            onClick = onCopy
        )

        ActionIcon(
            icon = Icons.Outlined.Refresh,
            description = "Regenerate",
            onClick = onRetry
        )

        ActionIcon(
            icon = Icons.Outlined.Share,
            description = "Share",
            onClick = onShare
        )

        ActionIcon(
            icon = Icons.Default.Delete,
            description = "Delete",
            onClick = onDelete
        )
    }
}

/**
 * Footer actions для user-сообщений.
 * Copy, Edit, Delete
 */
@Composable
fun UserMessageFooterActions(
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(top = 4.dp, end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionIcon(
            icon = Icons.Outlined.ContentCopy,
            description = "Copy",
            onClick = onCopy
        )

        ActionIcon(
            icon = Icons.Default.Edit,
            description = "Edit",
            onClick = onEdit
        )

        ActionIcon(
            icon = Icons.Default.Delete,
            description = "Delete",
            onClick = onDelete
        )
    }
}

@Composable
private fun ActionIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(32.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp)
        )
    }
}

data class RichMarkdownColors(
    private val base: DefaultMarkdownColors,
    val linkText: Color,
    val codeText: Color
) : MarkdownColors by base

// ------------- 9️⃣ INPUT AREA (Умный ввод) ---------------------------------
@Composable
fun ChatInputArea(
    inputValue: String,
    onValueChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onAttachClick: () -> Unit,
    onCancelClick: () -> Unit,
    isLoading: Boolean,
    isStreaming: Boolean,
    attachedFiles: List<FileAttachment>,
    onRemoveFile: (String) -> Unit,
    tokenCount: Int,
    clearInput: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    val minLines = 1
    val maxLines = 20
    val shape = RoundedCornerShape(if (expanded) 12.dp else 24.dp)

    Surface(
        modifier = Modifier.padding(8.dp),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            AnimatedVisibility(visible = attachedFiles.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    items(
                        items = attachedFiles,
                        key = { it.id }
                    ) { file ->
                        InputChip(
                            selected = true,
                            onClick = {},
                            label = { Text(file.fileName.take(20)) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onRemoveFile(file.id) }
                                )
                            },
                            colors = InputChipDefaults.inputChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = inputValue,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .weight(1f)
                        .align(Alignment.CenterVertically)
                        .animateContentSize(),
                    minLines = if (expanded) maxLines else minLines,
                    maxLines = if (expanded) maxLines else minLines,
                    placeholder = { Text("Message AI…") },
                    shape = shape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                            alpha = 0.5f
                        )
                    ),
                    trailingIcon = {
                        if (inputValue.isNotEmpty()) {
                            IconButton(onClick = { clearInput() }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Очистить поле",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    supportingText = {
                        if (tokenCount > 0) {
                            Text(
                                text = "$tokenCount tokens (~${tokenCount * 3.5} chars)",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (tokenCount > 10000) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.width(8.dp))

                FloatingActionButton(
                    onClick = { if (isLoading || isStreaming) onCancelClick() else onSendClick() },
                    containerColor = if (isLoading || isStreaming) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    contentColor = if (isLoading || isStreaming) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    },
                    elevation = FloatingActionButtonDefaults.elevation(0.dp)
                ) {
                    Icon(
                        imageVector = if (isLoading || isStreaming) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                        contentDescription = if (isLoading || isStreaming) "Stop" else "Send"
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                IconButton(onClick = onAttachClick) {
                    Icon(Icons.Default.AttachFile, contentDescription = "Attach")
                }

                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.CloseFullscreen else Icons.Default.Expand,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                    )
                }
            }
        }
    }
}

// ------------- ⚙️ ERROR BANNER --------------------------------------------
@Composable
fun ErrorBanner(error: String?, onDismiss: () -> Unit) {
    AnimatedVisibility(
        visible = error != null,
        enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut()
    ) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

