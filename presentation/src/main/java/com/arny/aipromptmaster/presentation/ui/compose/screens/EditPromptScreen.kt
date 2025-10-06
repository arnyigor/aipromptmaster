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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.arny.aipromptmaster.presentation.ui.compose.theme.AIPromptMasterTheme
import com.arny.aipromptmaster.presentation.ui.compose.theme.AppDimensions
import com.arny.aipromptmaster.presentation.ui.addprompt.EditPromptViewModel
import com.arny.aipromptmaster.presentation.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPromptScreen(
    navController: NavController,
    promptId: String?,
    viewModel: EditPromptViewModel = viewModel()
) {
    AIPromptMasterTheme {
        val context = LocalContext.current
        val uiState by viewModel.uiState.collectAsState()

        // Состояние формы
        var title by remember { mutableStateOf(TextFieldValue("")) }
        var content by remember { mutableStateOf(TextFieldValue("")) }
        var category by remember { mutableStateOf("") }

        LaunchedEffect(promptId) {
            if (promptId != null) {
                viewModel.loadPrompt(promptId)
            }
        }

        // Обновляем состояние формы при изменении uiState
        LaunchedEffect(uiState) {
            if (uiState is com.arny.aipromptmaster.presentation.ui.addprompt.EditPromptUiState.Content) {
                val content = uiState as com.arny.aipromptmaster.presentation.ui.addprompt.EditPromptUiState.Content
                title = TextFieldValue(content.prompt.title)
                content = TextFieldValue(content.prompt.content)
                category = content.prompt.category ?: ""
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (promptId == null) "Создать промпт" else "Редактировать промпт") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                painter = painterResource(id = R.drawable.outline_close_24),
                                contentDescription = "Назад"
                            )
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                // TODO: Сохранить промпт через ViewModel
                                navController.popBackStack()
                            }
                        ) {
                            Text("Сохранить")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(AppDimensions.margin_16),
                verticalArrangement = Arrangement.spacedBy(AppDimensions.margin_16)
            ) {
                // Заголовок промпта
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название промпта") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium
                )

                // Категория промпта
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Категория (необязательно)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Например: Программирование, Дизайн, Маркетинг...") }
                )

                // Разделитель
                Divider()

                // Содержимое промпта
                Text(
                    text = "Содержимое промпта:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    placeholder = {
                        Text("Введите текст промпта здесь...")
                    },
                    textStyle = MaterialTheme.typography.bodyLarge
                )

                // Кнопки действий
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppDimensions.margin_8)
                ) {
                    OutlinedButton(
                        onClick = { /* TODO: Предварительный просмотр */ },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Предпросмотр")
                    }

                    Button(
                        onClick = {
                            // TODO: Сохранить промпт
                            navController.popBackStack()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = title.text.isNotBlank() && content.text.isNotBlank()
                    ) {
                        Text("Сохранить")
                    }
                }
            }
        }
    }
}

// Помощник для создания нового промпта
@Composable
private fun CreatePromptHelper(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = AppDimensions.cardElevation)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimensions.margin_16),
            verticalArrangement = Arrangement.spacedBy(AppDimensions.margin_8)
        ) {
            Text(
                text = "💡 Советы по созданию промптов",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )

            Text(
                text = "• Будьте конкретны в описании задачи",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "• Укажите желаемый формат ответа",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "• Добавьте примеры для лучшего понимания",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "• Укажите ограничения и требования",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}