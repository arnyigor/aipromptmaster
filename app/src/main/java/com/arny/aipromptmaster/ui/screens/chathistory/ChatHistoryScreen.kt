package com.arny.aipromptmaster.ui.screens.chathistory

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arny.aipromptmaster.R
import com.arny.aipromptmaster.domain.models.Chat
import com.arny.aipromptmaster.domain.models.strings.StringHolder
import com.arny.aipromptmaster.ui.theme.AIPromptMasterComposeTheme
import com.arny.aipromptmaster.ui.utils.asString

@Preview(showBackground = true)
@Composable
fun PreviewChatHistoryLoading() {
    AIPromptMasterComposeTheme {
        Column(modifier = Modifier.padding(8.dp)) {
            ChatHistoryScreen(
                state = ChatHistoryUIState.Loading,
                onChatClicked = {},
                onNewChatClick = {},
                onDeleteChat = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewChatHistorySuccess() {
    AIPromptMasterComposeTheme {
        Column(modifier = Modifier.padding(8.dp)) {
            ChatHistoryScreen(
                state = ChatHistoryUIState.Success(
                    chats = listOf(
                        Chat("1", "Alice", System.currentTimeMillis(), "Hey!"),
                        Chat("2", "Bob", System.currentTimeMillis() - 3600_000, null),
                        Chat("3", "Carol", System.currentTimeMillis() - 7200_000, "See you soon.")
                    )
                ),
                onChatClicked = {},
                onNewChatClick = {},
                onDeleteChat = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewChatHistoryError() {
    AIPromptMasterComposeTheme {
        Column(modifier = Modifier.padding(8.dp)) {
            ChatHistoryScreen(
                state = ChatHistoryUIState.Error(StringHolder.Resource(R.string.system_error)), // Replace with real StringHolder if available
                onChatClicked = {},
                onNewChatClick = {},
                onDeleteChat = {}
            )
        }
    }
}

@Composable
fun ChatHistoryRoute(
    viewModel: ChatHistoryViewModel,
    onChatClicked: (String?) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    ChatHistoryScreen(
        state = state,
        onChatClicked = onChatClicked,
        onNewChatClick = { onChatClicked(null) },
        onDeleteChat = viewModel::deleteConversation
    )
}

@Composable
fun ChatHistoryScreen(
    state: ChatHistoryUIState,
    onChatClicked: (String) -> Unit,
    onNewChatClick: () -> Unit,
    onDeleteChat: (String) -> Unit
) {
    val context = LocalContext.current
    // Состояние для диалога подтверждения удаления
    var chatToDelete by remember { mutableStateOf<Chat?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier.padding(16.dp),
                onClick = onNewChatClick) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "New Chat")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when (state) {
                is ChatHistoryUIState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is ChatHistoryUIState.Error -> {
                    // Показываем ошибку
                    Text(
                        text = state.stringHolder?.asString(context)
                            .orEmpty(), // Тут нужна твоя логика извлечения строки
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is ChatHistoryUIState.Success -> {
                    if (state.chats.isEmpty()) {
                        // Empty View
                        Text(
                            text = "История чатов пуста",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        // Список чатов
                        ChatList(
                            chats = state.chats,
                            onChatClick = { onChatClicked(it.id) },
                            onChatLongClick = { chat -> chatToDelete = chat }
                        )
                    }
                }
            }
        }
    }

    // Диалог подтверждения удаления
    if (chatToDelete != null) {
        AlertDialog(
            onDismissRequest = { chatToDelete = null },
            title = { Text("Удалить чат?") },
            text = { Text("Вы действительно хотите удалить чат \"${chatToDelete?.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        chatToDelete?.let { onDeleteChat(it.id) }
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

@Composable
fun ChatList(
    chats: List<Chat>,
    onChatClick: (Chat) -> Unit,
    onChatLongClick: (Chat) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            items = chats,
            key = { it.id }
        ) { chat ->
            ChatRow(
                chat = chat,
                onClick = { onChatClick(chat) },
                onLongClick = { onChatLongClick(chat) }
            )
        }
    }
}
