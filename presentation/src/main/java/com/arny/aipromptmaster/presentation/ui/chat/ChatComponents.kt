package com.arny.aipromptmaster.presentation.ui.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.domain.models.ChatRole
import com.arny.aipromptmaster.domain.models.FileAttachment
import com.arny.aipromptmaster.domain.utils.FileUtils.formatFileSize
import io.noties.markwon.Markwon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ============================================================
// ERROR CARD
// ============================================================

@Composable
fun ErrorCard(
    modifier: Modifier = Modifier,
    message: String,
    severity: ErrorSeverity = ErrorSeverity.ERROR,
    onDismiss: () -> Unit,
) {
    val (backgroundColor, textColor, icon) = when (severity) {
        ErrorSeverity.ERROR -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Filled.ErrorOutline
        )
        ErrorSeverity.WARNING -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            Icons.Filled.Warning
        )
        ErrorSeverity.INFO -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            Icons.Filled.Info
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(24.dp)
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Закрыть",
                    tint = textColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ============================================================
// TOKEN INFO CARD
// ============================================================

@Composable
fun TokenInfoCard(
    tokenCount: Int,
    isAccurate: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Filled.Memory,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "≈ $tokenCount tokens",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.weight(1f))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isAccurate)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        if (isAccurate) Icons.Filled.CheckCircle else Icons.Filled.Info,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = if (isAccurate)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isAccurate) "Точно" else "Примерно",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isAccurate)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ============================================================
// CHAT MESSAGES LIST
// ============================================================

@Composable
fun ChatMessagesList(
    messages: List<ChatMessage>,
    isStreamingResponse: Boolean,
    markwon: Markwon,
    modelName: String,
    conversationFiles: List<FileAttachment>,
    onCopyMessage: (String) -> Unit,
    onRegenerateMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Отслеживаем, находимся ли внизу списка
    val isAtBottom by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem?.index == listState.layoutInfo.totalItemsCount - 1
        }
    }

    // Плавная прокрутка при новом сообщении
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && isAtBottom) {
            scope.launch {
                listState.animateScrollToItem(
                    index = messages.size - 1,
                    scrollOffset = 0
                )
            }
        }
    }

    // Плавная прокрутка при streaming
    LaunchedEffect(isStreamingResponse, messages.lastOrNull()?.content) {
        if (isStreamingResponse && messages.isNotEmpty() && isAtBottom) {
            scope.launch {
                listState.scrollToItem(
                    index = messages.size - 1,
                    scrollOffset = 0
                )
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 4.dp,
                end = 4.dp,
                top = 12.dp,
                bottom = 100.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(
                items = messages,
                key = { it.id }
            ) { message ->
                when (message.role) {
                    ChatRole.USER -> {
                        UserMessageBubble(
                            message = message,
                            markwon = markwon,
                            onCopyMessage = onCopyMessage
                        )
                    }
                    ChatRole.ASSISTANT -> {
                        AiMessageBubble(
                            message = message,
                            markwon = markwon,
                            modelName = modelName,
                            isStreaming = isStreamingResponse && message == messages.lastOrNull(),
                            conversationFiles = conversationFiles,
                            onCopyMessage = onCopyMessage,
                            onRegenerateMessage = onRegenerateMessage
                        )
                    }
                    else -> {}
                }
            }

            if (isStreamingResponse && messages.lastOrNull()?.role != ChatRole.ASSISTANT) {
                item(key = "typing_indicator") {
                    TypingIndicator()
                }
            }
        }

        // Кнопка "Вниз"
        AnimatedVisibility(
            visible = !isAtBottom && messages.isNotEmpty(),
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 120.dp)
        ) {
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        listState.animateScrollToItem(messages.size - 1)
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Прокрутить вниз",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

// ============================================================
// MESSAGE BUBBLES
// ============================================================

@Composable
fun UserMessageBubble(
    message: ChatMessage,
    markwon: Markwon,
    onCopyMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.End
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.85f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 4.dp
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                MarkdownText(
                    markdown = message.content,
                    markwon = markwon,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Actions (ТОЛЬКО копировать для пользователя)
        Row(
            modifier = Modifier.padding(top = 4.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = { onCopyMessage(message.content) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = "Копировать",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


// ChatComponents.kt - ОБНОВЛЕННАЯ ВЕРСИЯ AiMessageBubble

@Composable
fun AiMessageBubble(
    message: ChatMessage,
    markwon: Markwon,
    modelName: String,
    isStreaming: Boolean,
    conversationFiles: List<FileAttachment>,
    onCopyMessage: (String) -> Unit,
    onRegenerateMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // Model badge
        Row(
            modifier = Modifier.padding(bottom = 6.dp, start = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val modelId = message.modelId
            if (!modelId.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Filled.SmartToy,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = modelId,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        val messageFiles = conversationFiles.filter { file ->
            false // Временно отключено
        }

        if (messageFiles.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                messageFiles.forEach { file ->
                    AttachedFileCard(file = file)
                }
            }
        }

        // AI message content
        Card(
            modifier = Modifier.fillMaxWidth(0.95f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(
                topStart = 4.dp,
                topEnd = 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (isStreaming) {
                    TypewriterMarkdownText(
                        markdown = message.content,
                        markwon = markwon,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    MarkdownText(
                        markdown = message.content,
                        markwon = markwon,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Actions (ТОЛЬКО для ассистента)
        Row(
            modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Копировать
            IconButton(
                onClick = { onCopyMessage(message.content) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = "Копировать",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Регенерировать (ТОЛЬКО для ассистента)
            IconButton(
                onClick = { onRegenerateMessage(message.id) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "Регенерировать",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Карточка файла (компактная)
 */
@Composable
fun AttachedFileCard(
    file: FileAttachment,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(0.9f),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.InsertDriveFile,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatFileSize(file.fileSize),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ============================================================
// MARKDOWN TEXT HELPERS
// ============================================================

@Composable
fun MarkdownText(
    markdown: String,
    markwon: Markwon,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            android.widget.TextView(context).apply {
                val androidColor = android.graphics.Color.argb(
                    (color.alpha * 255).toInt(),
                    (color.red * 255).toInt(),
                    (color.green * 255).toInt(),
                    (color.blue * 255).toInt()
                )
                setTextColor(androidColor)

                // 🔥 ВКЛЮЧАЕМ ВЫДЕЛЕНИЕ ТЕКСТА
                setTextIsSelectable(true)

                // 🔥 Настраиваем цвет выделения (optional)
                highlightColor = android.graphics.Color.argb(
                    80, // прозрачность
                    (color.red * 255).toInt(),
                    (color.green * 255).toInt(),
                    (color.blue * 255).toInt()
                )

                // 🔥 Улучшаем отступы для лучшего UX
                setPadding(0, 8, 0, 8)
            }
        },
        update = { textView ->
            markwon.setMarkdown(textView, markdown)
        },
        modifier = modifier
    )
}

@Composable
fun TypewriterMarkdownText(
    markdown: String,
    markwon: Markwon,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    var displayedText by remember(markdown) { mutableStateOf("") }

    LaunchedEffect(markdown) {
        displayedText = ""
        markdown.forEachIndexed { index, _ ->
            displayedText = markdown.substring(0, index + 1)
            delay(30)
        }
    }

    AndroidView(
        factory = { context ->
            android.widget.TextView(context).apply {
                val androidColor = android.graphics.Color.argb(
                    (color.alpha * 255).toInt(),
                    (color.red * 255).toInt(),
                    (color.green * 255).toInt(),
                    (color.blue * 255).toInt()
                )
                setTextColor(androidColor)

                // 🔥 ВКЛЮЧАЕМ ВЫДЕЛЕНИЕ ТЕКСТА
                setTextIsSelectable(true)

                highlightColor = android.graphics.Color.argb(
                    80,
                    (color.red * 255).toInt(),
                    (color.green * 255).toInt(),
                    (color.blue * 255).toInt()
                )

                setPadding(0, 8, 0, 8)
            }
        },
        update = { textView ->
            markwon.setMarkdown(textView, displayedText)
        },
        modifier = modifier
    )
}

// ============================================================
// TYPING INDICATOR
// ============================================================

@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            TypingDot(modifier, delay = index * 150)
        }
    }
}

@Composable
private fun TypingDot(
    modifier: Modifier = Modifier,
    delay: Int = 0
) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")

    val scale by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 600,
                delayMillis = delay,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 600,
                delayMillis = delay,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = modifier
            .size(8.dp)
            .scale(scale)
            .alpha(alpha)
            .background(
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            )
    )
}

// ============================================================
// ATTACHED FILES ROW
// ============================================================

@Composable
fun AttachedFilesRow(
    files: List<FileAttachment>,
    onRemoveFile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = files,
            key = { it.id }
        ) { file ->
            AttachedFileChip(
                file = file,
                onRemove = { onRemoveFile(file.id) }
            )
        }
    }
}

@Composable
private fun AttachedFileChip(
    file: FileAttachment,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(file.id) {
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            ),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier.padding(
                    start = 12.dp,
                    end = 4.dp,
                    top = 8.dp,
                    bottom = 8.dp
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.AttachFile,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )

                Column(modifier = Modifier.widthIn(max = 150.dp)) {
                    Text(
                        text = file.fileName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatFileSize(file.fileSize),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                    )
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Remove",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}

// ============================================================
// MESSAGE INPUT CARD
// ============================================================

@Composable
fun MessageInputCard(
    isLoading: Boolean,
    onSendMessage: (String) -> Unit,
    onAttachFile: () -> Unit,
    onCancelRequest: () -> Unit,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var messageText by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(
                start = 8.dp,
                end = 8.dp,
                top = 8.dp,
                bottom = 8.dp
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onAttachFile,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Outlined.AttachFile,
                        contentDescription = "Прикрепить файл",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp, max = 120.dp)
                ) {
                    BasicTextField(
                        value = messageText,
                        onValueChange = { text ->
                            messageText = text
                            onTextChange(text)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        enabled = !isLoading,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (messageText.isNotBlank()) {
                                    onSendMessage(messageText)
                                    messageText = ""
                                    keyboardController?.hide()
                                }
                            }
                        ),
                        decorationBox = { innerTextField ->
                            if (messageText.isEmpty()) {
                                Text(
                                    text = "Введите сообщение...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                        .copy(alpha = 0.5f)
                                )
                            }
                            innerTextField()
                        }
                    )
                }

                AnimatedContent(
                    targetState = isLoading,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith
                                fadeOut(animationSpec = tween(300))
                    },
                    label = "send_cancel_button"
                ) { loading ->
                    if (loading) {
                        FilledIconButton(
                            onClick = onCancelRequest,
                            modifier = Modifier.size(40.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Отменить",
                                tint = MaterialTheme.colorScheme.onError
                            )
                        }
                    } else {
                        FilledIconButton(
                            onClick = {
                                if (messageText.isNotBlank()) {
                                    onSendMessage(messageText)
                                    messageText = ""
                                    keyboardController?.hide()
                                }
                            },
                            modifier = Modifier.size(40.dp),
                            enabled = messageText.isNotBlank(),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Отправить",
                                tint = if (messageText.isNotBlank())
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isLoading,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }
        }
    }
}
