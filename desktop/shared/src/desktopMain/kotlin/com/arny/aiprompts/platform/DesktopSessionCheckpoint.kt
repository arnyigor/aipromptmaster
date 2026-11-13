package com.arny.aiprompts.platform

import com.arkivanov.essenty.statekeeper.StateKeeperDispatcher
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json

/** Capture state on the UI thread, then persist changed snapshots off the UI thread. */
class DesktopSessionCheckpoint(
    private val keeper: StateKeeperDispatcher,
    private val store: DesktopSessionStore,
    private val intervalMillis: Long = 1000,
) {
    suspend fun run() {
        var previous: String? = null
        while (currentCoroutineContext().isActive) {
            val snapshot = withContext(Dispatchers.Main) { keeper.save() }
            val encoded = withContext(Dispatchers.Default) { Json.encodeToString(com.arkivanov.essenty.statekeeper.SerializableContainer.serializer(), snapshot) }
            if (encoded != previous) {
                try {
                    withContext(Dispatchers.IO) { store.saveEncoded(encoded) }
                    previous = encoded
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    // Keep the last complete generation and retry; never print drafts or settings.
                    System.err.println("Session checkpoint failed; previous snapshot retained")
                }
            }
            delay(intervalMillis)
        }
    }
}
