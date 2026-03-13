package com.arny.aipromptmaster.ui.models


import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.domain.models.ModelsFilter
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
            }
        }
    }

    // Передаем стейт и обработчики событий в "чистый" UI
    ModelsScreenContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent,
    )
}

// -----------------------------------------------------------------------------
// 2. Stateless Composable (Чистый UI, можно превьюить)
// -----------------------------------------------------------------------------
// ✅ ИСПРАВЛЕНО: правильное управление TextField state
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreenContent(
    state: ModelsUiState,
    snackbarHostState: SnackbarHostState,
    onEvent: (ModelsEvent) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var expanded by rememberSaveable { mutableStateOf(false) }

    // ✅ ИСПРАВЛЕНО: используем обычный String вместо TextFieldValue
    // Синхронизация происходит через LaunchedEffect
    var localQuery by remember { mutableStateOf("") }

    // ✅ Синхронизируем только при ВНЕШНИХ изменениях (не от пользователя)
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
                // --- Поиск ---
                SearchBar(
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    modifier = Modifier.padding(bottom = 8.dp),
                    inputField = {
                        TextField(
                            value = localQuery, // ✅ Используем локальный state
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
                                imageVector = Icons.Filled.AttachMoney,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
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
                    modifier = Modifier
                        .fillMaxSize(),
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
                            onClick = {
                                onEvent(ModelsEvent.SelectModel(model.id))
                            },
                            onFavoriteClick = {
                                onEvent(
                                    ModelsEvent.ToggleModelFavorite(model.id)
                                )
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ModelItem(
    model: LlmModel,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {

                // ----- Название и описание -----
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(Modifier.height(4.dp))

                // ----- Context length -----
                Text(
                    text = "Context: ${model.contextLength}",
                    style = MaterialTheme.typography.bodySmall
                )

                // ----- Цены -----
                // ----- Цены -----
                Row(verticalAlignment = Alignment.CenterVertically) {
                    /* ---------- Prompt (Исходящий) ---------- */
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = "Prompt",
                        modifier = Modifier.size(16.dp),
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

                    /* ---------- Completion (Входящий) -------- */
                    Text(
                        text = "/",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )

                    Icon(
                        imageVector = Icons.Filled.ArrowDownward,
                        contentDescription = "Completion",
                        modifier = Modifier.size(16.dp),
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
private fun ChipWithIcon(modality: String) {
    val icon = when (modality.lowercase()) {
        "text" -> Icons.Default.Description
        "image" -> Icons.Default.Image
        "file" -> Icons.Default.AttachFile
        "audio" -> Icons.Default.AudioFile
        "video" -> Icons.Default.VideoLibrary
        else -> Icons.AutoMirrored.Filled.HelpOutline
    }
//    "text","image","file","audio","video"
    Icon(
        modifier = Modifier.padding(end = 4.dp),
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary
    )
}

// -----------------------------------------------------------------------------
// 3. Previews
// -----------------------------------------------------------------------------

// ---------- Превью «Models List» ----------
@Preview(
    showBackground = true,
    name = "Models List",
    group = "Model screen"
)
@Composable
fun ModelsScreenListPreview() {
    // --- Данные для превью -------------------------------------------------
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
            pricingImage = null          // нет мультимодальности
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
            pricingPrompt = "0",
            pricingCompletion = "0",
            pricingImage = null
        )
    )

    // --------------------------------------------------------------------------
    AIPromptMasterComposeTheme(darkTheme = false) {
        ModelsScreenContent(
            state = ModelsUiState(
                models = dummyModels,
                isLoading = false,
                filter = ModelsFilter(query = "")   // пустой поиск
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onEvent = {}                           // заглушка для эвентов
        )
    }
}

// ---------- Превью «Loading State» ----------
@Preview(
    showBackground = true,
    name = "Loading State",
    group = "Model screen"
)
@Composable
fun ModelsScreenLoadingPreview() {
    AIPromptMasterComposeTheme(darkTheme = true) {
        ModelsScreenContent(
            state = ModelsUiState(
                models = emptyList(),
                isLoading = true,
                filter = ModelsFilter(query = "")   // фильтр не важен при загрузке
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onEvent = {}
        )
    }
}
