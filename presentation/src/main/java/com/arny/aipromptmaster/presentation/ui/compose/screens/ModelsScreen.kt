package com.arny.aipromptmaster.presentation.ui.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.arny.aipromptmaster.presentation.ui.modelsview.ModelsViewModel
import com.arny.aipromptmaster.presentation.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    navController: NavController,
    viewModel: ModelsViewModel = viewModel()
) {
    AIPromptMasterTheme {
        val context = LocalContext.current
        val uiState by viewModel.uiState.collectAsState()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Выбор модели ИИ") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                painter = painterResource(id = R.drawable.outline_close_24),
                                contentDescription = "Назад"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { /* TODO: Обновить список моделей */ }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_restore),
                                contentDescription = "Обновить"
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            when (uiState) {
                is com.arny.aipromptmaster.presentation.ui.modelsview.ModelsUIState.Loading -> {
                    LoadingIndicator(modifier = Modifier.padding(paddingValues))
                }
                is com.arny.aipromptmaster.presentation.ui.modelsview.ModelsUIState.Empty -> {
                    EmptyState(
                        message = "Нет доступных моделей",
                        iconResId = android.R.drawable.ic_menu_info_details,
                        modifier = Modifier.padding(paddingValues)
                    )
                }
                is com.arny.aipromptmaster.presentation.ui.modelsview.ModelsUIState.Error -> {
                    ErrorState(
                        message = "Ошибка загрузки моделей",
                        onRetry = { /* TODO: Перезагрузить модели */ },
                        modifier = Modifier.padding(paddingValues)
                    )
                }
                is com.arny.aipromptmaster.presentation.ui.modelsview.ModelsUIState.Content -> {
                    val content = uiState as com.arny.aipromptmaster.presentation.ui.modelsview.ModelsUIState.Content
                    ModelsList(
                        models = content.models,
                        selectedModelId = content.selectedModelId,
                        modifier = Modifier.padding(paddingValues),
                        onModelSelect = { model ->
                            // TODO: Выбрать модель через ViewModel
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelsList(
    models: List<com.arny.aipromptmaster.domain.models.LLMModels>,
    selectedModelId: String?,
    modifier: Modifier = Modifier,
    onModelSelect: (com.arny.aipromptmaster.domain.models.LLMModels) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(AppDimensions.margin_16),
        verticalArrangement = Arrangement.spacedBy(AppDimensions.margin_8)
    ) {
        items(models) { model ->
            ModelCard(
                model = model,
                isSelected = model.id == selectedModelId,
                onClick = { onModelSelect(model) }
            )
        }
    }
}

@Composable
private fun ModelCard(
    model: com.arny.aipromptmaster.domain.models.LLMModels,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) AppDimensions.elevationLarge else AppDimensions.cardElevation
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimensions.margin_16)
        ) {
            // Название модели
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )

                if (isSelected) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_favorite_filled),
                        contentDescription = "Выбрано",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppDimensions.margin_8))

            // Описание модели
            Text(
                text = model.description,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            Spacer(modifier = Modifier.height(AppDimensions.margin_8))

            // Информация о стоимости и возможностях
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Стоимость
                Text(
                    text = "Бесплатно", // TODO: Показать реальную стоимость
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                // Тип модели
                Text(
                    text = model.architecture.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            // Контекстное окно
            if (model.contextWindow > 0) {
                Spacer(modifier = Modifier.height(AppDimensions.margin_4))
                Text(
                    text = "Контекст: ${formatContextWindow(model.contextWindow)} токенов",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

private fun formatContextWindow(tokens: Int): String {
    return when {
        tokens >= 1000000 -> "${tokens / 1000000}M"
        tokens >= 1000 -> "${tokens / 1000}K"
        else -> tokens.toString()
    }
}

// Временные mock данные для демонстрации
@Composable
private fun getMockModels(): List<com.arny.aipromptmaster.domain.models.LLMModels> {
    return listOf(
        com.arny.aipromptmaster.domain.models.LLMModels(
            id = "gpt-4",
            name = "GPT-4",
            description = "Самая мощная модель от OpenAI для сложных задач",
            architecture = com.arny.aipromptmaster.data.models.ModelArchitecture.TRANSFORMER,
            contextWindow = 8192,
            inputPricing = com.arny.aipromptmaster.data.models.ModelPricing(0.03, "1K токенов"),
            outputPricing = com.arny.aipromptmaster.data.models.ModelPricing(0.06, "1K токенов")
        ),
        com.arny.aipromptmaster.domain.models.LLMModels(
            id = "claude-3",
            name = "Claude 3",
            description = "Модель от Anthropic с отличным пониманием контекста",
            architecture = com.arny.aipromptmaster.data.models.ModelArchitecture.TRANSFORMER,
            contextWindow = 200000,
            inputPricing = com.arny.aipromptmaster.data.models.ModelPricing(0.015, "1K токенов"),
            outputPricing = com.arny.aipromptmaster.data.models.ModelPricing(0.075, "1K токенов")
        ),
        com.arny.aipromptmaster.domain.models.LLMModels(
            id = "gemini-pro",
            name = "Gemini Pro",
            description = "Мультимодальная модель от Google",
            architecture = com.arny.aipromptmaster.data.models.ModelArchitecture.TRANSFORMER,
            contextWindow = 32768,
            inputPricing = com.arny.aipromptmaster.data.models.ModelPricing(0.0005, "1K токенов"),
            outputPricing = com.arny.aipromptmaster.data.models.ModelPricing(0.0015, "1K токенов")
        )
    )
}