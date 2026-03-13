package com.arny.aipromptmaster.ui.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf

class TopBarManager {
    val actions = mutableStateOf<List<AppBarAction>>(emptyList())
    val title = mutableStateOf("")

    @Volatile
    private var currentKey: Any? = null

    fun setActions(key: Any, newActions: List<AppBarAction>) {
        if (currentKey == key) return
        currentKey = key
        actions.value = newActions
    }

    fun setTitle(key: Any, newTitle: String) {
        if (currentKey != key) return
        title.value = newTitle
    }

    // Очистка при выходе из экрана
    fun clearIfCurrent(key: Any) {
        if (currentKey == key) {
            actions.value = emptyList()
            title.value = ""
            currentKey = null
        }
    }
}


// CompositionLocal для доступа из любой точки UI-дерева
val LocalTopBarManager = staticCompositionLocalOf<TopBarManager> {
    error("No TopBarManager provided")
}
