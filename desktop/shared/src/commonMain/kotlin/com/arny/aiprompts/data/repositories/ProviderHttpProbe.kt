package com.arny.aiprompts.data.repositories

import com.arny.promptcontract.ProviderProbe
import com.arny.promptcontract.ProviderProfile
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.*

class ProviderHttpProbe(private val client: HttpClient) : ProviderProbe {
    override suspend fun models(profile: ProviderProfile): List<String> {
        val valid = profile.validated()
        val response = client.get("${valid.baseUrl}/models") {
            if (valid.apiKey.isNotBlank()) header("Authorization", "Bearer ${valid.apiKey}")
        }
        require(response.status.isSuccess()) { "HTTP ${response.status.value}" }
        val root = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        require(root["data"] is JsonArray) { "Нет списка моделей" }
        return root["data"]!!.jsonArray.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
    }
}
