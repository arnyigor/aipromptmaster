package com.arny.sharedui

import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class PromptCardUi(
    val id: String,
    val title: String,
    val description: String = "",
    val tags: List<String> = emptyList(),
    val favorite: Boolean = false,
    val selected: Boolean = false,
    val canDelete: Boolean = false,
)

data class PromptBrowserUi(
    val prompts: List<PromptCardUi> = emptyList(),
    val query: String = "",
    val categories: List<String> = emptyList(),
    val category: String? = null,
    val tags: List<String> = emptyList(),
    val selectedTags: Set<String> = emptySet(),
    val sortOptions: List<String> = emptyList(),
    val sort: String = "",
    val favoritesOnly: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

/** Shared screen content. Navigation, persistence, clipboard and confirmation remain host actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptBrowser(
    state: PromptBrowserUi,
    onQuery: (String) -> Unit,
    onCategory: (String?) -> Unit,
    onOpen: (String) -> Unit,
    onFavorite: (String) -> Unit,
    onCreate: () -> Unit,
    onSort: ((String) -> Unit)? = null,
    onFavoritesOnly: (() -> Unit)? = null,
    onTag: ((String) -> Unit)? = null,
    onCopy: ((String) -> Unit)? = null,
    onDelete: ((String) -> Unit)? = null,
    header: @Composable () -> Unit = {},
    status: @Composable () -> Unit = {},
) {
    Scaffold(
        topBar = header,
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onCreate, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Создать") })
        },
        contentWindowInsets = WindowInsets(0.dp),
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PromptBrowserFilters(state, onQuery, onCategory, onSort, onFavoritesOnly, onTag)
            status()
            when {
                state.loading && state.prompts.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                state.error != null && state.prompts.isEmpty() -> Box(Modifier.fillMaxSize().padding(16.dp), Alignment.Center) { Text(state.error) }
                state.prompts.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Search, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Промпты не найдены", style = MaterialTheme.typography.titleMedium)
                        if (state.query.isNotBlank() || state.category != null || state.favoritesOnly || state.selectedTags.isNotEmpty()) {
                            Text("Попробуйте изменить поиск или фильтры", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = {
                                onQuery(""); onCategory(null)
                                if (state.favoritesOnly) onFavoritesOnly?.invoke()
                                state.selectedTags.forEach { onTag?.invoke(it) }
                            }) { Text("Сбросить фильтры") }
                        }
                    }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(320.dp), modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.prompts, key = { it.id }) { prompt ->
                        PromptCardContent(prompt, { onOpen(prompt.id) }, { onFavorite(prompt.id) },
                            onCopy?.let { { it(prompt.id) } }, onDelete?.let { { it(prompt.id) } }, Modifier.animateItem())
                    }
                }
            }
        }
    }
}

@Composable
private fun PromptBrowserFilters(
    state: PromptBrowserUi, onQuery: (String) -> Unit, onCategory: (String?) -> Unit,
    onSort: ((String) -> Unit)?, onFavoritesOnly: (() -> Unit)?, onTag: ((String) -> Unit)?,
) {
    var sortOpen by remember { mutableStateOf(false) }
    var categoryOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = state.query, onValueChange = onQuery, placeholder = { Text("Поиск промптов") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                Row {
                    if (state.query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, "Очистить поиск") }
                    if (onSort != null) Box {
                        IconButton(onClick = { sortOpen = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Сортировка: ${state.sort}") }
                        DropdownMenu(sortOpen, { sortOpen = false }) {
                            state.sortOptions.forEach { order -> DropdownMenuItem(text = { Text(order) }, onClick = { sortOpen = false; onSort(order) }) }
                        }
                    }
                }
            },
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.categories.isNotEmpty()) item("category") {
                Box {
                    AssistChip(onClick = { categoryOpen = true }, label = { Text(state.category ?: "Все категории", maxLines = 1) },
                        trailingIcon = { Icon(Icons.Default.ExpandMore, null) })
                    DropdownMenu(categoryOpen, { categoryOpen = false }) {
                        DropdownMenuItem(text = { Text("Все категории") }, onClick = { categoryOpen = false; onCategory(null) })
                        state.categories.forEach { category -> DropdownMenuItem(text = { Text(category) }, onClick = { categoryOpen = false; onCategory(category) }) }
                    }
                }
            }
            if (onFavoritesOnly != null) item("favorite") {
                FilterChip(selected = state.favoritesOnly, onClick = onFavoritesOnly, label = { Text("Избранное") })
            }
            if (onTag != null) items(state.tags, key = { "tag:$it" }) { tag ->
                FilterChip(selected = tag in state.selectedTags, onClick = { onTag(tag) }, label = { Text(tag, maxLines = 1) })
            }
        }
    }
}

@Composable
fun PromptCardContent(prompt: PromptCardUi, onOpen: () -> Unit, onFavorite: () -> Unit,
                      onCopy: (() -> Unit)? = null, onDelete: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed) 0.985f else 1f, spring(stiffness = 600f), label = "prompt-press")
    ElevatedCard(onClick = onOpen, modifier = modifier.fillMaxWidth().graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        interactionSource = interaction,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp, pressedElevation = 0.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = if (prompt.selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(prompt.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (prompt.description.isNotBlank()) Text(prompt.description, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        prompt.tags.take(2).forEach { tag ->
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.extraSmall) {
                                Text(tag, Modifier.widthIn(max = 100.dp).padding(horizontal = 6.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (prompt.tags.size > 2) Text("+${prompt.tags.size - 2}", style = MaterialTheme.typography.labelSmall)
                    }
                }
                IconToggleButton(checked = prompt.favorite, onCheckedChange = { onFavorite() }) {
                    Icon(if (prompt.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        if (prompt.favorite) "Удалить из избранного" else "Добавить в избранное")
                }
            }
            if (onCopy != null || (onDelete != null && prompt.canDelete)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onCopy?.let { TextButton(onClick = it) { Text("Копировать") } }
                if (prompt.canDelete) onDelete?.let { TextButton(onClick = it) { Text("Удалить") } }
            }
        }
    }
}
