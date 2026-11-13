package com.arny.aiprompts.presentation.ui.prompts

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.arny.aiprompts.domain.strings.asString
import com.arny.aiprompts.presentation.screens.PromptListComponent
import com.arny.sharedui.*

/** Desktop state/action adapter; the actual browser content is shared with Android. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptsScreen(component: PromptListComponent) {
    val state by component.state.collectAsState()
    val cards = remember(state.currentPrompts, state.selectedPromptId) {
        state.currentPrompts.map { PromptCardUi(it.id, it.title, it.description.orEmpty(), it.tags, it.isFavorite, it.id == state.selectedPromptId) }
    }
    PromptBrowser(
        state = PromptBrowserUi(
            prompts = cards, query = state.searchQuery,
            categories = state.availableCategories.filter { it != "Все категории" },
            category = state.selectedCategory.takeUnless { it == "Все категории" },
            sortOptions = state.availableSortOrders.map { it.title } + "Изменить направление",
            sort = state.selectedSortOrder.title, favoritesOnly = state.isFavoritesOnly,
            loading = state.isLoading, error = state.error?.asString(),
        ),
        onQuery = component::onSearchQueryChanged,
        onCategory = { component.onCategoryChanged(it ?: "Все категории") },
        onOpen = component::onPromptClicked, onFavorite = component::onFavoriteClicked,
        onCreate = component::onAddPromptClicked,
        onFavoritesOnly = { component.onFavoritesToggleChanged(!state.isFavoritesOnly) },
        onSort = { title ->
            if (title == "Изменить направление") component.onSortDirectionToggle()
            else state.availableSortOrders.firstOrNull { it.title == title }?.let(component::onSortOrderChanged)
        },
        header = {
            TopAppBar(title = { Column {
                Text("Промпты")
                Text("${state.currentPrompts.size} из ${state.allPrompts.size}", style = MaterialTheme.typography.labelMedium)
            } }, actions = {
                IconButton(onClick = { component.onMoreMenuToggle(true) }) { Icon(Icons.Default.MoreVert, "Дополнительные действия") }
                DropdownMenu(state.isMoreMenuVisible, { component.onMoreMenuToggle(false) }) {
                    DropdownMenuItem(text = { Text("Настройки") }, onClick = component::onSettingsClicked)
                    DropdownMenuItem(text = { Text("Синхронизировать") }, onClick = { component.onMoreMenuToggle(false); component.onSyncClicked() })
                    DropdownMenuItem(text = { Text("Удалить все промпты") }, onClick = { component.onMoreMenuToggle(false); component.onDeleteAllPromptsClicked() })
                }
            })
        },
    )
    if (state.showDeleteAllDialog) AlertDialog(
        onDismissRequest = component::onHideDeleteAllDialog,
        title = { Text("Удалить все промпты?") },
        text = { Text("Это действие нельзя отменить.") },
        confirmButton = { TextButton(onClick = component::onConfirmDeleteAll) { Text("Удалить все") } },
        dismissButton = { TextButton(onClick = component::onHideDeleteAllDialog) { Text("Отмена") } },
    )
    if (state.showDeleteDialog) AlertDialog(
        onDismissRequest = component::onHideDeleteDialog,
        title = { Text("Удалить промпт?") },
        confirmButton = { TextButton(onClick = component::onConfirmDelete) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = component::onHideDeleteDialog) { Text("Отмена") } },
    )
}
