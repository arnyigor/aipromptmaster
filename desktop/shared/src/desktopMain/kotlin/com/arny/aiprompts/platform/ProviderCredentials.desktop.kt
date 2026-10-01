package com.arny.aiprompts.platform

internal actual fun openRouterEnvironmentKey(): String? = System.getenv("OPENROUTER_API_KEY")
