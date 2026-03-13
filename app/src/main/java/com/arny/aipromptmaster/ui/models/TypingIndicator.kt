package com.arny.aipromptmaster.ui.models

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.State

@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier,
    dotSize: Dp = 8.dp,
    dotColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer
) {
    val travelDistancePx = with(LocalDensity.current) { 6.dp.toPx() }

    val transition = rememberInfiniteTransition(label = "typing_indicator")

    val dot1Offset by animateDotOffset(transition, travelDistancePx, startOffset = 0)
    val dot2Offset by animateDotOffset(transition, travelDistancePx, startOffset = 100)
    val dot3Offset by animateDotOffset(transition, travelDistancePx, startOffset = 200)

    Surface(
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp),
        color = containerColor,
        modifier = modifier.padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Dot(dotSize, dotColor, dot1Offset)
            Dot(dotSize, dotColor, dot2Offset)
            Dot(dotSize, dotColor, dot3Offset)
        }
    }
}

// Вспомогательный компонент точки
@Composable
private fun Dot(size: Dp, color: Color, offset: Float) {
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                translationY = offset // Двигаем точку вверх/вниз
            }
            .background(color, CircleShape)
    )
}

@Composable
private fun animateDotOffset(
    transition: InfiniteTransition,
    travelDistance: Float,
    startOffset: Int
): State<Float> {
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = -travelDistance,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                // Здесь НЕ нужно писать delayMillis или initialDelayMillis

                0f at 0 using LinearEasing
                -travelDistance at 300 using FastOutSlowInEasing
                0f at 600 using FastOutSlowInEasing
                0f at 1200
            },
            repeatMode = RepeatMode.Restart,
            // ИСПРАВЛЕНИЕ: Используем StartOffset правильно
            // Если ваша версия Compose очень старая и StartOffset не найден,
            // просто удалите эту строку (анимация будет работать, но без "волны")
            initialStartOffset = StartOffset(offsetMillis = startOffset)
        ),
        label = "dot_offset"
    )
}
