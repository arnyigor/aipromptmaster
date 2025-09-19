package com.arny.aiprompts.presentation.ui.llm

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arny.aiprompts.presentation.features.llm.LlmComponent
import com.arny.aiprompts.presentation.features.llm.LlmUiState
import com.arny.aiprompts.data.model.LlmModel
import com.arny.aiprompts.results.DataResult

@Composable
fun LlmScreen(component: LlmComponent) {
    val uiState by component.uiState.collectAsState()

    // Передаем состояние и коллбэки из компонента в UI
    LlmContent(
        state = uiState,
        onPromptChanged = component::onPromptChanged,
        onModelSelected = component::onModelSelected,
        onGenerateClicked = component::onGenerateClicked
    )
}

@Suppress("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlmContent(
    state: LlmUiState,
    onPromptChanged: (String) -> Unit,
    onModelSelected: (String) -> Unit,
    onGenerateClicked: () -> Unit
) {
    BoxWithConstraints {
        // Проверяем доступную ширину
        if (maxWidth > 600.dp) {
            // --- ЛЭЙАУТ ДЛЯ ШИРОКИХ ЭКРАНОВ (DESKTOP, ПЛАНШЕТ) ---
            Row(modifier = Modifier.fillMaxSize()) {
                ModelListPanel(
                    modifier = Modifier.fillMaxHeight().width(350.dp),
                    state = state,
                    onModelSelected = onModelSelected
                )
                VerticalDivider(modifier = Modifier.fillMaxHeight())
                ChatPanel(
                    modifier = Modifier.fillMaxHeight().weight(1f),
                    state = state,
                    onPromptChanged = onPromptChanged,
                    onGenerateClicked = onGenerateClicked
                )
            }
        } else {
            // --- ЛЭЙАУТ ДЛЯ УЗКИХ ЭКРАНОВ (ТЕЛЕФОН) ---
            // TODO: Реализовать навигацию между списком моделей и чатом.
            // Например, можно использовать BottomNavigation, или показывать сначала
            // чат, а выбор модели вынести в выпадающий список или отдельный экран.
            // Для простоты, покажем только панель чата.
            ChatPanel(
                modifier = Modifier.fillMaxSize(),
                state = state,
                onPromptChanged = onPromptChanged,
                onGenerateClicked = onGenerateClicked
            )
            // Выбор модели можно сделать через DropdownMenu в TopAppBar или по кнопке
        }
    }
}

@Composable
fun ModelListPanel(
    modifier: Modifier = Modifier,
    state: LlmUiState,
    onModelSelected: (String) -> Unit
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text("Select a model", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))

        when (val result = state.modelsResult) {
            is DataResult.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is DataResult.Success -> {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(result.data, key = { it.id }) { model ->
                        ModelItem(
                            model = model,
                            isSelected = model.isSelected,
                            onClick = { onModelSelected(model.id) }
                        )
                    }
                }
            }

            is DataResult.Error -> {
                Text("Error: ${result.exception?.message}", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun ChatPanel(
    modifier: Modifier = Modifier,
    state: LlmUiState,
    onPromptChanged: (String) -> Unit,
    onGenerateClicked: () -> Unit
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Send Prompt", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = state.prompt,
            onValueChange = onPromptChanged,
            label = { Text("Enter your prompt for '${state.selectedModel?.name ?: "no model selected"}'") },
            modifier = Modifier.fillMaxWidth().height(150.dp)
        )
        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onGenerateClicked,
            enabled = !state.isGenerating && state.selectedModel != null,
            modifier = Modifier.align(Alignment.End)
        ) {
            if (state.isGenerating) {
                CircularProgressIndicator(
                    Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Generate")
            }
        }
        Spacer(Modifier.height(16.dp))

        HorizontalDivider(modifier, DividerDefaults.Thickness, DividerDefaults.color)
        LazyColumn(modifier = Modifier.weight(1f).padding(top = 16.dp)) {
            item {
                Text(state.responseText, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}


@Composable
fun ModelItem(model: LlmModel, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(model.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "Context: ${model.contextLength ?: "N/A"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}