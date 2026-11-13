package com.arny.sharedui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Синхронизация каталога промптов: отдельный раздел настроек, чтобы не смешивать её
 * с профилями провайдеров и личным хранилищем.
 */
@Composable
fun CatalogSyncCard(onSync: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Каталог промптов", style = MaterialTheme.typography.titleMedium)
            Text(
                "Загружает опубликованный каталог, проверяет контрольные суммы и импортирует его транзакционно. Личные промпты не изменяются.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onSync, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text("Синхронизировать каталог")
            }
        }
    }
}
