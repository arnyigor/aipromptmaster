package com.arny.aipromptmaster.ui.screens.edit

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arny.aipromptmaster.domain.models.DomainPromptVariant
import com.arny.aipromptmaster.domain.models.DomainVariantId
import com.arny.aipromptmaster.domain.models.PromptContent
import org.koin.androidx.compose.koinViewModel
import java.util.UUID

/**
 * Экран редактирования промпта (Stateful).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptEditScreen(
    viewModel: PromptEditViewModel = koinViewModel(),
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val validationState by viewModel.validation.collectAsStateWithLifecycle()
    val saveResult by viewModel.saveResult.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current

    // Обработка результата сохранения
    LaunchedEffect(saveResult) {
        when (saveResult) {
            is PromptEditViewModel.SaveResult.Success -> onBack()
            is PromptEditViewModel.SaveResult.Error ->
                snackbarHostState.showSnackbar((saveResult as PromptEditViewModel.SaveResult.Error).message)

            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!uiState.isLoading) {
                FloatingActionButton(
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.onSaveClicked()
                    }
                ) {
                    Icon(Icons.Default.Save, contentDescription = "Сохранить")
                }
            }
        }
    ) { innerPadding ->
        if (uiState.isLoading || saveResult is PromptEditViewModel.SaveResult.Loading) {
            Box(Modifier
                .fillMaxSize()
                .padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            PromptEditContent(
                modifier = Modifier.padding(innerPadding),
                uiState = uiState,
                validationState = validationState,
                onTitleChange = viewModel::updateTitle,
                onCategoryChange = viewModel::updateCategory,
                onDescriptionChange = viewModel::updateDescription,
                onTagsChange = viewModel::updateTags,
                onContentRuChange = viewModel::updateContentRu,
                onContentEnChange = viewModel::updateContentEn,
                // События вариантов
                onAddVariant = viewModel::addVariant,
                onUpdateVariant = viewModel::updateVariant,
                onDeleteVariant = viewModel::deleteVariant,
                onToggleVariantExpand = viewModel::updateExpandedVariantIndex,
                categoryList = categories,
                categoryError = validationState.categoryError
            )
        }
    }
}

/**
 * Чистый UI (Stateless). Используется для рендеринга и Preview.
 */
@Composable
fun PromptEditContent(
    modifier: Modifier = Modifier,
    uiState: PromptEditViewModel.EditUiState,
    validationState: PromptEditViewModel.ValidationState,
    onTitleChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onTagsChange: (List<String>) -> Unit,
    onContentRuChange: (String) -> Unit,
    onContentEnChange: (String) -> Unit,
    onAddVariant: () -> Unit,
    onUpdateVariant: (Int, DomainPromptVariant) -> Unit,
    onDeleteVariant: (Int) -> Unit,
    onToggleVariantExpand: (Int) -> Unit,
    categoryList: List<String>,
    categoryError: String? = null
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- 1. Основная информация ---
        SectionHeader("Основная информация")

        OutlinedTextField(
            value = uiState.title,
            onValueChange = onTitleChange,
            label = { Text("Заголовок *") },
            isError = !validationState.isTitleValid,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )
        if (!validationState.isTitleValid) {
            Text(
                text = validationState.titleError ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(bottom = 16.dp)
            )
        }

        // Категория
        CategoryDropdown(uiState.category, onCategoryChange, categoryList, categoryError)
        if (!categoryError.isNullOrBlank()) {
            Text(
                text = categoryError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(top = 4.dp, bottom = 8.dp)
            )
        }

        // Описание
        OutlinedTextField(
            value = uiState.description ?: "",
            onValueChange = onDescriptionChange,
            label = { Text("Описание") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        // Теги
        TagsInput(uiState.tags, onTagsChange)

        Spacer(modifier = Modifier.height(24.dp))

        // --- 2. Основной контент ---
        SectionHeader("Основной промпт")

        if (validationState.contentError != null) {
            Text(
                text = validationState.contentError!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(bottom = 8.dp)
            )
        }

        ContentCard("RU", uiState.contentRu, onContentRuChange)
        ContentCard("EN", uiState.contentEn, onContentEnChange)

        Spacer(modifier = Modifier.height(24.dp))

        // --- 3. Варианты ---
        SectionHeader("Варианты и версии")

        if (uiState.variants.isEmpty()) {
            Text(
                "Нет дополнительных вариантов.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        } else {
            uiState.variants.forEachIndexed { index, variant ->
                val isValid =
                    validationState.variantsValidation.find { it.index == index }?.isValid ?: true
                val errors = validationState.variantsValidation.find { it.index == index }?.errors
                    ?: emptyList()
                val isExpanded = uiState.expandedVariantIndex == index

                VariantItem(
                    index = index,
                    variant = variant,
                    isExpanded = isExpanded,
                    isValid = isValid,
                    errors = errors,
                    onToggleExpand = { onToggleVariantExpand(index) },
                    onUpdate = { updated -> onUpdateVariant(index, updated) },
                    onDelete = { onDeleteVariant(index) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        Button(
            onClick = onAddVariant,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Добавить вариант")
        }

        Spacer(Modifier.height(64.dp)) // Отступ под FAB
    }
}

// --- Вспомогательные компоненты ---

@Composable
fun VariantItem(
    index: Int,
    variant: DomainPromptVariant,
    isExpanded: Boolean,
    isValid: Boolean,
    errors: List<String>,
    onToggleExpand: () -> Unit,
    onUpdate: (DomainPromptVariant) -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val rotateAnim by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "arrow"
    )

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить вариант?") },
            text = { Text("Это действие нельзя будет отменить.") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                }) { Text("Отмена") }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isValid) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.errorContainer.copy(
                alpha = 0.1f
            )
        ),
        border = if (!isValid) androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.error
        ) else null
    ) {
        Column {
            // Header строки
            Row(
                modifier = Modifier
                    .clickable { onToggleExpand() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Вариант #${index + 1}: ${variant.variantId.type.ifBlank { "Без типа" }}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (!isValid) {
                        Text(
                            "Есть ошибки заполнения",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.Delete, "Удалить", tint = MaterialTheme.colorScheme.error)
                }
                Icon(
                    Icons.Default.ArrowDropDown,
                    "Expand",
                    modifier = Modifier.rotate(rotateAnim)
                )
            }

            // Раскрывающаяся часть
            AnimatedVisibility(visible = isExpanded) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    if (errors.isNotEmpty()) {
                        errors.forEach { err ->
                            Text(
                                "• $err",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // Тип (например, "Креативный", "Строгий")
                    OutlinedTextField(
                        value = variant.variantId.type,
                        onValueChange = { newType ->
                            onUpdate(variant.copy(variantId = variant.variantId.copy(type = newType)))
                        },
                        label = { Text("Тип (Например: Технический)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = variant.content.ru,
                        onValueChange = { newRu ->
                            onUpdate(variant.copy(content = variant.content.copy(ru = newRu)))
                        },
                        label = { Text("Контент (RU)") },
                        minLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = variant.content.en,
                        onValueChange = { newEn ->
                            onUpdate(variant.copy(content = variant.content.copy(en = newEn)))
                        },
                        label = { Text("Content (EN)") },
                        minLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp, top = 4.dp)
    )
}

@Composable
fun ContentCard(lang: String, text: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = text,
        onValueChange = onValueChange,
        label = { Text("Текст ($lang)") },
        minLines = 3,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDropdown(
    current: String,
    onChange: (String) -> Unit,
    categories: List<String>,
    errorText: String? = null
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        OutlinedTextField(
            value = current,
            onValueChange = {},
            readOnly = true,
            label = { Text("Категория") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            isError = !errorText.isNullOrBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            categories.forEach { cat ->
                DropdownMenuItem(
                    text = { Text(cat) },
                    onClick = { onChange(cat); expanded = false })
            }
        }
    }
}

@Composable
fun TagsInput(tags: List<String>, onTagsChange: (List<String>) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }

    Column {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Теги (через запятую или Enter)") },
            trailingIcon = {
                IconButton(onClick = {
                    if (input.isNotBlank()) {
                        onTagsChange(tags + input.split(",").map { it.trim() }
                            .filter { it.isNotBlank() })
                        input = ""
                    }
                }) { Icon(Icons.Default.Add, "Add") }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { tag ->
                InputChip(
                    selected = false,
                    onClick = {},
                    label = { Text(tag) },
                    trailingIcon = {
                        Icon(
                            Icons.Default.Close,
                            "Del",
                            Modifier
                                .size(16.dp)
                                .clickable { onTagsChange(tags - tag) })
                    }
                )
            }
        }
    }
}

// --------------------------------------------------------------------------------
// PREVIEW IMPLEMENTATION
// --------------------------------------------------------------------------------

class PromptEditScreenPreviewProvider : PreviewParameterProvider<PromptEditViewModel.EditUiState> {
    override val values = sequenceOf(
        // 1. Обычное состояние
        PromptEditViewModel.EditUiState(
            title = "Code Refactoring",
            category = "IT",
            description = "Helps to refactor legacy code.",
            contentRu = "Ты эксперт...",
            variants = listOf(
                DomainPromptVariant(
                    DomainVariantId("Strict", UUID.randomUUID().toString(), 1),
                    PromptContent("Строгий режим", "Strict mode")
                )
            )
        ),
        // 2. Состояние загрузки
        PromptEditViewModel.EditUiState(isLoading = true),
        // 3. Ошибочный ввод: пустой заголовок и пустой RU контент
        PromptEditViewModel.EditUiState(
            title = "",
            category = "IT",
            description = null,
            contentRu = "",
            contentEn = "Some EN text",
            tags = listOf("test"),
            variants = emptyList()
        )
    )
}

@Preview(showBackground = true, heightDp = 1000)
@Composable
fun PromptEditScreenDefaultPreview(
    @PreviewParameter(PromptEditScreenPreviewProvider::class) uiState: PromptEditViewModel.EditUiState
) {
    PromptEditContent(
        uiState = uiState,
        validationState = PromptEditViewModel.ValidationState(
            isTitleValid = uiState.title.isNotBlank(),
            titleError = if (uiState.title.isBlank()) "Пусто" else null,
            contentError = if (uiState.contentRu.isBlank()) "Контент RU обязателен" else null
        ),
        onTitleChange = {},
        onCategoryChange = {},
        onDescriptionChange = {},
        onTagsChange = {},
        onContentRuChange = {},
        onContentEnChange = {},
        onAddVariant = {},
        onUpdateVariant = { _, _ -> },
        onDeleteVariant = {},
        onToggleVariantExpand = {},
        categoryList = emptyList()
    )
}

@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    heightDp = 1000
)
@Composable
fun PromptEditScreenDarkPreview(
    @PreviewParameter(PromptEditScreenPreviewProvider::class) uiState: PromptEditViewModel.EditUiState
) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        PromptEditContent(
            uiState = uiState,
            validationState = PromptEditViewModel.ValidationState(
                isTitleValid = uiState.title.isNotBlank(),
                titleError = if (uiState.title.isBlank()) "Пусто" else null,
                contentError = if (uiState.contentRu.isBlank()) "Контент RU обязателен" else null
            ),
            onTitleChange = {},
            onCategoryChange = {},
            onDescriptionChange = {},
            onTagsChange = {},
            onContentRuChange = {},
            onContentEnChange = {},
            onAddVariant = {},
            onUpdateVariant = { _, _ -> },
            onDeleteVariant = {},
            onToggleVariantExpand = {},
            categoryList = emptyList()
        )
    }
}
