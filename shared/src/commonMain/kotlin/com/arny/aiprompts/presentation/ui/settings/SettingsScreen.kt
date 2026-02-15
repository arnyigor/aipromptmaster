package com.arny.aiprompts.presentation.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.arny.aiprompts.presentation.screens.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    component: SettingsComponent,
    modifier: Modifier = Modifier
) {
    val state by component.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Показываем сообщение в Snackbar при изменении saveMessage
    LaunchedEffect(state.saveMessage) {
        state.saveMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            delay(1000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = component::onBackClicked) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Navigation Tabs
            SettingsTabs(
                activeSection = state.activeSection,
                onSectionChanged = component::onSectionChanged
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Content based on active section
            when (state.activeSection) {
                SettingsSection.API -> ApiSettingsSection(state, component)
                SettingsSection.GITHUB -> GitHubSettingsSection(state, component)
                SettingsSection.PERSONALIZATION -> PersonalizationSection(state, component)
            }
        }
    }
}

@Composable
private fun SettingsTabs(
    activeSection: SettingsSection,
    onSectionChanged: (SettingsSection) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TabButton(
                icon = Icons.Default.Key,
                label = "API",
                isActive = activeSection == SettingsSection.API,
                onClick = { onSectionChanged(SettingsSection.API) },
                modifier = Modifier.weight(1f)
            )
            TabButton(
                icon = Icons.Default.Cloud,
                label = "GitHub",
                isActive = activeSection == SettingsSection.GITHUB,
                onClick = { onSectionChanged(SettingsSection.GITHUB) },
                modifier = Modifier.weight(1f)
            )
            TabButton(
                icon = Icons.Default.Person,
                label = "Профиль",
                isActive = activeSection == SettingsSection.PERSONALIZATION,
                onClick = { onSectionChanged(SettingsSection.PERSONALIZATION) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TabButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (isActive) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        ),
        modifier = modifier
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

// ==================== API Settings Section ====================

@Composable
private fun ApiSettingsSection(
    state: SettingsState,
    component: SettingsComponent
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Настройки API",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            // OpenRouter API Key
            OutlinedTextField(
                value = state.openRouterApiKey,
                onValueChange = component::onOpenRouterApiKeyChanged,
                label = { Text("OpenRouter API ключ") },
                placeholder = { Text("sk-or-v1-...") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (state.isApiKeyVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = component::onToggleApiKeyVisibility) {
                        Icon(
                            imageVector = if (state.isApiKeyVisible) {
                                Icons.Filled.Visibility
                            } else {
                                Icons.Filled.VisibilityOff
                            },
                            contentDescription = if (state.isApiKeyVisible) "Скрыть" else "Показать"
                        )
                    }
                },
                singleLine = true
            )

            Button(
                onClick = component::onSaveOpenRouterApiKey,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading && state.openRouterApiKey.isNotBlank()
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Сохранить API ключ")
            }

            Divider()

            // Base URL
            Text(
                text = "Кастомный сервер (опционально)",
                style = MaterialTheme.typography.titleSmall
            )
            
            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = component::onBaseUrlChanged,
                label = { Text("Base URL") },
                placeholder = { Text("http://localhost:1234/v1/") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { 
                    Text("Для LMStudio, Ollama и др. Оставьте пустым для OpenRouter") 
                }
            )

            Button(
                onClick = component::onSaveBaseUrl,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading
            ) {
                Text("Сохранить URL")
            }

            // Security Info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "🔒 Безопасность",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "• API ключи хранятся в зашифрованном виде",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• На десктопе используются системные хранилища",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• На Android используется Android Keystore",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

// ==================== GitHub Settings Section ====================

@Composable
private fun GitHubSettingsSection(
    state: SettingsState,
    component: SettingsComponent
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Синхронизация с GitHub",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Настройте синхронизацию промптов с вашим GitHub репозиторием для бэкапа и версионирования.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // GitHub Token
            OutlinedTextField(
                value = state.gitHubToken,
                onValueChange = component::onGitHubTokenChanged,
                label = { Text("Personal Access Token (PAT)") },
                placeholder = { Text("ghp_...") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                supportingText = { 
                    Text("Токен с правами 'repo'. Создайте в Settings → Developer settings → Personal access tokens") 
                }
            )

            // GitHub Repo
            OutlinedTextField(
                value = state.gitHubRepo,
                onValueChange = component::onGitHubRepoChanged,
                label = { Text("Репозиторий") },
                placeholder = { Text("username/repo-name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text("Формат: username/repository") }
            )

            // Connection Test
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = component::onTestGitHubConnectionClicked,
                    enabled = state.gitHubToken.isNotBlank() && 
                             state.gitHubRepo.isNotBlank() &&
                             state.connectionStatus != ConnectionStatus.CHECKING
                ) {
                    if (state.connectionStatus == ConnectionStatus.CHECKING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Проверить соединение")
                }

                // Status Indicator
                when (state.connectionStatus) {
                    ConnectionStatus.SUCCESS -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.Green
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Подключено", color = Color.Green)
                        }
                    }
                    ConnectionStatus.ERROR -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ошибка", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    else -> {}
                }
            }

            // Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "ℹ️ Информация",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "• Промпты сохраняются в папку prompts/ в вашем репозитории",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• Каждый промпт - отдельный JSON файл",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• Репозиторий должен быть публичным или токен иметь доступ к приватным",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

// ==================== Personalization Section ====================

@Composable
private fun PersonalizationSection(
    state: SettingsState,
    component: SettingsComponent
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Персонализация",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Опишите себя, свой стиль общения и предпочтения. Эта информация будет добавляться ко всем вашим чатам как контекст.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = state.userContext,
                onValueChange = component::onUserContextChanged,
                label = { Text("Личный контекст") },
                placeholder = { 
                    Text("Например: Меня зовут Алекс. Я Android-разработчик. Отвечай кратко и по делу. Используй Kotlin для примеров кода.") 
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                maxLines = 10
            )

            Button(
                onClick = component::onSaveUserContext,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Сохранить контекст")
            }

            // Example Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "💡 Примеры",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "• 'Я врач, предпочитаю научный стиль'",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• 'Я студент, объясняй простыми словами'",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• 'Я пишу статьи, помогай с заголовками'",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
