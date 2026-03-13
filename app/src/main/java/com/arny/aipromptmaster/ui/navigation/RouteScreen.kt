package com.arny.aipromptmaster.ui.navigation

sealed class RouteScreen(
    val route: String
) {
    data object Models : RouteScreen(ROUTE_MODELS)
    data object Chats : RouteScreen(ROUTE_CHATS)
    data object ChatHistory : RouteScreen(ROUTE_HISTORY)
    data object Settings : RouteScreen(ROUTE_SETTINGS)
    data object Prompts : RouteScreen(ROUTE_PROMPTS)
    data object Home : RouteScreen(ROUTE_HOME)

    data object Chat : RouteScreen("$ROUTE_CHAT/{$CHAT_ID}") {
        fun getRoute(chatId: String) = "$ROUTE_CHAT/$chatId"
    }

    data object Prompt : RouteScreen("$ROUTE_PROMPT/{$PROMPT_ID}") {
        fun getRoute(promptId: String) = "$ROUTE_PROMPT/$promptId"
    }

    companion object {
        const val ROUTE_PROMPTS = "route_prompts"
        const val ROUTE_HOME = "route_home"
        const val ROUTE_CHAT = "route_chat"
        const val ROUTE_PROMPT = "route_prompt"
        const val ROUTE_MODELS = "route_models"
        const val ROUTE_CHATS = "route_chats"
        const val ROUTE_HISTORY = "route_history"
        const val ROUTE_SETTINGS = "route_settings"
        const val PROMPT_ID = "promptId"
        const val CHAT_ID = "chat_id"
    }
}