package com.arny.aipromptmaster.ui.models


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.domain.models.ModelsFilter
import com.arny.aipromptmaster.domain.models.SortType
import com.arny.aipromptmaster.ui.theme.AIPromptMasterComposeTheme
import com.arny.aipromptmaster.ui.utils.asString
import org.koin.androidx.compose.koinViewModel

// -----------------------------------------------------------------------------
// 1. Stateful Composable (подключен к VM, DI и навигации)
// -----------------------------------------------------------------------------
@Composable
fun ModelsScreen(
    viewModel: ModelsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    
    // Обработка одноразовых событий (Effects)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ModelsEffect.FavoriteToggled -> {
                    val message =
                        if (effect.added) "Добавлено в избранное" else "Удалено из избранного"
                    snackbarHostState.showSnackbar(message, withDismissAction = true)
                }

                is ModelsEffect.ShowMessage -> {
                    snackbarHostState.showSnackbar(
                        effect.message.asString(context = context),
                        withDismissAction = true
                    )
                }

                is ModelsEffect.AvailabilityCheckComplete -> {
                    val bestModelMsg = if (effect.bestModelId != null && effect.bestRating > 0) {
                        "\nЛучшая: ${effect.bestModelId} (рейтинг: ${effect.bestRating.toInt()})"
                    } else ""
                    snackbarHostState.showSnackbar(
                        "Проверка завершена: ${effect.availableCount}/${effect.totalCount} доступны$bestModelMsg",
                        withDismissAction = true
                    )
                }
            }
        }
    }

    ModelsScreenContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent,
    )
}

// -----------------------------------------------------------------------------
// 2. Stateless Composable (Чистый UI, можно превьюить)
// -----------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreenContent(
    state: ModelsUiState,
    snackbarHostState: SnackbarHostState,
    onEvent: (ModelsEvent) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var localQuery by remember { mutableStateOf("") }

    LaunchedEffect(state.filter.query) {
        if (localQuery != state.filter.query) {
            localQuery = state.filter.query
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Модели") },
                    actions = {
                        // Кнопка проверки free моделей
                        if (state.filter.isFreeOnly || state.models.any { it.isFree }) {
                            TextButton(
                                onClick = { onEvent(ModelsEvent.CheckFreeModelsAvailability) },
                                enabled = !state.isCheckingAvailability
                            ) {
                                if (state.isCheckingAvailability) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Проверить доступность",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Проверить")
                            }
                        }
                        
                        // Сортировка
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(
                                    Icons.Default.SwapVert,
                                    contentDescription = "Сортировка"
                                )
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                SortType.entries.forEach { sortType ->
                                    DropdownMenuItem(
                                        text = { Text(getSortTypeName(sortType)) },
                                        onClick = {
                                            onEvent(ModelsEvent.ChangeSort(sortType))
                                            showSortMenu = false
                                        },
                                        leadingIcon = {
                                            if (state.filter.sortType == sortType) {
                                                Icon(
                                                    Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
                
                // --- Поиск ---
                SearchBar(
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    inputField = {
                        TextField(
                            value = localQuery,
                            onValueChange = { newValue ->
                                localQuery = newValue
                                onEvent(ModelsEvent.Search(newValue))
                            },
                            placeholder = { Text("Найти модель...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null)
                            },
                            trailingIcon = {
                                if (localQuery.isNotEmpty()) {
                                    IconButton(onClick = {
                                        localQuery = ""
                                        onEvent(ModelsEvent.Search(""))
                                        focusManager.clearFocus()
                                        expanded = false
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Очистить")
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    focusManager.clearFocus()
                                    expanded = false
                                }
                            )
                        )
                    },
                    content = {}
                )

                // --- Фильтры ---
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    FilterChip(
                        selected = state.filter.isFavoritesOnly,
                        onClick = { onEvent(ModelsEvent.ToggleFavoritesOnly) },
                        label = { Text("Избранные") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (state.filter.isFavoritesOnly)
                                    Icons.Filled.Star
                                else
                                    Icons.Outlined.StarBorder,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    FilterChip(
                        selected = state.filter.isFreeOnly,
                        onClick = { onEvent(ModelsEvent.ToggleFree) },
                        label = { Text("Бесплатные") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    FilterChip(
                        selected = state.filter.isAvailableOnly,
                        onClick = { onEvent(ModelsEvent.ToggleAvailableOnly) },
                        label = { Text("Доступные") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
                
                // --- Прогресс проверки ---
                AnimatedVisibility(
                    visible = state.isCheckingAvailability,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val animatedProgress by animateFloatAsState(
                                targetValue = state.checkingProgress,
                                label = "progress"
                            )

                            Text(
                                text = "Проверка доступности: ${state.checkedCount}/${state.totalToCheck}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { onEvent(ModelsEvent.CancelAvailabilityCheck) }
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Отмена",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (state.isLoading && state.models.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = state.models,
                        key = { it.id }
                    ) { model ->
                        ModelItem(
                            model = model,
                            isChecking = state.isCheckingAvailability,
                            onClick = {
                                onEvent(ModelsEvent.SelectModel(model.id))
                            },
                            onFavoriteClick = {
                                onEvent(ModelsEvent.ToggleModelFavorite(model.id))
                            },
                            onCheckAvailabilityClick = {
                                onEvent(ModelsEvent.CheckModelAvailability(model.id))
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelItem(
    model: LlmModel,
    isChecking: Boolean = false,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onCheckAvailabilityClick: () -> Unit
) {
    val containerColor = if (model.isSelected)
        MaterialTheme.colorScheme.secondaryContainer
    else
        Color.Transparent

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Индикатор доступности
            AvailabilityIndicator(
                isAvailable = model.isAvailable,
                isChecking = isChecking,
                onCheckClick = onCheckAvailabilityClick,
                modifier = Modifier.padding(end = 12.dp)
            )

            Column(modifier = Modifier.weight(1f)) {

                // ----- Название и рейтинг -----
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    // Рейтинг
                    if (model.rating != null && model.rating > 0) {
                        RatingBadge(rating = model.rating)
                    }
                }

                Spacer(Modifier.height(4.dp))

                // ----- Context length и время отклика -----
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Context: ${model.contextLength}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (model.availabilityResponseTimeMs != null) {
                        Text(
                            text = " | ${model.availabilityResponseTimeMs}ms",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // ----- Цены -----
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = "Prompt",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = model.pricingPrompt,
                        color = if (model.pricingPrompt.equals("Free", ignoreCase = true))
                            MaterialTheme.colorScheme.tertiary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )

                    Text(
                        text = "/",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )

                    Icon(
                        imageVector = Icons.Filled.ArrowDownward,
                        contentDescription = "Completion",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${model.pricingCompletion} 1M",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(Modifier.height(4.dp))

                // ----- Поддерживаемые входные типы -----
                if (model.inputModalities.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        model.inputModalities.forEach { modality ->
                            ChipWithIcon(modality = modality)
                        }
                    }
                }
            }

            // ----- Иконка избранного -----
            IconButton(onClick = onFavoriteClick) {
                Icon(
                    imageVector = if (model.isFavorite) Icons.Filled.Star
                    else Icons.Outlined.StarBorder,
                    contentDescription = if (model.isFavorite)
                        "Убрать из избранного"
                    else
                        "В избранное",
                    tint = if (model.isFavorite)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AvailabilityIndicator(
    isAvailable: Boolean?,
    isChecking: Boolean,
    onCheckClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, color, description) = when {
        isChecking -> Triple(
            Icons.Default.Refresh,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Проверка..."
        )
        isAvailable == true -> Triple(
            Icons.Default.CheckCircle,
            Color(0xFF4CAF50), // Green
            "Доступна"
        )
        isAvailable == false -> Triple(
            Icons.Default.WifiOff,
            Color(0xFFF44336), // Red
            "Недоступна"
        )
        else -> Triple(
            Icons.Default.CheckCircle,
            MaterialTheme.colorScheme.outline,
            "Не проверена"
        )
    }

    IconButton(
        onClick = onCheckClick,
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun ChipWithIcon(modality: String) {
    val icon = when (modality.lowercase()) {
        "text" -> Icons.Default.Description
        "image" -> Icons.Default.Image
        "file" -> Icons.Default.AttachFile
        "audio" -> Icons.Default.AudioFile
        "video" -> Icons.Default.VideoLibrary
        else -> Icons.AutoMirrored.Filled.HelpOutline
    }
    Icon(
        modifier = Modifier.padding(end = 4.dp),
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun RatingBadge(rating: Float) {
    val (color, label) = when {
        rating >= 80 -> Color(0xFF4CAF50) to "A"       // Отлично
        rating >= 60 -> Color(0xFF8BC34A) to "B"       // Хорошо
        rating >= 40 -> Color(0xFFFFC107) to "C"       // Средне
        rating >= 20 -> Color(0xFFFF9800) to "D"       // Ниже среднего
        else -> Color(0xFFF44336) to "F"               // Плохо
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.2f)
    ) {
        Text(
            text = "${label} ${rating.toInt()}",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun getSortTypeName(sortType: SortType): String = when (sortType) {
    SortType.RATING -> "По рейтингу"
    SortType.FAVORITE -> "Избранные"
    SortType.NAME -> "По имени"
    SortType.CONTEXT -> "По контексту"
    SortType.PRICE -> "По цене"
    SortType.AVAILABILITY -> "По доступности"
    SortType.CHECKED -> "Сначала проверенные"
    SortType.AVAILABLE_FIRST -> "Сначала доступные"
}

// -----------------------------------------------------------------------------
// 3. Previews
// -----------------------------------------------------------------------------

@Preview(showBackground = true, name = "Models List", group = "Model screen")
@Composable
fun ModelsScreenListPreview() {
    val dummyModels = listOf(
        LlmModel(
            id = "1",
            name = "GPT‑4 Turbo",
            description = "Powerful model",
            isSelected = true,
            isFavorite = false,
            contextLength = "128k",
            created = 0L,
            inputModalities = listOf("text"),
            outputModalities = listOf("text"),
            pricingPrompt = "$1",
            pricingCompletion = "$25",
            pricingImage = null,
            isFree = false,
            isAvailable = true,
            rating = 85f,
            availabilityResponseTimeMs = 1200,
            lastAvailabilityCheck = System.currentTimeMillis()
        ),
        LlmModel(
            id = "2",
            name = "Mistral 7B Free",
            description = "Open source model",
            isSelected = false,
            isFavorite = true,
            contextLength = "8k",
            created = 0L,
            inputModalities = listOf("text"),
            outputModalities = listOf("text"),
            pricingPrompt = "Free",
            pricingCompletion = "Free",
            pricingImage = null,
            isFree = true,
            isAvailable = null,
            rating = null,
            availabilityResponseTimeMs = null,
            lastAvailabilityCheck = null
        ),
        LlmModel(
            id = "3",
            name = "Claude 3",
            description = "Another model",
            isSelected = false,
            isFavorite = false,
            contextLength = "200k",
            created = 0L,
            inputModalities = listOf("text", "image"),
            outputModalities = listOf("text"),
            pricingPrompt = "$3",
            pricingCompletion = "$15",
            pricingImage = null,
            isFree = false,
            isAvailable = false,
            rating = 45f,
            availabilityResponseTimeMs = 8500,
            lastAvailabilityCheck = System.currentTimeMillis()
        ),
        LlmModel(
            id = "4",
            name = "DeepSeek Chat",
            description = "Fast free model",
            isSelected = false,
            isFavorite = false,
            contextLength = "64k",
            created = 0L,
            inputModalities = listOf("text"),
            outputModalities = listOf("text"),
            pricingPrompt = "Free",
            pricingCompletion = "Free",
            pricingImage = null,
            isFree = true,
            isAvailable = true,
            rating = 72f,
            availabilityResponseTimeMs = 2500,
            lastAvailabilityCheck = System.currentTimeMillis()
        )
    )

    AIPromptMasterComposeTheme(darkTheme = false) {
        ModelsScreenContent(
            state = ModelsUiState(
                models = dummyModels,
                isLoading = false,
                filter = ModelsFilter(query = "", sortType = SortType.RATING),
                availableCount = 2,
                totalCheckedCount = 3
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onEvent = {}
        )
    }
}

@Preview(showBackground = true, name = "Loading State", group = "Model screen")
@Composable
fun ModelsScreenLoadingPreview() {
    AIPromptMasterComposeTheme(darkTheme = true) {
        ModelsScreenContent(
            state = ModelsUiState(
                models = emptyList(),
                isLoading = true,
                filter = ModelsFilter(query = "")
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onEvent = {}
        )
    }
}
