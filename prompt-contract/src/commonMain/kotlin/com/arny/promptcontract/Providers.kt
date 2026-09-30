package com.arny.promptcontract

import kotlinx.serialization.Serializable

@Serializable
data class ProviderProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String = "",
    val modelId: String = "",
    val requiresKey: Boolean = true
) {
    override fun toString(): String = "ProviderProfile(id=$id, name=$name, apiKey=<redacted>)"
    fun validated(): ProviderProfile {
        require(id.isNotBlank() && name.isNotBlank()) { "Укажите название провайдера" }
        val url = baseUrl.trim().trimEnd('/')
        require(Regex("https?://[^\\s/?#@]+(?:/[^\\s?#]*)?").matches(url)) { "Укажите адрес API с http:// или https:// без параметров и пароля" }
        require(id != "openrouter" || url == "https://openrouter.ai/api/v1") { "Для другого API создайте отдельный профиль" }
        require(!requiresKey || apiKey.isNotBlank()) { "Введите API-ключ или отключите обязательный ключ для локального сервера" }
        return copy(name = name.trim(), baseUrl = url, apiKey = apiKey.trim(), modelId = modelId.trim())
    }
    companion object {
        fun openRouter(key: String = "") = ProviderProfile("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", key)
    }
}

@Serializable
data class ProviderConfig(val profiles: List<ProviderProfile> = listOf(ProviderProfile.openRouter()), val activeId: String = "openrouter") {
    val active: ProviderProfile get() = profiles.firstOrNull { it.id == activeId } ?: profiles.first()
    fun upsert(profile: ProviderProfile): ProviderConfig {
        val valid = profile.validated()
        return copy(profiles = profiles.filterNot { it.id == valid.id } + valid, activeId = valid.id)
    }
    fun select(id: String): ProviderConfig { require(profiles.any { it.id == id }); return copy(activeId = id) }
    fun delete(id: String): ProviderConfig {
        require(id != "openrouter") { "Основной профиль OpenRouter нельзя удалить" }
        val remaining = profiles.filterNot { it.id == id }
        require(remaining.isNotEmpty())
        return copy(profiles = remaining, activeId = if (activeId == id) "openrouter" else activeId)
    }
    fun checked(): ProviderConfig {
        require(profiles.isNotEmpty() && profiles.map { it.id }.distinct().size == profiles.size)
        require(profiles.any { it.id == activeId } && profiles.any { it.id == "openrouter" })
        require(profiles.first { it.id == "openrouter" }.requiresKey)
        profiles.forEach { it.copy(requiresKey = false).validated() }
        return this
    }
}

interface ProviderStore {
    fun loadProviders(): ProviderConfig
    fun saveProviders(config: ProviderConfig)
    fun observeProviders(): kotlinx.coroutines.flow.Flow<ProviderConfig> = kotlinx.coroutines.flow.flowOf(loadProviders())
}
fun interface ProviderProbe { suspend fun models(profile: ProviderProfile): List<String> }

enum class ProviderPreset(val title: String, val url: String, val requiresKey: Boolean) {
    OPENAI("OpenAI", "https://api.openai.com/v1", true),
    LM_STUDIO("LM Studio", "http://localhost:1234/v1", false),
    OLLAMA("Ollama", "http://localhost:11434/v1", false),
    CUSTOM("Другой совместимый API", "", true)
}
