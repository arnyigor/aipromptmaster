package com.arny.aiprompts.platform

import com.arny.promptcontract.ProviderKeySource
import com.arny.promptcontract.ProviderProfile

internal expect fun openRouterEnvironmentKey(): String?

internal fun resolveProviderKey(profile: ProviderProfile): String = when (profile.keySource) {
    ProviderKeySource.STORED -> profile.apiKey
    ProviderKeySource.ENVIRONMENT -> {
        require(profile.id == "openrouter")
        openRouterEnvironmentKey()?.takeIf { it.isNotBlank() }
            ?: error("OPENROUTER_API_KEY недоступен. Перезапустите приложение после изменения системной переменной.")
    }
}
