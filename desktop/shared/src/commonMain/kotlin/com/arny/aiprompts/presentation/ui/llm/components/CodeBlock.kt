package com.arny.aiprompts.presentation.ui.llm.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.arny.aiprompts.presentation.ui.llm.theme.codeBlockBackground
import com.arny.aiprompts.presentation.ui.llm.theme.codeBlockText

/**
 * Компонент для отображения блока кода с подсветкой синтаксиса.
 * Стиль как в VS Code / Jan / LM Studio.
 *
 * @param code Текст кода
 * @param language Язык программирования (для подсветки)
 * @param modifier Модификатор
 */
@Composable
fun CodeBlock(
    code: String,
    language: String? = null,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val displayLanguage = remember(language) {
        language?.takeIf { it.isNotBlank() } ?: "text"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.codeBlockBackground())
    ) {
        // Header с языком и кнопкой копирования
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Язык
            Text(
                text = displayLanguage,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Кнопка копирования
            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(code))
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy code",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Код
        Text(
            text = code,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                color = MaterialTheme.colorScheme.codeBlockText()
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        )
    }
}

/**
 * Inline код внутри текста.
 */
@Composable
fun InlineCode(
    code: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.codeBlockBackground()
    ) {
        Text(
            text = code,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                color = MaterialTheme.colorScheme.codeBlockText()
            ),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * Определение языка программирования по markdown блоку.
 */
fun detectLanguage(infoString: String): String {
    val normalized = infoString.trim().lowercase()
    return when {
        normalized.contains("kotlin") -> "kotlin"
        normalized.contains("java") -> "java"
        normalized.contains("python") || normalized.contains("py") -> "python"
        normalized.contains("javascript") || normalized.contains("js") -> "javascript"
        normalized.contains("typescript") || normalized.contains("ts") -> "typescript"
        normalized.contains("json") -> "json"
        normalized.contains("xml") -> "xml"
        normalized.contains("html") -> "html"
        normalized.contains("css") -> "css"
        normalized.contains("sql") -> "sql"
        normalized.contains("bash") || normalized.contains("shell") || normalized.contains("sh") -> "bash"
        normalized.contains("yaml") || normalized.contains("yml") -> "yaml"
        normalized.contains("markdown") || normalized.contains("md") -> "markdown"
        normalized.contains("rust") || normalized.contains("rs") -> "rust"
        normalized.contains("go") || normalized.contains("golang") -> "go"
        normalized.contains("c++") || normalized.contains("cpp") -> "c++"
        normalized.contains("c#") || normalized.contains("csharp") -> "c#"
        else -> "text"
    }
}
