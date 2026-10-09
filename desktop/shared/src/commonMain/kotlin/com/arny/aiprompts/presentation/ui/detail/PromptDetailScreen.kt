@file:OptIn(ExperimentalTime::class)

package com.arny.aiprompts.presentation.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.arny.aiprompts.presentation.screens.PromptDetailComponent
import com.arny.aiprompts.presentation.screens.PromptDetailEvent
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime

@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Повторить")
        }
    }
}

@Suppress("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptivePromptDetailLayout(component: PromptDetailComponent) {
val state by component.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    val prompt = if (state.isEditing) state.draftPrompt else state.prompt
    if (state.improvement.visible) PromptImprovementDialog(state.improvement) { component.onEvent(PromptDetailEvent.Improvement(it)) }
    if (state.showDeleteDialog) ConfirmDeleteDialog(
        show = true,
        onConfirm = { component.onEvent(PromptDetailEvent.ConfirmDelete) },
        onDismiss = { component.onEvent(PromptDetailEvent.HideDeleteDialog) },
    )
    Scaffold(
        topBar = { TopAppBar(title = { Text(if (state.isEditing) "Редактирование промпта" else "Промпт") },
            navigationIcon = { IconButton(onClick = { component.onEvent(PromptDetailEvent.BackClicked) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            actions = {
                TextButton(onClick = { component.onEvent(PromptDetailEvent.OpenImprovement) }, enabled = prompt != null) { Text("Улучшить") }
                if (state.isEditing) TextButton(onClick = { component.onEvent(PromptDetailEvent.CancelClicked) }) { Text("Отмена") }
                else TextButton(onClick = { component.onEvent(PromptDetailEvent.EditClicked) }, enabled = prompt != null) { Text("Редактировать") }
            }) },
        floatingActionButton = { if (state.isEditing) FloatingActionButton(onClick = { component.onEvent(PromptDetailEvent.SaveClicked) }) { Icon(Icons.Default.Done, "Сохранить") } },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp),
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        if (state.isLoading && prompt == null) Box(modifier, Alignment.Center) { CircularProgressIndicator() }
        else if (prompt == null) ErrorState(state.error ?: "Промпт не найден", modifier) { component.onEvent(PromptDetailEvent.Refresh) }
        else if (state.isEditing) com.arny.sharedui.PromptEditorForm(
            state = com.arny.sharedui.PromptFormUi(prompt.title, prompt.description.orEmpty(), prompt.category, prompt.tags,
                prompt.content?.ru.orEmpty(), prompt.content?.en.orEmpty(), contentError = state.saveError),
            onTitle = { component.onEvent(PromptDetailEvent.TitleChanged(it)) },
            onDescription = { component.onEvent(PromptDetailEvent.DescriptionChanged(it)) },
            onCategory = { component.onEvent(PromptDetailEvent.CategoryChanged(it)) },
            onTags = { component.onEvent(PromptDetailEvent.TagsChanged(it)) },
            onRu = { component.onEvent(PromptDetailEvent.ContentChanged(PromptLanguage.RU, it)) },
            onEn = { component.onEvent(PromptDetailEvent.ContentChanged(PromptLanguage.EN, it)) }, modifier = modifier,
        ) else {
            val variants = prompt.wireDocument?.promptVariants.orEmpty()
            val content = variants.getOrNull(state.selectedVariantIndex)?.content
            com.arny.sharedui.PromptViewer(
                state = com.arny.sharedui.PromptFormUi(prompt.title, prompt.description.orEmpty(), prompt.category, prompt.tags,
                    content?.get("ru") ?: prompt.content?.ru.orEmpty(), content?.get("en") ?: prompt.content?.en.orEmpty()),
                onCopy = { clipboard.setText(AnnotatedString(it)) }, modifier = modifier,
                renderMarkdown = { MarkdownDisplay(it) },
                header = {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(prompt.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { component.onEvent(PromptDetailEvent.FavoriteClicked) }) { Icon(if (prompt.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Избранное") }
                    }
                    TextButton(onClick = { clipboard.setText(AnnotatedString(prompt.id)) }) { Text("ID: ${prompt.id}") }
                },
                variants = {
                    if (variants.isNotEmpty()) androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { androidx.compose.material3.FilterChip(selected = state.selectedVariantIndex == -1, onClick = { component.onEvent(PromptDetailEvent.VariantSelected(-1)) }, label = { Text("Основной") }) }
                        items(variants.size) { index -> androidx.compose.material3.FilterChip(selected = state.selectedVariantIndex == index,
                            onClick = { component.onEvent(PromptDetailEvent.VariantSelected(index)) }, label = { Text("Вариант ${index + 1}") }) }
                    }
                },
                metadata = {
                    prompt.metadata.author?.name?.takeIf { it.isNotBlank() }?.let { Text("Автор: $it") }
                    prompt.metadata.source?.takeIf { it.isNotBlank() }?.let { Text("Источник: $it") }
                    prompt.metadata.notes?.takeIf { it.isNotBlank() }?.let { Text(it) }
                    if (prompt.compatibleModels.any(String::isNotBlank)) Text("Совместимые модели: ${prompt.compatibleModels.joinToString()}")
                    if (prompt.isLocal) OutlinedButton(onClick = { component.onEvent(PromptDetailEvent.ShowDeleteDialog) }) { Text("Удалить промпт") }
                },
            )
        }
    }
}

@Composable
private fun ConfirmDeleteDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Подтверждение удаления") },
            text = {
                Text("Вы действительно хотите удалить этот промпт? Это действие нельзя отменить.")
            },
            confirmButton = {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismiss) {
                    Text("Отмена")
                }
            },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        )
    }
}


@Composable
fun MarkdownDisplay(
    content: String,
    modifier: Modifier = Modifier
) {
    Markdown(
        content = content,
        modifier = modifier,
    )
}
