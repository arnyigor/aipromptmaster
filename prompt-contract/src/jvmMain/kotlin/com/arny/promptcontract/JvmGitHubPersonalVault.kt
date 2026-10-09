@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
package com.arny.promptcontract

import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64

/** Shared JVM transport for Android/Desktop; authentication never enters URLs or logs. */
class JvmGitHubPersonalVault(private val baseUrl: String = "https://api.github.com") : PersonalVaultRemote {
    init {
        val uri = URI(baseUrl)
        require(baseUrl == "https://api.github.com" || (uri.scheme == "http" && uri.host in setOf("localhost", "127.0.0.1")))
    }
    override fun hash(value: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
    override suspend fun read(config: PersonalVaultConfig): PersonalVaultRemoteSnapshot = withContext(Dispatchers.IO) {
        config.checked()
        val repo = request(config, "GET", "/repos/${config.repository}")
        check(repo.first == 200) { "GitHub: репозиторий недоступен (HTTP ${repo.first})" }
        val metadata = Json.parseToJsonElement(repo.second).jsonObject
        require(metadata["private"]?.jsonPrimitive?.boolean == true) { "Для личных промптов выберите приватный репозиторий" }
        val branch = config.branch.ifBlank { metadata.getValue("default_branch").jsonPrimitive.content }
        val file = request(config, "GET", path(config.copy(branch = branch)))
        if (file.first == 404) return@withContext PersonalVaultRemoteSnapshot(PersonalVaultSnapshot(), null, branch)
        check(file.first == 200) { "GitHub: ошибка чтения (HTTP ${file.first})" }
        val content = Json.parseToJsonElement(file.second).jsonObject
        require(content["encoding"]?.jsonPrimitive?.content == "base64") { "Файл личных промптов слишком большой или имеет неверный формат" }
        val decoded = Base64.decode(content.getValue("content").jsonPrimitive.content.filterNot(Char::isWhitespace)).decodeToString()
        val sha = content.getValue("sha").jsonPrimitive.content
        require(sha.isNotBlank()) { "GitHub не вернул версию файла" }
        PersonalVaultRemoteSnapshot(Json.decodeFromString<PersonalVaultSnapshot>(decoded).checked(), sha, branch)
    }
    override suspend fun write(config: PersonalVaultConfig, snapshot: PersonalVaultSnapshot, expectedSha: String?) = withContext(Dispatchers.IO) {
        val json = Json { prettyPrint = true }.encodeToString(PersonalVaultSnapshot.serializer(), snapshot.checked())
        require(json.encodeToByteArray().size <= 900000) { "Личное хранилище превышает лимит одного файла 900 КБ" }
        val body = buildJsonObject {
            put("message", "Sync personal prompts from AI Prompt Master")
            put("content", Base64.encode(json.encodeToByteArray()))
            if (expectedSha != null) put("sha", expectedSha)
            if (config.branch.isNotBlank()) put("branch", config.branch)
        }.toString()
        val result = request(config.checked(), "PUT", path(config).substringBefore('?'), body)
        check(result.first in setOf(200, 201)) {
            if (result.first in setOf(409, 422)) "GitHub изменился после проверки. Проверьте изменения снова"
            else "GitHub: ошибка записи (HTTP ${result.first}). Проверьте права Contents: Read and write"
        }
    }
    private fun path(config: PersonalVaultConfig): String = "/repos/${config.repository}/contents/.aiprompts/personal-prompts.json" +
        if (config.branch.isBlank()) "" else "?ref=${URLEncoder.encode(config.branch, "UTF-8")}"
    private fun request(config: PersonalVaultConfig, method: String, path: String, body: String? = null): Pair<Int, String> {
        val connection = URI(baseUrl + path).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000; connection.readTimeout = 30000
            connection.setRequestProperty("Authorization", "Bearer ${config.token}")
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10")
            connection.setRequestProperty("User-Agent", "AI-Prompt-Master")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.encodeToByteArray()) }
            }
            val status = connection.responseCode
            if (status !in 200..299) return status to ""
            val bytes = connection.inputStream.use { stream ->
                val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) { val count = stream.read(buffer); if (count < 0) break
                    require(output.size() + count <= 2000000) { "Ответ GitHub превышает допустимый размер" }; output.write(buffer, 0, count) }
                output.toByteArray()
            }
            return status to bytes.decodeToString()
        } finally { connection.disconnect() }
    }
}
