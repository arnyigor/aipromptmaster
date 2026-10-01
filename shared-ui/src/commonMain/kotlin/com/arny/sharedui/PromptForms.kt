package com.arny.sharedui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class PromptFormUi(
    val title: String = "", val description: String = "", val category: String = "",
    val tags: List<String> = emptyList(), val ru: String = "", val en: String = "",
    val titleError: String? = null, val contentError: String? = null, val categoryError: String? = null,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PromptEditorForm(
    state: PromptFormUi, onTitle: (String) -> Unit, onDescription: (String) -> Unit,
    onCategory: (String) -> Unit, onTags: (List<String>) -> Unit,
    onRu: (String) -> Unit, onEn: (String) -> Unit,
    modifier: Modifier = Modifier, categories: List<String> = emptyList(),
    variants: @Composable ColumnScope.() -> Unit = {},
    showEnglish: Boolean = true,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Основная информация", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(state.title, onTitle, label = { Text("Заголовок *") }, singleLine = true,
            isError = state.titleError != null, supportingText = state.titleError?.let { { Text(it) } }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(state.category, onCategory, label = { Text("Категория") }, singleLine = true,
            isError = state.categoryError != null, supportingText = state.categoryError?.let { { Text(it) } }, modifier = Modifier.fillMaxWidth())
        if (categories.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.take(8).forEach { category -> SuggestionChip(onClick = { onCategory(category) }, label = { Text(category) }) }
        }
        OutlinedTextField(state.description, onDescription, label = { Text("Описание") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
        var tagInput by remember { mutableStateOf("") }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.tags.forEach { tag -> InputChip(selected = true, onClick = { onTags(state.tags - tag) }, label = { Text("$tag ×") }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(tagInput, { tagInput = it }, label = { Text("Добавить тег") }, singleLine = true, modifier = Modifier.weight(1f))
            TextButton(onClick = { onTags((state.tags + tagInput.trim()).distinct()); tagInput = "" }, enabled = tagInput.isNotBlank()) { Text("Добавить") }
        }
        state.contentError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        PromptTextEditor("RU", state.ru, onRu)
        if (showEnglish) PromptTextEditor("EN", state.en, onEn)
        variants()
        Spacer(Modifier.height(88.dp))
    }
}

@Composable
fun PromptContentCard(language: String, text: String, onCopy: (() -> Unit)? = null,
                      renderMarkdown: @Composable (String) -> Unit = { SelectionContainer { Text(it) } }) {
    var source by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(language, style = MaterialTheme.typography.titleMedium)
                Row {
                    TextButton(onClick = { source = !source }) { Text(if (source) "Markdown" else "Исходник") }
                    onCopy?.let { TextButton(onClick = it) { Text("Копировать") } }
                }
            }
            if (source) SelectionContainer { Text(text) } else renderMarkdown(text)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PromptViewer(
    state: PromptFormUi, onCopy: (String) -> Unit, modifier: Modifier = Modifier,
    renderMarkdown: @Composable (String) -> Unit = { SelectionContainer { Text(it) } },
    variants: @Composable ColumnScope.() -> Unit = {},
    metadata: @Composable ColumnScope.() -> Unit = {},
    header: @Composable ColumnScope.() -> Unit = {},
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        header()
        if (state.description.isNotBlank()) Text(state.description, style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.category.isNotBlank()) SuggestionChip(onClick = {}, enabled = false, label = { Text(state.category) })
            state.tags.forEach { tag -> SuggestionChip(onClick = {}, enabled = false, label = { Text(tag) }) }
        }
        variants()
        if (state.ru.isNotBlank()) PromptContentCard("RU", state.ru, { onCopy(state.ru) }, renderMarkdown)
        if (state.en.isNotBlank()) PromptContentCard("EN", state.en, { onCopy(state.en) }, renderMarkdown)
        metadata()
        Spacer(Modifier.height(88.dp))
    }
}
