package com.arny.promptcontract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CatalogEntry(val id: String, val path: String, val sha256: String)

@Serializable
data class CatalogManifest(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("snapshot_id") val snapshotId: String,
    @SerialName("source_commit") val sourceCommit: String,
    val count: Int,
    val entries: List<CatalogEntry>,
    @SerialName("deleted_ids") val deletedIds: List<String> = emptyList()
) {
    companion object {
        // Non-JSON suffix keeps old clients' recursive *.json readers compatible.
        const val FILE_NAME = "catalog.manifest"
        fun verify(source: String, files: Map<String, ByteArray>, hash: (ByteArray) -> String): CatalogManifest {
            val manifest = Json.decodeFromString<CatalogManifest>(source)
            require(manifest.schemaVersion == 1) { "Unsupported catalog manifest" }
            require(manifest.snapshotId.matches(Regex("[a-f0-9]{64}"))) { "Invalid snapshot ID" }
            require(manifest.count > 0 && manifest.count == manifest.entries.size) { "Invalid manifest count" }
            require(manifest.entries.map { it.id }.toSet().size == manifest.count) { "Duplicate manifest IDs" }
            require(manifest.entries.map { it.path }.toSet().size == manifest.count) { "Duplicate manifest paths" }
            require(files.keys == manifest.entries.map { it.path }.toSet()) { "Incomplete catalog snapshot" }
            manifest.entries.forEach { entry ->
                require(entry.path.matches(Regex("prompts/[a-z0-9_-]+/[A-Za-z0-9_-]+\\.json"))) { "Unsafe manifest path" }
                val data = files.getValue(entry.path)
                require(hash(data) == entry.sha256) { "Catalog checksum mismatch: ${entry.path}" }
                require(CatalogContract.parse(data.decodeToString()).id == entry.id) { "Catalog ID mismatch" }
            }
            require(manifest.deletedIds.all { it.matches(Regex("[A-Za-z0-9_-]+")) }) { "Invalid tombstone ID" }
            require(manifest.deletedIds.toSet().size == manifest.deletedIds.size) { "Duplicate tombstones" }
            require(manifest.deletedIds.none { id -> manifest.entries.any { it.id == id } }) { "Live ID marked deleted" }
            return manifest
        }
    }
}
