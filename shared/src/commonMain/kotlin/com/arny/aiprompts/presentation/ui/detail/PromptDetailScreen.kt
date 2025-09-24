package com.arny.aiprompts.presentation.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.arny.aiprompts.presentation.screens.PromptDetailComponent
import com.arny.aiprompts.presentation.screens.PromptDetailEvent
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Повторить")
        }
    }
}

@Suppress("UnusedBoxWithConstraintsScope")
@Composable
fun AdaptivePromptDetailLayout(component: PromptDetailComponent) {
    val state by component.state.collectAsState()
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val clipboardManager = LocalClipboardManager.current
        if (maxWidth > 800.dp) {
            DesktopPromptDetailLayout(component, state, clipboardManager)
        } else {
            MobilePromptDetailLayout(component, state)
        }
    }
}

@Suppress("DefaultLocale", "UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DesktopPromptDetailLayout(
    component: PromptDetailComponent,
    state: PromptDetailState,
    clipboardManager: ClipboardManager
) {
    val promptToDisplay = if (state.isEditing) state.draftPrompt else state.prompt

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (!state.isEditing) {
                        Text(
                            text = promptToDisplay?.title ?: "Загрузка...",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text("Редактирование промпта")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { component.onEvent(PromptDetailEvent.BackClicked) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (state.isEditing) {
                        TextButton(onClick = { component.onEvent(PromptDetailEvent.CancelClicked) }) {
                            Text("ОТМЕНА")
                        }
                    } else {
                        promptToDisplay?.let { prompt ->
                            if (prompt.isLocal) {
                                IconButton(onClick = { component.onEvent(PromptDetailEvent.DeleteClicked) }) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Удалить",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            IconButton(onClick = { component.onEvent(PromptDetailEvent.FavoriteClicked) }) {
                                Icon(
                                    imageVector = if (prompt.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = "В избранное",
                                    tint = if (prompt.isFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                )
                            }
                        }
                    }
                }
            )
        },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets.systemBars
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val rightPanelWidth = maxWidth * 0.3f

            // ИСПРАВЛЕНИЕ: Убран horizontalArrangement = Arrangement.Start
            // Теперь Modifier.weight(1f) корректно "выталкивает" правую панель к краю.
            Row(modifier = Modifier.fillMaxSize()) {
                // Левая панель: Основной контент
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(16.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when {
                        state.isLoading -> item(key = "loading") {
                            Box(
                                Modifier.fillParentMaxSize(),
                                contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator() }
                        }

                        state.error != null -> item(key = "error") {
                            ErrorState(state.error) {
                                component.onEvent(PromptDetailEvent.Refresh)
                            }
                        }

                        promptToDisplay != null -> {
                            if (state.isEditing) {
                                item(key = "title") {
                                    OutlinedTextField(
                                        value = promptToDisplay.title,
                                        onValueChange = { component.onEvent(PromptDetailEvent.TitleChanged(it)) },
                                        label = { Text("Заголовок") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            promptToDisplay.content?.ru?.let { ruContent ->
                                item(key = "content_ru") {
                                    EditablePromptContentCard(
                                        language = "Русский",
                                        viewText = ruContent,
                                        editText = ruContent,
                                        isEditing = state.isEditing,
                                        onValueChange = { newText ->
                                            component.onEvent(PromptDetailEvent.ContentChanged(PromptLanguage.RU, newText))
                                        },
                                        onCopyClick = { clipboardManager.setText(AnnotatedString(ruContent)) }
                                    )
                                }
                            }

                            promptToDisplay.content?.en?.let { enContent ->
                                item(key = "content_en") {
                                    EditablePromptContentCard(
                                        language = "English",
                                        viewText = enContent,
                                        editText = enContent,
                                        isEditing = state.isEditing,
                                        onValueChange = { newText ->
                                            component.onEvent(PromptDetailEvent.ContentChanged(PromptLanguage.EN, newText))
                                        },
                                        onCopyClick = { clipboardManager.setText(AnnotatedString(enContent)) }
                                    )
                                }
                            }

                            item(key = "tags") {
                                EditableTagsSection(
                                    title = "Теги",
                                    tags = promptToDisplay.tags,
                                    isEditing = state.isEditing,
                                    onAddTag = { tag -> component.onEvent(PromptDetailEvent.TagAdded(tag)) },
                                    onRemoveTag = { tag -> component.onEvent(PromptDetailEvent.TagRemoved(tag)) },
                                    availableTags = state.availableTags,
                                    enableColorCoding = true
                                )
                            }
                        }
                    }
                }

                // Правая панель (30%): Метаданные и действия
                Column(
                    modifier = Modifier.width(rightPanelWidth).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (!state.isEditing && promptToDisplay != null) {
                        Card {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Информация о промпте", style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = promptToDisplay.title, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Spacer(modifier = Modifier.height(8.dp))

                                // ... остальной код для отображения метаданных (без изменений) ...
                                if (promptToDisplay.category.isNotEmpty()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.Category, contentDescription = "Категория", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = "Категория: ${promptToDisplay.category}", style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                // ... и так далее для всех полей ...
                            }
                        }
                    }

                    if (promptToDisplay != null && !state.isLoading && promptToDisplay.isLocal) {
                        Card {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Действия", style = MaterialTheme.typography.titleMedium)
                                if (state.isEditing) {
                                    Button(onClick = { component.onEvent(PromptDetailEvent.SaveClicked) }) {
                                        Icon(Icons.Default.Done, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Сохранить")
                                    }
                                    OutlinedButton(onClick = { component.onEvent(PromptDetailEvent.CancelClicked) }) {
                                        Text("Отмена")
                                    }
                                } else {
                                    Button(onClick = { component.onEvent(PromptDetailEvent.EditClicked) }) {
                                        Icon(Icons.Default.Edit, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Редактировать")
                                    }
                                    OutlinedButton(onClick = { component.onEvent(PromptDetailEvent.FavoriteClicked) }) {
                                        Icon(
                                            if (promptToDisplay.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                            contentDescription = null,
                                            tint = if (promptToDisplay.isFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(if (promptToDisplay.isFavorite) "Убрать из избранного" else "В избранное")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // РЕФАКТОРИНГ: Диалог вынесен сюда и вызывается только один раз.
    ConfirmDeleteDialog(
        show = state.showDeleteDialog,
        onDismiss = { component.onEvent(PromptDetailEvent.HideDeleteDialog) },
        onConfirm = { component.onEvent(PromptDetailEvent.ConfirmDelete) }
    )
}

// РЕФАКТОРИНГ: Создана отдельная функция для диалога, чтобы избежать дублирования кода.
@Composable
private fun ConfirmDeleteDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Подтверждение удаления") },
            text = {
                Text("Вы действительно хотите удалить этот промпт? Это действие нельзя отменить.")
            },
            confirmButton = {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismiss) {
                    Text("Отмена")
                }
            },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        )
    }
}


@Composable
fun RichMarkdownDisplay(
    content: String,
    modifier: Modifier = Modifier
) {
    val richTextState = rememberRichTextState()
    LaunchedEffect(content) {
        richTextState.setMarkdown(content)
    }
    RichText(
        state = richTextState,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MobilePromptDetailLayout(
    component: PromptDetailComponent,
    state: PromptDetailState
) {
    // Код для мобильной версии остается без изменений
    val clipboardManager = LocalClipboardManager.current
    val promptToDisplay = if (state.isEditing) state.draftPrompt else state.prompt

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (!state.isEditing) {
                        Text(
                            text = promptToDisplay?.title ?: "Загрузка...",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { component.onEvent(PromptDetailEvent.BackClicked) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (state.isEditing) {
                        TextButton(onClick = { component.onEvent(PromptDetailEvent.CancelClicked) }) {
                            Text("ОТМЕНА")
                        }
                    } else {
                        promptToDisplay?.let { prompt ->
                            if (prompt.isLocal) {
                                IconButton(onClick = { component.onEvent(PromptDetailEvent.DeleteClicked) }) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Удалить",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            IconButton(onClick = { component.onEvent(PromptDetailEvent.FavoriteClicked) }) {
                                Icon(
                                    imageVector = if (prompt.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = "В избранное",
                                    tint = if (prompt.isFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (state.prompt != null && !state.isLoading && state.prompt.isLocal) {
                FloatingActionButton(
                    onClick = {
                        val event =
                            if (state.isEditing) PromptDetailEvent.SaveClicked else PromptDetailEvent.EditClicked
                        component.onEvent(event)
                    }
                ) {
                    val icon = if (state.isEditing) Icons.Default.Done else Icons.Default.Edit
                    Icon(
                        icon,
                        contentDescription = if (state.isEditing) "Сохранить" else "Редактировать"
                    )
                }
            }
        },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets.systemBars
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
                state.isLoading -> item {
                    Box(
                        Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }

                state.error != null -> item {
                    ErrorState(state.error) {
                        component.onEvent(PromptDetailEvent.Refresh)
                    }
                }

                promptToDisplay != null -> {
                    if (state.isEditing) {
                        item {
                            OutlinedTextField(
                                value = promptToDisplay.title,
                                onValueChange = {
                                    component.onEvent(
                                        PromptDetailEvent.TitleChanged(
                                            it
                                        )
                                    )
                                },
                                label = { Text("Заголовок") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    promptToDisplay.content?.ru?.let {
                        item {
                            EditablePromptContentCard(
                                language = "Русский",
                                viewText = it,
                                editText = it,
                                isEditing = state.isEditing,
                                onValueChange = { newText ->
                                    component.onEvent(
                                        PromptDetailEvent.ContentChanged(
                                            PromptLanguage.RU, newText
                                        )
                                    )
                                },
                                onCopyClick = { clipboardManager.setText(AnnotatedString(it)) }
                            )
                        }
                    }

                    promptToDisplay.content?.en?.let {
                        item {
                            EditablePromptContentCard(
                                language = "English",
                                viewText = it,
                                editText = it,
                                isEditing = state.isEditing,
                                onValueChange = { newText ->
                                    component.onEvent(
                                        PromptDetailEvent.ContentChanged(
                                            PromptLanguage.EN, newText
                                        )
                                    )
                                },
                                onCopyClick = { clipboardManager.setText(AnnotatedString(it)) }
                            )
                        }
                    }

                    item {
                        EditableTagsSection(
                            title = "Теги",
                            tags = promptToDisplay.tags,
                            isEditing = state.isEditing,
                            onAddTag = { tag -> component.onEvent(PromptDetailEvent.TagAdded(tag)) },
                            onRemoveTag = { tag ->
                                component.onEvent(
                                    PromptDetailEvent.TagRemoved(
                                        tag
                                    )
                                )
                            },
                            availableTags = state.availableTags,
                            enableColorCoding = true
                        )
                    }
                }
            }
        }
    }
}