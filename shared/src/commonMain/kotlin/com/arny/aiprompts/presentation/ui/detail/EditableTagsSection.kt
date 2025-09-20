package com.arny.aiprompts.presentation.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun EditableTagsSection(
    title: String,
    tags: List<String>,
    isEditing: Boolean,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    availableTags: List<String> = emptyList(),
    enableColorCoding: Boolean = true
) {
    @Composable
    fun getTagColor(tag: String): Color {
        if (!enableColorCoding) return MaterialTheme.colorScheme.primary

        val hash = tag.hashCode()
        val hue = (hash % 360).toFloat()
        return Color.hsl(hue, 0.6f, 0.5f)
    }
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tags.forEach { tag ->
                val tagColor = getTagColor(tag)
                if (isEditing) {
                    InputChip(
                        selected = false,
                        onClick = { /* Можно сделать для выделения */ },
                        label = {
                            Text(
                                tag,
                                color = if (enableColorCoding) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        colors = InputChipDefaults.inputChipColors(
                            containerColor = if (enableColorCoding) tagColor else MaterialTheme.colorScheme.surface,
                            labelColor = if (enableColorCoding) Color.White else MaterialTheme.colorScheme.onSurface
                        ),
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Удалить тег",
                                tint = if (enableColorCoding) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(InputChipDefaults.IconSize)
                                    .clickable { onRemoveTag(tag) }
                            )
                        }
                    )
                } else {
                    SuggestionChip(
                        onClick = { /* Можно реализовать поиск по тегу */ },
                        label = { Text(tag) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = if (enableColorCoding) tagColor.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface,
                            labelColor = if (enableColorCoding) tagColor else MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }
        }
        // Поле для добавления нового тега в режиме редактирования
        if (isEditing) {
            var newTagText by remember { mutableStateOf("") }
            var showSuggestions by remember { mutableStateOf(false) }

            // Фильтруем доступные теги для автодополнения
            val suggestions = remember(newTagText, availableTags) {
                if (newTagText.isBlank()) emptyList()
                else availableTags.filter {
                    it.contains(newTagText, ignoreCase = true) &&
                    !tags.contains(it) // Не показываем уже добавленные теги
                }.take(5) // Ограничиваем количество предложений
            }

            Column {
                OutlinedTextField(
                    value = newTagText,
                    onValueChange = {
                        newTagText = it
                        showSuggestions = it.isNotBlank()
                    },
                    label = { Text("Добавить тег") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    trailingIcon = {
                        IconButton(onClick = {
                            if (newTagText.isNotBlank()) {
                                onAddTag(newTagText)
                                newTagText = ""
                                showSuggestions = false
                            }
                        }) {
                            Icon(Icons.Default.Add, "Добавить")
                        }
                    }
                )

                // Автодополнение
                if (showSuggestions && suggestions.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            suggestions.forEach { suggestion ->
                                Text(
                                    text = suggestion,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onAddTag(suggestion)
                                            newTagText = ""
                                            showSuggestions = false
                                        }
                                        .padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
