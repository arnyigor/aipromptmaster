package com.arny.aipromptmaster.ui.screens.prompts

import android.content.ClipData
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.ui.theme.AIPromptMasterComposeTheme
import com.arny.aipromptmaster.ui.utils.asString
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Preview(name = "Light Mode", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PromptListScreenContentPreview() {
    val dummyPrompts = listOf(
        Prompt(
            id = "1",
            title = "Coding Assistant",
            description = "Help with Kotlin",
            isFavorite = true,
            isLocal = false,
            tags = listOf("Coding")
        ),
        Prompt(
            id = "2",
            title = "Creative Writer Creative Writer Creative Writer",
            description = "Write stories",
            tags = listOf("Writing")
        ),
        Prompt(
            id = "3",
            title = "SEO Expert",
            description = null,
            tags = listOf("SEO", "Marketing")
        )
    )

    // 2. Состояние заголовка/фильтров (как в реальном коде).
    val previewState = PromptListUiState(
        query = "",
        prompts = dummyPrompts,
        availableTags = dummyPrompts.flatMap { it.tags }.distinct(),
        selectedTags = emptySet(),
        isFiltersExpanded = true,
        availableCategories = emptyList(),   // у вас нет категорий
        selectedCategory = null,
        isSyncing = false,
    )

    AIPromptMasterComposeTheme {
        PromptListScreenContent(
            uiState = previewState,
            onIntent = {},
            onNavigateToEdit = { /* no-op */ },
        )
    }
}

@Composable
fun PromptListScreen(
    onNavigateToDetails: (String) -> Unit,
    onNavigateToEdit: (String?) -> Unit,
    viewModel: PromptListViewModel = koinViewModel(),
) {

    // Подписываемся на состояние фильтров
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    // 2. Обработка Эффектов (Side Effects)
    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is PromptListEffect.NavigateToDetails -> onNavigateToDetails(effect.promptId)

                is PromptListEffect.ShowToast -> Toast.makeText(
                    context,
                    effect.message.asString(context),
                    Toast.LENGTH_SHORT
                ).show()

                is PromptListEffect.Copy -> {
                    scope.launch {
                        clipboard.setClipEntry(
                            ClipData.newPlainText(effect.label, effect.text).toClipEntry()
                        )
                    }
                }

                is PromptListEffect.ShowDeleteConfirmation -> {
                    pendingDeleteId = effect.promptId
                }
            }
        }
    }

    // ------------- CONFIRMATION DIALOG --------------------
    pendingDeleteId?.let { id ->
        // Find the prompt in the current list to display its title.
        val targetPrompt = uiState.prompts.find { it.id == id }

        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Удалить") },
            text = {
                if (targetPrompt != null) {
                    Text("Вы уверены, что хотите удалить \"${targetPrompt.title}\"?")
                } else {
                    // Fallback – in the rare case it disappeared.
                    Text("Вы действительно хотите удалить этот пункт?")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.processIntent(PromptListIntent.RemovePrompt(id))
                        pendingDeleteId = null
                    }
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    // 3. Вызов Stateless компонента
    PromptListScreenContent(
        uiState = uiState,
        onIntent = viewModel::processIntent,
        onNavigateToEdit = onNavigateToEdit
    )
}

@Composable
fun PromptListScreenContent(
    uiState: PromptListUiState,
    onIntent: (PromptListIntent) -> Unit,
    onNavigateToEdit: (String?) -> Unit,
) {
    Scaffold(
        topBar = {
            Column {
                SearchAndFilterHeader(
                    state = uiState,
                    onIntent = onIntent
                )
                SyncStatusIndicator(state = uiState)
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Создать") },
                icon = { Icon(Icons.Default.Add, null) },
                onClick = { onNavigateToEdit(null) }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .fillMaxSize()
        ) {
            PromptListList(
                prompts = uiState.prompts,
                onIntent = onIntent,
            )
        }
    }
}


@Composable
private fun PromptListList(
    prompts: List<Prompt>,
    onIntent: (PromptListIntent) -> Unit,
) {
    if (prompts.isEmpty()) {
        // Empty State
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Список пуст") // Можно добавить красивую заглушку
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
        ) {
            items(
                items = prompts,
                key = { it.id }
            ) { prompt ->
                PromptCard(
                    prompt = prompt,
                    onClick = { onIntent(PromptListIntent.OnPromptClick(prompt.id)) },
                    onFavoriteClick = {
                        onIntent(
                            PromptListIntent.ToggleFavorite(
                                prompt.id,
                                prompt.isFavorite
                            )
                        )
                    },
                    onCopy = { onIntent(PromptListIntent.CopyText(prompt.id)) },
                    onRemove = { onIntent(PromptListIntent.RemovePending(prompt.id)) }
                )
            }
        }
    }
}

// === HEADER: Поиск и Фильтры ===

@Composable
fun SearchAndFilterHeader(
    state: PromptListUiState,
    onIntent: (PromptListIntent) -> Unit
) {
    val focusManager = LocalFocusManager.current

    // 1. Локальное состояние для текста и позиции курсора
    // Инициализируем его текущим значением из ViewModel
    var queryState by remember {
        mutableStateOf(TextFieldValue(text = state.query))
    }

    // 2. Синхронизация "ViewModel -> UI" (например, при нажатии "Очистить")
    // Если state.query изменился ИЗВНЕ и не совпадает с тем, что мы держим локально,
    // значит это внешнее событие (сброс), и мы должны обновить поле.
    LaunchedEffect(state.query) {
        if (state.query != queryState.text) {
            queryState = queryState.copy(
                text = state.query,
                selection = TextRange(state.query.length) // Ставим курсор в конец при внешнем изменении
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(bottom = 8.dp)
    ) {
        // --- 1. Строка Поиска ---
        OutlinedTextField(
            value = queryState, // Используем TextFieldValue!
            onValueChange = { newValue ->
                queryState = newValue // Мгновенно обновляем UI (курсор не прыгает)
                onIntent(PromptListIntent.SearchQueryChanged(newValue.text)) // Шлем в VM
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Поиск...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                Row {
                    // Показываем "Очистить", если есть текст или фильтры
                    if (state.query.isNotEmpty() || state.selectedTags.isNotEmpty() || state.selectedCategory != null) {
                        IconButton(onClick = {
                            onIntent(PromptListIntent.ClearSearchAndFilters)
                            focusManager.clearFocus() // Убираем клавиатуру при очистке
                        }) {
                            Icon(Icons.Default.Clear, "Clear")
                        }
                    }
                    IconButton(onClick = { onIntent(PromptListIntent.ToggleFiltersVisibility) }) {
                        Icon(
                            Icons.Default.FilterList,
                            "Filters",
                            tint = if (state.isFiltersExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
        )

        // --- 2. Панель фильтров (без изменений) ---
        AnimatedVisibility(
            visible = state.isFiltersExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {

                // А) Секция Категорий
                if (state.availableCategories.isNotEmpty()) {
                    Text(
                        text = "Категории",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = state.selectedCategory == null,
                                onClick = { onIntent(PromptListIntent.SelectCategory(null)) },
                                label = { Text("Все") }
                            )
                        }
                        items(state.availableCategories) { category ->
                            FilterChip(
                                selected = state.selectedCategory == category,
                                onClick = {
                                    val newCategory =
                                        if (state.selectedCategory == category) null else category
                                    onIntent(PromptListIntent.SelectCategory(newCategory))
                                },
                                label = { Text(category) },
                                leadingIcon = if (state.selectedCategory == category) {
                                    { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // Б) Секция Тегов
                if (state.availableTags.isNotEmpty()) {
                    Text(
                        text = "Теги",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.availableTags) { tag ->
                            val isSelected = state.selectedTags.contains(tag)
                            FilterChip(
                                selected = isSelected,
                                onClick = { onIntent(PromptListIntent.ToggleTag(tag)) },
                                label = { Text(tag) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            )
                        }
                    }
                }

                if (state.availableCategories.isEmpty() && state.availableTags.isEmpty()) {
                    Text(
                        text = "Фильтры загружаются...",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}


// === CARD COMPONENT ===

@Composable
fun PromptCard(
    prompt: Prompt,
    onClick: () -> Unit,
    onFavoriteClick: (Boolean) -> Unit,
    onCopy: () -> Unit,
    onRemove: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val elevation by animateDpAsState(if (isPressed) 6.dp else 2.dp, label = "elevation")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            Modifier
                .padding(8.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(4.dp)
                    .weight(1f)
            ) {
                Text(
                    text = prompt.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (!prompt.description.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = prompt.description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Теги внутри карточки
                if (prompt.tags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        prompt.tags.take(3).forEach { tag ->
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = tag,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        if (prompt.tags.size > 3) {
                            Text(
                                "+${prompt.tags.size - 3}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
            Column {
                IconButton(onClick = {
                    onFavoriteClick(prompt.isFavorite)
                }) {
                    Icon(
                        imageVector = if (prompt.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite"
                    )
                }
                IconButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, null, tint = MaterialTheme.colorScheme.primary)
                }
                if (prompt.isLocal) {          // <‑‑ NEW CONDITION
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SyncStatusIndicator(state: PromptListUiState) {
    if (state.isSyncing) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}