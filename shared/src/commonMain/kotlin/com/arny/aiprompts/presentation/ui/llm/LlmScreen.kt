package com.arny.aiprompts.presentation.ui.llm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arny.aiprompts.data.model.ChatMessage
import com.arny.aiprompts.data.model.ChatMessageRole
import com.arny.aiprompts.data.model.LlmModel
import com.arny.aiprompts.data.model.MessageStatus
import com.arny.aiprompts.presentation.features.llm.LlmComponent
import com.arny.aiprompts.presentation.features.llm.LlmUiState
import com.arny.aiprompts.presentation.features.llm.ModelCategory
import com.arny.aiprompts.presentation.features.llm.ModelSortOrder
import com.arny.aiprompts.results.DataResult
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun LlmScreen(component: LlmComponent) {
    val uiState by component.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Показываем ошибку в Snackbar
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { errorMessage ->
            snackbarHostState.showSnackbar(
                message = errorMessage,
                actionLabel = "OK",
                duration = SnackbarDuration.Short
            )
            component.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        // Передаем состояние и коллбэки из компонента в UI
        LlmContent(
            state = uiState,
            onPromptChanged = component::onPromptChanged,
            onModelSelected = component::onModelSelected,
            onGenerateClicked = component::onStreamingGenerateClicked,
            onSearchQueryChanged = component::onSearchQueryChanged,
            onCategorySelected = component::onCategorySelected,
            onSortOrderSelected = component::onSortOrderSelected,
            toggleModelSearch = component::toggleModelSearch,
            paddingValues = paddingValues
        )
    }
}

@Suppress("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlmContent(
    state: LlmUiState,
    onPromptChanged: (String) -> Unit,
    onModelSelected: (String) -> Unit,
    onGenerateClicked: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onCategorySelected: (ModelCategory) -> Unit,
    onSortOrderSelected: (ModelSortOrder) -> Unit,
    toggleModelSearch: () -> Unit,
    paddingValues: PaddingValues
) {
    Scaffold() { scaffoldPadding ->
        // Используем paddingValues для правильного позиционирования контента
        // clearError используется при очистке чата для сброса ошибок
        BoxWithConstraints(
            modifier = Modifier
                .padding(scaffoldPadding)
                .consumeWindowInsets(paddingValues) // Правильно используем paddingValues
        ) {
            // Проверяем доступную ширину
            if (maxWidth > 900.dp) {
                // --- ЛЭЙАУТ ДЛЯ ШИРОКИХ ЭКРАНОВ (DESKTOP) ---
                Row(modifier = Modifier.fillMaxSize()) {
                    // Левая панель с моделями
                    ModelListPanel(
                        modifier = Modifier.fillMaxHeight().width(380.dp),
                        state = state,
                        onModelSelected = onModelSelected,
                        onSearchQueryChanged = onSearchQueryChanged,
                        onCategorySelected = onCategorySelected,
                        onSortOrderSelected = onSortOrderSelected
                    )
                    // Разделитель
                    VerticalDivider(modifier = Modifier.fillMaxHeight())
                    // Основной чат
                    ChatPanel(
                        modifier = Modifier.fillMaxHeight().weight(1f),
                        state = state,
                        onPromptChanged = onPromptChanged,
                        onGenerateClicked = onGenerateClicked
                    )
                }
            } else {
                // --- ЛЭЙАУТ ДЛЯ УЗКИХ ЭКРАНОВ (МОБИЛЬНЫЕ) ---
                Column(modifier = Modifier.fillMaxSize()) {
                    // Панель поиска моделей (если включена)
                    if (state.showModelSearch) {
                        ModelSearchPanel(
                            state = state,
                            onSearchQueryChanged = onSearchQueryChanged,
                            onCategorySelected = onCategorySelected,
                            onClose = toggleModelSearch,
                            modifier = Modifier.fillMaxWidth().height(200.dp)
                        )
                        HorizontalDivider()
                    }

                    // Основной чат
                    ChatPanel(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        state = state,
                        onPromptChanged = onPromptChanged,
                        onGenerateClicked = onGenerateClicked,
                    )
                }
            }
        }
    }
}

@Composable
fun ModelListPanel(
    modifier: Modifier = Modifier,
    state: LlmUiState,
    onModelSelected: (String) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onCategorySelected: (ModelCategory) -> Unit,
    onSortOrderSelected: (ModelSortOrder) -> Unit
) {
    Column(modifier = modifier) {
        // Заголовок и поиск
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Выберите модель", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))

            // Поле поиска
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchQueryChanged,
                placeholder = { Text("Поиск моделей...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Поиск")
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(12.dp))

            // Фильтры категорий
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ModelCategory.entries) { category ->
                    FilterChip(
                        selected = state.selectedCategory == category,
                        onClick = { onCategorySelected(category) },
                        label = { Text(category.displayName) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Сортировка
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Сортировка:", style = MaterialTheme.typography.bodySmall)
                ModelSortOrder.entries.forEach { sortOrder ->
                    FilterChip(
                        selected = state.selectedSortOrder == sortOrder,
                        onClick = { onSortOrderSelected(sortOrder) },
                        label = { Text(sortOrder.displayName) }
                    )
                }
            }
        }

        HorizontalDivider()

        // Список моделей
        Box(modifier = Modifier.weight(1f)) {
            when (val result = state.modelsResult) {
                is DataResult.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is DataResult.Success -> {
                    if (state.displayModels.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Модели не найдены",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn {
                            items(state.displayModels, key = { it.id }) { model ->
                                ModelItem(
                                    model = model,
                                    isSelected = model.isSelected,
                                    onClick = { onModelSelected(model.id) }
                                )
                            }
                        }
                    }
                }

                is DataResult.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = "Ошибка",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Ошибка загрузки моделей",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            result.exception?.message?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModelSearchPanel(
    modifier: Modifier = Modifier,
    state: LlmUiState,
    onSearchQueryChanged: (String) -> Unit,
    onCategorySelected: (ModelCategory) -> Unit,
    onClose: () -> Unit
) {
    Column(modifier = modifier.background(MaterialTheme.colorScheme.surface)) {
        // Заголовок с кнопкой закрытия
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Модели",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Закрыть")
            }
        }

        HorizontalDivider()

        // Поиск и фильтры
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchQueryChanged,
                placeholder = { Text("Поиск моделей...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Поиск")
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(12.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ModelCategory.entries) { category ->
                    FilterChip(
                        selected = state.selectedCategory == category,
                        onClick = { onCategorySelected(category) },
                        label = { Text(category.displayName) }
                    )
                }
            }
        }

        HorizontalDivider()

        // ✅ Правильная обработка результата с использованием переменной
        Box(modifier = Modifier.weight(1f)) {
            when (val modelsResult = state.modelsResult) {
                is DataResult.Success -> {
                    if (state.displayModels.isEmpty()) {
                        // Пустой результат
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Модели не найдены",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        // Отображаем модели
                        LazyColumn {
                            items(state.displayModels) { model ->
                                ModelItem(
                                    model = model,
                                    isSelected = model.isSelected,
                                    onClick = { /* TODO: onModelSelected(model.id) */ }
                                )
                            }
                        }
                    }
                }
                is DataResult.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is DataResult.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = "Ошибка",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Ошибка загрузки моделей",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            modelsResult.exception?.message?.let { errorMsg ->
                                Text(
                                    errorMsg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatPanel(
    modifier: Modifier = Modifier,
    state: LlmUiState,
    onPromptChanged: (String) -> Unit,
    onGenerateClicked: () -> Unit,
    onRetryMessage: (String) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Автоскролл при появлении новых сообщений
    LaunchedEffect(state.chatHistory.size) {
        if (state.chatHistory.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(0)
            }
        }
    }

    Column(modifier = modifier) {
        // Список сообщений (reverse layout для чат-стиля)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            state = listState,
            reverseLayout = true
        ) {
            // Отображаем все сообщения из истории (включая streaming)
            items(
                items = state.chatHistory.reversed(),
                key = { it.id }
            ) { message ->
                ChatMessageItem(
                    message = message,
                    onRetry = { onRetryMessage(message.id) }
                )
                Spacer(Modifier.height(8.dp))
            }

            // Placeholder для пустого чата
            if (state.chatHistory.isEmpty()) {
                item {
                    EmptyChatPlaceholder(
                        selectedModel = state.selectedModel,
                        modifier = Modifier.fillParentMaxSize()
                    )
                }
            }
        }

        HorizontalDivider()

        // Панель ввода
        ChatInputPanel(
            prompt = state.prompt,
            onPromptChanged = onPromptChanged,
            onGenerateClicked = onGenerateClicked,
            isGenerating = state.isGenerating,
            selectedModel = state.selectedModel,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )
    }
}

@Composable
private fun EmptyChatPlaceholder(
    selectedModel: LlmModel?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Chat,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            Text(
                text = if (selectedModel != null) {
                    "Начните диалог с ${selectedModel.name}"
                } else {
                    "Выберите модель для начала"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ModelItem(model: LlmModel, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = model.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    model.contextLength?.let {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Контекст: ${it / 1000}K",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if ("image" in model.inputModalities) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Визуальная",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    onRetry: () -> Unit = {}
) {
    val isUser = message.role == ChatMessageRole.USER
    val backgroundColor = when {
        isUser -> MaterialTheme.colorScheme.primaryContainer
        message.isFailed() -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val shape = if (isUser) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = backgroundColor,
            shape = shape,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Аватар для AI
                if (!isUser) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.SmartToy,
                                contentDescription = "AI",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "AI",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Текст сообщения
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge
                )

                // Статус и время
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTimestamp(message.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    StatusIndicator(
                        status = message.status,
                        onRetry = onRetry
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusIndicator(
    status: MessageStatus,
    onRetry: () -> Unit
) {
    when (status) {
        is MessageStatus.Streaming -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Печатает...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        is MessageStatus.Failed -> {
            TextButton(
                onClick = onRetry,
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Повторить",
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Повторить", style = MaterialTheme.typography.bodySmall)
            }
        }
        is MessageStatus.Sending -> {
            CircularProgressIndicator(
                progress = { status.progress },
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp
            )
        }
        MessageStatus.Sent -> {
            // Для успешных сообщений индикатор не нужен
        }
    }
}

@Composable
fun ChatInputPanel(
    prompt: String,
    onPromptChanged: (String) -> Unit,
    onGenerateClicked: () -> Unit,
    isGenerating: Boolean,
    selectedModel: LlmModel?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Индикатор выбранной модели
        selectedModel?.let {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.SmartToy,
                        contentDescription = "Выбранная модель",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = it.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Контекст: ${it.contextLength?.div(1000)}K токенов",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // Поле ввода
        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChanged,
            placeholder = { Text("Напишите ваше сообщение... (Ctrl+Enter для отправки)") },
            enabled = !isGenerating,
            modifier = Modifier.fillMaxWidth().onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when {
                        // Ctrl+Enter для отправки
                        (keyEvent.isCtrlPressed && keyEvent.key == Key.Enter) -> {
                            if (prompt.isNotBlank() && selectedModel != null && !isGenerating) {
                                onGenerateClicked()
                                true
                            } else false
                        }
                        // Enter для новой строки (стандартное поведение)
                        (keyEvent.key == Key.Enter && !keyEvent.isCtrlPressed) -> {
                            // Разрешаем перенос строки
                            false
                        }

                        else -> false
                    }
                } else false
            },
            shape = RoundedCornerShape(16.dp),
            maxLines = 6,
            minLines = 1
        )

        Spacer(Modifier.height(8.dp))

        // Кнопка отправки
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isGenerating) {
                Text(
                    text = "Генерация ответа...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Button(
                    onClick = onGenerateClicked,
                    enabled = prompt.isNotBlank() && selectedModel != null,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
                    Spacer(Modifier.width(8.dp))
                    Text("Отправить")
                }
            }
        }
    }
}

fun formatTimestamp(timestamp: Long): String {
    return try {
        val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(timestamp)
        val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val time = localDateTime.time
        "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
    } catch (e: Exception) {
        e.printStackTrace()
        "Неизвестно"
    }
}
