package com.arny.aiprompts.presentation.ui.settings

import androidx.compose.runtime.Composable
import com.arny.promptcontract.ProviderManagerState
import com.arny.promptcontract.ProviderAction

@Composable
fun ProviderManagementCard(state: ProviderManagerState, onAction: (ProviderAction) -> Unit, mobile: Boolean = false) {
    com.arny.sharedui.ProviderManagementCard(state, onAction, mobile)
}
