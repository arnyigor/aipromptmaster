package com.arny.sharedui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arny.promptcontract.ProviderAction
import com.arny.promptcontract.ProviderManagerState

data class SettingsTabUi(val id: String, val label: String)

/** Shared provider/settings screen; host-only account and feedback sections are explicit slots. */
@Composable
fun SettingsPane(
    providers: ProviderManagerState, onProviderAction: (ProviderAction) -> Unit,
    modifier: Modifier = Modifier, mobile: Boolean = true,
    tabs: List<SettingsTabUi> = listOf(SettingsTabUi("API", "API")), selectedId: String = "API",
    onSection: (String) -> Unit = {}, extraContent: @Composable ColumnScope.(String) -> Unit = {},
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 900.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (tabs.size > 1) Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth()) {
                    tabs.forEach { tab -> Tab(selected = tab.id == selectedId, onClick = { onSection(tab.id) },
                        text = { Text(tab.label, maxLines = 1) }, modifier = Modifier.weight(1f)) }
                }
            }
            if (selectedId == "API") ProviderManagementCard(providers, onProviderAction, mobile)
            extraContent(selectedId)
        }
    }
}
