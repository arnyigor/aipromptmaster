package com.arny.aipromptmaster.presentation.ui.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.presentation.ui.compose.components.*
import com.arny.aipromptmaster.presentation.ui.compose.theme.AIPromptMasterTheme
import com.arny.aipromptmaster.presentation.ui.compose.theme.AppDimensions
import com.arny.aipromptmaster.presentation.ui.home.PromptsViewModel
import kotlinx.coroutines.flow.collectLatest
import com.arny.aipromptmaster.presentation.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptsScreen(
    navController: NavController,
    viewModel: PromptsViewModel = viewModel()
) {
    AIPromptMasterTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Промпты") },
                    actions = {
                        IconButton(onClick = { /* TODO: Синхронизация */ }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_restore),
                                contentDescription = "Синхронизация"
                            )
                        }
                        IconButton(onClick = { /* TODO: Поиск */ }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_content_copy),
                                contentDescription = "Поиск"
                            )
                        }
                    }
                )
            },
            floatingActionButton = {
                AddPromptFab(
                    onClick = {
                        navController.navigate("addPromptFragment")
                    }
                )
            }
        ) { paddingValues ->
            PromptsContent(
                modifier = Modifier.padding(paddingValues),
                navController = navController,
                viewModel = viewModel
            )
        }
    }
}

@Composable
private fun PromptsContent(
    modifier: Modifier = Modifier,
    navController: NavController,
    viewModel: PromptsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchState by viewModel.searchState.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        // Фильтры
        PromptsFilters(
            searchState = searchState,
            onFilterClick = { /* TODO: Обработка фильтров */ },
            onSortClick = { /* TODO: Сортировка */ }
        )

        // Список промптов
        when (uiState) {
            is com.arny.aipromptmaster.presentation.ui.home.PromptsUiState.Initial,
            is com.arny.aipromptmaster.presentation.ui.home.PromptsUiState.Content -> {
                PromptsList(
                    navController = navController,
                    viewModel = viewModel
                )
            }
            is com.arny.aipromptmaster.presentation.ui.home.PromptsUiState.Empty -> {
                EmptyState(
                    message = "Нет промптов",
                    iconResId = android.R.drawable.ic_menu_info_details
                )
            }
            is com.arny.aipromptmaster.presentation.ui.home.PromptsUiState.Error -> {
                ErrorState(
                    message = (uiState as com.arny.aipromptmaster.presentation.ui.home.PromptsUiState.Error).error.message ?: "Ошибка",
                    onRetry = { /* TODO: Повторить загрузку */ }
                )
            }
            is com.arny.aipromptmaster.presentation.ui.home.PromptsUiState.Loading -> {
                LoadingIndicator()
            }
        }
    }
}

@Composable
private fun PromptsFilters(
    searchState: com.arny.aipromptmaster.presentation.ui.home.SearchState,
    onFilterClick: (String) -> Unit,
    onSortClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AppDimensions.margin_8),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Основные фильтры
        Row {
            FilterChip(
                text = "Все",
                selected = searchState.status == null,
                onClick = { onFilterClick("all") }
            )

            Spacer(modifier = Modifier.width(AppDimensions.margin_8))

            FilterChip(
                text = "Избранное",
                selected = searchState.status == "favorite",
                onClick = { onFilterClick("favorites") }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Кнопка сортировки
        IconButton(onClick = onSortClick) {
            Icon(
                painter = painterResource(id = android.R.drawable.ic_menu_sort_by_size),
                contentDescription = "Сортировка"
            )
        }
    }

    // Динамические фильтры (категории, теги)
    if (searchState.category != null || searchState.tags.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimensions.margin_8),
            horizontalArrangement = Arrangement.spacedBy(AppDimensions.margin_8)
        ) {
            searchState.category?.let { category ->
                FilterChip(
                    text = category,
                    selected = true,
                    onClick = { /* TODO: Удалить фильтр категории */ }
                )
            }

            searchState.tags.forEach { tag ->
                FilterChip(
                    text = tag,
                    selected = true,
                    onClick = { /* TODO: Удалить фильтр тега */ }
                )
            }
        }
    }
}

@Composable
private fun PromptsList(
    navController: NavController,
    viewModel: PromptsViewModel
) {
    // Временные mock данные для демонстрации
    val mockPrompts = listOf(
        com.arny.aipromptmaster.domain.models.Prompt(
            id = "1",
            title = "Помощник программиста",
            content = "Ты опытный программист. Помоги мне решить задачу...",
            category = "Программирование",
            isFavorite = true,
            createdAt = java.util.Date(),
            modifiedAt = java.util.Date()
        ),
        com.arny.aipromptmaster.domain.models.Prompt(
            id = "2",
            title = "Креативный писатель",
            content = "Помоги мне написать увлекательную историю...",
            category = "Творчество",
            isFavorite = false,
            createdAt = java.util.Date(),
            modifiedAt = java.util.Date()
        )
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(AppDimensions.margin_8),
        verticalArrangement = Arrangement.spacedBy(AppDimensions.margin_8)
    ) {
        items(mockPrompts) { prompt ->
            PromptCard(
                title = prompt.title,
                content = prompt.content,
                category = prompt.category,
                isFavorite = prompt.isFavorite,
                onClick = {
                    navController.navigate("promptViewFragment/${prompt.id}")
                },
                onFavoriteClick = {
                    // TODO: Реализовать через ViewModel
                }
            )
        }
    }
}