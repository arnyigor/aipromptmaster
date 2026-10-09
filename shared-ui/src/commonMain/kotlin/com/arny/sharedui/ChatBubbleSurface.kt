package com.arny.sharedui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Message surface taken from the Android conversation screen. */
@Composable
fun ChatBubbleSurface(
    isUser: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = if (isUser) RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
            else RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
        color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        tonalElevation = if (isUser) 4.dp else 1.dp,
        shadowElevation = 1.dp,
        content = content,
    )
}
