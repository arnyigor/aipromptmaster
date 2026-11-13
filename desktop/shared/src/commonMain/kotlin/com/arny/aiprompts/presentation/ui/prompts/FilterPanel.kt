package com.arny.aiprompts.presentation.ui.prompts

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arny.aiprompts.presentation.screens.PromptListComponent

@Composable
fun FilterPanel(state: PromptsListState, component: PromptListComponent) {
    var categoryMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = component::onSearchQueryChanged,
            placeholder = { Text("Поиск промптов") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            trailingIcon = {
                Row {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { component.onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Очистить поиск")
                        }
                    }
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "Сортировка: ${state.selectedSortOrder.title}")
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            state.availableSortOrders.forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(order.title) },
                                    leadingIcon = { if (order == state.selectedSortOrder) Icon(Icons.Default.Check, null) },
                                    onClick = { component.onSortOrderChanged(order); sortMenu = false },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text(if (state.isSortAscending) "Порядок: по возрастанию" else "Порядок: по убыванию") },
                                onClick = { component.onSortDirectionToggle(); sortMenu = false })
                        }
                    }
                }
            },
        )
        if (state.isFiltersExpanded) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    AssistChip(onClick = { categoryMenu = true }, label = {
                        Text(state.selectedCategory, modifier = Modifier.widthIn(max = 180.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }, trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp)) })
                    DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                        state.availableCategories.forEach { category ->
                            DropdownMenuItem(text = { Text(category) }, onClick = { component.onCategoryChanged(category); categoryMenu = false })
                        }
                    }
                }
                FilterChip(selected = state.isFavoritesOnly,
                    onClick = { component.onFavoritesToggleChanged(!state.isFavoritesOnly) },
                    label = { Text("Избранное") },
                    leadingIcon = { Icon(Icons.Default.Favorite, null, Modifier.size(18.dp)) })
            }
        }
    }
}