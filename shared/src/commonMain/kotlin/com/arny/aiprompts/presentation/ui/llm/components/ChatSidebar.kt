package com.arny.aiprompts.presentation.ui.llm.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arny.aiprompts.data.model.ChatSession
import com.arny.aiprompts.data.model.getLastMessagePreview
import com.arny.aiprompts.presentation.ui.llm.theme.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Боковая панель со списком чатов.
 * Стиль как в Claude / ChatGPT.
 *
 * @param sessions Список сессий
 * @param selectedSessionId ID выбранной сессии
 * @param onSessionSelected Callback выбора сессии
 * @param onNewChat Callback создания нового чата
 * @param onDeleteSession Callback удаления сессии
 * @param onRenameSession Callback переименования сессии
 * @param onArchiveSession Callback архивирования сессии
 * @param modifier Модификатор
 */
@Composable
fun ChatSidebar(
    sessions: List<ChatSession>,
    selectedSessionId: String?,
    onSessionSelected: (String) -> Unit,
    onNewChat: () -> Unit,
    onDeleteSession: (String) -> Unit,
    onRenameSession: (String, String) -> Unit,
    onArchiveSession: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.sidebarBackground())
            .padding(8.dp)
    ) {
        // Заголовок и кнопка нового чата
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Чаты",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            IconButton(onClick = onNewChat) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Новый чат"
                )
            }
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Список чатов
        if (sessions.isEmpty()) {
            EmptySidebarState(onNewChat)
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(
                    items = sessions,
                    key = { it.id }
                ) { session ->
                    ChatSessionItem(
                        session = session,
                        isSelected = session.id == selectedSessionId,
                        onClick = { onSessionSelected(session.id) },
                        onDelete = { onDeleteSession(session.id) },
                        onRename = { newName -> onRenameSession(session.id, newName) },
                        onArchive = { onArchiveSession(session.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySidebarState(
    onNewChat: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.ChatBubble,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Нет чатов",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onNewChat) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Создать чат")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
private fun ChatSessionItem(
    session: ChatSession,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit,
    onArchive: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(session.name) }

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.sidebarSelectedItem()
            else -> MaterialTheme.colorScheme.sidebarBackground()
        },
        label = "background"
    )

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(backgroundColor)
                .clickable(onClick = onClick)
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Иконка и текст
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ChatBubbleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Preview последнего сообщения
                    val preview = session.getLastMessagePreview(30)
                    if (preview.isNotBlank()) {
                        Text(
                            text = preview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Время и токены
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = formatRelativeTime(session.updatedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )

                        val tokenCount = session.messages.sumOf { it.tokenCount ?: 0 }
                        if (tokenCount > 0) {
                            Text(
                                text = "• ${tokenCount} токенов",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            // Меню действий
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Меню",
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Переименовать") },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            showMenu = false
                            showRenameDialog = true
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Архивировать") },
                        leadingIcon = {
                            Icon(Icons.Default.Archive, null, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            showMenu = false
                            onArchive()
                        }
                    )

                    Divider()

                    DropdownMenuItem(
                        text = { Text("Удалить", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }

    // Диалог переименования
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Переименовать чат") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Название") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onRename(newName)
                        }
                        showRenameDialog = false
                    }
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@OptIn(ExperimentalTime::class)
private fun formatRelativeTime(timestamp: Long): String {
    return try {
        val instant = Instant.fromEpochMilliseconds(timestamp)
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        val diff = now.epochSeconds - instant.epochSeconds

        when {
            diff < 60 -> "только что"
            diff < 3600 -> "${diff / 60} мин назад"
            diff < 86400 -> "${diff / 3600} ч назад"
            diff < 604800 -> "${diff / 86400} дн назад"
            else -> {
                val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
                val date = localDateTime.date
                "${date.dayOfMonth}.${date.monthNumber}.${date.year}"
            }
        }
    } catch (e: Exception) {
        ""
    }
}
