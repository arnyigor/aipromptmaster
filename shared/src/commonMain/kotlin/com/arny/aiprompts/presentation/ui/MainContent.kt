package com.arny.aiprompts.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arny.aiprompts.presentation.navigation.MainComponent

/**
 * Common entry point for MainContent that routes to platform-specific implementations
 */
@Composable
fun MainContent(
    component: MainComponent,
    modifier: Modifier = Modifier
) {
    // Platform-specific implementations will be called based on the actual platform
    // This function serves as a common interface

    // For now, we'll use a simple platform detection approach
    // In a real application, you might want to use expect/actual functions
    val platform = getPlatform()

    when (platform) {
        Platform.Android -> MainContentAndroid(component, modifier)
        Platform.Desktop -> MainContentDesktop(component, modifier)
        Platform.Web -> MainContentWeb(component, modifier)
        Platform.IOS -> MainContentIOS(component, modifier) // For future iOS support
    }
}

/**
 * Platform detection utility
 */
expect fun getPlatform(): Platform

enum class Platform {
    Android,
    Desktop,
    Web,
    IOS
}

// Platform-specific content implementations (expect/actual)
@Composable
expect fun MainContentAndroid(component: MainComponent, modifier: Modifier)

@Composable
expect fun MainContentDesktop(component: MainComponent, modifier: Modifier)

@Composable
expect fun MainContentWeb(component: MainComponent, modifier: Modifier)

@Composable
expect fun MainContentIOS(component: MainComponent, modifier: Modifier)