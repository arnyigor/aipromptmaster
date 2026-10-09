package com.arny.promptcontract

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.random.Random

data class ProviderManagerState(
    val config: ProviderConfig = ProviderConfig(), val draft: ProviderProfile? = null,
    val checking: Boolean = false, val models: List<String> = emptyList(), val message: String? = null,
    val modelQuery: String = "",
    val environmentKeyAvailable: Boolean = false,
    val modelAvailability: ModelAvailabilityUi = ModelAvailabilityUi(),
) {
    val visibleModels: List<String> get() = models.filter { it.contains(modelQuery, ignoreCase = true) }.take(12)
}

sealed interface ProviderAction {
    data class KeySource(val value: ProviderKeySource) : ProviderAction
    data class Select(val id: String) : ProviderAction
    data class Edit(val id: String) : ProviderAction
    data class Create(val preset: ProviderPreset) : ProviderAction
    data class Delete(val id: String) : ProviderAction
    data class Name(val value: String) : ProviderAction
    data class Url(val value: String) : ProviderAction
    data class Key(val value: String) : ProviderAction
    data class Model(val value: String) : ProviderAction
    data class ModelSearch(val value: String) : ProviderAction
    data class RequiresKey(val value: Boolean) : ProviderAction
    data object Save : ProviderAction
    data object Test : ProviderAction
    data object CheckModel : ProviderAction
    data object Close : ProviderAction
}

/** One controller for both clients; credentials only go to the explicitly edited profile. */
class ProviderManager(private val store: ProviderStore, private val probe: ProviderProbe, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(ProviderManagerState(config = store.loadProviders().checked(), environmentKeyAvailable = store.environmentKeyAvailable()))
    val state = _state.asStateFlow()
    private var check: Job? = null
    private var revision = 0
    private val modelCheck = ModelAvailabilityController(store, probe, scope,
        onStateChanged = { result -> _state.update { it.copy(modelAvailability = result) } })

    fun onAction(action: ProviderAction) {
        if (action == ProviderAction.CheckModel) {
            if (!_state.value.checking) _state.value.draft?.let { modelCheck.check(it.modelId, it) }
            return
        }
        if (action == ProviderAction.Test) { modelCheck.reset(); test(); return }
        if (action !is ProviderAction.ModelSearch) modelCheck.reset()
        check?.cancel(); revision++
        _state.update { it.copy(checking = false, message = null) }
        try {
            when (action) {
                is ProviderAction.Select -> persist(_state.value.config.select(action.id))
                is ProviderAction.Edit -> _state.update { it.copy(draft = it.config.profiles.first { p -> p.id == action.id }, models = emptyList()) }
                is ProviderAction.Create -> _state.update { it.copy(draft = ProviderProfile("provider-${Random.nextLong().toString(16)}", action.preset.title, action.preset.url, requiresKey = action.preset.requiresKey), models = emptyList()) }
                is ProviderAction.Delete -> persist(_state.value.config.delete(action.id))
                ProviderAction.Save -> _state.value.draft?.let { persist(_state.value.config.upsert(it)); _state.update { state -> state.copy(draft = null) } }
                ProviderAction.Close -> _state.update { it.copy(draft = null, models = emptyList()) }
                else -> _state.update { state -> state.copy(draft = state.draft?.let { draft ->
                    when (action) {
                        is ProviderAction.Name -> draft.copy(name = action.value)
                        is ProviderAction.Url -> draft.copy(baseUrl = action.value, apiKey = if (action.value.trim().trimEnd('/') == draft.baseUrl.trim().trimEnd('/')) draft.apiKey else "")
                        is ProviderAction.Key -> draft.copy(apiKey = action.value)
                        is ProviderAction.KeySource -> draft.copy(keySource = action.value)
                        is ProviderAction.Model -> draft.copy(modelId = action.value)
                        is ProviderAction.RequiresKey -> draft.copy(requiresKey = if (draft.id == "openrouter") true else action.value)
                        else -> draft
                    }
                }, models = if (action is ProviderAction.Url) emptyList() else state.models,
                    modelQuery = if (action is ProviderAction.ModelSearch) action.value else state.modelQuery) }
            }
        } catch (error: Exception) { _state.update { it.copy(message = error.message ?: "Не удалось сохранить профиль") } }
    }
    private fun persist(config: ProviderConfig) {
        store.saveProviders(config.checked())
        _state.update { it.copy(config = config, message = "Профиль сохранён и выбран") }
    }
    private fun test() {
        check?.cancel()
        val token = ++revision
        val draft = _state.value.draft ?: return
        check = scope.launch {
            _state.update { it.copy(checking = true, message = null) }
            try {
                val models = probe.models(draft.validated()).distinct().sorted()
                if (token == revision) _state.update { it.copy(models = models, message = "Соединение работает. Моделей: ${models.size}") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                // HTTP bodies/URLs may echo credentials; never put raw exception text into UI.
                if (token == revision) _state.update { it.copy(message = "Не удалось получить модели. Проверьте адрес, ключ и доступность сервера.") }
            } finally { if (token == revision) _state.update { it.copy(checking = false) } }
        }
    }
}
