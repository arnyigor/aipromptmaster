package com.arny.aipromptmaster.presentation.ui.compose.screens

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.models.PromptContent
import com.arny.aipromptmaster.domain.models.strings.StringHolder
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.ui.compose.components.ErrorState
import com.arny.aipromptmaster.presentation.ui.compose.components.LoadingIndicator
import com.arny.aipromptmaster.presentation.ui.compose.theme.AIPromptMasterTheme
import com.arny.aipromptmaster.presentation.ui.compose.theme.AppDimensions
import com.arny.aipromptmaster.presentation.ui.view.PromptViewUiState
import com.arny.aipromptmaster.presentation.ui.view.PromptViewViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptViewScreen(
    navController: NavController,
    promptId: String,
    viewModel: PromptViewViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(promptId) {
        viewModel.loadPrompt()
    }

    PromptViewScreenContent(
        navController = navController,
        uiState = uiState,
        onRetry = { viewModel.loadPrompt() },
        onShare = { /* TODO: Поделиться промптом */ },
        onEdit = { /* TODO: Редактировать промпт */ },
        onUseInChat = { /* TODO: Использовать в чате */ },
        onCopyContent = { /* TODO: Копировать содержимое */ }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptViewScreenContent(
    navController: NavController,
    uiState: PromptViewUiState,
    onRetry: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onUseInChat: () -> Unit,
    onCopyContent: () -> Unit
) {
    AIPromptMasterTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Просмотр промпта") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                painter = painterResource(id = R.drawable.outline_close_24),
                                contentDescription = "Назад"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onShare) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_content_copy),
                                contentDescription = "Поделиться"
                            )
                        }
                        IconButton(onClick = onEdit) {
                            Icon(
                                painter = painterResource(id = R.drawable.outline_edit_24),
                                contentDescription = "Редактировать"
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            when (uiState) {
                is PromptViewUiState.Loading -> {
                    LoadingIndicator(modifier = Modifier.padding(paddingValues))
                }
                is PromptViewUiState.Error -> {
                    ErrorState(
                        message = "Ошибка загрузки промпта",
                        onRetry = onRetry,
                        modifier = Modifier.padding(paddingValues)
                    )
                }
                is PromptViewUiState.Content -> {
                    PromptViewContent(
                        prompt = uiState.prompt,
                        modifier = Modifier.padding(paddingValues),
                        onUseInChat = onUseInChat,
                        onCopyContent = onCopyContent,
                        onEdit = onEdit
                    )
                }
                PromptViewUiState.Initial -> {
                    // Начальное состояние - ничего не показываем или показываем заглушку
                }
            }
        }
    }
}

@Composable
private fun PromptViewContent(
    prompt: Prompt,
    modifier: Modifier = Modifier,
    onUseInChat: () -> Unit,
    onCopyContent: () -> Unit,
    onEdit: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AppDimensions.margin_16)
    ) {
        // Заголовок промпта
        Text(
            text = prompt.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = AppDimensions.margin_8)
        )

        // Описание промпта (если есть)
        prompt.description?.let { description ->
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = AppDimensions.margin_16)
                )
            }
        }

        // Метаданные промпта
        PromptMetadata(
            category = prompt.category,
            isFavorite = prompt.isFavorite,
            createdDate = formatDate(prompt.createdAt),
            modifier = Modifier.padding(bottom = AppDimensions.margin_16)
        )

        // Кнопки действий
        PromptActions(
            onUseInChat = onUseInChat,
            onCopyContent = onCopyContent,
            onEdit = onEdit,
            modifier = Modifier.padding(bottom = AppDimensions.margin_16)
        )

        // Разделитель
        HorizontalDivider(
            modifier = Modifier.padding(vertical = AppDimensions.margin_16),
            thickness = DividerDefaults.Thickness,
            color = DividerDefaults.color
        )

        // Содержимое промпта
        Text(
            text = "Содержимое промпта:",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = AppDimensions.margin_8)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = AppDimensions.cardElevation)
        ) {
            // ИСПРАВЛЕНО: Используем поле ru или en из PromptContent
            val contentText = prompt.content.ru.ifEmpty { prompt.content.en }

            Text(
                text = contentText,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppDimensions.margin_16)
            )
        }

        // Совместимые модели
        if (prompt.compatibleModels.isNotEmpty()) {
            Spacer(modifier = Modifier.height(AppDimensions.margin_16))

            Text(
                text = "Совместимые модели:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = AppDimensions.margin_8)
            )

            prompt.compatibleModels.forEach { model ->
                Text(
                    text = "• $model",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = AppDimensions.margin_4)
                )
            }
        }

        // Теги
        if (prompt.tags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(AppDimensions.margin_16))

            Text(
                text = "Теги:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = AppDimensions.margin_8)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimensions.margin_8)
            ) {
                prompt.tags.forEach { tag ->
                    AssistChip(
                        onClick = { },
                        label = { Text(tag) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PromptMetadata(
    category: String?,
    isFavorite: Boolean,
    createdDate: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        if (category != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Категория:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = category,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(AppDimensions.margin_4))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Избранное:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                painter = painterResource(
                    id = if (isFavorite) {
                        R.drawable.ic_favorite_filled
                    } else {
                        R.drawable.ic_favorite_border
                    }
                ),
                contentDescription = if (isFavorite) "В избранном" else "Не в избранном",
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(AppDimensions.margin_4))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Создано:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = createdDate,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun PromptActions(
    onUseInChat: () -> Unit,
    onCopyContent: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Button(
            onClick = onUseInChat,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(AppDimensions.margin_16)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_plus),
                contentDescription = null,
                modifier = Modifier.padding(end = AppDimensions.margin_8)
            )
            Text("Использовать в чате")
        }

        Spacer(modifier = Modifier.height(AppDimensions.margin_8))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimensions.margin_8)
        ) {
            OutlinedButton(
                onClick = onCopyContent,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_content_copy),
                    contentDescription = null,
                    modifier = Modifier.padding(end = AppDimensions.margin_8)
                )
                Text("Копировать")
            }

            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.outline_edit_24),
                    contentDescription = null,
                    modifier = Modifier.padding(end = AppDimensions.margin_8)
                )
                Text("Редактировать")
            }
        }
    }
}

// Утилита для форматирования даты
private fun formatDate(date: Date): String {
    val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    return formatter.format(date)
}

// Mock данные для Preview
private fun getMockPrompt() = Prompt(
    id = "1",
    title = "Помощник программиста",
    description = "Опытный программист, который поможет написать чистый и эффективный код",
    content = PromptContent(
        ru = """
            Ты опытный программист с глубокими знаниями различных языков программирования и лучших практик разработки.
            
            Твои задачи:
            1. Помогать писать чистый, читаемый и эффективный код
            2. Предлагать оптимальные архитектурные решения
            3. Объяснять сложные концепции простым языком
            4. Указывать на потенциальные ошибки и способы их исправления
            5. Следовать принципам SOLID и другим best practices
            
            При ответе всегда:
            - Предоставляй примеры кода с комментариями
            - Объясняй, почему выбрано то или иное решение
            - Предлагай альтернативные подходы, если они есть
        """.trimIndent(),
        en = """
            You are an experienced programmer with deep knowledge of various programming languages and development best practices.
            
            Your tasks:
            1. Help write clean, readable, and efficient code
            2. Suggest optimal architectural solutions
            3. Explain complex concepts in simple language
            4. Point out potential errors and ways to fix them
            5. Follow SOLID principles and other best practices
            
            When responding, always:
            - Provide code examples with comments
            - Explain why a particular solution was chosen
            - Suggest alternative approaches if available
        """.trimIndent()
    ),
    compatibleModels = listOf("GPT-4", "Claude 3", "GPT-3.5"),
    category = "Программирование",
    tags = listOf("coding", "development", "best-practices", "clean-code"),
    isFavorite = true,
    status = "active",
    createdAt = Date(),
    modifiedAt = Date()
)

// PREVIEW: Светлая тема
@Preview(
    name = "Просмотр промпта - Светлая тема",
    showBackground = true,
    showSystemUi = true
)
@Composable
private fun PromptViewScreenPreviewLight() {
    PromptViewScreenContent(
        navController = rememberNavController(),
        uiState = PromptViewUiState.Content(getMockPrompt()),
        onRetry = {},
        onShare = {},
        onEdit = {},
        onUseInChat = {},
        onCopyContent = {}
    )
}

// PREVIEW: Темная тема
@Preview(
    name = "Просмотр промпта - Темная тема",
    showBackground = true,
    showSystemUi = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun PromptViewScreenPreviewDark() {
    PromptViewScreenContent(
        navController = rememberNavController(),
        uiState = PromptViewUiState.Content(getMockPrompt()),
        onRetry = {},
        onShare = {},
        onEdit = {},
        onUseInChat = {},
        onCopyContent = {}
    )
}

// PREVIEW: Состояние загрузки
@Preview(
    name = "Загрузка",
    showBackground = true,
    showSystemUi = true
)
@Composable
private fun PromptViewScreenPreviewLoading() {
    PromptViewScreenContent(
        navController = rememberNavController(),
        uiState = PromptViewUiState.Loading,
        onRetry = {},
        onShare = {},
        onEdit = {},
        onUseInChat = {},
        onCopyContent = {}
    )
}

// PREVIEW: Состояние ошибки
@Preview(
    name = "Ошибка",
    showBackground = true,
    showSystemUi = true
)
@Composable
private fun PromptViewScreenPreviewError() {
    PromptViewScreenContent(
        navController = rememberNavController(),
        uiState = PromptViewUiState.Error(StringHolder.Text("Не удалось загрузить промпт")),
        onRetry = {},
        onShare = {},
        onEdit = {},
        onUseInChat = {},
        onCopyContent = {}
    )
}

// PREVIEW: Промпт без избранного
@Preview(
    name = "Не в избранном",
    showBackground = true
)
@Composable
private fun PromptViewScreenPreviewNotFavorite() {
    PromptViewScreenContent(
        navController = rememberNavController(),
        uiState = PromptViewUiState.Content(
            getMockPrompt().copy(isFavorite = false)
        ),
        onRetry = {},
        onShare = {},
        onEdit = {},
        onUseInChat = {},
        onCopyContent = {}
    )
}
