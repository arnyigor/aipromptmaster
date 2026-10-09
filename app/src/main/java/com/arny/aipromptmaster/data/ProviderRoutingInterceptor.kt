package com.arny.aipromptmaster.data

import com.arny.aipromptmaster.domain.repositories.ISettingsRepository
import kotlinx.serialization.json.*
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl

class ProviderRoutingInterceptor(private val settings: ISettingsRepository) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val profile = settings.loadProviders().active
        val request = chain.request()
        val suffix = request.url.encodedPath.removePrefix("/api/v1/")
        val target = (profile.baseUrl.trimEnd('/') + "/" + suffix).toHttpUrl()
        val builder = request.newBuilder().url(target).removeHeader("Authorization")
        if (profile.apiKey.isNotBlank()) builder.header("Authorization", "Bearer ${profile.apiKey}")
        if (profile.id != "openrouter") builder.removeHeader("HTTP-Referer").removeHeader("X-Title")
        if (suffix == "chat/completions" && request.body != null) {
            val buffer = okio.Buffer(); request.body!!.writeTo(buffer)
            val data = Json.parseToJsonElement(buffer.readUtf8()).jsonObject
            val wire = com.arny.promptcontract.completionRequestBody(
                data.getValue("model").jsonPrimitive.content, profile.baseUrl, data.getValue("messages").jsonArray,
                data["stream"]?.jsonPrimitive?.booleanOrNull ?: false,
                data["max_completion_tokens"]?.jsonPrimitive?.intOrNull ?: data["max_tokens"]?.jsonPrimitive?.intOrNull ?: 2048,
                data["temperature"]?.jsonPrimitive?.doubleOrNull, data["top_p"]?.jsonPrimitive?.doubleOrNull)
            builder.method(request.method, wire.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        }
        return chain.proceed(builder.build())
    }
}
