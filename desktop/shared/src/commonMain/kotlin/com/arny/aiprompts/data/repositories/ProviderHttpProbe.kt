package com.arny.aiprompts.data.repositories

import com.arny.promptcontract.ProviderProbe
import com.arny.promptcontract.ProviderProfile
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.contentType
import io.ktor.http.ContentType
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.*

class ProviderHttpProbe(private val client: HttpClient) : ProviderProbe {
    override suspend fun checkModel(profile: ProviderProfile, modelId: String) {
        val valid = profile.validated()
        val key = com.arny.aiprompts.platform.resolveProviderKey(valid)
        val response = client.post("${valid.baseUrl}/chat/completions") {
            if (key.isNotBlank()) header("Authorization", "Bearer $key")
            contentType(ContentType.Application.Json)
            setBody(com.arny.promptcontract.modelProbeBody(modelId, valid.baseUrl))
        }
        if (!response.status.isSuccess()) throw com.arny.promptcontract.ModelProbeHttpError(response.status.value)
        com.arny.promptcontract.verifyModelProbeResponse(response.bodyAsText())
    }
    override suspend fun models(profile: ProviderProfile): List<String> {
        val valid = profile.validated()
        val key = com.arny.aiprompts.platform.resolveProviderKey(valid)
        if (valid.id == "openrouter") {
            val auth = client.get("${valid.baseUrl}/auth/key") { header("Authorization", "Bearer $key") }
            require(auth.status.isSuccess()) { "HTTP ${auth.status.value}" }
        }
        val response = client.get("${valid.baseUrl}/models") {
            if (key.isNotBlank()) header("Authorization", "Bearer $key")
        }
        require(response.status.isSuccess()) { "HTTP ${response.status.value}" }
        val root = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        require(root["data"] is JsonArray) { "Нет списка моделей" }
        return root["data"]!!.jsonArray.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
    }
}
