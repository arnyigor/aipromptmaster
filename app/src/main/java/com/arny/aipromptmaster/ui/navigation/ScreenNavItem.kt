package com.arny.aipromptmaster.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.arny.aipromptmaster.R

sealed class ScreenNavItem(
    val routeScreen: RouteScreen,
    val icon: ImageVector,
    val iconUnselected: ImageVector,
    val text: Int
) {
    data object Home : ScreenNavItem(
        routeScreen = RouteScreen.Home,
        icon = Icons.Filled.Home,
        iconUnselected = Icons.Outlined.Home,
        text = R.string.nav_home,
    )

    data object Chats : ScreenNavItem(
        routeScreen = RouteScreen.Chats,
        icon = Icons.AutoMirrored.Filled.Chat,
        iconUnselected = Icons.AutoMirrored.Outlined.Chat,
        text = R.string.nav_history,
    )

    data object Settings : ScreenNavItem(
        routeScreen = RouteScreen.Settings,
        icon = Icons.Filled.Settings,
        iconUnselected = Icons.Outlined.Settings,
        text = R.string.action_settings,
    )
}