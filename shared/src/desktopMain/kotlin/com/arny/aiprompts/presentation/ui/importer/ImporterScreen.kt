package com.arny.aiprompts.presentation.ui.importer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
                    component = component
                )
                TwoPaneEditorPanel(
                    modifier = Modifier.weight(3f), // Дадим ему больше места
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

// --- НОВЫЙ ДВУХПАНЕЛЬНЫЙ РЕДАКТОР ---
@Composable
private fun TwoPaneEditorPanel(
    modifier: Modifier,
    state: ImporterState,
    component: ImporterComponent
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Левая часть - Инспектор Поста
        PostInspectorPanel(
            state = state,
            modifier = Modifier.weight(1f),
            htmlContent = state.selectedPost?.fullHtmlContent.orEmpty(),
            onAction = component::onBlockActionClicked
        )
        // Правая часть - Поля для редактирования
        EditorFields(
            modifier = Modifier.weight(1f),
            editedData = state.currentEditedData, // Передаем EditedPostData?
            component = component
        )
    }
}

// --- НОВЫЙ ИНСПЕКТОР ПОСТА ---
@Composable
private fun PostInspectorPanel(
    state: ImporterState,
    modifier: Modifier,
    htmlContent: String,
    onAction: (text: String, target: BlockActionTarget) -> Unit
) {
    val blocks = remember(htmlContent) {
        PostStructureParser().parse(htmlContent)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Инспектор Поста", style = MaterialTheme.typography.titleMedium)
        if (blocks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Не удалось разобрать контент")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    state.selectedPost?.let { post ->
                        Text("Автор: ${post.author.name}", style = MaterialTheme.typography.labelMedium)
                        Text("Дата: ${post.date}", style = MaterialTheme.typography.labelMedium)
                    }
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                }
                items(blocks) { block ->
                    when (block.type) {
                        BlockType.TEXT -> InteractiveTextBlock(block.content, onAction)
                        BlockType.SPOILER -> InteractiveSpoilerBlock(block, onAction)
                        BlockType.QUOTE -> InteractiveQuoteBlock(block.content, onAction)
                    }
                }
            }
        }
    }
}

// --- ИНТЕРАКТИВНЫЕ БЛОКИ ---

@Composable
fun InteractiveTextBlock(text: String, onAction: (String, BlockActionTarget) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ActionButtons(text, onAction)
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
fun InteractiveSpoilerBlock(block: ParsedPostBlock, onAction: (String, BlockActionTarget) -> Unit) {
    var isExpanded by remember { mutableStateOf(true) }

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { isExpanded = !isExpanded } // <-- Сворачивание
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(block.title ?: "Спойлер", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                ActionButtons(block.content, onAction)
            }
            // Отображаем контент, только если спойлер развернут
            if (isExpanded) {
                Text(block.content, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
            } else {
                // В свернутом состоянии показываем превью
                Text(
                    text = "(${block.content.take(80)}...)",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun InteractiveQuoteBlock(text: String, onAction: (String, BlockActionTarget) -> Unit) {
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

// --- ПЕРЕИСПОЛЬЗУЕМЫЕ КНОПКИ ДЕЙСТВИЙ ---
@Composable
private fun ActionButtons(textToAssign: String, onAction: (String, BlockActionTarget) -> Unit) {
    Row {
        ActionButton("T", "Назначить как Заголовок") { onAction(textToAssign, BlockActionTarget.TITLE) }
        ActionButton("D", "Добавить в Описание") { onAction(textToAssign, BlockActionTarget.DESCRIPTION) }
        ActionButton("C", "Добавить в Контент") { onAction(textToAssign, BlockActionTarget.CONTENT) }
    }
}

@Composable
private fun ActionButton(label: String, contentDescription: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(32.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun PostListPanel(
    modifier: Modifier,
    state: ImporterState,
    component: ImporterComponent
) {
    // --- ФИЛЬТРУЕМ СПИСОК ПЕРЕД ОТОБРАЖЕНИЕМ ---
    val filteredPosts = remember(state.rawPosts, state.searchQuery) {
        if (state.searchQuery.isBlank()) {
            state.rawPosts
        } else {
            state.rawPosts.filter { post ->
                // Ищем в "сыром" HTML-контенте, так как там есть всё
                post.fullHtmlContent.contains(state.searchQuery, ignoreCase = true)
            }
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Найденные посты (${filteredPosts.size} / ${state.rawPosts.size})", style = MaterialTheme.typography.titleMedium)
        
        // --- НОВЫЙ ПОИСКОВЫЙ TextField ---
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { /* no-op, handled by component */ },
            label = { Text("Поиск по постам...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        LazyColumn(
            modifier = Modifier.fillMaxHeight().border(1.dp, MaterialTheme.colorScheme.outlineVariant),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Отображаем отфильтрованный список
            items(filteredPosts, key = { it.postId }) { post ->
                val isSelected = state.selectedPostId == post.postId
                val isReadyToImport = post.postId in state.postsToImport
                
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { component.onPostClicked(post.postId) }
                        .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                if (isReadyToImport) Color.Green else Color.Transparent
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

                    // --- ИСПРАВЛЕНИЕ ЗДЕСЬ ---
                    val editedContent = state.editedData[post.postId]?.content
                    // "Предзаполнено" - если стратегия смогла извлечь непустой контент
                    val wasPrefilled = !editedContent.isNullOrBlank()

                    if (wasPrefilled) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle, // Иконка "успех"
                            contentDescription = "Данные предзаполнены",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    } else if (post.isLikelyPrompt) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline, // Иконка "подсказка"
                            contentDescription = "Вероятно, промпт",
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }
    }
}

// --- ФИНАЛЬНАЯ ВЕРСИЯ ПАНЕЛИ РЕДАКТИРОВАНИЯ ---
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorFields(
    modifier: Modifier = Modifier,
    editedData: EditedPostData?, // Принимает nullable EditedPostData
    component: ImporterComponent
) {
    // Если черновика нет (ничего не выбрано), показываем заглушку
    if (editedData == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Выберите пост из списка или все посты обработаны.")
        }
        return // Выходим из функции
    }

    // Если черновик есть, рисуем поля
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 16.dp)) {
        // Поля Title и Description ...
        OutlinedTextField(
            value = editedData.title,
            onValueChange = { newTitle ->
                component.onEditDataChanged(editedData.copy(title = newTitle))
            },
            label = { Text("Заголовок") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = editedData.description,
            onValueChange = { newDescription ->
                component.onEditDataChanged(editedData.copy(description = newDescription))
            },
            label = { Text("Описание") },
            modifier = Modifier.fillMaxWidth().height(150.dp)
        )
        Spacer(Modifier.height(16.dp))

        // --- СЕКЦИЯ С ВАРИАНТАМИ ---
        if (editedData.variants.size > 1) {
            Text("Варианты:", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                editedData.variants.forEach { variant ->
                    val isSelected = variant.content == editedData.content
                    FilterChip(
                        selected = isSelected,
                        onClick = { component.onVariantSelected(variant) },
                        label = { Text(variant.title) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = editedData.content,
            onValueChange = { newContent ->
                component.onEditDataChanged(editedData.copy(content = newContent))
            },
            label = { Text("Контент промпта") },
            modifier = Modifier.fillMaxWidth().weight(1f)
        )

        Spacer(Modifier.height(16.dp))

        // КНОПКИ УПРАВЛЕНИЯ ПОСТОМ
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = component::onSaveAndSelectNextClicked,
                modifier = Modifier.weight(1f)
            ) {
                Text("Сохранить и следующий")
            }
            OutlinedButton(onClick = component::onSkipPostClicked) {
                Text("Пропустить")
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