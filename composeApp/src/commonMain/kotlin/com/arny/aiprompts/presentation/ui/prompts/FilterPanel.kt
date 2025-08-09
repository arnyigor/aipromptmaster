package com.arny.aiprompts.presentation.ui.prompts

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arny.aiprompts.presentation.screens.PromptListComponent

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterPanel(state: PromptsListState, component: PromptListComponent) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = component::onSearchQueryChanged,
            label = { Text("Поиск") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            // При нажатии вызываем метод компонента с пустой строкой,
                            // чтобы очистить поле поиска
                            component.onSearchQueryChanged("")
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Очистить поиск" // Важно для доступности
                        )
                    }
                }
            },
            singleLine = true // Рекомендуется для полей поиска
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), // Пространство между строками, если будет перенос
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            ExposedDropdown(
                modifier = Modifier.widthIn(min = 150.dp),
                label = "Категория",
                items = state.availableCategories,
                selectedItem = state.selectedCategory,
                onItemSelected = component::onCategoryChanged
            )

            ExposedDropdown(
                modifier = Modifier.widthIn(min = 150.dp),
                label = "Сортировка",
                items = state.availableSortOrders.map { it.title },
                selectedItem = state.selectedSortOrder.title,
                onItemSelected = { title ->
                    state.availableSortOrders.find { it.title == title }?.let(component::onSortOrderChanged)
                }
            )

            IconButton(onClick = component::onSortDirectionToggle) {
                Icon(
                    imageVector = if (state.isSortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = "Сменить направление сортировки"
                )
            }

            IconToggleButton(
                checked = state.isFavoritesOnly,
                onCheckedChange = component::onFavoritesToggleChanged
            ) {
                Icon(
                    imageVector = if (state.isFavoritesOnly) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Только избранное"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExposedDropdown(
    modifier: Modifier = Modifier, // ИСПРАВЛЕНО: Добавлен modifier как параметр
    label: String,
    items: List<String>,
    selectedItem: String,
    onItemSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        modifier = modifier,
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedItem,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item) },
                    onClick = {
                        onItemSelected(item)
                        expanded = false
                    }
                )
            }
        }
    }
}