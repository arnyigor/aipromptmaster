package com.arny.aiprompts.presentation.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.arny.aiprompts.presentation.screens.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    component: SettingsComponent,
    modifier: Modifier = Modifier
) {
val state by component.state.collectAsState()
    val vault by component.personalVault.collectAsState()
    val providers by component.providers.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.saveMessage) { state.saveMessage?.let { snackbar.showSnackbar(it) } }
    Scaffold(modifier = modifier, topBar = {
        TopAppBar(title = { Text("Настройки") }, navigationIcon = {
            IconButton(onClick = component::onBackClicked) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
        })
    }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        com.arny.sharedui.SettingsPane(providers, component::onProviderAction, Modifier.padding(padding), mobile = false,
            tabs = listOf(com.arny.sharedui.SettingsTabUi("API", "Модели"), com.arny.sharedui.SettingsTabUi("GITHUB", "GitHub"),
                com.arny.sharedui.SettingsTabUi("PERSONALIZATION", "Профиль")),
            selectedId = state.activeSection.name, onSection = { component.onSectionChanged(SettingsSection.valueOf(it)) },
            extraContent = { section -> when (SettingsSection.valueOf(section)) {
                SettingsSection.API -> Unit
                SettingsSection.GITHUB -> com.arny.sharedui.PersonalVaultCard(vault, component::onPersonalVaultAction)
                SettingsSection.PERSONALIZATION -> PersonalizationSection(state, component)
            } },
        )
    }
}

@Composable
private fun SettingsTabs(
    activeSection: SettingsSection,
    onSectionChanged: (SettingsSection) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TabButton(
                icon = Icons.Default.Key,
                label = "API",
                isActive = activeSection == SettingsSection.API,
                onClick = { onSectionChanged(SettingsSection.API) },
                modifier = Modifier.weight(1f)
            )
            TabButton(
                icon = Icons.Default.Cloud,
                label = "GitHub",
                isActive = activeSection == SettingsSection.GITHUB,
                onClick = { onSectionChanged(SettingsSection.GITHUB) },
                modifier = Modifier.weight(1f)
            )
            TabButton(
                icon = Icons.Default.Person,
                label = "Профиль",
                isActive = activeSection == SettingsSection.PERSONALIZATION,
                onClick = { onSectionChanged(SettingsSection.PERSONALIZATION) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TabButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Tab(
        selected = isActive,
        onClick = onClick,
        selectedContentColor = MaterialTheme.colorScheme.primary,
        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
        text = { Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium) },
    )
}

// ==================== GitHub Settings Section ====================

// ==================== Personalization Section ====================

@Composable
private fun PersonalizationSection(
    state: SettingsState,
    component: SettingsComponent
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Персонализация",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Опишите себя, свой стиль общения и предпочтения. Эта информация будет добавляться ко всем вашим чатам как контекст.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = state.userContext,
                onValueChange = component::onUserContextChanged,
                label = { Text("Личный контекст") },
                placeholder = { 
                    Text("Например: Меня зовут Алекс. Я Android-разработчик. Отвечай кратко и по делу. Используй Kotlin для примеров кода.") 
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                maxLines = 10
            )

            Button(
                onClick = component::onSaveUserContext,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Сохранить контекст")
            }

            // Example Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "💡 Примеры",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "• 'Я врач, предпочитаю научный стиль'",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• 'Я студент, объясняй простыми словами'",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• 'Я пишу статьи, помогай с заголовками'",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
