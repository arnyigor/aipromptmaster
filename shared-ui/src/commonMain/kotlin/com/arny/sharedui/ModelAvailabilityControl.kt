package com.arny.sharedui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.arny.promptcontract.ModelAvailabilityUi

/** Availability belongs to the model field in provider settings. */
@Composable
fun ModelAvailabilityControl(modelId: String, state: ModelAvailabilityUi, onCheck: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onCheck, enabled = enabled && modelId.isNotBlank() && !state.checking) {
            Text(if (state.checking) "Проверка модели…" else "Проверить доступность модели")
        }
        Text("Короткий запрос · возможна тарификация", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.checking) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.message?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.available == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
    }
}
