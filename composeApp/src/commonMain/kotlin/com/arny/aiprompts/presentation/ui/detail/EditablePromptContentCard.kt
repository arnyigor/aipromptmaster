package com.arny.aiprompts.presentation.ui.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun EditablePromptContentCard(
    modifier: Modifier = Modifier,
    language: String,
    viewText: String,
    editText: String, // Текст из draftPrompt
    isEditing: Boolean,
    onValueChange: (String) -> Unit,
    onCopyClick: () -> Unit
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(language, style = MaterialTheme.typography.titleLarge)
                // Кнопка копирования доступна только в режиме просмотра
                if (!isEditing) {
                    IconButton(onClick = onCopyClick) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Копировать")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            if (isEditing) {
                OutlinedTextField(
                    value = editText,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 150.dp),
                    label = { Text("Содержимое промпта") }
                )
            } else {
                SelectionContainer {
                    Text(viewText, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
