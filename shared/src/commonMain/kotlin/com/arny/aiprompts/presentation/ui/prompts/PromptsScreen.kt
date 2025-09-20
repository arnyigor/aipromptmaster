package com.arny.aiprompts.presentation.ui.prompts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arny.aiprompts.domain.model.Prompt
import com.arny.aiprompts.presentation.screens.PromptListComponent

enum class ScreenSize {
    MOBILE,
    DESKTOP
}

@Suppress("UnusedBoxWithConstraintsScope")
@Composable
fun PromptsScreen(component: PromptListComponent) {
    val state by component.state.collectAsState()
    BoxWithConstraints {
        val screenSize = when {
            maxWidth >= 1024.dp -> ScreenSize.DESKTOP
            else -> ScreenSize.MOBILE
        }

        val snackbarHostState = remember { SnackbarHostState() }

        Scaffold(
            topBar = {
                PromptsTopAppBar(
                    state = state,
                    screenSize = screenSize,
                    component = component
                )
            },
            floatingActionButton = {
                if (screenSize == ScreenSize.MOBILE) {
                    FloatingActionButton(onClick = component::onAddPromptClicked) {
                        Icon(Icons.Default.Add, contentDescription = "Добавить промпт")
                    }
                }
            },
            bottomBar = {
                if (screenSize == ScreenSize.MOBILE) {
                    // Можно добавить bottom navigation для мобильных устройств
                    // BottomAppBar { ... }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets.systemBars
        ) { paddingValues ->
            // Основной контент
            Box(modifier = Modifier.padding(paddingValues).padding(16.dp)) {
                when (screenSize) {
                    ScreenSize.DESKTOP -> DesktopLayout(state, component)
                    ScreenSize.MOBILE -> MobileLayout(state, component)
                }
            }
        }
    }
}

@Composable
private fun DesktopLayout(state: PromptsListState, component: PromptListComponent) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // Левая панель (фильтры и список)
        MainContent(
            modifier = Modifier.weight(1f),
            state = state,
            component = component
        )
        // Правая панель (действия)
        ActionPanel(
            modifier = Modifier.width(220.dp),
            onAdd = component::onAddPromptClicked,
            onSettings = component::onSettingsClicked,
            onScraperNavigate = component::onNavigateToScraperClicked,
            onLLMNavigate = component::onNavigateToLLMClicked
        )
    }
}

@Composable
private fun TabletLayout(state: PromptsListState, component: PromptListComponent) {
    Column {
        // Верхняя панель с действиями
        ActionPanel(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            onAdd = component::onAddPromptClicked,
            onSettings = component::onSettingsClicked,
            onScraperNavigate = component::onNavigateToScraperClicked,
            onLLMNavigate = component::onNavigateToLLMClicked
        )
        // Основной контент
        MainContent(
            modifier = Modifier.weight(1f),
            state = state,
            component = component
        )
    }
}

@Composable
private fun MobileLayout(state: PromptsListState, component: PromptListComponent) {
    MainContent(
        modifier = Modifier.fillMaxSize(),
        state = state,
        component = component
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromptsTopAppBar(
    state: PromptsListState,
    screenSize: ScreenSize,
    component: PromptListComponent
) {
    TopAppBar(
        title = {
            // Разный заголовок для разных лейаутов
            val titleText = when (screenSize) {
                ScreenSize.DESKTOP -> "Prompt Manager - Показано ${state.currentPrompts.size} из ${state.allPrompts.size}"
                ScreenSize.MOBILE -> "Prompts"
            }
            Text(titleText)
        },
        actions = {
            // Показываем меню "три точки" только на мобильной и планшетной версиях
            if (screenSize != ScreenSize.DESKTOP) {
                Box {
                    IconButton(onClick = { component.onMoreMenuToggle(true) }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Дополнительные действия")
                    }
                    // Выпадающее меню
                    DropdownMenu(
                        expanded = state.isMoreMenuVisible,
                        onDismissRequest = { component.onMoreMenuToggle(false) }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Настройки") },
                            onClick = {
                                component.onSettingsClicked()
                                component.onMoreMenuToggle(false)
                            }
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun MainContent(
    modifier: Modifier = Modifier,
    state: PromptsListState,
    component: PromptListComponent
) {

    val listState = rememberLazyListState()

    // Скролл к первому элементу при изменении списка промптов
    LaunchedEffect(state.currentPrompts) {
        if (state.currentPrompts.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        FilterPanel(state = state, component = component)

        when {
            state.isLoading && state.currentPrompts.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                Alignment.Center
            ) { CircularProgressIndicator() }

            state.error != null -> ErrorState(message = state.error, onRetry = component::onRefresh)
            state.currentPrompts.isEmpty() -> EmptyState(message = "Промпты не найдены")
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.currentPrompts, key = { it.id }) { prompt ->
                        PromptListItem(
                            prompt = prompt,
                            isSelected = state.selectedPromptId == prompt.id,
                            onClick = { component.onPromptClicked(prompt.id) },
                            onFavoriteClick = { component.onFavoriteClicked(prompt.id) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PromptListItem(
    prompt: Prompt,
    isSelected: Boolean, // <-- 1. Добавляем новый параметр
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    // Вместо Card с elevation используем ElevatedCard из M3
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        // <-- 2. Управляем цветом в зависимости от того, выбран ли элемент
        colors = if (isSelected) {
            // Если выбран, используем более заметный цвет фона
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        } else {
            // Иначе используем цвета по умолчанию для ElevatedCard
            CardDefaults.elevatedCardColors()
        }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = prompt.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = prompt.description.orEmpty().ifEmpty { "Нет описания" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (prompt.tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        prompt.tags.forEach { tag ->
                            SuggestionChip(
                                onClick = { /* no-op */ },
                                label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp)) // Добавим небольшой отступ
            IconButton(onClick = onFavoriteClick) {
                Icon(
                    imageVector = if (prompt.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "В избранное",
                    tint = if (prompt.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


// Вспомогательные Composable для состояний
@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon(AppIcons.EmptyList, ...)
        Text(message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon(AppIcons.Error, ...)
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Повторить")
        }
    }
}
