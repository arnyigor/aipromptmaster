package com.arny.sharedui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** The active model is text, not an icon hidden behind a menu. */
@Composable
fun ChatModelSelector(model: String?, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onSelect, modifier = modifier.heightIn(min = 48.dp),
        shape = MaterialTheme.shapes.medium, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        Icon(Icons.Default.SmartToy, null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(model?.takeIf { it.isNotBlank() } ?: "Выбрать модель", Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.ExpandMore, "Изменить модель", Modifier.size(20.dp))
    }
}
