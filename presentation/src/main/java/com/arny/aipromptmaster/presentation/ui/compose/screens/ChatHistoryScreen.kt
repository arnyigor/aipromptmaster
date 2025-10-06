package com.arny.aipromptmaster.presentation.ui.compose.screens

import androidx.compose.foundation.clickable
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
import com.arny.aipromptmaster.presentation.ui.chathistory.ChatHistoryViewModel
import com.arny.aipromptmaster.presentation.ui.compose.components.*
import com.arny.aipromptmaster.presentation.ui.compose.theme.AIPromptMasterTheme
import com.arny.aipromptmaster.presentation.ui.compose.theme.AppDimensions
import com.arny.aipromptmaster.presentation.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHistoryScreen(
    navController: NavController,
    viewModel: ChatHistoryViewModel = viewModel()
) {
    AIPromptMasterTheme {
        val context = LocalContext.current
        val uiState by viewModel.uiState.collectAsState()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("История чатов") },
                    actions = {
                        IconButton(onClick = { /* TODO: Поиск по истории */ }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_content_copy),
                                contentDescription = "Поиск"
                            )
                        }
                        IconButton(onClick = { /* TODO: Фильтры */ }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_tune),
                                contentDescription = "Фильтры"
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            when (uiState) {
                is com.arny.aipromptmaster.presentation.ui.chathistory.ChatHistoryUIState.Loading -> {
                    LoadingIndicator(modifier = Modifier.padding(paddingValues))
                }
                is com.arny.aipromptmaster.presentation.ui.chathistory.ChatHistoryUIState.Empty -> {
                    EmptyState(
                        message = "Нет истории чатов",
                        iconResId = R.drawable.ic_history,
                        modifier = Modifier.padding(paddingValues)
                    )
                }
                is com.arny.aipromptmaster.presentation.ui.chathistory.ChatHistoryUIState.Error -> {
                    ErrorState(
                        message = "Ошибка загрузки истории",
                        onRetry = { /* TODO: Перезагрузить историю */ },
                        modifier = Modifier.padding(paddingValues)
                    )
                }
                is com.arny.aipromptmaster.presentation.ui.chathistory.ChatHistoryUIState.Content -> {
                    val content = uiState as com.arny.aipromptmaster.presentation.ui.chathistory.ChatHistoryUIState.Content
                    ChatHistoryList(
                        chatItems = content.chats,
                        modifier = Modifier.padding(paddingValues),
                        navController = navController
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatHistoryList(
    chatItems: List<com.arny.aipromptmaster.presentation.ui.chathistory.ChatItem>,
    modifier: Modifier = Modifier,
    navController: NavController
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(AppDimensions.margin_16),
        verticalArrangement = Arrangement.spacedBy(AppDimensions.margin_8)
    ) {
        items(chatItems) { chatItem ->
            ChatHistoryCard(
                chatItem = chatItem,
                onClick = {
                    navController.navigate("chatFragment/${chatItem.id}")
                },
                onDelete = {
                    // TODO: Показать диалог подтверждения удаления
                }
            )
        }
    }
}

@Composable
private fun ChatHistoryCard(
    chatItem: com.arny.aipromptmaster.presentation.ui.chathistory.ChatItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = AppDimensions.cardElevation)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimensions.margin_16)
        ) {
            // Заголовок чата
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chatItem.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_delete),
                        contentDescription = "Удалить чат",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppDimensions.margin_8))

            // Последнее сообщение
            if (chatItem.lastMessage.isNotEmpty()) {
                Text(
                    text = chatItem.lastMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    modifier = Modifier.padding(bottom = AppDimensions.margin_8)
                )
            }

            // Метаданные
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chatItem.modelName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = chatItem.messageCount.toString() + " сообщений",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = chatItem.lastActivityDate, // TODO: Форматировать дату
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Временные mock данные для демонстрации
@Composable
private fun getMockChatItems(): List<com.arny.aipromptmaster.presentation.ui.chathistory.ChatItem> {
    return listOf(
        com.arny.aipromptmaster.presentation.ui.chathistory.ChatItem(
            id = "1",
            title = "Помощь с Kotlin кодом",
            lastMessage = "Спасибо за помощь! Код работает отлично и намного понятнее...",
            modelName = "GPT-4",
            messageCount = 12,
            lastActivityDate = "2 часа назад"
        ),
        com.arny.aipromptmaster.presentation.ui.chathistory.ChatItem(
            id = "2",
            title = "Объяснение алгоритма",
            lastMessage = "Теперь я понимаю как работает этот алгоритм. Ваше объяснение было очень подробным...",
            modelName = "Claude-3",
            messageCount = 8,
            lastActivityDate = "Вчера"
        ),
        com.arny.aipromptmaster.presentation.ui.chathistory.ChatItem(
            id = "3",
            title = "Генерация идей для проекта",
            lastMessage = "Отличные идеи! Особенно понравилась концепция с использованием микросервисов...",
            modelName = "GPT-4",
            messageCount = 15,
            lastActivityDate = "3 дня назад"
        )
    )
}