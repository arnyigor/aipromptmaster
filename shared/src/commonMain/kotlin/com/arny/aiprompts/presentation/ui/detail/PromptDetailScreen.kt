package com.arny.aiprompts.presentation.ui.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arny.aiprompts.presentation.screens.PromptDetailComponent
import com.arny.aiprompts.presentation.screens.PromptDetailEvent

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

@Composable
fun AdaptivePromptDetailLayout(component: PromptDetailComponent) {
    val state by component.state.collectAsState()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth > 800.dp) {
            // --- DESKTOP LAYOUT ---
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Левая панель (70%): Основной контент (редакторы)
                LazyColumn(modifier = Modifier.weight(0.7f)) {
                    // Здесь размещаем EditablePromptContentCard для RU, EN и т.д.
                }
                // Правая панель (30%): Метаданные
                Column(modifier = Modifier.weight(0.3f)) {
                    // Здесь секции с тегами, переменными, моделями.
                    // Они тоже могут быть редактируемыми.
                }
            }
        } else {
            // --- MOBILE LAYOUT ---
            // Наш уже существующий LazyColumn-лейаут
            MobilePromptDetailLayout(component, state)
        }
    }
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
                        // Кнопка "В избранное" в режиме просмотра
                        promptToDisplay?.let { prompt ->
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
            if (state.prompt != null && !state.isLoading) {
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
        }
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
                            onAddTag = { /* component.onEvent(...) */ },
                            onRemoveTag = { /* component.onEvent(...) */ }
                        )
                    }
                }
            }
        }
    }
}
