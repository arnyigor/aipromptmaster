package com.arny.aiprompts.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.arny.aiprompts.presentation.navigation.MainComponent
import com.arny.aiprompts.presentation.navigation.Platform
import com.arny.aiprompts.presentation.navigation.MainScreen

// Android-specific implementation using the file we already created
@Composable
actual fun MainContentAndroid(
    component: MainComponent,
    modifier: Modifier
) {
    MainContentAndroidImpl(component, modifier)
}

@Composable
actual fun MainContentDesktop(
    component: MainComponent,
    modifier: Modifier
) {
    // For Android target, provide a fallback or placeholder
    androidx.compose.material3.Text("Desktop UI not available on Android")
}

@Composable
actual fun MainContentWeb(
    component: MainComponent,
    modifier: Modifier
) {
    // For Android target, provide a fallback or placeholder
    androidx.compose.material3.Text("Web UI not available on Android")
}

@Composable
actual fun MainContentIOS(
    component: MainComponent,
    modifier: Modifier
) {
    // For Android target, provide a fallback or placeholder
    androidx.compose.material3.Text("iOS UI not available on Android")
}

actual fun getPlatform(): Platform = Platform.Android

// Keep the existing implementation as MainContentAndroidImpl
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainContentAndroidImpl(
    component: MainComponent,
    modifier: Modifier = Modifier
) {
    val state by component.state.collectAsState()
    val childStack by component.childStack.subscribeAsState()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Handle back navigation
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch {
            drawerState.close()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MainNavigationDrawer(
                currentScreen = state.currentScreen,
                onNavigateToPrompts = component::navigateToPrompts,
                onNavigateToChat = component::navigateToChat,
                onNavigateToImport = component::navigateToImport,
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
                        onNavigateToImport = component::navigateToImport,
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
                                    // TODO: Create PromptListScreen that accepts PromptListComponent
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
    onNavigateToImport: () -> Unit,
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
                icon = { Icon(Icons.Default.List, contentDescription = "Prompts") },
                label = { Text("Prompts") },
                selected = currentScreen == MainScreen.PROMPTS,
                onClick = {
                    onNavigateToPrompts()
                    onCloseDrawer()
                }
            )

            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Chat, contentDescription = "Chat") },
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
    onNavigateToImport: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            icon = { Icon(Icons.Default.List, contentDescription = "Prompts") },
            label = { Text("Prompts") },
            selected = currentScreen == MainScreen.PROMPTS,
            onClick = onNavigateToPrompts
        )

        NavigationBarItem(
            icon = { Icon(Icons.Default.Chat, contentDescription = "Chat") },
            label = { Text("Chat") },
            selected = currentScreen == MainScreen.CHAT,
            onClick = onNavigateToChat
        )

        if (MainComponent.IS_IMPORT_ENABLED) {
            // Import доступен только на Desktop в dev режиме
        }

        NavigationBarItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings") },
            selected = currentScreen == MainScreen.SETTINGS,
            onClick = onNavigateToSettings
        )
    }
}