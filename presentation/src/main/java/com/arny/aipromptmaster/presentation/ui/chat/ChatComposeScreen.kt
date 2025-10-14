package com.arny.aipromptmaster.presentation.ui.chat

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arny.aipromptmaster.domain.models.errors.DomainError
import com.arny.aipromptmaster.domain.results.DataResult
import com.arny.aipromptmaster.presentation.ui.chat.compose.ApiErrorDialog
import com.arny.aipromptmaster.presentation.ui.chat.compose.DestructiveConfirmDialog
import com.arny.aipromptmaster.presentation.utils.asString
import io.noties.markwon.Markwon

const val TAG = "ChatComposeScreen"

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
    val attachedFiles by viewModel.attachedFiles.collectAsStateWithLifecycle()
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
                                    message = handleErrorMessage(context, error, error.detailedMessage),
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
                    onCopyMessage = onCopyToClipboard,
                    onRegenerateMessage = { text ->
                        viewModel.updateInputText(text)
                    },
                    onViewFile = onNavigateToFileViewer,
                    modifier = Modifier.weight(1f)
                )

                // Attached files
                AnimatedVisibility(
                    visible = attachedFiles.isNotEmpty(),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    AttachedFilesRow(
                        files = attachedFiles,
                        onRemoveFile = { viewModel.removeAttachedFile(it) }
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
