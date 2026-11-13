package com.arny.sharedui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
fun PromptTextEditor(language: String, text: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = text, onValueChange = onChange, label = { Text("Текст ($language)") },
        minLines = 5, textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        modifier = modifier.fillMaxWidth().heightIn(min = 180.dp)
    )
}
