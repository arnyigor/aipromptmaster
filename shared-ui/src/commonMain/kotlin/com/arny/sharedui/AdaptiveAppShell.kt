package com.arny.sharedui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class WindowLayout { Compact, Medium, Expanded }
fun windowLayout(widthDp: Float): WindowLayout = when {
    widthDp < 600f -> WindowLayout.Compact
    widthDp < 840f -> WindowLayout.Medium
    else -> WindowLayout.Expanded
}

data class AppDestination(val id: String, val title: String, val icon: ImageVector)
val LocalWindowLayout = compositionLocalOf { WindowLayout.Compact }

/** Layout has no route/state ownership. Content keeps the same composition path during resize. */
@Composable
fun AdaptiveAppShell(
    destinations: List<AppDestination>, selectedId: String, onSelect: (String) -> Unit,
    modifier: Modifier = Modifier, navigationVisible: Boolean = true,
    topBar: @Composable () -> Unit = {},
    content: @Composable (WindowLayout) -> Unit
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val layout = windowLayout(maxWidth.value)
        CompositionLocalProvider(LocalWindowLayout provides layout) {
            Scaffold(topBar = topBar, bottomBar = {
                if (navigationVisible && layout == WindowLayout.Compact) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest, tonalElevation = 0.dp) {
                    destinations.forEach { item -> NavigationBarItem(
                        selected = item.id == selectedId, onClick = { onSelect(item.id) },
                        icon = { Icon(item.icon, item.title) }, label = { Text(item.title, maxLines = 1) }
                    ) }
                }
            }) { padding ->
                Row(Modifier.fillMaxSize().padding(padding)) {
                    if (navigationVisible && layout == WindowLayout.Medium) NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            destinations.forEach { item -> NavigationRailItem(
                                selected = item.id == selectedId, onClick = { onSelect(item.id) },
                                icon = { Icon(item.icon, item.title) }, label = { Text(item.title) }
                            ) }
                        }
                    }
                    if (navigationVisible && layout == WindowLayout.Expanded) {
                        Column(Modifier.width(220.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(12.dp)) {
                            Text("AI Prompt Master", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(12.dp))
                            Text("Библиотека и AI-диалоги", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp, bottom = 24.dp))
                            destinations.forEach { item -> NavigationDrawerItem(
                                label = { Text(item.title) }, selected = item.id == selectedId,
                                onClick = { onSelect(item.id) }, icon = { Icon(item.icon, item.title) }
                            ) }
                        }
                    }
                    key("screen-content") { ContentReveal(selectedId, Modifier.weight(1f).fillMaxHeight()) { content(layout) } }
                }
            }
        }
    }
}
