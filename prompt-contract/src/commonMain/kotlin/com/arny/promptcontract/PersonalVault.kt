package com.arny.promptcontract

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PersonalVaultConfig(val repository: String = "", val branch: String = "", val token: String = "") {
    override fun toString(): String = "PersonalVaultConfig(repository=$repository, branch=$branch, token=<redacted>)"
    fun checked(): PersonalVaultConfig {
        require(repository.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) { "Укажите репозиторий owner/repo" }
        require(repository.substringBefore('/').matches(Regex("[A-Za-z0-9][A-Za-z0-9-]*")) && repository.substringAfter('/') !in setOf(".", "..")) { "Проверьте имя владельца и репозитория" }
        require(branch.isEmpty() || (branch.matches(Regex("[A-Za-z0-9_./-]+")) && !branch.contains(".."))) { "Проверьте ветку" }
        require(repository.length <= 200 && branch.length <= 255) { "Слишком длинное имя репозитория или ветки" }
        require(token.matches(Regex("[A-Za-z0-9_.-]{1,512}"))) { "Введите GitHub token" }
        return this
    }
    val target: String get() = "$repository@$branch"
}
@Serializable data class PersonalVaultSnapshot(val version: Int = 1, val prompts: List<PromptJson> = emptyList()) {
    fun checked(): PersonalVaultSnapshot {
        require(version == 1 && prompts.size <= 10000) { "Неподдерживаемый формат личного хранилища" }
        require(prompts.all { !it.id.isNullOrBlank() && it.isLocal && !it.title.isNullOrBlank() }) { "Хранилище содержит некорректные или публичные записи" }
        require(prompts.map { it.id }.distinct().size == prompts.size) { "Повторяющиеся ID в хранилище" }
        return copy(prompts = prompts.map(::canonicalPersonalPrompt).sortedBy { it.id })
    }
}
/** Both database adapters produce the same bytes, including nullable fields and map order. */
@OptIn(kotlin.time.ExperimentalTime::class)
fun canonicalPersonalPrompt(prompt: PromptJson): PromptJson {
    fun ordered(content: Map<String, String>) = content.entries.sortedBy { it.key }.associate { it.key to it.value }
    fun date(value: String?): String {
        if (value.isNullOrBlank()) return "1970-01-01T00:00:00Z"
        val parsed = runCatching { kotlin.time.Instant.parse(value) }.getOrElse {
            runCatching { kotlin.time.Instant.parse(value + "Z") }.getOrElse { error("Неверная дата личного промпта: ${prompt.id}") }
        }
        return kotlin.time.Instant.fromEpochMilliseconds(parsed.toEpochMilliseconds()).toString()
    }
    return prompt.copy(description = prompt.description.orEmpty(), category = prompt.category.orEmpty().lowercase(),
        status = prompt.status.orEmpty().lowercase(), version = prompt.version.orEmpty(),
        content = ordered(prompt.content + mapOf("ru" to prompt.content["ru"].orEmpty(), "en" to prompt.content["en"].orEmpty())),
        promptVariants = prompt.promptVariants.map { it.copy(content = ordered(it.content)) },
        compatibleModels = prompt.compatibleModels.map(String::trim).filter(String::isNotBlank),
        metadata = MetadataJson(AuthorJson(prompt.metadata?.author?.id.orEmpty(), prompt.metadata?.author?.name.orEmpty()), prompt.metadata?.source.orEmpty(), prompt.metadata?.notes.orEmpty()),
        rating = prompt.rating ?: Rating(), createdAt = date(prompt.createdAt), updatedAt = date(prompt.updatedAt))
}
@Serializable data class PersonalVaultBaseline(val target: String, val hashes: Map<String, String>)
interface PersonalVaultStore {
    fun loadPersonalVault(): PersonalVaultConfig?
    fun savePersonalVault(config: PersonalVaultConfig)
    fun disconnectPersonalVault()
    fun loadPersonalVaultBaseline(): PersonalVaultBaseline?
    fun savePersonalVaultBaseline(baseline: PersonalVaultBaseline)
}
data class PersonalVaultRemoteSnapshot(val snapshot: PersonalVaultSnapshot, val sha: String?, val branch: String = "")
interface PersonalVaultRemote {
    suspend fun read(config: PersonalVaultConfig): PersonalVaultRemoteSnapshot
    suspend fun write(config: PersonalVaultConfig, snapshot: PersonalVaultSnapshot, expectedSha: String?)
    fun hash(value: ByteArray): String
}
interface PersonalVaultLocal {
    suspend fun snapshot(): PersonalVaultSnapshot
    suspend fun validate(expected: PersonalVaultSnapshot, result: PersonalVaultSnapshot) {}
    suspend fun apply(expected: PersonalVaultSnapshot, result: PersonalVaultSnapshot)
}
data class PersonalVaultConflict(val id: String, val local: PromptJson?, val remote: PromptJson?, val useRemote: Boolean? = null)
data class PersonalVaultChange(val id: String, val title: String, val locally: String, val github: String)
data class PersonalVaultUi(
    val config: PersonalVaultConfig = PersonalVaultConfig(), val busy: Boolean = false,
    val message: String? = null, val ready: Boolean = false, val conflicts: List<PersonalVaultConflict> = emptyList(),
    val localCount: Int = 0, val remoteCount: Int = 0, val resultCount: Int = 0,
    val changes: List<PersonalVaultChange> = emptyList(),
)
sealed interface PersonalVaultAction {
    data class Repository(val value: String) : PersonalVaultAction
    data class Branch(val value: String) : PersonalVaultAction
    data class Token(val value: String) : PersonalVaultAction
    data class Resolve(val id: String, val useRemote: Boolean) : PersonalVaultAction
    data object Preview : PersonalVaultAction
    data object Apply : PersonalVaultAction
    data object Disconnect : PersonalVaultAction
}
data class PersonalVaultPlan(
    val local: PersonalVaultSnapshot, val remote: PersonalVaultRemoteSnapshot,
    val merged: Map<String, PromptJson>, val conflicts: List<PersonalVaultConflict>,
)
fun planPersonalVault(local: PersonalVaultSnapshot, remote: PersonalVaultRemoteSnapshot, base: Map<String, String>, hash: (ByteArray) -> String): PersonalVaultPlan {
    val left = local.checked().prompts.associateBy { it.id!! }
    val right = remote.snapshot.checked().prompts.associateBy { it.id!! }
    fun fingerprint(value: PromptJson?): String? = value?.let { hash(Json.encodeToString(PromptJson.serializer(), it).encodeToByteArray()) }
    val result = mutableMapOf<String, PromptJson>()
    val conflicts = mutableListOf<PersonalVaultConflict>()
    (left.keys + right.keys + base.keys).sorted().forEach { id ->
        val l = left[id]; val r = right[id]
        val lh = fingerprint(l); val rh = fingerprint(r); val bh = base[id]
        val chosen = when {
            lh == rh -> l
            lh == bh -> r
            rh == bh -> l
            else -> { conflicts.add(PersonalVaultConflict(id, l, r)); null }
        }
        if (chosen != null) result[id] = chosen
    }
    return PersonalVaultPlan(local.checked(), remote.copy(snapshot = remote.snapshot.checked()), result, conflicts)
}
class PersonalVaultManager(
    private val store: PersonalVaultStore, private val local: PersonalVaultLocal,
    private val remote: PersonalVaultRemote, private val scope: CoroutineScope,
) {
    private val initial = runCatching { store.loadPersonalVault() }
    private val _state = MutableStateFlow(PersonalVaultUi(config = initial.getOrNull() ?: PersonalVaultConfig(),
        message = if (initial.isFailure) "Проверьте сохранённые настройки GitHub" else null))
    val state = _state.asStateFlow()
    private var plan: PersonalVaultPlan? = null
    fun onAction(action: PersonalVaultAction) {
        if (_state.value.busy) return
        when (action) {
            is PersonalVaultAction.Repository -> edit(_state.value.config.copy(repository = action.value.trim()))
            is PersonalVaultAction.Branch -> edit(_state.value.config.copy(branch = action.value.trim()))
            is PersonalVaultAction.Token -> edit(_state.value.config.copy(token = action.value.trim()))
            PersonalVaultAction.Disconnect -> execute {
                store.disconnectPersonalVault(); plan = null
                _state.value = PersonalVaultUi(message = "GitHub отключён. Личные промпты остались на устройстве")
            }
            is PersonalVaultAction.Resolve -> {
                _state.value = _state.value.copy(conflicts = _state.value.conflicts.map { if (it.id == action.id) it.copy(useRemote = action.useRemote) else it })
                refreshChanges()
            }
            PersonalVaultAction.Preview -> execute {
                val entered = _state.value.config.checked()
                val downloaded = remote.read(entered)
                val config = if (downloaded.branch.isNotBlank()) entered.copy(branch = downloaded.branch) else entered
                store.savePersonalVault(config)
                _state.value = _state.value.copy(config = config)
                val baseline = store.loadPersonalVaultBaseline()?.takeIf { it.target == config.target }?.hashes.orEmpty()
                require(downloaded.sha != null || baseline.isEmpty()) { "Файл хранилища удалён на GitHub; автоматическое удаление локальных записей остановлено" }
                val prepared = planPersonalVault(local.snapshot(), downloaded, baseline, remote::hash)
                plan = prepared
                _state.value = _state.value.copy(ready = true, conflicts = prepared.conflicts, localCount = prepared.local.prompts.size,
                    remoteCount = prepared.remote.snapshot.prompts.size, resultCount = prepared.merged.size, message = "Изменения подготовлены; выберите версии при конфликтах и примените")
                refreshChanges()
            }
            PersonalVaultAction.Apply -> execute {
                val prepared = requireNotNull(plan) { "Сначала проверьте изменения" }
                require(_state.value.conflicts.all { it.useRemote != null }) { "Разрешите все конфликты" }
                val merged = prepared.merged.toMutableMap()
                _state.value.conflicts.forEach { conflict ->
                    val chosen = if (conflict.useRemote == true) conflict.remote else conflict.local
                    if (chosen == null) merged.remove(conflict.id) else merged[conflict.id] = chosen
                }
                val result = PersonalVaultSnapshot(prompts = merged.toSortedMap().values.toList()).checked()
                require(local.snapshot() == prepared.local) { "Локальные промпты изменились. Проверьте изменения снова" }
                local.validate(prepared.local, result)
                val config = _state.value.config.checked()
                if (result != prepared.remote.snapshot) remote.write(config, result, prepared.remote.sha)
                else require(remote.read(config).sha == prepared.remote.sha) { "GitHub изменился после проверки. Проверьте изменения снова" }
                local.apply(prepared.local, result)
                store.savePersonalVaultBaseline(PersonalVaultBaseline(config.target, result.prompts.associate { it.id!! to remote.hash(Json.encodeToString(PromptJson.serializer(), it).encodeToByteArray()) }))
                plan = null
                _state.value = _state.value.copy(ready = false, conflicts = emptyList(), message = "Личные промпты синхронизированы: ${result.prompts.size}")
            }
        }
    }
    private fun edit(config: PersonalVaultConfig) { plan = null; _state.value = PersonalVaultUi(config = config) }
    private fun refreshChanges() {
        val prepared = plan ?: return
        val unresolved = _state.value.conflicts.filter { it.useRemote == null }.map { it.id }.toSet()
        val merged = prepared.merged.toMutableMap()
        _state.value.conflicts.filter { it.useRemote != null }.forEach { conflict ->
            (if (conflict.useRemote == true) conflict.remote else conflict.local)?.let { merged[conflict.id] = it }
        }
        val left = prepared.local.prompts.associateBy { it.id!! }
        val right = prepared.remote.snapshot.prompts.associateBy { it.id!! }
        fun action(old: PromptJson?, new: PromptJson?) = when { old == new -> "без изменений"; new == null -> "удалить"; old == null -> "добавить"; else -> "обновить" }
        val changes = (left.keys + right.keys + merged.keys).sorted().filterNot { it in unresolved }.mapNotNull { id ->
            val selected = merged[id]
            if (selected == left[id] && selected == right[id]) null
            else PersonalVaultChange(id, (selected ?: left[id] ?: right[id])?.title ?: id, action(left[id], selected), action(right[id], selected))
        }
        _state.value = _state.value.copy(changes = changes, resultCount = merged.size)
    }
    private fun execute(block: suspend () -> Unit) {
        _state.value = _state.value.copy(busy = true, message = null)
        scope.launch {
            try { block() }
            catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { plan = null; _state.value = _state.value.copy(ready = false, message = error.message ?: "Ошибка синхронизации") }
            finally { _state.value = _state.value.copy(busy = false) }
        }
    }
}

/** Chunked preferences keep Windows' value limit from truncating a large baseline. */
class PersonalVaultPreferences(private val get: (String) -> String?, private val put: (String, String) -> Unit) : PersonalVaultStore {
    override fun loadPersonalVault() = get("personal_vault_config")?.takeIf(String::isNotBlank)?.let { Json.decodeFromString<PersonalVaultConfig>(it) }
    override fun disconnectPersonalVault() = put("personal_vault_config", "")
    override fun savePersonalVault(config: PersonalVaultConfig) = put("personal_vault_config", Json.encodeToString(PersonalVaultConfig.serializer(), config.checked()))
    override fun loadPersonalVaultBaseline(): PersonalVaultBaseline? {
        val marker = get("personal_vault_base_marker") ?: return null
        val parts = marker.split(':'); require(parts.size == 2)
        val count = parts[1].toInt(); require(count in 1..10000)
        val json = (0 until count).joinToString("") { get("personal_vault_base_${parts[0]}_$it") ?: error("Неполное состояние синхронизации") }
        return Json.decodeFromString<PersonalVaultBaseline>(json)
    }
    override fun savePersonalVaultBaseline(baseline: PersonalVaultBaseline) {
        val generation = if (get("personal_vault_base_marker")?.substringBefore(':') == "1") 0 else 1
        val chunks = Json.encodeToString(PersonalVaultBaseline.serializer(), baseline).chunked(1024)
        chunks.forEachIndexed { index, value -> put("personal_vault_base_${generation}_$index", value) }
        put("personal_vault_base_marker", "$generation:${chunks.size}")
    }
}
