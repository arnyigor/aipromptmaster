package com.arny.promptcontract

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Uses the existing variables_json column; reads old map-only values too. */
@Serializable
data class StoredPromptData(
    val storageVersion: Int = 1,
    val variables: Map<String, String> = emptyMap(),
    val document: PromptJson? = null
)

object PromptStorage {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun encode(variables: Map<String, String>, document: PromptJson?): String =
        json.encodeToString(StoredPromptData(variables = variables, document = document))

    fun decode(source: String): StoredPromptData {
        val fields = json.parseToJsonElement(source) as? JsonObject
            ?: error("Invalid stored prompt data")
        return if (fields["storageVersion"] != null && fields["variables"] is JsonObject) {
            val stored = json.decodeFromString<StoredPromptData>(source)
            require(stored.storageVersion == 1) { "Unsupported prompt storage version" }
            stored
        } else {
            StoredPromptData(variables = json.decodeFromString<Map<String, String>>(source))
        }
    }
}
