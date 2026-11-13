package com.arny.aipromptmaster.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import com.arny.aipromptmaster.ui.navigation.LocalResultStore
import com.arny.aipromptmaster.ui.navigation.rememberResultStore
import com.arny.aipromptmaster.ui.navigation.AppBottomBar
import com.arny.aipromptmaster.ui.navigation.AppNavKey
import com.arny.aipromptmaster.ui.navigation.AppTopBar
import com.arny.aipromptmaster.ui.navigation.ChatDetailKey
import com.arny.aipromptmaster.ui.navigation.ChatHistoryKey
import com.arny.aipromptmaster.ui.navigation.LocalTopBarManager
import com.arny.aipromptmaster.ui.navigation.ModelsKey
import com.arny.aipromptmaster.ui.navigation.PromptDetailKey
import com.arny.aipromptmaster.ui.navigation.PromptEditKey
import com.arny.aipromptmaster.ui.navigation.PromptsKey
import com.arny.aipromptmaster.ui.navigation.SettingsKey
import com.arny.aipromptmaster.ui.navigation.SystemPromptKey
import com.arny.aipromptmaster.ui.navigation.TopAppBarState
import com.arny.aipromptmaster.ui.navigation.TopBarManager
import com.arny.aipromptmaster.ui.navigation.rememberAppEntryProvider
import com.arny.aipromptmaster.ui.navigation.rememberMultiBackStackManager
import com.arny.aipromptmaster.ui.navigation.toScreenConfig
import com.arny.aipromptmaster.ui.theme.AIPromptMasterComposeTheme
import com.arny.sharedui.SyncTabPager
import com.arny.sharedui.AdaptiveAppShell
import com.arny.sharedui.AppDestination
import com.arny.sharedui.WindowLayout
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.automirrored.filled.Chat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AIPromptMasterComposeTheme {
                AIPromptMasterComposeApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIPromptMasterComposeApp() {
    val backStackManager = rememberMultiBackStackManager()
    val tabKeys = listOf<AppNavKey>(PromptsKey(), ChatHistoryKey, ModelsKey, SettingsKey)
    val pagerState = rememberPagerState(initialPage = tabKeys.indexOf(backStackManager.currentTab).coerceAtLeast(0), pageCount = { 4 })
    val selectedPage = tabKeys.indexOf(backStackManager.currentTab).coerceAtLeast(0)
    SyncTabPager(pagerState, selectedPage) { page ->
        backStackManager.switchTab(tabKeys[page])
    }

    val topBarManager = remember { TopBarManager() }
    // Т.к. toScreenConfig - Composable функция (из-за stringResource),
    // мы должны получить конфиг вне derivedStateOf, но реактивно.
    val topKey:AppNavKey = (backStackManager.currentBackStack.lastOrNull() ?: PromptsKey) as AppNavKey

    // Ориентируемся: корневой ли это экран модели?
    val isRootScreen = backStackManager.currentBackStack.size == 1

    // Изначальный конфиг от NavKey
    var screenConfig = topKey.toScreenConfig()

    // Корректировка для "Модели"
    if (topKey is ModelsKey) {
        screenConfig = screenConfig.copy(
            showBottomBar = isRootScreen,
            showBackButton = !isRootScreen
        )
    }

    val resultStore = rememberResultStore()

    CompositionLocalProvider(
        LocalTopBarManager provides topBarManager,
        LocalResultStore provides resultStore
    ) {
val entryProvider = rememberAppEntryProvider(
            onNavigateToDetails = { promptId -> backStackManager.navigateTo(PromptDetailKey(promptId)) },
            onChatClicked = { chatId -> backStackManager.navigateTo(ChatDetailKey(chatId)) },
            onSystemClick = { chatId -> backStackManager.navigateTo(SystemPromptKey(chatId)) },
            onModelsClick = { backStackManager.navigateTo(ModelsKey) },
            navToPrompts = { navScreen -> backStackManager.navigateTo(PromptsKey(navScreen)) },
            navigateToEdit = { id -> backStackManager.navigateTo(PromptEditKey(id)) },
            onBack = { backStackManager.goBack() }
        )
        val title by topBarManager.title
        AdaptiveAppShell(
            destinations = listOf(
                AppDestination("0", "Промпты", Icons.Default.Home),
                AppDestination("1", "Чаты", Icons.AutoMirrored.Filled.Chat),
                AppDestination("2", "Модели", Icons.Default.ImportExport),
                AppDestination("3", "Настройки", Icons.Default.Settings)
            ),
            selectedId = selectedPage.toString(),
            onSelect = { id -> backStackManager.switchTab(tabKeys[id.toInt()]) },
            navigationVisible = screenConfig.showBottomBar,
            modifier = Modifier.fillMaxSize(),
            topBar = {
                AppTopBar(
                    state = TopAppBarState(
                        title = screenConfig.title,
                        showBackButton = screenConfig.showBackButton
                    ),
                    onNavigationClick = { backStackManager.goBack() },
                    actions = topBarManager.actions.value,
                    title = title,
                )
            }
        ) { layout ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                com.arny.sharedui.TabPager(
                    state = pagerState,
                    // Отключаем предзагрузку соседних страниц для экономии памяти,
                    // или оставляем 1, но учитываем это в логике (здесь логика не зависит от этого)
                    // Важно: запрещаем свайп, если мы не на главном экране таба (опционально)
                    swipeEnabled = !screenConfig.showBackButton
                ) { page ->
                    val tabKey = when (page) {
                        0 -> PromptsKey()
                        1 -> ChatHistoryKey
                        2 -> ModelsKey
                        3 -> SettingsKey
                        else -> PromptsKey
                    } as AppNavKey
                    NavDisplay(
                        backStack = backStackManager.getBackStackFor(tabKey),
                        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
                        entryProvider = entryProvider
                    )
                }
            }
        }
    }
}
