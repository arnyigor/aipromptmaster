package com.arny.aipromptmaster.ui.screens.systemprompt

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arny.aipromptmaster.domain.models.AppConstants
import com.arny.aipromptmaster.ui.navigation.LocalTopBarManager
import com.arny.aipromptmaster.ui.screens.chat.ChatInputArea
import org.koin.androidx.compose.koinViewModel

/**
 * ────────────────────────────────────────
 *  STATEFUL WRAPPER – обрабатывает ViewModel и side‑effects.
 * ────────────────────────────────────────
 */
@Composable
fun SystemPromptPickerScreen(
    viewModel: SystemPromptViewModel = koinViewModel(),
    navToPrompts: () -> Unit,
) {
    val systemPrompt by viewModel.systemPrompt.collectAsState()
    val context = LocalContext.current
    val screenConfig by viewModel.screenConfig.collectAsState()
    val topBarManager = LocalTopBarManager.current

    DisposableEffect(screenConfig.actions) {
        topBarManager.setActions(
            key = AppConstants.SYSTEM_PROMPT_SCREEN_KEY,
            newActions = screenConfig.actions
        )
        onDispose {
            topBarManager.clearIfCurrent(AppConstants.SYSTEM_PROMPT_SCREEN_KEY)
        }
    }

    LaunchedEffect(Unit) {            // подписка на эффекты
        viewModel.effectFlow.collect { effect ->
            when (effect) {
                is SystemPromptEffect.NavigateToPrompts -> navToPrompts()
                is SystemPromptEffect.ShowToast -> Toast.makeText(
                    context, effect.message, Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    SystemPromptPickerContent(
        systemPrompt = systemPrompt,
        onSystemPromptChange = viewModel::onTextChanged,
    )
}
/**
 * ────────────────────────────────────────
 *  STATELESS COMPOSABLE – чистый UI.
 * ────────────────────────────────────────
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemPromptPickerContent(
    systemPrompt: String,
    onSystemPromptChange: (String) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = onSystemPromptChange,
                label = { Text("Системный промпт") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (systemPrompt.isNotEmpty()) {
                        IconButton(onClick = { onSystemPromptChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Очистить поле",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
            )
        }
    }
}

/**
 * ────────────────────────────────────────
 *  PREVIEW – демонстрация stateless‑компонента.
 * ────────────────────────────────────────
 */
@Preview(showBackground = true)
@Composable
fun PreviewSystemPromptPickerContent() {
    SystemPromptPickerContent(
        systemPrompt = "Пример системного промпта",
        onSystemPromptChange = {},
    )
}
