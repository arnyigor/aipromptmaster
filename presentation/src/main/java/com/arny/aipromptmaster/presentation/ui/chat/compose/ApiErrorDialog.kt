package com.arny.aipromptmaster.presentation.ui.chat.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arny.aipromptmaster.domain.models.errors.DomainError

// ============================================================
// API ERROR DIALOG
// ============================================================

/**
 * Compose Dialog для API ошибок
 */
@Composable
fun ApiErrorDialog(
    error: DomainError.Api,
    onDismiss: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val title = when (error.code) {
        400 -> "Ошибка запроса"
        401, 403 -> "Ошибка авторизации"
        429 -> "Превышен лимит"
        500, 502, 503 -> "Ошибка сервера"
        else -> "Ошибка API"
    }

    val showSettingsButton = error.code in listOf(401, 403)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = error.detailedMessage.takeIf { it.isNotBlank() }
                        ?: error.message
                        ?: "Произошла ошибка API",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = "Код: ${error.code}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("ОК")
            }
        },
        dismissButton = if (showSettingsButton) {
            {
                TextButton(
                    onClick = {
                        onDismiss()
                        onNavigateToSettings()
                    }
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("Настройки")
                    }
                }
            }
        } else null
    )
}

// ============================================================
// CONFIRM DIALOG (для подтверждения действий)
// ============================================================

/**
 * Универсальный диалог подтверждения
 * Используется для: очистки чата, удаления файлов и т.д.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "ОК",
    dismissText: String = "Отмена",
    icon: ImageVector = Icons.Filled.Warning,
    confirmButtonColor: ButtonColors = ButtonDefaults.textButtonColors()
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = confirmButtonColor
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        }
    )
}

// ============================================================
// DESTRUCTIVE CONFIRM DIALOG (для опасных действий)
// ============================================================

/**
 * Диалог подтверждения для деструктивных действий
 * (удаление, очистка и т.д.)
 */
@Composable
fun DestructiveConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "Удалить",
    dismissText: String = "Отмена"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Filled.DeleteForever,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText)
            }
        }
    )
}

// ============================================================
// INFO DIALOG (для информационных сообщений)
// ============================================================

/**
 * Информационный диалог
 */
@Composable
fun InfoDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    confirmText: String = "Понятно"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(confirmText)
            }
        }
    )
}
