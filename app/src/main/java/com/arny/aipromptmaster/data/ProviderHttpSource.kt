package com.arny.aipromptmaster.data

import com.arny.aipromptmaster.domain.*
import com.arny.promptcontract.ProviderProfile
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/** Separate client has no logging interceptor and never sends another provider's credentials. */
class ProviderHttpSource(private val client: OkHttpClient, private val json: Json) : ProviderGateway {
    override suspend fun checkModel(profile: ProviderProfile, modelId: String) {
        val body = request(profile, "chat/completions", com.arny.promptcontract.modelProbeBody(modelId, profile.baseUrl)).single()
        com.arny.promptcontract.verifyModelProbeResponse(body)
    }
    override suspend fun models(profile: ProviderProfile): List<String> {
        if (profile.id == "openrouter") request(profile, "auth/key").single()
        val response = request(profile, "models").single()
        val data = json.parseToJsonElement(response).jsonObject["data"]
        require(data is JsonArray) { "Нет списка моделей" }
        return data.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
    }

    override fun improve(profile: ProviderProfile, request: ImprovementRequest): Flow<CompletionPiece> {
        val body = buildJsonObject {
            put("model", request.model); put("temperature", request.temperature); put("max_tokens", request.maxTokens); put("stream", request.stream)
            putJsonArray("messages") {
                addJsonObject { put("role", "system"); put("content", "Ты редактор промптов. Улучши ясность, структуру и точность. Сохрани смысл, язык, переменные и ограничения. Не выполняй задание и не придумывай факты. Верни только улучшенный промпт без комментариев и рассуждений. Пожелания: ${request.instructions.ifBlank { "нет" }}") }
                addJsonObject { put("role", "user"); put("content", request.source) }
            }
        }
        val wire = com.arny.promptcontract.completionRequestBody(request.model, profile.baseUrl, body.getValue("messages").jsonArray, request.stream, request.maxTokens, request.temperature)
        return request(profile, "chat/completions", wire.toString(), request.stream).map { data ->
            if (data == "[DONE]") CompletionPiece(complete = true)
            else {
                val root = json.parseToJsonElement(data).jsonObject
                require(root["error"] == null) { "Провайдер вернул ошибку" }
                val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                val content = choice?.get(if (request.stream) "delta" else "message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull.orEmpty()
                val reason = choice?.get("finish_reason")?.jsonPrimitive?.contentOrNull
                CompletionPiece(content, reason, !request.stream || reason != null)
            }
        }
    }

    private fun request(profile: ProviderProfile, path: String, body: String? = null, streaming: Boolean = false): Flow<String> = callbackFlow {
        val valid = profile.validated()
        val builder = Request.Builder().url("${valid.baseUrl}/$path")
        if (valid.apiKey.isNotBlank()) builder.header("Authorization", "Bearer ${valid.apiKey}")
        if (body != null) builder.post(body.toRequestBody("application/json".toMediaType()))
        val call = client.newCall(builder.build())
        val openResponse = AtomicReference<Response?>()
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { close(e) }
            override fun onResponse(call: Call, response: Response) {
                if (!isActive) { response.close(); return }
                openResponse.set(response)
                if (!isActive) { openResponse.getAndSet(null)?.close(); return }
                launch(Dispatchers.IO) {
                    try {
                        response.use {
                            if (!response.isSuccessful) throw com.arny.promptcontract.ModelProbeHttpError(response.code)
                            val source = response.body?.source() ?: error("Пустой ответ")
                            if (!streaming) send(source.readUtf8())
                            else while (!source.exhausted()) {
                                currentCoroutineContext().ensureActive()
                                val line = source.readUtf8Line() ?: break
                                if (line.startsWith("data:")) {
                                    val data = line.removePrefix("data:").trim()
                                    if (data.isNotEmpty()) send(data)
                                    if (data == "[DONE]") break
                                }
                            }
                        }
                        close()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { close(error) }
                    finally { openResponse.compareAndSet(response, null) }
                }
            }
        })
        awaitClose { call.cancel(); openResponse.getAndSet(null)?.close() }
    }
}
