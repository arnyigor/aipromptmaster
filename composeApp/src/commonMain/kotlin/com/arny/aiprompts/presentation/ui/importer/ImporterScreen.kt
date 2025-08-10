package com.arny.aiprompts.presentation.ui.importer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImporterScreen(component: ImporterComponent) {
    val state by component.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ассистент Импорта (${state.rawPosts.size} постов)") },
                navigationIcon = {
                    IconButton(onClick = component::onBackClicked) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Row(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                PostListPanel(
                    modifier = Modifier.weight(1f),
                    state = state,
                    onPostClicked = component::onPostClicked,
                    onTogglePostForImport = component::onTogglePostForImport
                )
                EditorPanel(
                    modifier = Modifier.weight(2f),
                    state = state,
                    onTitleChanged = component::onTitleChanged,
                    onDescriptionChanged = component::onDescriptionChanged,
                    onContentChanged = component::onContentChanged
                )
                ActionsPanel(
                    modifier = Modifier.width(200.dp),
                    state = state,
                    onImportClicked = component::onImportClicked
                )
            }
        }
    }
}


@Composable
private fun PostListPanel(
    modifier: Modifier,
    state: ImporterState,
    onPostClicked: (String) -> Unit,
    onTogglePostForImport: (String, Boolean) -> Unit
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Найденные посты", style = MaterialTheme.typography.titleMedium)
        LazyColumn(
            modifier = Modifier.fillMaxHeight().border(1.dp, MaterialTheme.colorScheme.outlineVariant),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(state.rawPosts, key = { it.postId }) { post ->
                val isSelected = state.selectedPostId == post.postId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPostClicked(post.postId) }
                        .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = post.postId in state.postsToImport,
                        onCheckedChange = { isChecked -> onTogglePostForImport(post.postId, isChecked) }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(post.author.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            post.postId,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (post.isLikelyPrompt) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Вероятно, промпт",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorPanel(
    modifier: Modifier,
    state: ImporterState,
    onTitleChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onContentChanged: (String) -> Unit
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.selectedPost == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Выберите пост для просмотра и редактирования")
            }
        } else {
            OutlinedTextField(
                value = state.editableTitle,
                onValueChange = onTitleChanged,
                label = { Text("Заголовок") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = state.editableDescription,
                onValueChange = onDescriptionChanged,
                label = { Text("Описание") },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            OutlinedTextField(
                value = state.editableContent,
                onValueChange = onContentChanged,
                label = { Text("Контент промпта") },
                modifier = Modifier.fillMaxWidth().weight(2f)
            )

            // TODO: Добавить сюда WebView для предпросмотра HTML
            Box(
                modifier = Modifier.fillMaxWidth().weight(3f)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Здесь будет WebView для предпросмотра HTML", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}


@Composable
private fun ActionsPanel(
    modifier: Modifier,
    state: ImporterState,
    onImportClicked: () -> Unit
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Готово к импорту: ${state.postsToImport.size} шт.")
        Button(
            onClick = onImportClicked,
            enabled = state.postsToImport.isNotEmpty() && !state.isLoading
        ) {
            Text("Создать JSON файлы")
        }

        if (state.error != null) {
            Text(state.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}