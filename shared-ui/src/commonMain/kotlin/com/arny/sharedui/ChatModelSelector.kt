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
    val label = shortModelName(model)
    OutlinedButton(onClick = onSelect, modifier = modifier.heightIn(min = 36.dp),
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)) {
        Icon(Icons.Default.SmartToy, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label.ifBlank { "Выбрать модель" },
            style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Default.ExpandMore, "Изменить модель", Modifier.size(18.dp))
    }
}
