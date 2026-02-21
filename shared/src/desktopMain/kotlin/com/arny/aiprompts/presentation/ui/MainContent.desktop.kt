package com.arny.aiprompts.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.arny.aiprompts.presentation.ui.scraper.ScraperScreen
import com.arny.aiprompts.presentation.ui.scraperwizard.ScraperWizardScreen
import com.arny.aiprompts.presentation.ui.settings.SettingsScreen


/**
 * ---- 4️⃣  Individual previews (optional but handy for quick checks)----
 */
@Preview
@Composable
private fun PreviewSidebarExpanded() {
    MainSidebar(
        currentScreen = MainScreen.PROMPTS,
        sidebarCollapsed = false,
        onNavigateToScraper = {},
        onNavigateToScraperWizard = {},
        onNavigateToPrompts = {},
        onNavigateToChat = {},
        onNavigateToImport = {},
        onNavigateToSettings = {},
        onToggleSidebar = {},
        modifier = Modifier
    )
}

@Preview
@Composable
private fun PreviewSidebarCollapsed() {
    MainSidebar(
        currentScreen = MainScreen.PROMPTS,
        sidebarCollapsed = true,
        onNavigateToScraper = {},
        onNavigateToScraperWizard = {},
        onNavigateToPrompts = {},
        onNavigateToChat = {},
        onNavigateToImport = {},
        onNavigateToSettings = {},
        onToggleSidebar = {},
        modifier = Modifier
    )
}

@Composable
fun MainContentDesktopImpl(component: MainComponent) {
    val state by component.state.collectAsState()
    val childStack by component.childStack.subscribeAsState()
    val activeChild = childStack.active.instance

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Sidebar (navigation + workspace)
        MainSidebar(
            currentScreen = state.currentScreen,
            sidebarCollapsed = state.sidebarCollapsed,
            onNavigateToScraper = component::navigateToScraper,
            onNavigateToScraperWizard = component::navigateToScraperWizard,
            onNavigateToPrompts = component::navigateToPrompts,
            onNavigateToChat = component::navigateToChat,
            onNavigateToImport = { component.navigateToImport(emptyList()) },
            onNavigateToSettings = component::navigateToSettings,
            onToggleSidebar = component::toggleSidebar,
            modifier = Modifier.width(if (state.sidebarCollapsed) 60.dp else 240.dp)
        )

        // Central content area (chat, etc)
        Column(modifier = Modifier.weight(1f)) {
            MainTopBarDesktop(
                activeChild = activeChild,
                onToggleSidebar = component::toggleSidebar,
                onTogglePropertiesPanel = component::togglePropertiesPanel,
                sidebarCollapsed = state.sidebarCollapsed,
                propertiesPanelCollapsed = state.propertiesPanelCollapsed
            )
            Children(
                stack = component.childStack,
                animation = stackAnimation(fade())
            ) { child ->
                when (val instance = child.instance) {
                    is MainComponent.Child.Prompts -> {
                        PromptsScreen(component = instance.component)
                    }

                    is MainComponent.Child.PromptDetails -> {
                        AdaptivePromptDetailLayout(component = instance.component)
                    }

                    is MainComponent.Child.Chat -> {
                        LlmScreen(component = instance.component)
                    }

                    is MainComponent.Child.Scraper -> {
                        if (MainComponent.IS_IMPORT_ENABLED) ScraperScreen(
                            component = instance.component
                        )
                    }

                    is MainComponent.Child.ScraperWizard -> {
                        if (MainComponent.IS_IMPORT_ENABLED) ScraperWizardScreen(
                            component = instance.component,
                            onNavigateBack = { component.navigateToPrompts() }
                        )
                    }

                    is MainComponent.Child.Import -> {
                        if (MainComponent.IS_IMPORT_ENABLED) ImporterScreen(
                            component = instance.component
                        ) else Text("Import not available", modifier = Modifier.fillMaxSize())
                    }

                    is MainComponent.Child.Settings -> {
                        SettingsScreen(component = instance.component)
                    }
                }
            }
            MainStatusBar(activeWorkspace = state.activeWorkspace)
        }

        // Properties panel, collapsible (right)
        if (!state.propertiesPanelCollapsed) {
            MainPropertiesPanel(
                activeChild = activeChild,
                modifier = Modifier.width(300.dp)
            )
        }
    }
}

@Composable
private fun MainSidebar(
    currentScreen: MainScreen,
    sidebarCollapsed: Boolean,
    onNavigateToScraper: () -> Unit,
    onNavigateToScraperWizard: () -> Unit,
    onNavigateToPrompts: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onToggleSidebar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(if (sidebarCollapsed) 4.dp else 8.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (sidebarCollapsed) "AI" else "AI Prompt Manager",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onToggleSidebar) {
                Icon(
                    imageVector = if (sidebarCollapsed)
                        Icons.Default.ChevronRight
                    else
                        Icons.Default.ChevronLeft,
                    contentDescription = if (sidebarCollapsed) "Expand" else "Collapse"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Navigation Items
        if (!sidebarCollapsed) {

            NavigationItem(
                icon = Icons.AutoMirrored.Filled.List,
                label = "Prompts",
                selected = currentScreen == MainScreen.PROMPTS,
                onClick = onNavigateToPrompts
            )

            NavigationItem(
                icon = Icons.AutoMirrored.Filled.Chat,
                label = "Chat",
                selected = currentScreen == MainScreen.CHAT,
                onClick = onNavigateToChat
            )

            if (MainComponent.IS_IMPORT_ENABLED) {
                // Scraper Wizard - основной инструмент импорта
                NavigationItem(
                    icon = Icons.Default.Download,
                    label = "Scraper",
                    selected = currentScreen == MainScreen.SCRAPER_WIZARD,
                    onClick = onNavigateToScraperWizard
                )
            }

            NavigationItem(
                icon = Icons.Default.Settings,
                label = "Settings",
                selected = currentScreen == MainScreen.SETTINGS,
                onClick = onNavigateToSettings
            )

            Spacer(modifier = Modifier.weight(1f))

            // Workspaces Section
            Text(
                text = "Workspaces",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            NavigationItem(
                icon = Icons.Default.Person,
                label = "Personal",
                selected = false,
                onClick = { /* TODO: Workspace selection */ }
            )

            NavigationItem(
                icon = Icons.Default.Work,
                label = "Work Projects",
                selected = false,
                onClick = { /* TODO: Workspace selection */ }
            )
        } else {
            // Collapsed sidebar - just icons
            if (MainComponent.IS_IMPORT_ENABLED) {
                IconButton(onClick = onNavigateToScraperWizard) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Scraper",
                        tint = if (currentScreen == MainScreen.SCRAPER_WIZARD)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            IconButton(onClick = onNavigateToPrompts) {
                Icon(
                    Icons.AutoMirrored.Filled.List,
                    contentDescription = "Prompts",
                    tint = if (currentScreen == MainScreen.PROMPTS)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(onClick = onNavigateToChat) {
                Icon(
                    Icons.AutoMirrored.Filled.Chat,
                    contentDescription = "Chat",
                    tint = if (currentScreen == MainScreen.CHAT)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            }

            if (MainComponent.IS_IMPORT_ENABLED) {
                IconButton(onClick = onNavigateToImport) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Import",
                        tint = if (currentScreen == MainScreen.IMPORT)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            IconButton(onClick = onNavigateToSettings) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = if (currentScreen == MainScreen.SETTINGS)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun NavigationItem(
    icon: Any,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (selected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surface,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon as? androidx.compose.ui.graphics.vector.ImageVector
                    ?: Icons.Default.Info,
                contentDescription = label,
                tint = if (selected)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBarDesktop(
    activeChild: MainComponent.Child,
    onToggleSidebar: () -> Unit,
    onTogglePropertiesPanel: () -> Unit,
    sidebarCollapsed: Boolean,
    propertiesPanelCollapsed: Boolean
) {
    TopAppBar(
        title = {
            Text(
                text = when (activeChild) {
                    is MainComponent.Child.Prompts -> "Промпты"
                    is MainComponent.Child.PromptDetails -> "Детали промпта"
                    is MainComponent.Child.Chat -> "Чат"
                    is MainComponent.Child.Scraper -> "Скрапер"
                    is MainComponent.Child.ScraperWizard -> "Мастер импорта"
                    is MainComponent.Child.Import -> "Импорт"
                    is MainComponent.Child.Settings -> "Настройки"
                }
            )
        },
        navigationIcon = {
            IconButton(onClick = onToggleSidebar) {
                Icon(
                    imageVector = if (sidebarCollapsed)
                        Icons.Default.Menu
                    else
                        Icons.AutoMirrored.Filled.MenuOpen,
                    contentDescription = "Toggle Sidebar"
                )
            }
        },
        actions = {
            IconButton(onClick = onTogglePropertiesPanel) {
                Icon(
                    imageVector = if (propertiesPanelCollapsed)
                        Icons.Default.Info
                    else
                        Icons.Default.Close,
                    contentDescription = if (propertiesPanelCollapsed) "Show Properties" else "Hide Properties"
                )
            }
        }
    )
}

@Composable
private fun MainStatusBar(
    activeWorkspace: Workspace?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = activeWorkspace?.name ?: "No workspace selected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Connected to Ollama | Model: llama2 | Tokens: 1.2k",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MainPropertiesPanel(
    activeChild: MainComponent.Child,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp)
    ) {
        Text(
            text = "Properties",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        when (activeChild) {
            is MainComponent.Child.Scraper -> {
                Text("Настройки скрапера будут показаны здесь")
            }

            is MainComponent.Child.Prompts -> {
                Text("Свойства промптов будут показаны здесь")
            }

            is MainComponent.Child.PromptDetails -> {
                Text("Свойства деталей промпта будут показаны здесь")
            }

            is MainComponent.Child.Chat -> {
                Text("Настройки чата будут показаны здесь")
            }

            is MainComponent.Child.Import -> {
                Text("Прогресс импорта будет показан здесь")
            }

            is MainComponent.Child.ScraperWizard -> {
                Text("Настройки мастера импорта")
            }

            is MainComponent.Child.Settings -> {
                Text("Панель настроек будет показана здесь")
            }
        }
    }
}