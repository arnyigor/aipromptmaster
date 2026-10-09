package com.arny.promptcontract

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

data class ModelAvailabilityUi(val modelId: String = "", val providerName: String = "",
    val checking: Boolean = false, val available: Boolean? = null, val message: String? = null)

/** Only a status code crosses the transport boundary; no response body or credentials. */
class ModelProbeHttpError(val status: Int) : Exception("HTTP $status")
class ModelProbeResponseError : Exception("Invalid model probe response")

fun modelProbeBody(modelId: String, baseUrl: String = ""): String = buildJsonObject {
    val tokenField = if (baseUrl.trim().trimEnd('/').lowercase() in setOf("https://api.openai.com/v1", "https://openrouter.ai/api/v1"))
        "max_completion_tokens" else "max_tokens"
    put("model", modelId); put("stream", false); put(tokenField, 16)
    putJsonArray("messages") { addJsonObject { put("role", "user"); put("content", "Reply OK.") } }
}.toString()

fun verifyModelProbeResponse(body: String) {
    val root = Json.parseToJsonElement(body).jsonObject
    if ((root["error"] != null && root["error"] != JsonNull) || root["choices"] !is JsonArray ||
        root["choices"]!!.jsonArray.firstOrNull()?.jsonObject?.get("message") !is JsonObject) throw ModelProbeResponseError()
}

/** Explicit diagnostic request: never reads or writes conversations or drafts. */
class ModelAvailabilityController(private val store: ProviderStore, private val probe: ProviderProbe,
    private val scope: CoroutineScope, private val timeoutMillis: Long = 30_000,
    private val onStateChanged: (ModelAvailabilityUi) -> Unit = {}) {
    private val _state = MutableStateFlow(ModelAvailabilityUi())
    val state = _state.asStateFlow()
    private var job: Job? = null
    private var revision = 0
    private var checkedProfile: ProviderProfile? = null

    private fun update(value: ModelAvailabilityUi) { _state.value = value; onStateChanged(value) }
    fun reset() { revision++; job?.cancel(); checkedProfile = null; update(ModelAvailabilityUi()) }
    fun retainFor(modelId: String) {
        if (_state.value.modelId.isEmpty()) return
        if (_state.value.modelId != modelId || (checkedProfile != null &&
            runCatching { store.loadProviders().active }.getOrNull() != checkedProfile)) reset()
    }
    fun check(modelId: String, draft: ProviderProfile? = null) {
        if (_state.value.checking) return
        if (modelId.isBlank()) { update(ModelAvailabilityUi(available = false, message = "Выберите модель")); return }
        val token = ++revision
        update(ModelAvailabilityUi(modelId = modelId, checking = true))
        job = scope.launch {
            var provider = ""
            try {
                val profile = draft ?: store.loadProviders().active
                checkedProfile = profile
                provider = profile.name
                if (profile.requiresKey && ((profile.keySource == ProviderKeySource.STORED && profile.apiKey.isBlank()) ||
                    (profile.keySource == ProviderKeySource.ENVIRONMENT && !store.environmentKeyAvailable()))) {
                    update(ModelAvailabilityUi(modelId, provider, available = false, message = "Настройте API-ключ провайдера"))
                    return@launch
                }
                update(ModelAvailabilityUi(modelId, provider, checking = true))
                withTimeout(timeoutMillis) { probe.checkModel(profile.validated(), modelId) }
                if (token == revision) update(ModelAvailabilityUi(modelId, provider, available = true, message = "Модель доступна · $provider"))
            } catch (_: TimeoutCancellationException) { fail(token, modelId, provider, "Провайдер не ответил за время проверки") }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: ModelProbeHttpError) {
                val message = when (error.status) {
                    401 -> "Ключ не принят провайдером (401)"
                    402 -> "Недостаточно средств или кредитов (402)"
                    403 -> "Нет доступа к модели (403)"
                    404 -> "Модель или адрес API не найдены (404)"
                    429 -> "Превышен лимит запросов — повторите позже (429)"
                    400, 422 -> "Модель отклонила проверочный запрос (${error.status})"
                    else -> "Ошибка провайдера (HTTP ${error.status})"
                }
                fail(token, modelId, provider, message)
            } catch (_: ModelProbeResponseError) { fail(token, modelId, provider, "Провайдер вернул ответ, который не подтверждает доступность модели") }
            catch (_: Exception) { fail(token, modelId, provider, "Не удалось проверить модель. Проверьте сеть и настройки провайдера") }
            finally { if (token == revision) update(_state.value.copy(checking = false)) }
        }
    }
    private fun fail(token: Int, modelId: String, provider: String, message: String) {
        if (token == revision) update(ModelAvailabilityUi(modelId, provider, available = false, message = message))
    }
}
