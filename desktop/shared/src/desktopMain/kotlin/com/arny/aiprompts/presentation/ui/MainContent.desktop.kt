package com.arny.aiprompts.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.pager.rememberPagerState
import com.arny.sharedui.SyncTabPager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arny.aiprompts.presentation.navigation.MainComponent
import com.arny.aiprompts.presentation.navigation.MainScreen
import com.arny.aiprompts.presentation.navigation.Workspace
import com.arny.aiprompts.presentation.ui.detail.AdaptivePromptDetailLayout
import com.arny.aiprompts.presentation.ui.importer.ImporterScreen
import com.arny.aiprompts.presentation.ui.llm.LlmScreen
import com.arny.aiprompts.presentation.ui.prompts.PromptsScreen
import com.arny.aiprompts.presentation.ui.scraperwizard.ScraperWizardScreen
import com.arny.aiprompts.presentation.ui.settings.SettingsScreen


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContentDesktopImpl(component: MainComponent) {
    val state by component.state.collectAsState()
    val childStack by component.childStack.subscribeAsState()
    val activeChild = childStack.active.instance
    val destinations = buildList {
        add(com.arny.sharedui.AppDestination("PROMPTS", "Промпты", Icons.AutoMirrored.Filled.List))
        add(com.arny.sharedui.AppDestination("CHAT", "Чаты", Icons.AutoMirrored.Filled.Chat))
        if (MainComponent.IS_IMPORT_ENABLED) {
            add(com.arny.sharedui.AppDestination("SCRAPER_WIZARD", "Сбор", Icons.Default.Download))
            add(com.arny.sharedui.AppDestination("IMPORT", "Импорт", Icons.Default.ImportExport))
        }
        add(com.arny.sharedui.AppDestination("SETTINGS", "Настройки", Icons.Default.Settings))
    }
    val pager = rememberPagerState(initialPage = destinations.indexOfFirst { it.id == state.currentScreen.name }.coerceAtLeast(0), pageCount = { destinations.size })
    fun select(screen: MainScreen) = when (screen) {
        MainScreen.PROMPTS -> component.navigateToPrompts()
        MainScreen.CHAT -> component.navigateToChat()
        MainScreen.SCRAPER_WIZARD -> component.navigateToScraperWizard()
        MainScreen.IMPORT -> component.navigateToImport(emptyList())
        MainScreen.SETTINGS -> component.navigateToSettings()
    }
    SyncTabPager(pager, destinations.indexOfFirst { it.id == state.currentScreen.name }) { page ->
        select(MainScreen.valueOf(destinations[page].id))
    }
    com.arny.sharedui.AdaptiveAppShell(
        destinations = destinations,
        selectedId = state.currentScreen.name,
        onSelect = { id -> select(MainScreen.valueOf(id)) },
    ) { layout ->
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f)) {
                com.arny.sharedui.TabPager(pager, swipeEnabled = childStack.backStack.isEmpty()) { page ->
                // Components retain state independently; only the active page may own modal windows.
                if (page == pager.currentPage) {
                Children(stack = component.stackFor(MainScreen.valueOf(destinations[page].id)), animation = stackAnimation(fade())) { child ->
                    when (val instance = child.instance) {
                        is MainComponent.Child.Prompts -> PromptsScreen(instance.component)
                        is MainComponent.Child.PromptDetails -> AdaptivePromptDetailLayout(instance.component)
                        is MainComponent.Child.Chat -> LlmScreen(instance.component)
                        is MainComponent.Child.ScraperWizard -> ScraperWizardScreen(instance.component, onNavigateBack = component::navigateToPrompts)
                        is MainComponent.Child.Import -> ImporterScreen(instance.component)
                        is MainComponent.Child.Settings -> SettingsScreen(instance.component)
                    }
                }
                }
                }
            }

        }
    }
}
