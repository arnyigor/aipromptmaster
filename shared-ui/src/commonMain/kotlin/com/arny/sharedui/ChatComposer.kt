package com.arny.sharedui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Clear
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
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
    Surface(modifier = modifier.fillMaxWidth().padding(8.dp), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(8.dp)) {
            attachments()
                TextField(value, onValueChange, placeholder = { Text("Сообщение…") },
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).onPreviewKeyEvent { event ->
                        if (sendOnEnter && event.type == KeyEventType.KeyDown && event.key == Key.Enter && !event.isShiftPressed && canSend && !generating) {
                            onSend(); true
                        } else false
                    },
                    enabled = !generating, minLines = if (expanded) 4 else 1, maxLines = if (expanded) 8 else 4,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                onAttach?.let { IconButton(onClick = it, enabled = !generating) { Icon(Icons.Default.AttachFile, "Прикрепить файл") } }
                if (value.isNotBlank() && onClear != null) IconButton(onClick = onClear, enabled = !generating) {
                    Icon(Icons.Default.Clear, "Очистить сообщение")
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
                        if (expanded) "Свернуть поле сообщения" else "Развернуть поле сообщения")
                }
                Spacer(Modifier.weight(1f))
                FilledIconButton(onClick = if (generating) onCancel else onSend, enabled = generating || canSend) {
                    Icon(if (generating) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                        if (generating) "Остановить генерацию" else "Отправить сообщение")
                }
            }
        }
    }
}
