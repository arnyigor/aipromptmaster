package com.arny.aiprompts.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.essenty.backhandler.BackHandler
import com.arny.aiprompts.presentation.navigation.MainComponent
import com.arny.aiprompts.presentation.navigation.MainScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
  fun MainContentAndroid(
    component: MainComponent
) {
    val state by component.state.collectAsState()
    val childStack by component.childStack.subscribeAsState()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Handle back navigation - removed BackHandler as it's causing compilation issues

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MainNavigationDrawer(
                currentScreen = state.currentScreen,
                onNavigateToPrompts = component::navigateToPrompts,
                onNavigateToChat = component::navigateToChat,
                onNavigateToSettings = component::navigateToSettings,
                onCloseDrawer = {
                    scope.launch {
                        drawerState.close()
                    }
                }
            )
        },
        content = {
            Scaffold(
                topBar = {
                    MainTopBar(
                        currentScreen = state.currentScreen,
                        onMenuClick = {
                            scope.launch {
                                drawerState.open()
                            }
                        }
                    )
                },
                bottomBar = {
                    MainBottomBar(
                        currentScreen = state.currentScreen,
                        onNavigateToPrompts = component::navigateToPrompts,
                        onNavigateToChat = component::navigateToChat,
                        onNavigateToSettings = component::navigateToSettings
                    )
                },
                content = { paddingValues ->
                    // Add swipe gesture support for navigation
                    val screens = listOf(MainScreen.PROMPTS, MainScreen.CHAT, MainScreen.IMPORT, MainScreen.SETTINGS)
                    val currentIndex = screens.indexOf(state.currentScreen)

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures { change, dragAmount ->
                                    change.consume()
                                    if (kotlin.math.abs(dragAmount) > 100) { // Minimum swipe distance
                                        val nextIndex = when {
                                            dragAmount > 0 && currentIndex > 0 -> currentIndex - 1 // Swipe right
                                            dragAmount < 0 && currentIndex < screens.size - 1 -> currentIndex + 1 // Swipe left
                                            else -> currentIndex
                                        }

                                        if (nextIndex != currentIndex) {
                                            when (screens[nextIndex]) {
                                                MainScreen.PROMPTS -> component.navigateToPrompts()
                                                MainScreen.CHAT -> component.navigateToChat()
                                                MainScreen.IMPORT -> component.navigateToImport()
                                                MainScreen.SETTINGS -> component.navigateToSettings()
                                            }
                                        }
                                    }
                                }
                            }
                    ) {
                        // Main content based on current screen
                        when (state.currentScreen) {
                            MainScreen.PROMPTS -> {
                                val promptsChild = childStack.active.instance as? MainComponent.Child.Prompts
                                promptsChild?.component?.let { promptsComponent ->
                                    Text(
                                        text = "Prompts Module - ${promptsComponent.state.value.allPrompts.size} prompts loaded",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } ?: Text("Loading Prompts...", modifier = Modifier.fillMaxSize())
                            }
                            MainScreen.CHAT -> {
                                val chatChild = childStack.active.instance as? MainComponent.Child.Chat
                                chatChild?.component?.let { chatComponent ->
                                    // TODO: Create LlmScreen that accepts LlmComponent
                                    Text(
                                        text = "Chat Module - Ready",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } ?: Text("Loading Chat...", modifier = Modifier.fillMaxSize())
                            }
                            // Import доступен только на Desktop в dev режиме
                            MainScreen.SETTINGS -> {
                                Text(
                                    text = "Settings Screen - Coming Soon",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            MainScreen.IMPORT -> {}
                        }
                    }
                }
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainNavigationDrawer(
    currentScreen: MainScreen,
    onNavigateToPrompts: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onCloseDrawer: () -> Unit
) {
    ModalDrawerSheet {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(16.dp)
        ) {
            Text(
                text = "AI Prompt Manager",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            NavigationDrawerItem(
                icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Prompts") },
                label = { Text("Prompts") },
                selected = currentScreen == MainScreen.PROMPTS,
                onClick = {
                    onNavigateToPrompts()
                    onCloseDrawer()
                }
            )

            NavigationDrawerItem(
                icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chat") },
                label = { Text("Chat") },
                selected = currentScreen == MainScreen.CHAT,
                onClick = {
                    onNavigateToChat()
                    onCloseDrawer()
                }
            )

            if (MainComponent.IS_IMPORT_ENABLED) {
                // Import доступен только на Desktop в dev режиме
            }

            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                label = { Text("Settings") },
                selected = currentScreen == MainScreen.SETTINGS,
                onClick = {
                    onNavigateToSettings()
                    onCloseDrawer()
                }
            )

            Spacer(modifier = Modifier.weight(1f))

            // Workspace section
            Text(
                text = "Workspaces",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Work, contentDescription = "Personal") },
                label = { Text("Personal") },
                selected = false,
                onClick = { /* TODO: Implement workspace selection */ }
            )

            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Business, contentDescription = "Work") },
                label = { Text("Work Projects") },
                selected = false,
                onClick = { /* TODO: Implement workspace selection */ }
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(
    currentScreen: MainScreen,
    onMenuClick: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = when (currentScreen) {
                    MainScreen.PROMPTS -> "Prompts"
                    MainScreen.CHAT -> "Chat"
                    MainScreen.IMPORT -> "Import"
                    MainScreen.SETTINGS -> "Settings"
                }
            )
        },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.Menu, contentDescription = "Menu")
            }
        },
        actions = {
            IconButton(onClick = { /* TODO: Search */ }) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }
            IconButton(onClick = { /* TODO: Settings */ }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More")
            }
        }
    )
}

@Composable
private fun MainBottomBar(
    currentScreen: MainScreen,
    onNavigateToPrompts: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Prompts") },
            label = { Text("Prompts") },
            selected = currentScreen == MainScreen.PROMPTS,
            onClick = onNavigateToPrompts
        )

        NavigationBarItem(
            icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chat") },
            label = { Text("Chat") },
            selected = currentScreen == MainScreen.CHAT,
            onClick = onNavigateToChat
        )

        NavigationBarItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings") },
            selected = currentScreen == MainScreen.SETTINGS,
            onClick = onNavigateToSettings
        )
    }
}