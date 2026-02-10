package com.arny.aiprompts.presentation.ui.llm.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arny.aiprompts.data.model.ChatMessage
import com.arny.aiprompts.data.model.ChatMessageRole
import com.arny.aiprompts.data.model.MessageStatus
import com.arny.aiprompts.presentation.ui.llm.theme.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Карточка сообщения в чате.
 * Поддерживает Markdown, код, различные статусы.
 *
 * @param message Сообщение
 * @param isLast Флаг последнего сообщения (для автоскролла)
 * @param onRetry Callback для повтора
 * @param onEdit Callback для редактирования
 * @param onCopy Callback для копирования
 * @param modifier Модификатор
 */
@OptIn(ExperimentalTime::class)
@Composable
fun ChatMessageCard(
    message: ChatMessage,
    isLast: Boolean = false,
    onRetry: () -> Unit = {},
    onEdit: () -> Unit = {},
    onCopy: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isUser = message.role == ChatMessageRole.USER
    val isSystem = message.role == ChatMessageRole.SYSTEM

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(300))
            .padding(vertical = 4.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Контейнер сообщения
        Row(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            // Аватар для AI
            if (!isUser && !isSystem) {
                AIAvatar(modifier = Modifier.padding(end = 8.dp))
            }

            Column(
                modifier = Modifier.weight(1f, fill = false),
                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
            ) {
                // Бабл сообщения
                MessageBubble(
                    message = message,
                    isUser = isUser,
                    isSystem = isSystem
                )

                // Статус и действия
                if (!isSystem) {
                    MessageActions(
                        message = message,
                        onRetry = onRetry,
                        onEdit = onEdit,
                        onCopy = onCopy,
                        isUser = isUser
                    )
                }
            }
        }
    }
}

@Composable
private fun AIAvatar(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(32.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.aiAvatarBackground()
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Chat,
                contentDescription = "AI",
                tint = MaterialTheme.colorScheme.aiAvatarIcon(),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    isUser: Boolean,
    isSystem: Boolean
) {
    val backgroundColor = when {
        isSystem -> MaterialTheme.colorScheme.systemMessageBackground()
        isUser -> MaterialTheme.colorScheme.userMessageBackground()
        message.isFailed() -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.aiMessageBackground()
    }

    val shape = when {
        isSystem -> RoundedCornerShape(4.dp)
        isUser -> RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = 16.dp,
            bottomEnd = 4.dp
        )
        else -> RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = 4.dp,
            bottomEnd = 16.dp
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = backgroundColor,
        tonalElevation = if (isUser) 0.dp else 1.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Заголовок для system сообщений
            if (isSystem) {
                Text(
                    text = "System",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            // Текст сообщения (Markdown)
            MessageContent(
                content = message.content,
                isUser = isUser
            )

            // Индикатор статуса
            if (message.isStreaming()) {
                Spacer(modifier = Modifier.height(8.dp))
                StreamingIndicator()
            }
        }
    }
}

@Composable
private fun MessageContent(
    content: String,
    isUser: Boolean
) {
    // TODO: Заменить на полноценный Markdown рендерер
    // Пока используем простой текст с базовым форматированием
    Text(
        text = content,
        style = MaterialTheme.typography.bodyLarge,
        color = if (isUser) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    )
}

@Composable
private fun StreamingIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(12.dp),
            strokeWidth = 1.5.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Думает...",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun MessageActions(
    message: ChatMessage,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    isUser: Boolean
) {
    Row(
        modifier = Modifier
            .padding(top = 4.dp, start = if (isUser) 0.dp else 40.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Время
        Text(
            text = formatMessageTime(message.timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )

        // Статус ошибки
        if (message.isFailed()) {
            TextButton(
                onClick = onRetry,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Retry",
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "Повторить",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        // Кнопки действий
        if (!isUser) {
            // Копировать
            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            // Редактировать (только для AI сообщений с ошибкой)
            if (message.isFailed()) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            // Редактировать свое сообщение
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@OptIn(ExperimentalTime::class)
private fun formatMessageTime(timestamp: Long): String {
    return try {
        val instant = Instant.fromEpochMilliseconds(timestamp)
        val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val time = localDateTime.time
        "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
    } catch (e: Exception) {
        ""
    }
}

/**
 * Сообщение о пустом чате.
 */
@Composable
fun EmptyChatPlaceholder(
    selectedModel: String?,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Chat,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (selectedModel != null) {
                "Начните диалог с $selectedModel"
            } else {
                "Выберите модель для начала"
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = onNewChat) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Создать новый чат")
        }
    }
}
