package com.arny.aiprompts.presentation.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
fun EditablePromptContentCard(
    language: String,
    viewText: String,
    editText: String,
    isEditing: Boolean,
    onValueChange: (String) -> Unit,
    onCopyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language,
                    style = MaterialTheme.typography.titleMedium
                )
                Row {
                    // Переключатель режима просмотра только в режиме чтения
                    if (!isEditing) {
                        var showMarkdown by remember { mutableStateOf(true) }

                        IconButton(onClick = { showMarkdown = !showMarkdown }) {
                            Icon(
                                imageVector = if (showMarkdown) Icons.Default.Code else Icons.Default.Visibility,
                                contentDescription = if (showMarkdown) "Показать исходник" else "Показать Markdown"
                            )
                        }
                    }

                    IconButton(onClick = onCopyClick) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Копировать")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isEditing) {
                // Режим редактирования - обычное текстовое поле
                OutlinedTextField(
                    value = editText,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    placeholder = { Text("Введите содержимое промпта (поддерживается Markdown)...") }
                )
            } else {
                // Режим просмотра
                var showMarkdown by remember { mutableStateOf(true) }

                if (showMarkdown) {
                    // Отображение с markdown форматированием
                    RichMarkdownDisplay(
                        content = viewText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                    )
                } else {
                    // Отображение исходного markdown текста
                    Text(
                        text = viewText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    }
}
