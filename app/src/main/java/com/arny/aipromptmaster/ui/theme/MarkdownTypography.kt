package com.arny.aipromptmaster.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.MarkdownTypography

/**
 * The library defaults headings to displayLarge/displayMedium/displaySmall (57/45/36 sp),
 * which look oversized inside a chat bubble. Chat uses a compact scale instead.
 */
@Composable
fun compactMarkdownTypography(): MarkdownTypography {
    val base = MaterialTheme.typography
    val compact = base.bodyMedium
    return markdownTypography(
        h1 = base.titleLarge,
        h2 = base.titleMedium,
        h3 = base.titleSmall,
        h4 = base.titleSmall,
        h5 = base.labelLarge,
        h6 = base.labelLarge,
        text = compact,
        paragraph = compact,
        ordered = compact,
        bullet = compact,
        list = compact,
        code = base.bodySmall.copy(fontFamily = FontFamily.Monospace),
        inlineCode = compact.copy(fontFamily = FontFamily.Monospace),
        quote = compact.copy(fontStyle = FontStyle.Italic),
        table = base.bodySmall,
    )
}
