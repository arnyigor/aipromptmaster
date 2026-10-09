package com.arny.sharedui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.arny.promptcontract.*

@Composable
fun PersonalVaultCard(state: PersonalVaultUi, onAction: (PersonalVaultAction) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Личные промпты · GitHub", style = MaterialTheme.typography.titleMedium)
            Text("Приватный репозиторий для синхронизации Android и Desktop. Добавляйте промпты как обычно; здесь синхронизируются только личные записи.")
            Text("Файл: .aiprompts/personal-prompts.json · до 900 КБ", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(state.config.repository, { onAction(PersonalVaultAction.Repository(it)) }, label = { Text("Репозиторий owner/repo") }, singleLine = true, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.config.branch, { onAction(PersonalVaultAction.Branch(it)) }, label = { Text("Ветка (пусто — основная)") }, singleLine = true, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.config.token, { onAction(PersonalVaultAction.Token(it)) }, label = { Text("GitHub token") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !state.busy,
                supportingText = { Text("Fine-grained token: этот репозиторий, Contents — Read and write") }, modifier = Modifier.fillMaxWidth())
            Button({ onAction(PersonalVaultAction.Preview) }, enabled = !state.busy) { Text("Проверить изменения") }
            if (state.config.repository.isNotBlank() || state.config.token.isNotBlank())
                TextButton({ onAction(PersonalVaultAction.Disconnect) }, enabled = !state.busy) { Text("Отключить GitHub") }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.message?.let { Text(it) }
            if (state.ready) {
                Text("Локально: ${state.localCount}; на GitHub: ${state.remoteCount}")
                state.changes.take(30).forEach { change ->
                    Text("${change.title}\nНа устройстве: ${change.locally}; GitHub: ${change.github}", style = MaterialTheme.typography.bodySmall)
                }
                if (state.changes.size > 30) Text("И ещё ${state.changes.size - 30} изменений")
                state.conflicts.forEach { conflict ->
                    HorizontalDivider()
                    Text(conflict.local?.title ?: conflict.remote?.title ?: conflict.id)
                    Text("Локально: ${if (conflict.local == null) "удалено" else "изменено"}; GitHub: ${if (conflict.remote == null) "удалено" else "изменено"}")
                    conflict.local?.let { Text("Локальная версия:\n${it.content.values.joinToString("\n\n")}", style = MaterialTheme.typography.bodySmall) }
                    conflict.remote?.let { Text("Версия GitHub:\n${it.content.values.joinToString("\n\n")}", style = MaterialTheme.typography.bodySmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(conflict.useRemote == false, { onAction(PersonalVaultAction.Resolve(conflict.id, false)) }, label = { Text("Локальная") }, enabled = !state.busy)
                        FilterChip(conflict.useRemote == true, { onAction(PersonalVaultAction.Resolve(conflict.id, true)) }, label = { Text("GitHub") }, enabled = !state.busy)
                    }
                }
                Text("Применение обновит личные записи на обоих устройствах; удаления также синхронизируются.")
                Button({ onAction(PersonalVaultAction.Apply) }, enabled = !state.busy && state.conflicts.all { it.useRemote != null }) { Text("Применить синхронизацию") }
            }
        }
    }
}
