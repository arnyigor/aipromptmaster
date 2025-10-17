package com.arny.aipromptmaster.presentation.ui.chat

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arny.aipromptmaster.domain.models.FileAttachment
import com.arny.aipromptmaster.domain.models.errors.DomainError
import com.arny.aipromptmaster.domain.results.DataResult
import com.arny.aipromptmaster.domain.utils.FileUtils.formatFileSize
import com.arny.aipromptmaster.presentation.ui.chat.compose.ApiErrorDialog
import com.arny.aipromptmaster.presentation.ui.chat.compose.DestructiveConfirmDialog
import com.arny.aipromptmaster.presentation.utils.asString
import io.noties.markwon.Markwon

@Composable
fun ChatComposeScreen(
    viewModel: ChatViewModel,
    markwon: Markwon,
    onNavigateToFileViewer: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onAttachFileClick: () -> Unit,
    onCopyToClipboard: (String) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val estimatedTokens by viewModel.estimatedTokens.collectAsStateWithLifecycle()
    val isAccurate by viewModel.isAccurate.collectAsStateWithLifecycle()
    val conversationFiles by viewModel.conversationFiles.collectAsStateWithLifecycle() // 🔥 НОВОЕ
    val selectModeResult by viewModel.selectedModelResult.collectAsStateWithLifecycle()

    // 🔥 Состояние для обычных ошибок (карточка)
    var errorState by remember { mutableStateOf<ErrorState?>(null) }

    // 🔥 Состояние для критичных API ошибок (диалог)
    var apiErrorDialogState by remember { mutableStateOf<DomainError.Api?>(null) }

    // 🔥 Состояние диалога очистки чата
    var showClearChatDialog by remember { mutableStateOf(false) }

    // 🔥 1. Слушаем ошибки из selectedModelResult
    LaunchedEffect(selectModeResult) {
        if (selectModeResult is DataResult.Error) {
            val error = (selectModeResult as DataResult.Error<*>).exception
            if (error is DomainError.Api && error.code in listOf(401, 403)) {
                // Критичные ошибки → Dialog
                apiErrorDialogState = error
            } else {
                // Остальные → Карточка
                errorState = ErrorState(
                    message = error?.message ?: "Ошибка загрузки модели",
                    severity = ErrorSeverity.ERROR
                )
            }
        }
    }

    // 🔥 2. Слушаем ошибки из uiEvents
    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is ChatUiEvent.ShowError -> {
                    val error = event.error
                    when (error) {
                        is DomainError.Api -> {
                            if (error.code in listOf(401, 403)) {
                                // Критичные API ошибки → Dialog
                                apiErrorDialogState = error
                            } else {
                                // Остальные API ошибки → Карточка
                                errorState = ErrorState(
                                    message = handleErrorMessage(
                                        context,
                                        error,
                                        error.detailedMessage
                                    ),
                                    severity = when (error.code) {
                                        429 -> ErrorSeverity.WARNING
                                        else -> ErrorSeverity.ERROR
                                    }
                                )
                            }
                        }

                        is DomainError.Local -> {
                            errorState = ErrorState(
                                message = handleErrorMessage(context, error, "Локальная ошибка"),
                                severity = ErrorSeverity.WARNING
                            )
                        }

                        is DomainError.Generic -> {
                            errorState = ErrorState(
                                message = handleErrorMessage(context, error, "Неизвестная ошибка"),
                                severity = ErrorSeverity.ERROR
                            )
                        }

                        else -> {
                            errorState = ErrorState(
                                message = error.message ?: "Произошла ошибка",
                                severity = ErrorSeverity.ERROR
                            )
                        }
                    }
                }

                is ChatUiEvent.RequestClearChat -> {
                    showClearChatDialog = true
                }

                else -> {}
            }
        }
    }

    // 🔥 Показываем API Error Dialog
    apiErrorDialogState?.let { error ->
        ApiErrorDialog(
            error = error,
            onDismiss = { apiErrorDialogState = null },
            onNavigateToSettings = onNavigateToSettings
        )
    }

    if (showClearChatDialog) {
        DestructiveConfirmDialog(
            title = "Очистить историю чата?",
            message = "При удалении истории чата удалится история разговора и ИИ модель не будет помнить о чем был разговор.",
            confirmText = "Очистить",
            dismissText = "Отмена",
            onConfirm = {
                viewModel.onRemoveChatHistory()
            },
            onDismiss = {
                showClearChatDialog = false
            }
        )
    }

    MaterialTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Token info
                AnimatedVisibility(
                    visible = estimatedTokens > 0,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    TokenInfoCard(
                        tokenCount = estimatedTokens,
                        isAccurate = isAccurate
                    )
                }

                // 🔥 Error card (для некритичных ошибок)
                AnimatedVisibility(
                    visible = errorState != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    errorState?.let { state ->
                        ErrorCard(
                            message = state.message,
                            severity = state.severity,
                            onDismiss = { errorState = null }
                        )
                    }
                }

                // Messages list
                ChatMessagesList(
                    messages = uiState.messages,
                    isStreamingResponse = uiState.isStreamingResponse,
                    markwon = markwon,
                    modelName = when (val result = selectModeResult) {
                        is DataResult.Success -> result.data.name
                        else -> ""
                    },
                    conversationFiles = conversationFiles, // 🔥 Передаем файлы чата
                    onCopyMessage = onCopyToClipboard,
                    onRegenerateMessage = { messageId -> // 🔥 ИЗМЕНЕНО
                        viewModel.regenerateMessage(messageId)
                    },
                    modifier = Modifier.weight(1f)
                )

                AnimatedVisibility(
                    visible = conversationFiles.isNotEmpty(),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    ConversationFilesRow(
                        files = conversationFiles,
                        onRemoveFile = { viewModel.removeFileFromChat(it) }
                    )
                }

                // Message input
                MessageInputCard(
                    isLoading = uiState.isLoading,
                    onSendMessage = { viewModel.sendMessage(it) },
                    onAttachFile = onAttachFileClick,
                    onCancelRequest = { viewModel.cancelCurrentRequest() },
                    onTextChange = { viewModel.updateInputText(it) }
                )
            }
        }
    }
}


/**
 * Строка с файлами чата (внизу экрана, как в RikkaHub)
 */
@Composable
fun ConversationFilesRow(
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
            key = { it }
        ) { file ->
            ConversationFileChip(
                file = file,
                onRemove = { onRemoveFile(file.id) }
            )
        }
    }
}

@Composable
private fun ConversationFileChip(
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
                containerColor = MaterialTheme.colorScheme.primaryContainer
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
                    Icons.AutoMirrored.Filled.InsertDriveFile,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Column(modifier = Modifier.widthIn(max = 150.dp)) {
                    Text(
                        text = file.fileName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatFileSize(file.fileSize),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Удалить",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

private fun handleErrorMessage(
    context: Context,
    error: DomainError,
    default: String
): String {
    val shMessage = error.stringHolder.asString(context)
        .takeIf { it.isNotBlank() }
    val message = when (error) {
        is DomainError.Api -> error.detailedMessage
        else -> error.message
    }

    return buildString {
        val firstPart = shMessage ?: message
        val secondPart = if (shMessage != null && message != null) message else null

        if (!firstPart.isNullOrBlank()) {
            append(firstPart)
        }
        if (!secondPart.isNullOrBlank()) {
            // Добавляем пробел между частями, если обе не пусты, по желанию можно изменить разделитель
            if (!firstPart.isNullOrBlank()) {
                append(" ")
            }
            append(secondPart)
        }
        if (isBlank()) {
            append(default)
        }
    }
}

data class ErrorState(
    val message: String,
    val severity: ErrorSeverity
)

enum class ErrorSeverity {
    ERROR, WARNING, INFO
}
