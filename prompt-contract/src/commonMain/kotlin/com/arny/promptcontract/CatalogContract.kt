package com.arny.promptcontract

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Public wire document. Unknown fields remain intact for each client's adapter. */
data class CatalogDocument(val id: String, val title: String, val fields: JsonObject)

/** Legacy ZIPs provide no authoritative deletion list. Validation precedes any DB writes. */
object CatalogContract {
    private val json = Json

    fun parse(source: String): CatalogDocument {
        val fields = json.parseToJsonElement(source).jsonObject
        fun requiredString(name: String): String {
            val value = fields[name] as? JsonPrimitive
            require(value != null && value.isString && value.content.isNotBlank()) {
                "Catalog record has no valid $name"
            }
            return value.content
        }
        return CatalogDocument(requiredString("id"), requiredString("title"), fields)
    }

    fun validateSnapshot(identities: List<Pair<String, String>>) {
        require(identities.isNotEmpty()) { "Empty catalog snapshot" }
        require(identities.all { (id, title) -> id.isNotBlank() && title.isNotBlank() }) {
            "Invalid catalog identity"
        }
        require(identities.map { it.first }.toSet().size == identities.size) {
            "Duplicate catalog IDs"
        }
    }
}
