package com.arny.aiprompts.presentation.ui

import androidx.compose.material3.MaterialTheme
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.slide
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arny.aiprompts.presentation.navigation.RootComponent
import com.arny.aiprompts.presentation.ui.importer.ImporterScreen
import com.arny.aiprompts.presentation.ui.prompts.PromptsScreen
import com.arny.aiprompts.presentation.ui.scraper.ScraperScreen

@androidx.compose.runtime.Composable
actual fun RootContent(component: RootComponent) {
    MaterialTheme {
        Children(
            stack = component.stack,
            animation = stackAnimation(slide())
        ) { child ->
            // Этот `when` теперь находится в desktopMain и может "видеть"
            // все экраны из desktopMain, такие как ImporterScreen.
            when (val instance = child.instance) {
                is RootComponent.Child.List -> PromptsScreen(component = instance.component)
                is RootComponent.Child.Scraper -> ScraperScreen(component = instance.component)
                is RootComponent.Child.Importer -> ImporterScreen(component = instance.component)
            }
        }
    }
}