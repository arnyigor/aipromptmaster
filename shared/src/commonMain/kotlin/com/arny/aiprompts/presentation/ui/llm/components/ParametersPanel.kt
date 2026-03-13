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
    modifier: Modifier = Modifier
) {
    val settings = session?.settings ?: ChatSettings()
    var systemPrompt by remember(session?.systemPrompt) { 
        mutableStateOf(session?.systemPrompt ?: "") 
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.parameterPanelBackground())
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Заголовок
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Параметры",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Divider(modifier = Modifier.padding(vertical = 16.dp))

        // Информация о модели
        ModelInfoCard(selectedModel)

        Spacer(modifier = Modifier.height(16.dp))

        // System Prompt
        SystemPromptSection(
            value = systemPrompt,
            onValueChange = { 
                systemPrompt = it
                onSystemPromptChanged(it)
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Параметры генерации
        GenerationParameters(
            settings = settings,
            onSettingsChanged = onSettingsChanged
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Статистика
        SessionStatistics(session)
    }
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
private fun SystemPromptSection(
    value: String,
    onValueChange: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "System Prompt",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            TextButton(onClick = { isExpanded = !isExpanded }) {
                Text(if (isExpanded) "Свернуть" else "Развернуть")
            }
        }

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("Введите system prompt...") },
            minLines = if (isExpanded) 8 else 3,
            maxLines = if (isExpanded) 12 else 3,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace
            ),
            shape = RoundedCornerShape(8.dp)
        )

        if (value.isNotBlank()) {
            Text(
                text = "${value.length} символов",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
private fun GenerationParameters(
    settings: ChatSettings,
    onSettingsChanged: (ChatSettings) -> Unit
) {
    Column {
        Text(
            text = "Генерация",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Temperature
        ParameterSlider(
            label = "Temperature",
            value = settings.temperature,
            onValueChange = { 
                onSettingsChanged(settings.copy(temperature = it))
            },
            valueRange = 0.0f..2.0f,
            steps = 39, // 0.05 шаг
            description = "Креативность ответа"
        )

        // Max Tokens
        ParameterIntField(
            label = "Max Tokens",
            value = settings.maxTokens,
            onValueChange = { 
                onSettingsChanged(settings.copy(maxTokens = it))
            },
            min = 1,
            max = 8192,
            description = "Максимальная длина ответа"
        )

        // Top P
        ParameterSlider(
            label = "Top P",
            value = settings.topP,
            onValueChange = { 
                onSettingsChanged(settings.copy(topP = it))
            },
            valueRange = 0.0f..1.0f,
            steps = 19,
            description = " nucleus sampling"
        )

        // Context Window
        ParameterIntField(
            label = "Context Window",
            value = settings.contextWindow,
            onValueChange = { 
                onSettingsChanged(settings.copy(contextWindow = it))
            },
            min = 1,
            max = 50,
            description = "Количество сообщений в контексте"
        )
    }
}

@Composable
private fun ParameterSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    description: String
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Text(
                text = "%.2f".format(value),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.parameterValueColor()
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ParameterIntField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    min: Int,
    max: Int,
    description: String
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            OutlinedTextField(
                value = value.toString(),
                onValueChange = { newValue ->
                    newValue.toIntOrNull()?.let {
                        if (it in min..max) {
                            onValueChange(it)
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(80.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Monospace
                ),
                singleLine = true
            )
        }
    }
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
