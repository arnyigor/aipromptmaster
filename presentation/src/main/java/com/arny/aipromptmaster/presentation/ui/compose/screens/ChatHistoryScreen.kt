package com.arny.aipromptmaster.presentation.ui.compose.screens

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.arny.aipromptmaster.domain.models.Chat
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.ui.chathistory.ChatHistoryUIState
import com.arny.aipromptmaster.presentation.ui.chathistory.ChatHistoryViewModel
import com.arny.aipromptmaster.presentation.ui.compose.components.*
import com.arny.aipromptmaster.presentation.ui.compose.theme.AIPromptMasterTheme
import com.arny.aipromptmaster.presentation.ui.compose.theme.AppDimensions
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHistoryScreen(
    navController: NavController,
    viewModel: ChatHistoryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    ChatHistoryScreenContent(
        navController = navController,
        uiState = uiState,
        onDeleteChat = { chatId ->
            viewModel.deleteConversation(chatId)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHistoryScreenContent(
    navController: NavController,
    uiState: ChatHistoryUIState,
    onDeleteChat: (String) -> Unit
) {
    AIPromptMasterTheme {
        var chatToDelete by remember { mutableStateOf<Chat?>(null) }

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
                is ChatHistoryUIState.Loading -> {
                    LoadingIndicator(modifier = Modifier.padding(paddingValues))
                }
                is ChatHistoryUIState.Success -> {
                    if (uiState.chats.isEmpty()) {
                        EmptyState(
                            message = "Нет истории чатов",
                            iconResId = R.drawable.ic_history,
                            modifier = Modifier.padding(paddingValues)
                        )
                    } else {
                        ChatHistoryList(
                            chats = uiState.chats,
                            modifier = Modifier.padding(paddingValues),
                            navController = navController,
                            onDeleteClick = { chat ->
                                chatToDelete = chat
                            }
                        )
                    }
                }
                is ChatHistoryUIState.Error -> {
                    ErrorState(
                        message = uiState.stringHolder?.toString() ?: "Ошибка загрузки истории",
                        onRetry = { /* Reload будет автоматически через Flow */ },
                        modifier = Modifier.padding(paddingValues)
                    )
                }
            }
        }

        // Диалог подтверждения удаления
        chatToDelete?.let { chat ->
            AlertDialog(
                onDismissRequest = { chatToDelete = null },
                title = { Text("Удалить чат?") },
                text = { Text("Вы уверены, что хотите удалить чат \"${chat.name}\"?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteChat(chat.conversationId)
                            chatToDelete = null
                        }
                    ) {
                        Text("Удалить", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { chatToDelete = null }) {
                        Text("Отмена")
                    }
                }
            )
        }
    }
}

@Composable
private fun ChatHistoryList(
    chats: List<Chat>,
    modifier: Modifier = Modifier,
    navController: NavController,
    onDeleteClick: (Chat) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(AppDimensions.margin_16),
        verticalArrangement = Arrangement.spacedBy(AppDimensions.margin_8)
    ) {
        items(chats) { chat ->
            ChatHistoryCard(
                chat = chat,
                onClick = {
                    navController.navigate("chatFragment/${chat.conversationId}")
                },
                onDelete = {
                    onDeleteClick(chat)
                }
            )
        }
    }
}

@Composable
private fun ChatHistoryCard(
    chat: Chat,
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
                    text = chat.name,
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

            // Последнее сообщение
            chat.lastMessage?.let { lastMessage ->
                if (lastMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(AppDimensions.margin_8))
                    Text(
                        text = lastMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppDimensions.margin_8))

            // Время последней активности
            Text(
                text = formatRelativeTime(chat.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ИСПРАВЛЕНО: Принимает Long вместо Date
private fun formatRelativeTime(timestampMillis: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestampMillis

    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "только что"
        diff < TimeUnit.HOURS.toMillis(1) -> {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
            "$minutes мин назад"
        }
        diff < TimeUnit.DAYS.toMillis(1) -> {
            val hours = TimeUnit.MILLISECONDS.toHours(diff)
            "$hours ч назад"
        }
        diff < TimeUnit.DAYS.toMillis(7) -> {
            val days = TimeUnit.MILLISECONDS.toDays(diff)
            when (days) {
                1L -> "вчера"
                2L -> "позавчера"
                else -> "$days дней назад"
            }
        }
        else -> {
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestampMillis))
        }
    }
}

// ИСПРАВЛЕНО: Mock данные соответствуют модели Chat
private fun getMockChats() = listOf(
    Chat(
        conversationId = "1",
        name = "Помощь с Kotlin кодом",
        lastMessage = "Спасибо за помощь! Код работает отлично и намного понятнее...",
        timestamp = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2)
    ),
    Chat(
        conversationId = "2",
        name = "Объяснение алгоритма",
        lastMessage = "Теперь я понимаю как работает этот алгоритм. Ваше объяснение было очень подробным...",
        timestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
    ),
    Chat(
        conversationId = "3",
        name = "Генерация идей для проекта",
        lastMessage = "Отличные идеи! Особенно понравилась концепция с использованием микросервисов...",
        timestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(3)
    ),
    Chat(
        conversationId = "4",
        name = "Вопросы по архитектуре",
        lastMessage = null,
        timestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
    ),
    Chat(
        conversationId = "5",
        name = "Оптимизация приложения",
        lastMessage = "Применил все рекомендации - производительность выросла на 40%!",
        timestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(14)
    )
)

// PREVIEW: Светлая тема со списком чатов
@Preview(
    name = "История чатов - Светлая тема",
    showBackground = true,
    showSystemUi = true
)
@Composable
private fun ChatHistoryScreenPreviewLight() {
    ChatHistoryScreenContent(
        navController = rememberNavController(),
        uiState = ChatHistoryUIState.Success(getMockChats()),
        onDeleteChat = {}
    )
}

// PREVIEW: Темная тема
@Preview(
    name = "История чатов - Темная тема",
    showBackground = true,
    showSystemUi = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun ChatHistoryScreenPreviewDark() {
    ChatHistoryScreenContent(
        navController = rememberNavController(),
        uiState = ChatHistoryUIState.Success(getMockChats()),
        onDeleteChat = {}
    )
}

// PREVIEW: Пустой список
@Preview(
    name = "Пустая история",
    showBackground = true,
    showSystemUi = true
)
@Composable
private fun ChatHistoryScreenPreviewEmpty() {
    ChatHistoryScreenContent(
        navController = rememberNavController(),
        uiState = ChatHistoryUIState.Success(emptyList()),
        onDeleteChat = {}
    )
}

// PREVIEW: Состояние загрузки
@Preview(
    name = "Загрузка",
    showBackground = true,
    showSystemUi = true
)
@Composable
private fun ChatHistoryScreenPreviewLoading() {
    ChatHistoryScreenContent(
        navController = rememberNavController(),
        uiState = ChatHistoryUIState.Loading,
        onDeleteChat = {}
    )
}

// PREVIEW: Состояние ошибки
@Preview(
    name = "Ошибка",
    showBackground = true,
    showSystemUi = true
)
@Composable
private fun ChatHistoryScreenPreviewError() {
    ChatHistoryScreenContent(
        navController = rememberNavController(),
        uiState = ChatHistoryUIState.Error(
            com.arny.aipromptmaster.domain.models.strings.StringHolder.Text(
                "Не удалось загрузить историю чатов"
            )
        ),
        onDeleteChat = {}
    )
}

// PREVIEW: Одна карточка чата
@Preview(
    name = "Карточка чата",
    showBackground = true
)
@Composable
private fun ChatHistoryCardPreview() {
    AIPromptMasterTheme {
        ChatHistoryCard(
            chat = Chat(
                conversationId = "1",
                name = "Помощь с Kotlin кодом",
                lastMessage = "Спасибо за помощь! Код работает отлично и намного понятнее...",
                timestamp = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2)
            ),
            onClick = {},
            onDelete = {}
        )
    }
}
