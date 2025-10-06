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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.arny.aipromptmaster.presentation.ui.compose.theme.AIPromptMasterTheme
import com.arny.aipromptmaster.presentation.ui.compose.theme.AppDimensions
import com.arny.aipromptmaster.presentation.ui.settings.SettingsViewModel
import com.arny.aipromptmaster.presentation.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = viewModel()
) {
    AIPromptMasterTheme {
        val context = LocalContext.current
        val uiState by viewModel.uiState.collectAsState()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Настройки") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                painter = painterResource(id = R.drawable.outline_close_24),
                                contentDescription = "Назад"
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppDimensions.margin_16)
            ) {
                // API Настройки
                SettingsCard(title = "API Настройки") {
                    OutlinedTextField(
                        value = "sk-...", // TODO: Получить из ViewModel
                        onValueChange = { /* TODO: Обновить API ключ */ },
                        label = { Text("OpenRouter API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { /* TODO: Показать/скрыть пароль */ }) {
                                Icon(
                                    painter = painterResource(id = android.R.drawable.ic_menu_view),
                                    contentDescription = "Показать пароль"
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(AppDimensions.margin_12))

                    Button(
                        onClick = { /* TODO: Проверить API ключ */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Проверить подключение")
                    }
                }

                // Настройки приложения
                SettingsCard(title = "Приложение") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Темная тема")
                        Switch(
                            checked = false, // TODO: Получить из ViewModel
                            onCheckedChange = { /* TODO: Переключить тему */ }
                        )
                    }

                    Spacer(modifier = Modifier.height(AppDimensions.margin_8))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Автосохранение")
                        Switch(
                            checked = true, // TODO: Получить из ViewModel
                            onCheckedChange = { /* TODO: Переключить автосохранение */ }
                        )
                    }
                }

                // Синхронизация
                SettingsCard(title = "Синхронизация") {
                    Button(
                        onClick = { /* TODO: Запустить синхронизацию */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Синхронизировать данные")
                    }

                    Spacer(modifier = Modifier.height(AppDimensions.margin_8))

                    OutlinedButton(
                        onClick = { /* TODO: Экспорт данных */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Экспорт данных")
                    }
                }

                // О приложении
                SettingsCard(title = "О приложении") {
                    Column {
                        Text(
                            text = "AI Prompt Master",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Версия 1.0.0", // TODO: Получить реальную версию
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(AppDimensions.margin_8))
                        Text(
                            text = "Управление промптами и чатами с ИИ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Кнопки действий
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppDimensions.margin_16),
                    horizontalArrangement = Arrangement.spacedBy(AppDimensions.margin_8)
                ) {
                    OutlinedButton(
                        onClick = { /* TODO: Очистить кэш */ },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Очистить кэш")
                    }

                    Button(
                        onClick = { /* TODO: Сбросить настройки */ },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Сбросить")
                    }
                }

                Spacer(modifier = Modifier.height(AppDimensions.margin_16))
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimensions.margin_16),
        elevation = CardDefaults.cardElevation(defaultElevation = AppDimensions.cardElevation)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimensions.margin_16)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.padding(bottom = AppDimensions.margin_12)
            )
            content()
        }
    }
}