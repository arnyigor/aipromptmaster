package com.arny.aipromptmaster.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector

data class AppBarAction(
    val icon: ImageVector,
    val contentDescription: String?,
    val showIcon: Boolean = true,
    val isTitleAction: Boolean = false,
    val onClick: () -> Unit
)