package com.arny.aiprompts.platform

import com.arkivanov.essenty.statekeeper.SerializableContainer
import com.arny.aiprompts.di.SettingsFactory
import kotlinx.serialization.json.Json
import java.util.UUID

/** Encrypted local session; chunking respects the Windows Preferences value limit. */
class DesktopSessionStore {
    private val settings = SettingsFactory().create("window_session")
    @Synchronized fun load(): SerializableContainer? = runCatching {
        val generation = settings.getStringOrNull("active") ?: return null
        val count = settings.getIntOrNull("$generation-count") ?: return null
        require(count in 1..10_000)
        val json = (0 until count).joinToString("") { settings.getString("$generation-$it", "") }
        Json.decodeFromString(SerializableContainer.serializer(), json)
    }.getOrNull()

    fun save(state: SerializableContainer) {
        saveEncoded(Json.encodeToString(SerializableContainer.serializer(), state))
    }

    @Synchronized internal fun saveEncoded(encoded: String) {
        val old = settings.getStringOrNull("active")
        val generation = UUID.randomUUID().toString()
        val chunks = encoded.chunked(3000)
        chunks.forEachIndexed { index, value -> settings.putString("$generation-$index", value) }
        settings.putInt("$generation-count", chunks.size)
        settings.putString("active", generation)
        old?.let {
            val count = settings.getInt("$it-count", 0)
            repeat(count) { index -> settings.remove("$old-$index") }
            settings.remove("$old-count")
        }
    }
}
