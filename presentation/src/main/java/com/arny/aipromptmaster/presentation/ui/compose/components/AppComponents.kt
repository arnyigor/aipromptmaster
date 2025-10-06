package com.arny.aipromptmaster.presentation.ui.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arny.aipromptmaster.presentation.ui.compose.theme.AppDimensions
import com.arny.aipromptmaster.presentation.ui.compose.theme.LocalExtendedColors
import com.arny.aipromptmaster.presentation.R

// Кнопка действия в чате (копировать, регенерировать и т.д.)
@Composable
fun ChatActionButton(
    onClick: () -> Unit,
    iconResId: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(AppDimensions.chatActionButtonSize)
    ) {
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(AppDimensions.iconSizeMedium)
        )
    }
}

// Чип фильтра
@Composable
fun FilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extendedColors = LocalExtendedColors.current

    val backgroundColor = if (selected) {
        extendedColors.chipBackgroundSelected
    } else {
        extendedColors.chipBackgroundNormal
    }

    val textColor = if (selected) {
        extendedColors.chipTextSelected
    } else {
        extendedColors.chipTextNormal
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(AppDimensions.chipCornerRadius))
            .clickable(onClick = onClick),
        color = backgroundColor,
        shape = RoundedCornerShape(AppDimensions.chipCornerRadius)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = AppDimensions.margin_12,
                vertical = AppDimensions.margin_8
            ),
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// Карточка промпта в списке
@Composable
fun PromptCard(
    title: String,
    content: String,
    category: String?,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = AppDimensions.cardElevation),
        shape = RoundedCornerShape(AppDimensions.cardCornerRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimensions.margin_16)
        ) {
            // Заголовок и кнопка избранного
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                ChatActionButton(
                    onClick = onFavoriteClick,
                    iconResId = if (isFavorite) {
                        R.drawable.ic_favorite_filled // Замените на ваш ресурс
                    } else {
                        R.drawable.ic_favorite_border // Замените на ваш ресурс
                    },
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites"
                )
            }

            // Категория
            if (category != null) {
                Text(
                    text = category,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = AppDimensions.margin_4)
                )
            }

            // Содержимое промпта (первые несколько строк)
            Text(
                text = content.take(100) + if (content.length > 100) "..." else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        }
    }
}

// Пустое состояние
@Composable
fun EmptyState(
    message: String,
    iconResId: Int? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(AppDimensions.margin_16),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (iconResId != null) {
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = null,
                modifier = Modifier.size(AppDimensions.iconSizeLarge * 2),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(AppDimensions.margin_16))
        }

        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// Состояние ошибки
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(AppDimensions.margin_16),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = android.R.drawable.ic_dialog_alert), // Замените на ваш ресурс
            contentDescription = null,
            modifier = Modifier.size(AppDimensions.iconSizeLarge * 2),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(AppDimensions.margin_16))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(AppDimensions.margin_16))

        Button(onClick = onRetry) {
            Text(text = "Повторить") // Замените на строковый ресурс
        }
    }
}

// Индикатор загрузки
@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

// FAB кнопка добавления промпта
@Composable
fun AddPromptFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LargeFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_plus), // Замените на ваш ресурс
            contentDescription = "Добавить промпт"
        )
    }
}