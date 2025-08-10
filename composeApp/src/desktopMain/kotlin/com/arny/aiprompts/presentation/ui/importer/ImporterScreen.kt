package com.arny.aiprompts.presentation.ui.importer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.arny.aiprompts.data.parser.BlockType
import com.arny.aiprompts.data.parser.ParsedPostBlock
import com.arny.aiprompts.data.parser.PostStructureParser

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Row(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.isLoading && state.rawPosts.isEmpty()) { // Показываем индикатор только при первоначальной загрузке
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                PostListPanel(
                    modifier = Modifier.weight(1f),
                    state = state,
                    onPostClicked = component::onPostClicked
                )
                EditorPanel(
                    modifier = Modifier.weight(2f),
                    state = state,
                    component = component
                )
                ActionsPanel(
                    modifier = Modifier.width(220.dp),
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
    onPostClicked: (String) -> Unit
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Найденные посты", style = MaterialTheme.typography.titleMedium)
        // TODO: Добавить сюда фильтры
        LazyColumn(
            modifier = Modifier.fillMaxHeight().border(1.dp, MaterialTheme.colorScheme.outlineVariant),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(state.rawPosts, key = { it.postId }) { post ->
                val isSelected = state.selectedPostId == post.postId
                val isReadyToImport = post.postId in state.postsToImport
                // TODO: Добавить статус "Пропущен"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPostClicked(post.postId) }
                        .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // --- НОВЫЙ ИНДИКАТОР СТАТУСА ---
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isReadyToImport -> Color.Green
                                    // isSkipped -> Color.Gray
                                    else -> Color.Transparent
                                }
                            )
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
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
    component: ImporterComponent
) {
    val editedData = state.currentEditedData

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (state.selectedPost == null || editedData == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Выберите пост для просмотра и редактирования")
            }
        } else {
            var selectedTab by remember { mutableStateOf(0) }
            val tabs = listOf("Редактор", "HTML-превью")

            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            // Контент вкладок
            when (selectedTab) {
                0 -> EditorFields(editedData, component)
                1 -> HtmlPreviewPanel(state.selectedPost?.fullHtmlContent ?: "")
            }
        }
    }
}

@Composable
private fun EditorFields(
    editedData: EditedPostData,
    component: ImporterComponent,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // --- ПОЛЯ ДЛЯ РЕДАКТИРОВАНИЯ ДАННЫХ ---

        // Поле для заголовка
        OutlinedTextField(
            value = editedData.title,
            onValueChange = { newTitle ->
                // При изменении создаем копию черновика и отправляем в компонент
                component.onEditDataChanged(editedData.copy(title = newTitle))
            },
            label = { Text("Заголовок") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // Поле для описания
        OutlinedTextField(
            value = editedData.description,
            onValueChange = { newDescription ->
                component.onEditDataChanged(editedData.copy(description = newDescription))
            },
            label = { Text("Описание") },
            modifier = Modifier.fillMaxWidth().height(150.dp) // Немного увеличим высоту
        )

        // Поле для основного контента промпта
        OutlinedTextField(
            value = editedData.content,
            onValueChange = { newContent ->
                component.onEditDataChanged(editedData.copy(content = newContent))
            },
            label = { Text("Контент промпта") },
            modifier = Modifier.fillMaxWidth().weight(1f) // Занимает все доступное оставшееся место
        )

        // TODO: Добавить сюда поля для редактирования Категории и Тегов
        // --- КНОПКИ УПРАВЛЕНИЯ ПОСТОМ ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = component::onSaveAndSelectNextClicked,
                modifier = Modifier.weight(1f) // Растягиваем, чтобы занять место
            ) {
                Text("Сохранить и следующий")
            }
            OutlinedButton(onClick = component::onSkipPostClicked) {
                Text("Пропустить")
            }
        }
    }
}

// --- НОВЫЙ "ИНСПЕКТОР ПОСТА" ---
@Composable
private fun HtmlPreviewPanel(htmlContent: String) {
    // `remember` кэширует результат парсинга, пока htmlContent не изменится
    val blocks = remember(htmlContent) {
        PostStructureParser().parse(htmlContent)
    }
    val clipboardManager = LocalClipboardManager.current

    if (blocks.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Не удалось разобрать контент поста")
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(blocks) { block ->
                when (block.type) {
                    BlockType.TEXT -> TextBlock(block.content)
                    BlockType.SPOILER -> SpoilerBlock(block) { textToCopy ->
                        clipboardManager.setText(AnnotatedString(textToCopy))
                    }

                    BlockType.QUOTE -> QuoteBlock(block.content)
                }
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
        // --- Убираем кнопку выбора файлов, т.к. они приходят с предыдущего экрана ---
        // Button(onClick = { /* ... */ }) { Text("Выбрать HTML файлы") }
        // Divider()

        Text("Готово к импорту: ${state.postsToImport.size} шт.")
        Button(
            onClick = onImportClicked,
            enabled = state.postsToImport.isNotEmpty() && !state.isLoading
        ) {
            Text("Сгенерировать JSON")
        }

        if (state.error != null) {
            Text(state.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
// --- Компоненты для каждого типа блока ---

@Composable
fun TextBlock(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 8.dp),
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
fun SpoilerBlock(block: ParsedPostBlock, onCopy: (String) -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = block.title ?: "Спойлер",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onCopy(block.content) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.ContentCopy, "Копировать содержимое")
                }
            }
            Text(
                text = block.content,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun QuoteBlock(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}