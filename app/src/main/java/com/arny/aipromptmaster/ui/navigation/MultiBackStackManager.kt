package com.arny.aipromptmaster.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Улучшенный manager с полностью независимыми back stacks
 */
@Stable
class MultiBackStackManager(
    initialTab: AppNavKey,
    private val backStacks: Map<AppNavKey, SnapshotStateList<AppNavKey>>
) {
    var currentTab by mutableStateOf(initialTab)
        private set

    /**
     * Получить back stack для КОНКРЕТНОГО таба (не только текущего)
     */
    fun getBackStackFor(tab: AppNavKey): SnapshotStateList<AppNavKey> =
        backStacks[tab] ?: mutableStateListOf(tab)

    /**
     * Текущий активный back stack
     */
    val currentBackStack: SnapshotStateList<AppNavKey>
        get() = getBackStackFor(currentTab)

    /**
     * Навигация в ТЕКУЩИЙ back stack
     */
    fun navigateTo(key: AppNavKey) {
        currentBackStack.add(key)
    }

    /**
     * Навигация назад в ТЕКУЩЕМ back stack
     */
    fun goBack(): Boolean = if (currentBackStack.size > 1) {
        currentBackStack.removeAt(currentBackStack.lastIndex)
        true
    } else {
        false
    }

    /**
     * Переключение таба
     */
    fun switchTab(tab: AppNavKey) {
        currentTab = tab
    }

    companion object {
        private val json = Json {
            allowStructuredMapKeys = true
            ignoreUnknownKeys = true
        }

        val Saver = Saver<MultiBackStackManager, String>(
            save = { manager ->
                json.encodeToString(
                    BackStackData(
                        currentTab = manager.currentTab,
                        backStacks = manager.backStacks.mapValues { it.value.toList() }
                    )
                )
            },
            restore = { jsonString ->
                val data = json.decodeFromString<BackStackData>(jsonString)
                MultiBackStackManager(
                    initialTab = data.currentTab,
                    backStacks = data.backStacks.mapValues {
                        it.value.toMutableStateList()
                    }
                )
            }
        )
    }
}

@Serializable
data class BackStackData(
    val currentTab: AppNavKey,
    val backStacks: Map<AppNavKey, List<AppNavKey>>
)

@Composable
fun rememberMultiBackStackManager(): MultiBackStackManager {
    return rememberSaveable(saver = MultiBackStackManager.Saver) {
        MultiBackStackManager(
            initialTab = PromptsKey(),
            backStacks = mapOf(
                PromptsKey() to mutableStateListOf(PromptsKey()),
                ChatHistoryKey to mutableStateListOf(ChatHistoryKey),
                SettingsKey to mutableStateListOf(SettingsKey)
            )
        )
    }
}
