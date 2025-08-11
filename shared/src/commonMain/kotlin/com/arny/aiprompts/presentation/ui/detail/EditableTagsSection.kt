package com.arny.aiprompts.presentation.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun EditableTagsSection(
    title: String,
    tags: List<String>,
    isEditing: Boolean,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit
) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tags.forEach { tag ->
                if (isEditing) {
                    InputChip(
                        selected = false,
                        onClick = { /* Можно сделать для выделения */ },
                        label = { Text(tag) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Удалить тег",
                                modifier = Modifier.size(InputChipDefaults.IconSize)
                                    .clickable { onRemoveTag(tag) }
                            )
                        }
                    )
                } else {
                    SuggestionChip(
                        onClick = { /* Можно реализовать поиск по тегу */ },
                        label = { Text(tag) }
                    )
                }
            }
        }
        // Поле для добавления нового тега в режиме редактирования
        if (isEditing) {
            var newTagText by remember { mutableStateOf("") }
            OutlinedTextField(
                value = newTagText,
                onValueChange = { newTagText = it },
                label = { Text("Добавить тег") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                trailingIcon = {
                    IconButton(onClick = {
                        if (newTagText.isNotBlank()) {
                            onAddTag(newTagText)
                            newTagText = ""
                        }
                    }) {
                        Icon(Icons.Default.Add, "Добавить")
                    }
                }
            )
        }
    }
}
