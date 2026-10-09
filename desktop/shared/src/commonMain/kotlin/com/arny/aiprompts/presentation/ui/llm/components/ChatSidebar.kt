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
    Column(modifier.fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Чаты", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onNewChat) { Icon(Icons.Default.Add, "Новый чат") }
        }
        HorizontalDivider()
        com.arny.sharedui.ConversationList(
            sessions, key = { it.id }, modifier = Modifier.weight(1f),
            emptyContent = { EmptySidebarState(onNewChat) },
        ) { session ->
            ChatSessionItem(session, session.id == selectedSessionId,
                { onSessionSelected(session.id) }, { onDeleteSession(session.id) },
                { onRenameSession(session.id, it) }, { onArchiveSession(session.id) })
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
    var newName by remember(session.id, session.name) { mutableStateOf(session.name) }
    com.arny.sharedui.ConversationRow(
        title = session.name, preview = session.getLastMessagePreview(100),
        isSelected = isSelected, onClick = onClick,
        metadata = {
            Text(formatRelativeTime(session.updatedAt), style = MaterialTheme.typography.labelSmall)
            val tokenCount = session.messages.sumOf { it.tokenCount ?: 0 }
            if (tokenCount > 0) Text("$tokenCount токенов", style = MaterialTheme.typography.labelSmall)
        },
        actions = {
            Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "Действия с чатом") }
                DropdownMenu(showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Переименовать") }, onClick = { showMenu = false; showRenameDialog = true })
                    DropdownMenuItem(text = { Text("Архивировать") }, onClick = { showMenu = false; onArchive() })
                    DropdownMenuItem(text = { Text("Удалить") }, onClick = { showMenu = false; onDelete() })
                }
            }
        },
    )
    if (showRenameDialog) AlertDialog(
        onDismissRequest = { showRenameDialog = false }, title = { Text("Переименовать чат") },
        text = { OutlinedTextField(newName, { newName = it }, label = { Text("Название") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onRename(newName.trim()); showRenameDialog = false }, enabled = newName.isNotBlank()) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Отмена") } },
    )
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
