package com.arny.sharedui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Draw-only transition: keeps a single screen and its state owners alive. */
@Composable
internal fun ContentReveal(trigger: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val progress = remember { Animatable(1f) }
    var previousTrigger by remember { mutableStateOf(trigger) }
    LaunchedEffect(trigger) {
        if (previousTrigger == trigger) return@LaunchedEffect
        previousTrigger = trigger
        progress.snapTo(0.65f)
        progress.animateTo(1f, tween(180))
    }
    Box(modifier.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 8.dp.toPx()
    }) { content() }
}
