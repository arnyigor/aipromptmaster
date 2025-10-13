package com.arny.aipromptmaster.presentation.ui.compose.screens

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.models.PromptContent
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.ui.compose.components.*
import com.arny.aipromptmaster.presentation.ui.compose.theme.AIPromptMasterTheme
import com.arny.aipromptmaster.presentation.ui.compose.theme.AppDimensions
import com.arny.aipromptmaster.presentation.ui.home.PromptsViewModel
import com.arny.aipromptmaster.presentation.ui.home.PromptsUiState
import com.arny.aipromptmaster.presentation.ui.home.SearchState
import java.util.Date

// Основной экран с ViewModel
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptsScreen(
    navController: NavController,
    viewModel: PromptsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchState by viewModel.searchState.collectAsState()

    PromptsScreenContent(
        navController = navController,
        uiState = uiState,
        searchState = searchState,
        onSyncClick = { viewModel.synchronize() },
        onFilterClick = { filter ->
            when (filter) {
                "all" -> viewModel.setStatusFilter(null)
                "favorites" -> viewModel.setStatusFilter("favorite")
            }
        },
        onSortClick = { viewModel.onSortButtonClicked() },
        onRemoveFilter = { viewModel.removeFilter(it) },
        onToggleFavorite = { viewModel.toggleFavorite(it) },
        onAddPromptClick = { navController.navigate("addPromptFragment") },
        onPromptClick = { promptId ->
            navController.navigate("promptViewFragment/$promptId")
        }
    )
}

// Контент экрана без зависимости от ViewModel (для Preview)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptsScreenContent(
    navController: NavController,
    uiState: PromptsUiState,
    searchState: SearchState,
    onSyncClick: () -> Unit,
    onFilterClick: (String) -> Unit,
    onSortClick: () -> Unit,
    onRemoveFilter: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onAddPromptClick: () -> Unit,
    onPromptClick: (String) -> Unit
) {
    AIPromptMasterTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Промпты") },
                    actions = {
                        IconButton(onClick = onSyncClick) {
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
                AddPromptFab(onClick = onAddPromptClick)
            }
        ) { paddingValues ->
            Column(modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
            ) {
                // Фильтры
                PromptsFilters(
                    searchState = searchState,
                    onFilterClick = onFilterClick,
                    onSortClick = onSortClick,
                    onRemoveFilter = onRemoveFilter
                )

                // Список промптов
                when (uiState) {
                    is PromptsUiState.Initial,
                    is PromptsUiState.Content -> {
                        PromptsListContent(
                            prompts = getMockPrompts(),
                            onPromptClick = onPromptClick,
                            onToggleFavorite = onToggleFavorite
                        )
                    }

                    is PromptsUiState.Empty -> {
                        EmptyState(
                            message = "Нет промптов",
                            iconResId = android.R.drawable.ic_menu_info_details
                        )
                    }

                    is PromptsUiState.Error -> {
                        ErrorState(
                            message = uiState.error.message ?: "Ошибка",
                            onRetry = onSyncClick
                        )
                    }

                    is PromptsUiState.Loading -> {
                        LoadingIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun PromptsFilters(
    searchState: SearchState,
    onFilterClick: (String) -> Unit,
    onSortClick: () -> Unit,
    onRemoveFilter: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AppDimensions.margin_8),
        verticalAlignment = Alignment.CenterVertically
    ) {
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

        IconButton(onClick = onSortClick) {
            Icon(
                painter = painterResource(id = android.R.drawable.ic_menu_sort_by_size),
                contentDescription = "Сортировка"
            )
        }
    }

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
                    onClick = { onRemoveFilter(category) }
                )
            }

            searchState.tags.forEach { tag ->
                FilterChip(
                    text = tag,
                    selected = true,
                    onClick = { onRemoveFilter(tag) }
                )
            }
        }
    }
}

@Composable
private fun PromptsListContent(
    prompts: List<Prompt>,
    onPromptClick: (String) -> Unit,
    onToggleFavorite: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(AppDimensions.margin_8),
        verticalArrangement = Arrangement.spacedBy(AppDimensions.margin_8)
    ) {
        items(prompts) { prompt ->
            PromptCard(
                title = prompt.title,
                content = prompt.content,
                category = prompt.category,
                isFavorite = prompt.isFavorite,
                onClick = { onPromptClick(prompt.id) },
                onFavoriteClick = { onToggleFavorite(prompt.id) }
            )
        }
    }
}

// Mock данные для Preview
private fun getMockPrompts() = listOf(
    Prompt(
        id = "1",
        title = "Помощник программиста",
        description = "Помогает писать качественный код",
        content = PromptContent(
            ru = "Ты опытный программист. Помоги мне написать чистый и эффективный код.",
            en = "You are an experienced programmer. Help me write clean and efficient code."
        ),
        compatibleModels = listOf("GPT-4", "Claude"),
        category = "Программирование",
        tags = listOf("coding", "development"),
        isFavorite = true,
        status = "active",
        createdAt = Date(),
        modifiedAt = Date()
    ),
    Prompt(
        id = "2",
        title = "Креативный писатель",
        description = "Помощник для творческого письма",
        content = PromptContent(
            ru = "Ты креативный писатель. Помоги мне создать увлекательную историю.",
            en = "You are a creative writer. Help me create an engaging story."
        ),
        compatibleModels = listOf("GPT-4", "GPT-3.5"),
        category = "Творчество",
        tags = listOf("writing", "creativity"),
        isFavorite = false,
        status = "active",
        createdAt = Date(),
        modifiedAt = Date()
    ),
    Prompt(
        id = "3",
        title = "Бизнес-аналитик",
        description = "Анализирует бизнес-процессы",
        content = PromptContent(
            ru = "Ты бизнес-аналитик. Помоги проанализировать данные и дать рекомендации.",
            en = "You are a business analyst. Help analyze data and provide recommendations."
        ),
        compatibleModels = listOf("GPT-4"),
        category = "Бизнес",
        tags = listOf("analytics", "business"),
        isFavorite = true,
        status = "active",
        createdAt = Date(),
        modifiedAt = Date()
    )
)

// PREVIEW: Светлая тема с контентом
@Preview(
    name = "Промпты - Светлая тема",
    showBackground = true,
    showSystemUi = true
)
@Composable
private fun PromptsScreenPreviewLight() {
    PromptsScreenContent(
        navController = rememberNavController(),
        uiState = PromptsUiState.Content,
        searchState = SearchState(),
        onSyncClick = {},
        onFilterClick = {},
        onSortClick = {},
        onRemoveFilter = {},
        onToggleFavorite = {},
        onAddPromptClick = {},
        onPromptClick = {}
    )
}

// PREVIEW: Темная тема
@Preview(
    name = "Промпты - Темная тема",
    showBackground = true,
    showSystemUi = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun PromptsScreenPreviewDark() {
    PromptsScreenContent(
        navController = rememberNavController(),
        uiState = PromptsUiState.Content,
        searchState = SearchState(),
        onSyncClick = {},
        onFilterClick = {},
        onSortClick = {},
        onRemoveFilter = {},
        onToggleFavorite = {},
        onAddPromptClick = {},
        onPromptClick = {}
    )
}

// PREVIEW: Пустое состояние
@Preview(
    name = "Пустой список",
    showBackground = true,
    showSystemUi = true
)
@Composable
private fun PromptsScreenPreviewEmpty() {
    PromptsScreenContent(
        navController = rememberNavController(),
        uiState = PromptsUiState.Empty,
        searchState = SearchState(),
        onSyncClick = {},
        onFilterClick = {},
        onSortClick = {},
        onRemoveFilter = {},
        onToggleFavorite = {},
        onAddPromptClick = {},
        onPromptClick = {}
    )
}

// PREVIEW: Состояние загрузки
@Preview(
    name = "Загрузка",
    showBackground = true,
    showSystemUi = true
)
@Composable
private fun PromptsScreenPreviewLoading() {
    PromptsScreenContent(
        navController = rememberNavController(),
        uiState = PromptsUiState.Loading,
        searchState = SearchState(),
        onSyncClick = {},
        onFilterClick = {},
        onSortClick = {},
        onRemoveFilter = {},
        onToggleFavorite = {},
        onAddPromptClick = {},
        onPromptClick = {}
    )
}

// PREVIEW: С активными фильтрами
@Preview(
    name = "С фильтрами",
    showBackground = true,
    showSystemUi = true
)
@Composable
private fun PromptsScreenPreviewWithFilters() {
    PromptsScreenContent(
        navController = rememberNavController(),
        uiState = PromptsUiState.Content,
        searchState = SearchState(
            category = "Программирование",
            tags = listOf("coding", "development")
        ),
        onSyncClick = {},
        onFilterClick = {},
        onSortClick = {},
        onRemoveFilter = {},
        onToggleFavorite = {},
        onAddPromptClick = {},
        onPromptClick = {}
    )
}

// PREVIEW: Multipreview для светлой и темной темы (Android Studio Arctic Fox+)
@Preview(name = "Light Mode", showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Mode", showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class LightAndDarkPreview

@LightAndDarkPreview
@Composable
private fun PromptsScreenPreviewBothThemes() {
    PromptsScreenContent(
        navController = rememberNavController(),
        uiState = PromptsUiState.Content,
        searchState = SearchState(),
        onSyncClick = {},
        onFilterClick = {},
        onSortClick = {},
        onRemoveFilter = {},
        onToggleFavorite = {},
        onAddPromptClick = {},
        onPromptClick = {}
    )
}
