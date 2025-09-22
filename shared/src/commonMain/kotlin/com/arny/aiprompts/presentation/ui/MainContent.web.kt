package com.arny.aiprompts.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arny.aiprompts.presentation.navigation.MainComponent
import com.arny.aiprompts.presentation.navigation.Platform
import com.arny.aiprompts.presentation.navigation.MainScreen

@Composable
actual fun MainContentWeb(
    component: MainComponent,
    modifier: Modifier
) {
    val state by component.state.collectAsState()
    val childStack by component.childStack.subscribeAsState()

    Column(modifier = modifier.fillMaxSize()) {
        // Top Navigation Bar
        TopAppBar(
            title = {
                Text(
                    text = when (state.currentScreen) {
                        MainScreen.PROMPTS -> "Prompts"
                        MainScreen.CHAT -> "Chat"
                        MainScreen.IMPORT -> "Import"
                        MainScreen.SETTINGS -> "Settings"
                    }
                )
            },
            actions = {
                Button(onClick = component::navigateToPrompts) {
                    Text("Prompts")
                }
                Button(onClick = component::navigateToChat) {
                    Text("Chat")
                }
                // Import доступен только на Desktop в dev режиме
                Button(onClick = component::navigateToSettings) {
                    Text("Settings")
                }
            }
        )

        // Main Content
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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

        // Bottom Status Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = state.activeWorkspace?.name ?: "No workspace selected",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Web Version - Responsive Layout",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
actual fun MainContentAndroid(
    component: MainComponent,
    modifier: Modifier
) {
    androidx.compose.material3.Text("Android UI not available on Web")
}

@Composable
actual fun MainContentDesktop(
    component: MainComponent,
    modifier: Modifier
) {
    androidx.compose.material3.Text("Desktop UI not available on Web")
}

@Composable
actual fun MainContentIOS(
    component: MainComponent,
    modifier: Modifier
) {
    androidx.compose.material3.Text("iOS UI not available on Web")
}
