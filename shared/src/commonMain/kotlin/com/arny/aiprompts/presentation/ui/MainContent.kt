package com.arny.aiprompts.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arny.aiprompts.presentation.navigation.MainComponent
import kotlinx.serialization.Serializable

/**
 * Common entry point for MainContent that routes to platform-specific implementations
 */
@Composable
fun MainContent(
    component: MainComponent,
    modifier: Modifier = Modifier
) {
    val platform = getPlatform()

    when (platform) {
        Platform.Android -> MainContentAndroid(component, modifier)
        Platform.Desktop -> MainContentDesktop(component, modifier)
        Platform.Web -> TODO("Not yet Implemented")
        Platform.IOS -> TODO("Not yet Implemented")
    }
}

/**
 * Platform detection utility
 */
expect fun getPlatform(): Platform

@Serializable
enum class Platform {
    Android,
    Desktop,
    Web,
    IOS
}

@Composable
expect fun MainContentAndroid(component: MainComponent, modifier: Modifier)

@Composable
expect fun MainContentDesktop(component: MainComponent, modifier: Modifier)
