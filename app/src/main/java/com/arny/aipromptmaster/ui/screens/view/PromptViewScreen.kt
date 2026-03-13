package com.arny.aipromptmaster.ui.screens.view

import android.content.ClipData
import android.widget.Toast
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arny.aipromptmaster.R
import com.arny.aipromptmaster.domain.models.DomainPromptVariant
import com.arny.aipromptmaster.domain.models.DomainVariantId
import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.models.PromptContent
import com.arny.aipromptmaster.ui.theme.AIPromptMasterComposeTheme
import com.arny.aipromptmaster.ui.utils.asString
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.util.UUID

/**
 * Helper that builds a `DomainPromptVariant` with the given parameters.
 */
private fun variant(
    type: String = "type",
    id: String = UUID.randomUUID().toString(),
    priority: Int,
    ru: String = "Русский текст",
    en: String = "English text"
) = DomainPromptVariant(
    variantId = DomainVariantId(type, id, priority),
    content = PromptContent(ru = ru, en = en)
)

@Preview(showBackground = true)
@Composable
fun PromptDetailsPreview_Content() {
    AIPromptMasterComposeTheme {
        PromptDetailsContent(
            uiState = PromptViewUiState.Content(
                prompt = Prompt(
                    id = UUID.randomUUID().toString(),
                    title = "Masterpiece Prompt",
                    description = "Masterpiece description",

                    isFavorite = true,
                    category = "Illustration"
                ),
                selectedVariantIndex = -1,
                availableVariants = listOf(
                    variant(priority = 10, ru = "Вариант A", en = "Variant A"),
                    variant(priority = 20, ru = "Вариант B", en = "Variant B"),
                    variant(priority = 30, ru = "Вариант C", en = "Variant C")
                ),
                currentContent = PromptContent(
                    ru = "Привет Привет Привет Привет" +
                            " Привет Привет Привет Привет Привет",
                    en = "Hello"
                ),
                isLocal = true
            ),
            onEvent = {} // Пустая лямбда, нам не важна логика в превью
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PromptDetailsPreview_Content_Dark() {
    AIPromptMasterComposeTheme {
        PromptDetailsContent(
            uiState = PromptViewUiState.Content(
                prompt = Prompt(
                    id = UUID.randomUUID().toString(),
                    title = "Night Masterpiece",
                    description = "Dark theme preview",

                    isFavorite = false,
                    category = "Story"
                ),
                selectedVariantIndex = 0,
                availableVariants = listOf(
                    variant(priority = 5, ru = "Вариант D", en = "Variant D"),
                    variant(priority = 10, ru = "Вариант E", en = "Variant E")
                ),
                currentContent = PromptContent(
                    ru = "Тёмный контент",
                    en = "Dark content"
                ),
                isLocal = false
            ),
            onEvent = {}
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptDetailsScreen(
    viewModel: PromptViewViewModel = koinViewModel(),
    onBack: () -> Unit,
    navigateToEdit: (String?) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    // Локальное состояние диалога (UI-only state)
    val showDeleteDialog = remember { mutableStateOf(false) }

    // 2. Единая точка обработки Side Effects (VM -> UI)
    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                // Эффекты (Команды от VM)
                is PromptUiEffect.CopyToClipboard -> {
                    scope.launch {
                        clipboard.setClipEntry(
                            ClipData.newPlainText(effect.label, effect.text).toClipEntry()
                        )
                    }
                    // Тост показываем здесь, так как это UI-эффект
                    Toast.makeText(context, effect.label, Toast.LENGTH_SHORT).show()
                }

                is PromptUiEffect.ShowDeleteDialog -> {
                    showDeleteDialog.value = true
                }

                is PromptUiEffect.ShowToast -> {
                    Toast.makeText(
                        context,
                        effect.message.asString(context),
                        Toast.LENGTH_LONG
                    ).show()
                }

                is PromptUiEffect.NavigateBack -> onBack()
                is PromptUiEffect.NavigateToEdit -> navigateToEdit(effect.id)
            }
        }
    }

    if (showDeleteDialog.value) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog.value = false },
            title = { Text("Удалить промпт?") },
            text = { Text("Вы уверены? Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePrompt() // Вызываем метод VM
                    showDeleteDialog.value = false
                }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog.value = false }) { Text("Отмена") }
            }
        )
    }

    // 4. Отображение контента (Stateless)
    PromptDetailsContent(
        uiState = uiState,
        // Здесь мы превращаем UI-события в вызовы методов VM
        onEvent = { intent ->
            when (intent) {
                PromptUiIntent.ToggleFavorite -> viewModel.toggleFavorite()
                is PromptUiIntent.SelectVariant -> viewModel.selectVariant(intent.index)
                is PromptUiIntent.CopyText -> viewModel.copyContent(intent.text, intent.label)
                is PromptUiIntent.RequestDelete -> viewModel.showDeleteConfirmation()
                is PromptUiIntent.NavigateToEdit -> viewModel.navigateToEdit()
            }
        }
    )
}

@Composable
fun PromptDetailsContent(
    uiState: PromptViewUiState,
    onEvent: (PromptUiIntent) -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            // FAB для копирования всего контента
            if (uiState is PromptViewUiState.Content) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.copy)) },
                        icon = { Icon(Icons.Default.ContentCopy, null) },
                        onClick = {
                            // Логика получения полного контента (можно вынести в VM или оставить helper)
                            val fullContent = buildString {
                                if (uiState.currentContent.ru.isNotEmpty()) append("🇷🇺 Русский:\n${uiState.currentContent.ru}\n\n")
                                if (uiState.currentContent.en.isNotEmpty()) append("🇬🇧 English:\n${uiState.currentContent.en}")
                            }
                            onEvent(
                                PromptUiIntent.CopyText(
                                    text = fullContent,
                                    label = "Полный промпт скопирован"
                                )
                            )
                        }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when (uiState) {
                is PromptViewUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is PromptViewUiState.Error -> {
                    Text(
                        text = "Error loading prompt", // state.stringHolder...
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is PromptViewUiState.Content -> {
                    PromptViewContent(
                        state = uiState,
                        onToggleFavorite = {
                            onEvent(PromptUiIntent.ToggleFavorite)
                        },
                        onCopyId = { id ->
                            onEvent(PromptUiIntent.CopyText(id, "ID скопирован"))
                        },
                        onVariantSelected = { id ->
                            onEvent(PromptUiIntent.SelectVariant(id))
                        },
                        onCopyText = { txt, lbl ->
                            onEvent(PromptUiIntent.CopyText(txt, lbl))
                        },
                        onDelete = {
                            onEvent(PromptUiIntent.RequestDelete)
                        },
                        navigateToEdit = { onEvent(PromptUiIntent.NavigateToEdit) }
                    )
                }

                PromptViewUiState.Initial -> {}
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PromptViewContent(
    state: PromptViewUiState.Content,
    onToggleFavorite: () -> Unit,
    onCopyId: (String) -> Unit,
    onVariantSelected: (Int) -> Unit,
    onCopyText: (String, String) -> Unit,
    onDelete: () -> Unit,
    navigateToEdit: (String?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = state.prompt.title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (state.prompt.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (state.prompt.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Tags Section
        if (state.prompt.tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.prompt.tags.forEach { tag ->
                    AssistChip(
                        onClick = {},
                        label = { Text(tag) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = null
                    )
                }
            }
        }

        // Category Section
        if (state.prompt.category.isNotEmpty()) {
            Text(
                text = state.prompt.category,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        // ID Card
        OutlinedCard(
            onClick = { onCopyId(state.prompt.id) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ID",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = state.prompt.id,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Variants Selector
        if (state.availableVariants.isNotEmpty()) {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Варианты", // stringResource(R.string.prompt_variants)
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    SecondaryScrollableTabRow(
                        selectedTabIndex = state.selectedVariantIndex + 1,
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        edgePadding = 0.dp,
                        indicator = {},
                        divider = {},
                    ) {
                        // Main variant chip
                        VariantTab(
                            selected = state.selectedVariantIndex == -1,
                            text = "Основной", // stringResource(R.string.main_variant)
                            onClick = { onVariantSelected(-1) }
                        )

                        state.availableVariants.forEachIndexed { index, _ ->
                            VariantTab(
                                selected = state.selectedVariantIndex == index,
                                text = "Вариант ${index + 1}", // Можно брать имя из variant.type
                                onClick = { onVariantSelected(index) }
                            )
                        }
                    }
                }
            }
        }

        // Main Content Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column {
                // RU Content
                if (state.currentContent.ru.isNotEmpty()) {
                    ExpandableSection(
                        title = "🇷🇺 Русский",
                        content = state.currentContent.ru,
                        onCopy = { onCopyText(state.currentContent.ru, "Russian copied") }
                    )
                }

                if (state.currentContent.ru.isNotEmpty() && state.currentContent.en.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                }

                // EN Content
                if (state.currentContent.en.isNotEmpty()) {
                    ExpandableSection(
                        title = "🇬🇧 English",
                        content = state.currentContent.en,
                        onCopy = { onCopyText(state.currentContent.en, "English copied") }
                    )
                }
            }
        }

        // Models Section
        if (state.prompt.compatibleModels.any { it.isNotBlank() }) {
            Text(
                text = "Compatible Models",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.prompt.compatibleModels.forEach { model ->
                    SuggestionChip(onClick = {}, label = { Text(model) })
                }
            }
        }

        // Delete Button (if local)
        if (state.isLocal) {
            OutlinedButton(
                onClick = onDelete,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Удалить промпт")
            }
            OutlinedButton(
                onClick = { navigateToEdit(state.prompt.id) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Редактировать")
            }
        }

        // Отступ под FAB
        Spacer(Modifier.height(72.dp))
    }
}

@Composable
fun VariantTab(selected: Boolean, text: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        modifier = Modifier.padding(end = 8.dp)
    )
}

@Composable
fun ExpandableSection(
    title: String,
    content: String,
    collapsedMaxLines: Int = 3,
    onCopy: () -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }
    val showExpandIcon = content.lines().size > collapsedMaxLines

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header with expand controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        if (showExpandIcon) isExpanded = !isExpanded
                    }
                )
                if (showExpandIcon) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { isExpanded = !isExpanded }
                    )
                }
            }
            IconButton(onClick = onCopy) {
                Icon(
                    Icons.Default.ContentCopy,
                    "Copy all",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Selectable text content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .run {
                    if (!isExpanded && showExpandIcon) {
                        clickable { isExpanded = true }
                    } else this
                }
        ) {
            SelectionContainer {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    overflow = if (isExpanded || !showExpandIcon) TextOverflow.Visible else TextOverflow.Ellipsis,
                    maxLines = if (isExpanded || !showExpandIcon) Int.MAX_VALUE else collapsedMaxLines
                )
            }

            // Gradient overlay for collapsed state
            if (!isExpanded && showExpandIcon) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surface
                                ),
                                startY = 0.8f * (collapsedMaxLines * 20f)
                            )
                        )
                )
            }
        }
    }
}
