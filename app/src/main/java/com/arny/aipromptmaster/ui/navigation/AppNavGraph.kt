package com.arny.aipromptmaster.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ImportExport
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

@Composable
fun AppBottomBar(
    selectedTab: AppNavKey,
    onTabSelected: (Int) -> Unit
) {
    val items = listOf(
        BottomNavItem(PromptsKey("Главная"), "Главная", Icons.Filled.Home, Icons.Outlined.Home),
        BottomNavItem(
            ChatHistoryKey,
            "Чаты",
            Icons.AutoMirrored.Filled.Chat,
            Icons.AutoMirrored.Outlined.Chat
        ),
        BottomNavItem(ModelsKey, "Модели", Icons.Filled.ImportExport, Icons.Outlined.ImportExport),
        BottomNavItem(SettingsKey, "Настройки", Icons.Filled.Settings, Icons.Outlined.Settings)
    )

    NavigationBar {
        items.forEachIndexed { index, item ->
            val selected = selectedTab == item.key

            NavigationBarItem(
                colors = NavigationBarItemDefaults.colors().copy(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.secondary
                ),
                icon = {
                    Icon(
                        if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label
                    )
                },
                label = { Text(item.label) },
                selected = selected,
                onClick = { onTabSelected(index) }
            )
        }
    }
}

private data class BottomNavItem(
    val key: AppNavKey,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

data class TopAppBarState(
    val title: String = "",
    val showBackButton: Boolean = false,
    val actions: @Composable RowScope.() -> Unit = {}
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    state: TopAppBarState,
    onNavigationClick: () -> Unit = {},
    actions: List<AppBarAction> = emptyList(),
    title: String
) {
    // Разделяем «напрямую» и «overflow»
    val primaryActions = actions.filter { it.showIcon && !it.isTitleAction }
    val overflowActions = actions.filterNot { it.showIcon || it.isTitleAction }
    val titleActions = actions.filter { it.isTitleAction }
    val dynamicTitle = title.takeIf { it.isNotBlank() } ?: state.title
    var expanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text(
                fontSize = 16.sp,
                modifier = Modifier.clickable(
                    onClick = {
                        if (titleActions.isNotEmpty()) {
                            titleActions.first().onClick()
                        }
                    }
                ), text = dynamicTitle)
        },

        navigationIcon = {
            if (state.showBackButton) {
                IconButton(onClick = onNavigationClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
            }
        },
        actions = {
            // 1️⃣ Показать все «primary» иконки
            primaryActions.forEach { action ->
                IconButton(
                    onClick = action.onClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(action.icon, contentDescription = action.contentDescription)
                }
            }

            // 2️⃣ Если есть действия для overflow – добавляем кнопку «…»
            if (overflowActions.isNotEmpty()) {
                IconButton(onClick = { expanded = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Больше действий"
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.zIndex(1f)          // чтобы было поверх
                ) {
                    overflowActions.forEach { action ->
                        DropdownMenuItem(
                            text = { Text(action.contentDescription.orEmpty()) },
                            leadingIcon = {
                                Icon(action.icon, contentDescription = null)
                            },
                            onClick = {
                                expanded = false
                                action.onClick()
                            }
                        )
                    }
                }
            }
        }
    )
}

