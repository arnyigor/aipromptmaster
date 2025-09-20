package com.arny.aiprompts.presentation.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.slide
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arny.aiprompts.presentation.navigation.RootComponent
import com.arny.aiprompts.presentation.ui.detail.PromptDetailScreen
import com.arny.aiprompts.presentation.ui.prompts.PromptsScreen
import com.arny.aiprompts.presentation.ui.scraper.ScraperScreen

@Composable
actual fun RootContent(component: RootComponent) {
    MaterialTheme {
        Children(
            stack = component.stack,
            animation = stackAnimation(slide()),
            modifier = Modifier.consumeWindowInsets(WindowInsets.systemBars)
        ) { child ->
            // Этот `when` теперь находится в desktopMain и может "видеть"
            // все экраны из desktopMain, такие как ImporterScreen.
            when (val instance = child.instance) {
                is RootComponent.Child.List -> PromptsScreen(component = instance.component)
                is RootComponent.Child.Scraper -> ScraperScreen(component = instance.component)
                is RootComponent.Child.Importer -> {}
                is RootComponent.Child.Details-> PromptDetailScreen(component = instance.component)
                is RootComponent.Child.LLM -> {} // LLM not supported on Android yet
            }
        }
    }
}