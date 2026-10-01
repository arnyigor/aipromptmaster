package com.arny.sharedui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

/** Same touch and keyboard input for both hosts; attachment pickers remain platform actions. */
@Composable
fun ChatComposer(
    value: String, onValueChange: (String) -> Unit, onSend: () -> Unit, onCancel: () -> Unit,
    generating: Boolean, canSend: Boolean, modifier: Modifier = Modifier,
    onAttach: (() -> Unit)? = null, attachments: @Composable () -> Unit = {},
    sendOnEnter: Boolean = false,
    onClear: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 2.dp) {
        Column(Modifier.padding(12.dp)) {
            attachments()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (value.isNotBlank() && onClear != null) TextButton(onClick = onClear, enabled = !generating) { Text("Очистить") }
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Свернуть" else "Развернуть") }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onAttach?.let { IconButton(onClick = it, enabled = !generating) { Icon(Icons.Default.AttachFile, "Прикрепить файл") } }
                OutlinedTextField(value, onValueChange, placeholder = { Text("Сообщение…") },
                    modifier = Modifier.weight(1f).onPreviewKeyEvent { event ->
                        if (sendOnEnter && event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed && canSend && !generating) {
                            onSend(); true
                        } else false
                    },
                    enabled = !generating, minLines = if (expanded) 8 else 1, maxLines = if (expanded) 20 else 8,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                FilledIconButton(onClick = if (generating) onCancel else onSend, enabled = generating || canSend) {
                    Icon(if (generating) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                        if (generating) "Остановить генерацию" else "Отправить сообщение")
                }
            }
        }
    }
}
