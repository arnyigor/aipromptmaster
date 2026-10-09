package com.arny.aipromptmaster.ui.screens.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import org.koin.androidx.compose.koinViewModel


@Preview(showBackground = true)
@Composable
fun SettingsContentPreview() {
    val dummyState = SettingsUiState(
        apiKey = "sk_test_XXXXXXXXXXXXXXXXXXXX",
        isSaving = false,
        message = null
    )

    MaterialTheme {
        Surface {
            SettingsContent(
                uiState = dummyState,
                onApiKeyChanged = {},
                onSaveClicked = {},
                onFeedbackChanged = {},
                onSendFeedback = {},
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel(),
    providers: com.arny.aipromptmaster.ui.providers.ProvidersViewModel = koinViewModel()
) {
    // 1. Собираем состояние ViewModel
    val uiState by viewModel.state.collectAsState()
    val vault by providers.personalVault.state.collectAsStateWithLifecycle()
    val providerState by providers.manager.state.collectAsStateWithLifecycle()

    // 2. Snackbar‑хост – хранится в stateful‑компоненте, чтобы не пересоздавался при каждом рендере
    val snackbarHostState = remember { SnackbarHostState() }

    // 3. Side‑effects (Snackbar)
    LaunchedEffect(uiState.message) {
        uiState.message?.let { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        content = { innerPadding ->
            SettingsContent(
                modifier = Modifier.padding(innerPadding),
                uiState = uiState,
                onApiKeyChanged = viewModel::onApiKeyChanged,
                onSaveClicked = viewModel::saveApiKey,
                onSendFeedback = viewModel::sendFeedback,
                onFeedbackChanged = viewModel::onFeedbackChanged,
                vaultState = vault,
                onVaultAction = providers.personalVault::onAction,
                providerState = providerState,
                onProviderAction = providers.manager::onAction,
            )
        }
    )
}

@Composable
fun SettingsContent(
    modifier: Modifier = Modifier,
    uiState: SettingsUiState,
    onApiKeyChanged: (String) -> Unit,
    onSaveClicked: () -> Unit,
    onFeedbackChanged: (String) -> Unit,
    onSendFeedback: () -> Unit,
    providerState: com.arny.promptcontract.ProviderManagerState? = null,
    vaultState: com.arny.promptcontract.PersonalVaultUi? = null,
    onVaultAction: (com.arny.promptcontract.PersonalVaultAction) -> Unit = {},
    onProviderAction: (com.arny.promptcontract.ProviderAction) -> Unit = {}
) {
com.arny.sharedui.SettingsPane(
        providers = providerState ?: com.arny.promptcontract.ProviderManagerState(
            config = com.arny.promptcontract.ProviderConfig(listOf(com.arny.promptcontract.ProviderProfile.openRouter(uiState.apiKey)))),
        onProviderAction = onProviderAction, modifier = modifier,
        extraContent = {
            vaultState?.let { com.arny.sharedui.PersonalVaultCard(it, onVaultAction) }
            Text("Фидбек", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(uiState.feedbackText, onFeedbackChanged, label = { Text("Напишите ваш отзыв") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 200.dp))
            Button(onClick = onSendFeedback, enabled = !uiState.isSendingFeedback && uiState.feedbackText.isNotBlank(),
                modifier = Modifier.fillMaxWidth()) {
                if (uiState.isSendingFeedback) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Text("Отправить")
            }
        },
    )
}
