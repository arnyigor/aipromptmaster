package com.arny.sharedui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.arny.promptcontract.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProviderManagementCard(state: ProviderManagerState, onAction: (ProviderAction) -> Unit, mobile: Boolean = true) {
    val uriHandler = LocalUriHandler.current
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Провайдеры LLM", style = MaterialTheme.typography.titleLarge)
            Text("Выберите сервис для чата и улучшения промптов.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.config.profiles.forEach { profile ->
                val selected = profile.id == state.config.activeId
                Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null) {
                Column(Modifier.padding(12.dp)) {
                    Text(profile.name + if (profile.id == state.config.activeId) " • выбран" else "", style = MaterialTheme.typography.titleMedium)
                    Text(profile.baseUrl, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    FlowRow {
                        if (!selected) TextButton(onClick = { onAction(ProviderAction.Select(profile.id)) }) { Text("Использовать") }
                        TextButton(onClick = { onAction(ProviderAction.Edit(profile.id)) }) { Text("Настроить") }
                        if (profile.id != "openrouter") TextButton(onClick = { onAction(ProviderAction.Delete(profile.id)) }) { Text("Удалить") }
                    }
                }
                }
            }
            Text("Добавить профиль")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProviderPreset.entries.forEach { preset -> OutlinedButton(onClick = { onAction(ProviderAction.Create(preset)) }) { Text(preset.title) } }
            }
            state.draft?.let { draft ->
                HorizontalDivider()
                OutlinedTextField(draft.name, { onAction(ProviderAction.Name(it)) }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(draft.baseUrl, { onAction(ProviderAction.Url(it)) }, label = { Text("Адрес API, включая /v1") }, enabled = draft.id != "openrouter", singleLine = true, modifier = Modifier.fillMaxWidth())
                if (mobile && draft.id != "openrouter") Text("Для локального сервера укажите IP компьютера в вашей сети. localhost — адрес этого устройства.", style = MaterialTheme.typography.bodySmall)
                if (!mobile && draft.id == "openrouter") {
                    Row(Modifier.fillMaxWidth().toggleable(draft.keySource == ProviderKeySource.ENVIRONMENT, role = Role.Checkbox, onValueChange = {
                            onAction(ProviderAction.KeySource(if (it) ProviderKeySource.ENVIRONMENT else ProviderKeySource.STORED))
                        }), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(draft.keySource == ProviderKeySource.ENVIRONMENT, onCheckedChange = null)
                        Text("Использовать системный ключ OpenRouter", Modifier.weight(1f))
                    }
                    if (draft.keySource == ProviderKeySource.ENVIRONMENT) Text(
                        if (state.environmentKeyAvailable) "OPENROUTER_API_KEY доступен. Значение не копируется в настройки."
                        else "OPENROUTER_API_KEY недоступен. После добавления переменной перезапустите приложение.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (draft.keySource == ProviderKeySource.STORED) OutlinedTextField(draft.apiKey, { onAction(ProviderAction.Key(it)) }, label = { Text("API-ключ этого профиля") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false), singleLine = true, modifier = Modifier.fillMaxWidth())
                if (draft.id == "openrouter") TextButton(onClick = { uriHandler.openUri("https://openrouter.ai/settings/keys") }) { Text("Получить ключ OpenRouter") }
                Row(Modifier.fillMaxWidth().toggleable(draft.requiresKey, enabled = draft.id != "openrouter", role = Role.Checkbox,
                    onValueChange = { onAction(ProviderAction.RequiresKey(it)) }), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(draft.requiresKey, onCheckedChange = null, enabled = draft.id != "openrouter")
                    Text("Требуется API-ключ", Modifier.weight(1f))
                }
                OutlinedTextField(draft.modelId, { onAction(ProviderAction.Model(it)) }, label = { Text("Модель по умолчанию (ID)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { onAction(ProviderAction.Test) }, enabled = !state.checking) { Text(if (state.checking) "Проверка…" else "Проверить и получить модели") }
                if (state.checking) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.models.isNotEmpty()) OutlinedTextField(state.modelQuery, { onAction(ProviderAction.ModelSearch(it)) }, label = { Text("Поиск среди моделей провайдера") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.visibleModels.forEach { id -> SuggestionChip(onClick = { onAction(ProviderAction.Model(id)) }, label = { Text(id) }) }
                }
                if (state.models.size > 12) Text("Уточните поиск, чтобы найти нужную модель. Можно также ввести ID вручную.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onAction(ProviderAction.Save) }) { Text("Сохранить и использовать") }
                    TextButton(onClick = { onAction(ProviderAction.Close) }) { Text("Отмена") }
                }
            }
            state.message?.let { Text(it) }
        }
    }
}
