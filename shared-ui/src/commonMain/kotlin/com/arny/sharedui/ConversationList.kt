package com.arny.sharedui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun <T> ConversationList(
    conversations: List<T>,
    key: (T) -> String,
    modifier: Modifier = Modifier,
    emptyContent: @Composable () -> Unit = { Text("История чатов пуста") },
    row: @Composable (T) -> Unit,
) {
    if (conversations.isEmpty()) Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { emptyContent() }
    else LazyColumn(modifier, contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(conversations, key = key) { row(it) }
    }
}

@Composable
fun ConversationRow(
    title: String,
    preview: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    metadata: @Composable () -> Unit = {},
    actions: @Composable () -> Unit = {},
) {
    Surface(
        modifier.fillMaxWidth().semantics { selected = isSelected }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(preview.ifBlank { "Нет сообщений" }, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                metadata()
            }
            actions()
        }
    }
}
