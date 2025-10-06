package com.arny.aipromptmaster.presentation.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arny.aipromptmaster.presentation.ui.compose.theme.AppDimensions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.arny.aipromptmaster.presentation.R

// Сообщение пользователя
@Composable
fun UserMessage(
    text: String,
    modifier: Modifier = Modifier,
    onRegenerateClick: (() -> Unit)? = null,
    onCopyClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = AppDimensions.margin_16,
                vertical = AppDimensions.margin_8
            )
    ) {
        // Само сообщение
        Box(
            modifier = Modifier
                .align(Alignment.End)
                .clip(
                    RoundedCornerShape(
                        topStart = AppDimensions.chatMessageCornerRadius,
                        topEnd = AppDimensions.chatMessageCornerRadius,
                        bottomStart = AppDimensions.chatMessageCornerRadius,
                        bottomEnd = AppDimensions.cornerRadiusSmall
                    )
                )
                .background(MaterialTheme.colorScheme.primary)
                .padding(
                    horizontal = AppDimensions.chatMessagePaddingHorizontal,
                    vertical = AppDimensions.chatMessagePaddingVertical
                )
        ) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 16.sp,
                lineHeight = 20.sp
            )
        }

        // Кнопки действий
        if (onRegenerateClick != null || onCopyClick != {}) {
            Row(
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = AppDimensions.margin_8)
            ) {
                if (onRegenerateClick != null) {
                    ChatActionButton(
                        onClick = onRegenerateClick,
                        iconResId = R.drawable.ic_restore, // Замените на ваш ресурс
                        contentDescription = "Регенерировать",
                        modifier = Modifier.padding(end = AppDimensions.margin_8)
                    )
                }

                ChatActionButton(
                    onClick = onCopyClick,
                    iconResId = R.drawable.ic_content_copy, // Замените на ваш ресурс
                    contentDescription = "Копировать"
                )
            }
        }
    }
}

// Сообщение ИИ
@Composable
fun AIMessage(
    text: String,
    modelName: String? = null,
    isTyping: Boolean = false,
    modifier: Modifier = Modifier,
    onCopyClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = AppDimensions.margin_16,
                vertical = AppDimensions.margin_8
            )
    ) {
        // Название модели (если указано)
        if (modelName != null) {
            Text(
                text = modelName,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(bottom = AppDimensions.margin_8)
                    .align(Alignment.Start)
            )
        }

        // Карточка сообщения
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            shape = RoundedCornerShape(
                topStart = AppDimensions.cornerRadiusSmall,
                topEnd = AppDimensions.chatMessageCornerRadius,
                bottomStart = AppDimensions.chatMessageCornerRadius,
                bottomEnd = AppDimensions.chatMessageCornerRadius
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppDimensions.margin_16)
            ) {
                // Текст сообщения
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    lineHeight = 20.sp
                )

                // Индикатор печати
                if (isTyping) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = AppDimensions.margin_8)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(AppDimensions.iconSizeMedium),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }

        // Кнопка копирования
        ChatActionButton(
            onClick = onCopyClick,
            iconResId = R.drawable.ic_content_copy, // Замените на ваш ресурс
            contentDescription = "Копировать",
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = AppDimensions.margin_8)
        )
    }
}

// Сообщение с файлом
@Composable
fun FileMessage(
    fileName: String,
    fileSize: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = AppDimensions.margin_16,
                vertical = AppDimensions.margin_8
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimensions.margin_16),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_file_text),
                contentDescription = null,
                modifier = Modifier
                    .size(AppDimensions.iconSizeMedium)
                    .padding(end = AppDimensions.margin_12),
                tint = MaterialTheme.colorScheme.primary
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = fileSize,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Поле ввода сообщения
@Composable
fun MessageInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Введите сообщение...",
    enabled: Boolean = true
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(AppDimensions.margin_12),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(AppDimensions.cornerRadiusExtraLarge)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimensions.margin_8),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Кнопка прикрепления файла
            IconButton(
                onClick = { /* TODO: Реализовать выбор файла */ },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.outline_attach_file_24),
                    contentDescription = "Прикрепить файл",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Поле ввода
            TextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = AppDimensions.margin_8),
                placeholder = {
                    Text(
                        text = placeholder,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                maxLines = 5,
                enabled = enabled
            )

            // Кнопка отправки
            IconButton(
                onClick = onSendClick,
                modifier = Modifier.size(40.dp),
                enabled = text.isNotBlank() && enabled
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_plus),
                    contentDescription = "Отправить",
                    tint = if (text.isNotBlank() && enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

// Индикатор генерации ответа
@Composable
fun GeneratingIndicator(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(AppDimensions.margin_12),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(AppDimensions.cornerRadiusExtraLarge)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimensions.margin_12),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(AppDimensions.iconSizeMedium)
                    .padding(end = AppDimensions.margin_12)
            )

            Text(
                text = "Генерация ответа...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}