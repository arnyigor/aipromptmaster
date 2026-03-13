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
    viewModel: SettingsViewModel = koinViewModel()
) {
    // 1. Собираем состояние ViewModel
    val uiState by viewModel.state.collectAsState()

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
    onSendFeedback: () -> Unit
) {
    val context = LocalContext.current
    // Сохраняем видимость пароля, чтобы при пересоздании UI состояние не терялось.
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        /* ---------- 1. Поле ввода API‑ключа ---------- */
        OutlinedTextField(
            value = uiState.apiKey,
            onValueChange = onApiKeyChanged,
            label = { Text("API‑ключ") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            visualTransformation = if (isPasswordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                // Иконка «показать/скрыть»
                val image = if (isPasswordVisible)
                    Icons.Default.VisibilityOff
                else
                    Icons.Default.Visibility

                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                    Icon(
                        image,
                        contentDescription = if (isPasswordVisible) "Скрыть ключ" else "Показать ключ"
                    )
                }
            },
        )

        /* ---------- 2. Кнопки: Сохранить / Получить ключ ---------- */
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onSaveClicked,
                enabled = !uiState.isSaving && uiState.apiKey.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier
                            .size(24.dp)
                            .padding(end = 8.dp)
                    )
                }
                Text("Сохранить")
            }

            OutlinedButton(
                onClick = {
                    val intent =
                        Intent(Intent.ACTION_VIEW, "https://openrouter.ai/settings/keys".toUri())
                    context.startActivity(intent)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Получить ключ")
            }
        }

        /* ---------- 5. Пояснительная карточка ---------- */
        Card(
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Ваш API‑ключ хранится в зашифрованных SharedPreferences (EncryptedSharedPreferences). Он доступен только этому приложению и не сохраняется в логах. При удалении приложения ключ будет потерян.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        /* ---------- 3. Заголовок и поле ввода фидбека ---------- */
        Text(
            text = "Фидбек",
            style = MaterialTheme.typography.titleMedium,   // крупный заголовок
            modifier = Modifier.padding(bottom = 4.dp)       // небольшое расстояние до поля
        )

        OutlinedTextField(
            value = uiState.feedbackText,
            onValueChange = onFeedbackChanged,
            label = { Text("Напишите ваш отзыв") },         // подпись внутри поля
            singleLine = false,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp, max = 200.dp),
            keyboardOptions = KeyboardOptions.Default
        )

        /* ---------- 4. Кнопка отправки фидбека ---------- */
        Button(
            onClick = onSendFeedback,
            enabled = !uiState.isSendingFeedback && uiState.feedbackText.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.isSendingFeedback) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text("Отправить")
            }
        }
    }
}