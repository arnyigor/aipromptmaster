package com.arny.aipromptmaster.ui.providers

import androidx.compose.runtime.Composable
import com.arny.promptcontract.ProviderManagerState
import com.arny.promptcontract.ProviderAction

@Composable
fun ProviderManagementCard(state: ProviderManagerState, onAction: (ProviderAction) -> Unit, mobile: Boolean = true) {
    com.arny.sharedui.ProviderManagementCard(state, onAction, mobile)
}
