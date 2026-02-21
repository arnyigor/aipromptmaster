package com.arny.aiprompts.presentation.ui.scraper

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.arny.aiprompts.domain.model.PromptData

/**
 * Dialog for previewing and accepting/rejecting scraped prompts one-by-one.
 * Supports toggling between parsed content and original HTML content.
 */
@Composable
fun PromptPreviewDialog(
    prompt: PromptData,
    currentIndex: Int,
    totalCount: Int,
    acceptedCount: Int,
    skippedCount: Int,
    hasPrev: Boolean,
    hasNext: Boolean,
    showOriginalHtml: Boolean = false,
    htmlContent: String? = null,
    onAccept: () -> Unit,
    onSkip: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToggleHtmlView: () -> Unit,
    onClose: () -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // --- Header ---
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Превью промпта",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        // Progress indicator
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "${currentIndex + 1} / $totalCount",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Stats
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "✅ $acceptedCount | ⏭ $skippedCount",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = "Закрыть")
                        }
                    }
                }

                // --- Content ---
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Title
                    Text(
                        text = prompt.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    // Category/Tags
                    if (prompt.tags.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            prompt.tags.take(3).forEach { tag ->
                                AssistChip(
                                    onClick = { },
                                    label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    // Description
                    if (!prompt.description.isNullOrBlank()) {
                        Text(
                            text = "Описание:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = prompt.description,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    HorizontalDivider()

                    // Content with toggle button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (showOriginalHtml) "Оригинальный HTML:" else "Спарсенное содержимое:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        // Toggle button
                        Button(
                            onClick = onToggleHtmlView,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (showOriginalHtml) 
                                    MaterialTheme.colorScheme.secondary 
                                else 
                                    MaterialTheme.colorScheme.tertiary
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                if (showOriginalHtml) Icons.Default.TextFields else Icons.Default.Code,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (showOriginalHtml) "Парсинг" else "HTML",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (showOriginalHtml && htmlContent != null) {
                            // Show original HTML content
                            Text(
                                text = htmlContent,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .padding(12.dp)
                                    .heightIn(min = 100.dp, max = 300.dp)
                                    .verticalScroll(rememberScrollState())
                            )
                        } else {
                            // Show parsed content
                            Text(
                                text = prompt.variants.firstOrNull()?.content ?: "Нет содержимого",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // Author if available
                    prompt.author?.let { author ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Автор:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = author.name,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    // ID
                    Text(
                        text = "ID: ${prompt.id}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // --- Footer Actions ---
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous
                        OutlinedButton(
                            onClick = onPrev,
                            enabled = hasPrev
                        ) {
                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null)
                            Text("Назад")
                        }

                        // Skip
                        OutlinedButton(
                            onClick = onSkip,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = null)
                            Text("Пропустить")
                        }

                        // Accept
                        Button(
                            onClick = onAccept,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Принять")
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}
