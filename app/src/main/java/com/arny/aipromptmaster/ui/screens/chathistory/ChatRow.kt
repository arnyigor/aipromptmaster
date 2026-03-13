package com.arny.aipromptmaster.ui.screens.chathistory

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arny.aipromptmaster.domain.models.Chat
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalFoundationApi::class)
@Preview(showBackground = true)
@Composable
fun PreviewChatRow() {
    MaterialTheme {          // Wrap in your app theme if you have one
        Column(modifier = Modifier.padding(8.dp)) {
            ChatRow(
                chat = Chat(
                    id = "1",
                    name = "Alice",
                    timestamp = System.currentTimeMillis(),
                    lastMessage = "Hey, how are you?"
                ),
                onClick = {},
                onLongClick = {}
            )
            ChatRow(
                chat = Chat(
                    id = "2",
                    name = "Bob",
                    timestamp = System.currentTimeMillis() - 3600_000,
                    lastMessage = null   // Example of a missing message
                ),
                onClick = {},
                onLongClick = {}
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class) // Для combinedClickable (long click)
@Composable
fun ChatRow(
    chat: Chat,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            // Обработка кликов: и обычного, и долгого
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // === Левая часть: Имя и последнее сообщение ===
            Column(
                modifier = Modifier.weight(1f) // Занимает все доступное место
            ) {
                Text(
                    text = chat.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = chat.lastMessage ?: "Нет сообщений", // Твой ресурс R.string.no_messages
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // === Правая часть: Дата/Время ===
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                // В KMP форматирование даты лучше вынести в отдельную функцию
                text = formatChatDate(chat.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Возвращает строку вида «yyyy‑MM‑dd HH:mm».
 */
@OptIn(ExperimentalTime::class, FormatStringsInDatetimeFormats::class)
fun formatChatDate(timestamp: Long, pattern: String = "yyyy-MM-dd HH:mm"): String {
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return local.format(LocalDateTime.Format {
        byUnicodePattern(pattern)
    })
}