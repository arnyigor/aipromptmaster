package com.arny.aiprompts.presentation.ui.llm.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arny.aiprompts.data.model.ChatSession
import com.arny.aiprompts.data.model.ChatSettings
import com.arny.aiprompts.data.model.LlmModel
import com.arny.aiprompts.presentation.ui.llm.theme.*

/**
 * Панель параметров в стиле Jan / LM Studio.
 * Технический, информативный дизайн с точными значениями.
 *
 * @param session Текущая сессия чата
 * @param selectedModel Выбранная модель
 * @param onSettingsChanged Callback изменения настроек
 * @param onSystemPromptChanged Callback изменения system prompt
 * @param modifier Модификатор
 */
@Composable
fun ParametersPanel(
    session: ChatSession?,
    selectedModel: LlmModel?,
    onSettingsChanged: (ChatSettings) -> Unit,
    onSystemPromptChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null
) {
    com.arny.sharedui.ChatParametersPane(
        systemPrompt = session?.systemPrompt.orEmpty(),
        onSystemPromptChanged = onSystemPromptChanged, modifier = modifier, onDismiss = onDismiss,
        modelInfo = { ModelInfoCard(selectedModel) },
        generation = { GenerationParameters(session?.settings ?: ChatSettings(), onSettingsChanged) },
        statistics = { SessionStatistics(session) },
    )
}

@Composable
private fun ModelInfoCard(model: LlmModel?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Модель",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.parameterLabelColor()
            )

            if (model != null) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Характеристики
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ModelStatItem(
                        label = "Контекст",
                        value = "${(model.contextLength ?: 0) / 1000}K"
                    )

                    model.pricingPrompt?.let {
                        ModelStatItem(
                            label = "Цена",
                            value = "${it} $/1M"
                        )
                    }
                }

                // Возможности
                if (model.inputModalities.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        model.inputModalities.take(3).forEach { modality ->
                            val icon = when (modality) {
                                "text" -> Icons.Default.TextFields
                                "image" -> Icons.Default.Image
                                "audio" -> Icons.Default.Mic
                                else -> Icons.Default.Star
                            }
                            AssistChip(
                                onClick = { },
                                label = { Text(modality, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = {
                                    Icon(icon, null, modifier = Modifier.size(14.dp))
                                }
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Модель не выбрана",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ModelStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.parameterValueColor()
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.statLabelColor()
        )
    }
}

@Composable
private fun GenerationParameters(
    settings: ChatSettings,
    onSettingsChanged: (ChatSettings) -> Unit
) {
    com.arny.sharedui.GenerationParameters(
        com.arny.sharedui.GenerationParametersUi(settings.temperature, settings.maxTokens, settings.topP, settings.contextWindow),
        { onSettingsChanged(settings.copy(temperature = it.temperature, maxTokens = it.maxTokens, topP = it.topP, contextWindow = it.contextWindow)) },
    )
}

@Composable
private fun SessionStatistics(session: ChatSession?) {
    if (session == null) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Статистика",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem("Сообщений", session.messages.size.toString())
                StatItem("Токенов", session.messages.sumOf { it.tokenCount ?: 0 }.toString())
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.statValueColor()
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.statLabelColor()
        )
    }
}
