package com.arny.aiprompts.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arny.aiprompts.presentation.features.llm.LlmComponent
import com.arny.aiprompts.presentation.navigation.MainComponent
import com.arny.aiprompts.presentation.navigation.MainScreen
import com.arny.aiprompts.presentation.navigation.Workspace
import com.arny.aiprompts.presentation.ui.importer.ImporterScreen
import com.arny.aiprompts.presentation.ui.llm.LlmScreen
import com.arny.aiprompts.presentation.ui.prompts.PromptsScreen

@Composable
fun MainContentDesktopImpl(component: MainComponent) {
    val state by component.state.collectAsState()
    val childStack by component.childStack.subscribeAsState()
    val activeChild = childStack.active.instance

    // Keyboard shortcuts
    LaunchedEffect(Unit) {
        // Note: In a real implementation, you would use a proper keyboard event handler
        // This is a simplified example
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .onKeyEvent { keyEvent ->
                when {
                    (keyEvent.key == Key.CtrlLeft || keyEvent.key == Key.CtrlRight) &&
                            keyEvent.key == Key.One && keyEvent.type == KeyEventType.KeyDown -> {
                        component.navigateToPrompts()
                        true
                    }

                    (keyEvent.key == Key.CtrlLeft || keyEvent.key == Key.CtrlRight) &&
                            keyEvent.key == Key.Two && keyEvent.type == KeyEventType.KeyDown -> {
                        component.navigateToChat()
                        true
                    }
                    // Import доступен только на Desktop в dev режиме - проверяется в IS_IMPORT_ENABLED
                    (keyEvent.key == Key.CtrlLeft || keyEvent.key == Key.CtrlRight) &&
                            keyEvent.key == Key.Four && keyEvent.type == KeyEventType.KeyDown -> {
                        component.navigateToSettings()
                        true
                    }

                    keyEvent.key == Key.B && keyEvent.type == KeyEventType.KeyDown -> {
                        component.toggleSidebar()
                        true
                    }

                    keyEvent.key == Key.F11 && keyEvent.type == KeyEventType.KeyDown -> {
                        // Fullscreen toggle (would need window management)
                        true
                    }

                    keyEvent.key == Key.Escape && keyEvent.type == KeyEventType.KeyDown -> {
                        // Close dialogs or go back
                        true
                    }

                    else -> false
                }
            }
    ) {
        // Left Sidebar - Navigation
        MainSidebar(
            currentScreen = state.currentScreen,
            sidebarCollapsed = state.sidebarCollapsed,
            onNavigateToPrompts = component::navigateToPrompts,
            onNavigateToChat = component::navigateToChat,
            onNavigateToImport = { component.navigateToImport(emptyList()) },
            onNavigateToSettings = component::navigateToSettings,
            onToggleSidebar = component::toggleSidebar,
            modifier = Modifier.width(if (state.sidebarCollapsed) 60.dp else 240.dp)
        )

        // Main Content Area
        Column(modifier = Modifier.weight(1f)) {
            // Top Bar
            MainTopBarDesktop(
                activeChild = activeChild,
                onToggleSidebar = component::toggleSidebar,
                sidebarCollapsed = state.sidebarCollapsed
            )

            // Content based on child stack
            Children(
                stack = component.childStack,
                animation = stackAnimation(fade())
            ) { child ->
                when (val instance = child.instance) {
                    is MainComponent.Child.Prompts -> PromptsScreen(component = instance.component)
                    is MainComponent.Child.PromptDetails -> Text("Prompt Details Screen - Coming Soon", modifier = Modifier.fillMaxSize(), style = MaterialTheme.typography.headlineMedium)
                    is MainComponent.Child.Chat -> LlmScreen(component = instance.component)
                    is MainComponent.Child.Import -> {
                        if (MainComponent.IS_IMPORT_ENABLED) {
                            ImporterScreen(component = instance.component)
                        } else {
                            Text("Import not available", modifier = Modifier.fillMaxSize())
                        }
                    }
                    is MainComponent.Child.Settings -> Text("Settings Screen - Coming Soon", modifier = Modifier.fillMaxSize(), style = MaterialTheme.typography.headlineMedium)
                }
            }

            // Status Bar
            MainStatusBar(
                activeWorkspace = state.activeWorkspace
            )
        }

        // Right Properties Panel (collapsible)
        if (!state.sidebarCollapsed) {
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
                icon = Icons.Default.List,
                label = "Prompts",
                selected = currentScreen == MainScreen.PROMPTS,
                onClick = onNavigateToPrompts
            )

            NavigationItem(
                icon = Icons.Default.Chat,
                label = "Chat",
                selected = currentScreen == MainScreen.CHAT,
                onClick = onNavigateToChat
            )

            if (MainComponent.IS_IMPORT_ENABLED) {
                NavigationItem(
                    icon = Icons.Default.Download,
                    label = "Import",
                    selected = currentScreen == MainScreen.IMPORT,
                    onClick = onNavigateToImport
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
            IconButton(onClick = onNavigateToPrompts) {
                Icon(
                    Icons.Default.List,
                    contentDescription = "Prompts",
                    tint = if (currentScreen == MainScreen.PROMPTS)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(onClick = onNavigateToChat) {
                Icon(
                    Icons.Default.Chat,
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
    sidebarCollapsed: Boolean
) {
    TopAppBar(
        title = {
            Text(
                text = when (activeChild) {
                    is MainComponent.Child.Prompts -> "Prompts"
                    is MainComponent.Child.PromptDetails -> "Prompt Details"
                    is MainComponent.Child.Chat -> "Chat"
                    is MainComponent.Child.Import -> "Import"
                    is MainComponent.Child.Settings -> "Settings"
                }
            )
        },
        navigationIcon = {
            IconButton(onClick = onToggleSidebar) {
                Icon(
                    imageVector = if (sidebarCollapsed)
                        Icons.Default.Menu
                    else
                        Icons.Default.MenuOpen,
                    contentDescription = "Toggle Sidebar"
                )
            }
        },
        actions = {
            IconButton(onClick = { /* TODO: Search */ }) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }
            IconButton(onClick = { /* TODO: Settings */ }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
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
            is MainComponent.Child.Prompts -> {
                Text("Prompts properties will be shown here")
            }

            is MainComponent.Child.PromptDetails -> {
                Text("Prompt details properties will be shown here")
            }

            is MainComponent.Child.Chat -> {
                Text("Chat settings will be shown here")
            }

            is MainComponent.Child.Import -> {
                Text("Import progress will be shown here")
            }

            is MainComponent.Child.Settings -> {
                Text("Settings panel will be shown here")
            }
        }
    }
}