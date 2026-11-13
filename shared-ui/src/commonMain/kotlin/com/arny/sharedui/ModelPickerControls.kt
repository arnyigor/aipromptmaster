package com.arny.sharedui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

data class ModelFilterOption(val id: String, val title: String, val selected: Boolean)
data class ModelSortOption(val id: String, val title: String)

/** Search and filter state belongs to the host; only menu expansion is local UI state. */
@Composable
fun ModelPickerControls(
    query: String,
    filters: List<ModelFilterOption>,
    sortOptions: List<ModelSortOption>,
    selectedSortId: String,
    onQueryChange: (String) -> Unit,
    onFilterClick: (String) -> Unit,
    onSortClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sortExpanded by remember { mutableStateOf(false) }
    // Keep the typed text local: the host echoes the query back asynchronously and a stale echo
    // reordered characters during fast typing. Only an external reset clears the field.
    var queryText by remember { mutableStateOf(query) }
    LaunchedEffect(query) { if (query.isEmpty() && queryText.isNotEmpty()) queryText = "" }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = queryText, onValueChange = { queryText = it; onQueryChange(it) },
            label = { Text("Поиск моделей") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = { if (queryText.isNotEmpty()) TextButton(onClick = { queryText = ""; onQueryChange("") }) { Text("Очистить") } },
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            filters.forEach { filter ->
                FilterChip(selected = filter.selected, onClick = { onFilterClick(filter.id) }, label = { Text(filter.title, maxLines = 1) })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Сортировка", modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelMedium)
            Box {
                TextButton(onClick = { sortExpanded = true }, modifier = Modifier.semantics { contentDescription = "Выбрать сортировку" }) {
                    Text(sortOptions.firstOrNull { it.id == selectedSortId }?.title ?: "Выбрать", maxLines = 1)
                }
                DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                    sortOptions.forEach { option ->
                        DropdownMenuItem(text = { Text(option.title) }, onClick = { onSortClick(option.id); sortExpanded = false })
                    }
                }
            }
        }
    }
}
