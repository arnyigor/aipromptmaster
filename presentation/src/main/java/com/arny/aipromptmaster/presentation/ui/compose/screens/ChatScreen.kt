package com.arny.aipromptmaster.presentation.ui.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.arny.aipromptmaster.presentation.ui.chat.ChatViewModel
import com.arny.aipromptmaster.presentation.ui.compose.components.*
import com.arny.aipromptmaster.presentation.ui.compose.theme.AIPromptMasterTheme
import com.arny.aipromptmaster.presentation.ui.compose.theme.AppDimensions
import kotlinx.coroutines.launch
import com.arny.aipromptmaster.presentation.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavController,
    chatId: String?,
    viewModel: ChatViewModel = viewModel()
) {
    AIPromptMasterTheme {
        val chatState by viewModel.chatState.collectAsState()
        val chatData by viewModel.chatData.collectAsState()
        val attachments by viewModel.attachments.collectAsState()
        val clipboardManager = LocalClipboardManager.current
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val listState = rememberLazyListState()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(chatData.selectedModel?.name ?: "Чат") },
                    actions = {
                        IconButton(onClick = { /* TODO: Выбор модели */ }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_tune),
                                contentDescription = "Выбор модели"
                            )
                        }
                        IconButton(onClick = { /* TODO: Настройки */ }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_settings),
                                contentDescription = "Настройки"
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Список сообщений
                ChatMessagesList(
                    messages = chatData.messages,
                    modelName = chatData.selectedModel?.name.orEmpty(),
                    isStreaming = chatState is com.arny.aipromptmaster.presentation.ui.chat.ChatUiState.Streaming,
                    modifier = Modifier.weight(1f),
                    listState = listState,
                    onCopyMessage = { text ->
                        clipboardManager.setText(AnnotatedString(text))
                    }
                )

                // Индикатор токенов
                if (chatData.messages.isNotEmpty()) {
                    TokenInfoCard(
                        tokenCount = "~150", // TODO: Реальный расчет токенов
                        modifier = Modifier.padding(horizontal = AppDimensions.margin_12)
                    )
                }

                // Карточка ошибки
                if (chatState is com.arny.aipromptmaster.presentation.ui.chat.ChatUiState.Error) {
                    ErrorCard(
                        message = (chatState as com.arny.aipromptmaster.presentation.ui.chat.ChatUiState.Error).message,
                        onDismiss = { /* TODO: Скрыть ошибку */ },
                        modifier = Modifier.padding(AppDimensions.margin_12)
                    )
                }

                // Карточка генерации
                if (chatState is com.arny.aipromptmaster.presentation.ui.chat.ChatUiState.Streaming) {
                    GeneratingCard(
                        statusText = "Генерация ответа...",
                        onCancel = { viewModel.cancelCurrentRequest() }
                    )
                }

                // Поле ввода сообщения
                MessageInputCard(
                    attachments = attachments,
                    onSendMessage = { message ->
                        viewModel.sendMessage(message)
                    },
                    onAttachFile = { /* TODO: Прикрепить файл */ },
                    onRemoveAttachment = { attachmentId ->
                        viewModel.removeAttachment(attachmentId)
                    },
                    enabled = chatState !is com.arny.aipromptmaster.presentation.ui.chat.ChatUiState.Streaming
                )
            }
        }

        // Запускаем скролл к концу списка при добавлении новых сообщений
        LaunchedEffect(chatData.messages.size) {
            if (chatData.messages.isNotEmpty()) {
                listState.animateScrollToItem(chatData.messages.size - 1)
            }
        }
    }
}

@Composable
private fun ChatMessagesList(
    messages: List<com.arny.aipromptmaster.domain.models.ChatMessage>,
    modelName: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onCopyMessage: (String) -> Unit
) {
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(
            top = AppDimensions.margin_16,
            bottom = AppDimensions.margin_16
        ),
        verticalArrangement = Arrangement.spacedBy(AppDimensions.margin_8)
    ) {
        items(messages) { message ->
            when (message.role) {
                com.arny.aipromptmaster.domain.models.ChatRole.USER -> {
                    if (message.fileAttachment != null) {
                        FileMessage(
                            fileName = message.fileAttachment.fileName,
                            fileSize = formatFileSize(message.fileAttachment.size)
                        )
                    } else {
                        UserMessage(
                            text = message.content,
                            onCopyClick = { onCopyMessage(message.content) },
                            onRegenerateClick = { /* TODO: Регенерация */ }
                        )
                    }
                }
                com.arny.aipromptmaster.domain.models.ChatRole.ASSISTANT -> {
                    AIMessage(
                        text = message.content,
                        modelName = modelName,
                        isTyping = isStreaming && messages.lastOrNull() == message,
                        onCopyClick = { onCopyMessage(message.content) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TokenInfoCard(
    tokenCount: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = AppDimensions.elevationLarge)
    ) {
        Row(
            modifier = Modifier
                .padding(AppDimensions.margin_8)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_token_stats),
                contentDescription = null,
                modifier = Modifier
                    .size(AppDimensions.iconSizeSmall)
                    .padding(end = AppDimensions.margin_4),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )

            Text(
                text = tokenCount,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )

            Text(
                text = " tokens",
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = AppDimensions.margin_4)
            )
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = AppDimensions.elevationExtraLarge)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimensions.margin_12),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_error),
                contentDescription = null,
                modifier = Modifier
                    .size(AppDimensions.iconSizeMedium)
                    .padding(end = AppDimensions.margin_12),
                tint = MaterialTheme.colorScheme.error
            )

            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onDismiss) {
                Icon(
                    painter = painterResource(id = R.drawable.outline_close_24),
                    contentDescription = "Закрыть",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun GeneratingCard(
    statusText: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(AppDimensions.margin_12),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = AppDimensions.elevationExtraLarge),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(AppDimensions.cornerRadiusExtraLarge)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimensions.margin_12),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(AppDimensions.iconSizeMedium)
                    .padding(end = AppDimensions.margin_12)
            )

            Text(
                text = statusText,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )

            TextButton(onClick = onCancel) {
                Text("Отмена")
            }
        }
    }
}

@Composable
private fun MessageInputCard(
    attachments: List<com.arny.aipromptmaster.presentation.ui.chat.UiAttachment>,
    onSendMessage: (String) -> Unit,
    onAttachFile: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var messageText by remember { mutableStateOf("") }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(AppDimensions.margin_12),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = AppDimensions.elevationExtraLarge),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(AppDimensions.cornerRadiusExtraLarge)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimensions.margin_8)
        ) {
            // Прикрепленные файлы
            if (attachments.isNotEmpty()) {
                AttachmentChips(
                    attachments = attachments,
                    onRemoveAttachment = onRemoveAttachment,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = AppDimensions.margin_8)
                )
            }

            // Поле ввода и кнопки
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onAttachFile,
                    enabled = enabled
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.outline_attach_file_24),
                        contentDescription = "Прикрепить файл",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = AppDimensions.margin_8),
                    placeholder = {
                        Text(
                            "Введите сообщение...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent
                    ),
                    maxLines = 5,
                    enabled = enabled
                )

                IconButton(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            onSendMessage(messageText)
                            messageText = ""
                        }
                    },
                    enabled = messageText.isNotBlank() && enabled
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_plus),
                        contentDescription = "Отправить",
                        tint = if (messageText.isNotBlank() && enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentChips(
    attachments: List<com.arny.aipromptmaster.presentation.ui.chat.UiAttachment>,
    onRemoveAttachment: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(AppDimensions.margin_8)
    ) {
        attachments.forEach { attachment ->
            InputChip(
                selected = false,
                onClick = { /* TODO: Показать детали файла */ },
                label = {
                    Text("${attachment.displayName} • ${formatFileSize(attachment.size)}")
                },
                avatar = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_file_text),
                        contentDescription = null,
                        modifier = Modifier.size(InputChipDefaults.AvatarSize)
                    )
                },
                trailingIcon = {
                    if (attachment.uploadStatus != com.arny.aipromptmaster.presentation.ui.chat.UploadStatus.UPLOADING) {
                        IconButton(
                            onClick = { onRemoveAttachment(attachment.id) },
                            modifier = Modifier.size(18.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.outline_close_24),
                                contentDescription = "Удалить"
                            )
                        }
                    }
                }
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}