package com.arny.aiprompts.presentation.ui.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
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

@Composable
fun PromptDetailScreen(component: PromptDetailComponent) {
    AdaptivePromptDetailLayout(component = component)
}

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
        // Icon(AppIcons.Error, ...)
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
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
//    println("AdaptivePromptDetailLayout state:${state.prompt?.id}")
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val clipboardManager = LocalClipboardManager.current
        if (maxWidth > 800.dp) {
            DesktopPromptDetailLayout(component, state, clipboardManager)
        } else {
            MobilePromptDetailLayout(component, state)
        }
    }
}

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
                        // Кнопка отмены в режиме редактирования
                        TextButton(onClick = { component.onEvent(PromptDetailEvent.CancelClicked) }) {
                            Text("ОТМЕНА")
                        }
                    } else {
                        // Кнопки в режиме просмотра
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
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues), // Добавляем paddingValues от Scaffold
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Левая панель (70%): Основной контент
            LazyColumn(
                modifier = Modifier.weight(0.7f).padding(16.dp),
                contentPadding = PaddingValues(bottom = 16.dp), // Убираем большой отступ снизу
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
                        // Редактируемый заголовок (только в режиме редактирования)
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

                        // Контент на русском
                        promptToDisplay.content?.ru?.let { ruContent ->
                            item(key = "content_ru") {
                                EditablePromptContentCard(
                                    language = "Русский",
                                    viewText = ruContent,
                                    editText = ruContent,
                                    isEditing = state.isEditing,
                                    onValueChange = { newText ->
                                        component.onEvent(
                                            PromptDetailEvent.ContentChanged(
                                                PromptLanguage.RU, newText
                                            )
                                        )
                                    },
                                    onCopyClick = { clipboardManager.setText(AnnotatedString(ruContent)) }
                                )
                            }
                        }

                        // Контент на английском
                        promptToDisplay.content?.en?.let { enContent ->
                            item(key = "content_en") {
                                EditablePromptContentCard(
                                    language = "English",
                                    viewText = enContent,
                                    editText = enContent,
                                    isEditing = state.isEditing,
                                    onValueChange = { newText ->
                                        component.onEvent(
                                            PromptDetailEvent.ContentChanged(
                                                PromptLanguage.EN, newText
                                            )
                                        )
                                    },
                                    onCopyClick = { clipboardManager.setText(AnnotatedString(enContent)) }
                                )
                            }
                        }

                        // Теги в левой панели (для desktop удобнее)
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
                modifier = Modifier.weight(0.3f).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Информационная карточка
                if (!state.isEditing && promptToDisplay != null) {
                    Card {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Информация о промпте",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = promptToDisplay.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Кнопки действий
                if (promptToDisplay != null && !state.isLoading && promptToDisplay.isLocal) {
                    Card {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Действия",
                                style = MaterialTheme.typography.titleMedium
                            )

                            if (state.isEditing) {
                                Button(
                                    onClick = { component.onEvent(PromptDetailEvent.SaveClicked) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Done, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Сохранить")
                                }

                                OutlinedButton(
                                    onClick = { component.onEvent(PromptDetailEvent.CancelClicked) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Отмена")
                                }
                            } else {
                                Button(
                                    onClick = { component.onEvent(PromptDetailEvent.EditClicked) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Редактировать")
                                }

                                OutlinedButton(
                                    onClick = { component.onEvent(PromptDetailEvent.FavoriteClicked) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
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
            
                    // Диалог подтверждения удаления
                    if (state.showDeleteDialog) {
                        AlertDialog(
                            onDismissRequest = { component.onEvent(PromptDetailEvent.HideDeleteDialog) },
                            title = { Text("Подтверждение удаления") },
                            text = {
                                Text("Вы действительно хотите удалить этот промпт? Это действие нельзя отменить.")
                            },
                            confirmButton = {
                                Button(
                                    onClick = { component.onEvent(PromptDetailEvent.ConfirmDelete) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("Удалить", color = MaterialTheme.colorScheme.onError)
                                }
                            },
                            dismissButton = {
                                OutlinedButton(onClick = { component.onEvent(PromptDetailEvent.HideDeleteDialog) }) {
                                    Text("Отмена")
                                }
                            },
                            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
                        )
                    }
                }
            }
        }

        // Диалог подтверждения удаления
        if (state.showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { component.onEvent(PromptDetailEvent.HideDeleteDialog) },
                title = { Text("Подтверждение удаления") },
                text = {
                    Text("Вы действительно хотите удалить этот промпт? Это действие нельзя отменить.")
                },
                confirmButton = {
                    Button(
                        onClick = { component.onEvent(PromptDetailEvent.ConfirmDelete) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Удалить", color = MaterialTheme.colorScheme.onError)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { component.onEvent(PromptDetailEvent.HideDeleteDialog) }) {
                        Text("Отмена")
                    }
                },
                properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
            )
        }
    }
}

@Composable
fun RichMarkdownDisplay(
    content: String,
    modifier: Modifier = Modifier
) {
    // Создаем состояние для RichText
    val richTextState = rememberRichTextState()

    // Устанавливаем markdown контент при изменении
    LaunchedEffect(content) {
        richTextState.setMarkdown(content)
    }

    RichText(
        state = richTextState, // Передаем state, а не text!
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
    val clipboardManager = LocalClipboardManager.current

    // Ключевой момент: выбираем, какой промпт отображать (оригинал или черновик)
    // Это избавляет от множества if/else в коде ниже.
    val promptToDisplay = if (state.isEditing) state.draftPrompt else state.prompt

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // В режиме просмотра заголовок нередактируемый
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
                        // Кнопка отмены в режиме редактирования
                        TextButton(onClick = { component.onEvent(PromptDetailEvent.CancelClicked) }) {
                            Text("ОТМЕНА")
                        }
                    } else {
                        // Кнопки в режиме просмотра
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
                    Icon(icon, contentDescription = if (state.isEditing) "Сохранить" else "Редактировать")
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
            ), // Отступ снизу для FAB
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
                state.isLoading -> item {
                    Box(
                        Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }

                state.error != null -> item { ErrorState(state.error) { component.onEvent(PromptDetailEvent.Refresh) } }
                promptToDisplay != null -> {
                    // --- Редактируемый Заголовок ---
                    if (state.isEditing) {
                        item {
                            OutlinedTextField(
                                value = promptToDisplay.title,
                                onValueChange = { component.onEvent(PromptDetailEvent.TitleChanged(it)) },
                                label = { Text("Заголовок") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // --- Редактируемый контент (RU) ---
                    promptToDisplay.content?.ru?.let {
                        item {
                            EditablePromptContentCard(
                                language = "Русский",
                                viewText = it,
                                editText = it, // В draftPrompt мы будем менять это поле
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

                    // --- Редактируемый контент (EN) ---
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

                    // --- Редактируемые теги ---
                    item {
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
    }
}
