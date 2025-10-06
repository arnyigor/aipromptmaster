package com.arny.aipromptmaster.presentation.ui.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.arny.aipromptmaster.presentation.ui.compose.theme.AIPromptMasterTheme
import com.arny.aipromptmaster.presentation.ui.compose.theme.AppDimensions
import com.arny.aipromptmaster.presentation.ui.view.PromptViewViewModel
import com.arny.aipromptmaster.presentation.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptViewScreen(
    navController: NavController,
    promptId: String,
    viewModel: PromptViewViewModel = viewModel()
) {
    AIPromptMasterTheme {
        val context = LocalContext.current
        val uiState by viewModel.uiState.collectAsState()

        LaunchedEffect(promptId) {
            viewModel.loadPrompt(promptId)
        }

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
                        IconButton(onClick = { /* TODO: Поделиться промптом */ }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_content_copy),
                                contentDescription = "Поделиться"
                            )
                        }
                        IconButton(onClick = { /* TODO: Редактировать промпт */ }) {
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
                is com.arny.aipromptmaster.presentation.ui.view.PromptViewUiState.Loading -> {
                    LoadingIndicator(modifier = Modifier.padding(paddingValues))
                }
                is com.arny.aipromptmaster.presentation.ui.view.PromptViewUiState.Error -> {
                    ErrorState(
                        message = "Ошибка загрузки промпта",
                        onRetry = { viewModel.loadPrompt(promptId) },
                        modifier = Modifier.padding(paddingValues)
                    )
                }
                is com.arny.aipromptmaster.presentation.ui.view.PromptViewUiState.Content -> {
                    val content = uiState as com.arny.aipromptmaster.presentation.ui.view.PromptViewUiState.Content
                    PromptViewContent(
                        prompt = content.prompt,
                        modifier = Modifier.padding(paddingValues),
                        navController = navController
                    )
                }
            }
        }
    }
}

@Composable
private fun PromptViewContent(
    prompt: com.arny.aipromptmaster.domain.models.Prompt,
    modifier: Modifier = Modifier,
    navController: NavController
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(AppDimensions.margin_16)
    ) {
        // Заголовок промпта
        Text(
            text = prompt.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = AppDimensions.margin_8)
        )

        // Метаданные промпта
        PromptMetadata(
            category = prompt.category,
            isFavorite = prompt.isFavorite,
            createdDate = prompt.createdAt.toString(), // TODO: Форматировать дату
            modifier = Modifier.padding(bottom = AppDimensions.margin_16)
        )

        // Кнопки действий
        PromptActions(
            onUseInChat = { /* TODO: Использовать в чате */ },
            onCopyContent = { /* TODO: Копировать содержимое */ },
            onEdit = { /* TODO: Редактировать промпт */ },
            modifier = Modifier.padding(bottom = AppDimensions.margin_16)
        )

        // Разделитель
        Divider(modifier = Modifier.padding(vertical = AppDimensions.margin_16))

        // Содержимое промпта
        Text(
            text = "Содержимое промпта:",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = AppDimensions.margin_8)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            elevation = CardDefaults.cardElevation(defaultElevation = AppDimensions.cardElevation)
        ) {
            Text(
                text = prompt.content,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppDimensions.margin_16)
                    .verticalScroll(rememberScrollState())
            )
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