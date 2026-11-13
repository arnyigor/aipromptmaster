package com.arny.aipromptmaster.data.repositories

import android.util.Log
import com.arny.aipromptmaster.data.api.OpenRouterService
import com.arny.aipromptmaster.data.db.daos.ModelDao
import com.arny.aipromptmaster.data.mappers.toDomain
import com.arny.aipromptmaster.data.mappers.toEntity
import com.arny.aipromptmaster.data.models.ChatCompletionRequestDTO
import com.arny.aipromptmaster.data.models.MessageDTO
import com.arny.aipromptmaster.data.models.ModelDTO
import com.arny.aipromptmaster.domain.models.DataResult
import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.domain.models.ModelsFilter
import com.arny.aipromptmaster.domain.models.errors.DomainError
import com.arny.aipromptmaster.domain.models.strings.StringHolder
import com.arny.aipromptmaster.domain.repositories.ISettingsRepository
import com.arny.aipromptmaster.domain.repositories.ModelRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.math.BigDecimal
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

class ModelRepositoryImpl(
    private val api: OpenRouterService,
    private val dao: ModelDao,
    private val syncMetadata: SyncMetadata,
    private val settingsRepository: ISettingsRepository,
    private val providerGateway: com.arny.aipromptmaster.domain.ProviderGateway? = null
) : ModelRepository {

    /**
     * Результат проверки доступности модели с доп. данными для рейтинга
     */
    data class AvailabilityResult(
        val isAvailable: Boolean,
        val responseTimeMs: Long
    )

    /**
     * Рассчитывает рейтинг модели на основе доступности и параметров.
     * Аналог Python-скрипта: 35% доступность + 20% latency + 20% speed + 10% context + 5% price + 5% params + 5% free
     */
    private fun calculateRating(
        isAvailable: Boolean,
        responseTimeMs: Long,
        contextLength: Int,
        isFree: Boolean,
        pricingPrompt: String
    ): Float {
        if (!isAvailable) return 0f

        // Доступность: 35%
        val availabilityScore = 1.0f

        // Latency: 20% - чем меньше, тем лучше (0.5s отлично, 10s плохо)
        val latencyScore = if (responseTimeMs > 0) {
            val latency = responseTimeMs.toFloat()
            kotlin.math.max(0f, kotlin.math.min(1f, 1f - ((latency - 500f) / 9500f)))
        } else {
            0.5f
        }

        // Context: 10% - log scale (4k -> 0, 1M -> 1)
        val contextScore = if (contextLength > 0) {
            val value = (ln(contextLength.toDouble()) - ln(4096.0)) / (ln(1000000.0) - ln(4096.0))
            kotlin.math.max(0.0, kotlin.math.min(1.0, value)).toFloat()
        } else {
            0.25f
        }

        // Price: 5% - бесплатные получают максимум
        val priceScore = if (isFree) 1f else {
            val price = pricingPrompt.toBigDecimalOrZero()
            if (price <= BigDecimal.ZERO) 1f
            else kotlin.math.max(0f, kotlin.math.min(1f, 1f - (price.toFloat() * 1000f / 15f)))
        }

        // Free bonus: 5%
        val freeScore = if (isFree) 1f else 0.5f

        // Context bonus: модели с большим контекстом получают небольшой бонус
        val contextBonus = if (contextLength >= 128000) 0.05f else 0f

        // Итоговый рейтинг: 0-100
        val rating = 100f * (
            0.35f * availabilityScore +
            0.20f * latencyScore +
            0.20f * (latencyScore * 0.5f) + // Speed оценка (упрощенная)
            0.10f * contextScore +
            0.05f * priceScore +
            0.05f * freeScore +
            0.05f * contextBonus
        )

        return max(0f, min(100f, rating))
    }

    private fun String.toBigDecimalOrZero() = try {
        java.math.BigDecimal(this)
    } catch (_: Exception) {
        java.math.BigDecimal.ZERO
    }

    private val favoriteMutex = Mutex()

    private fun getApiKey(): String {
        return settingsRepository.getApiKey() 
            ?: throw IllegalStateException("API key not configured")
    }

    override fun getSelectedModelFlow(): Flow<DataResult<LlmModel>> = kotlinx.coroutines.flow.combine(dao.getSelectedModelFlow(), settingsRepository.observeProviders()) { entity, config ->
            val profile = config.active
            if (profile.id != "openrouter" || profile.modelId.isNotBlank()) {
                if (profile.modelId.isBlank()) DataResult.Error(DomainError.Generic("No model selected"))
                else DataResult.Success(
                    if (entity?.id == profile.modelId && entity.providerId == profile.id) entity.toDomain()
                    else profileModel(profile.modelId)
                )
            } else
            if (entity == null) {
                DataResult.Error(DomainError.Generic("No model selected"))
            } else {
                DataResult.Success(entity.toDomain())
            }
        }

    override suspend fun getSelectedModel(): LlmModel? = try {
           val profile = settingsRepository.loadProviders().active
           if (profile.id != "openrouter") profile.modelId.takeIf { it.isNotBlank() }?.let(::profileModel)
           else if (profile.modelId.isNotBlank()) dao.getModelById(profile.id, profile.modelId)?.toDomain() ?: profileModel(profile.modelId, profile.id)
           else dao.getSelectedModel()?.toDomain()
       } catch (e: Exception) {
           null
       }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    /**
     * Список моделей для экрана выбора: читаем БД по всем провайдерам, чтобы можно было
     * выбрать модель любого сохранённого профиля. Если для активного не-OpenRouter профиля
     * записей ещё нет, подтягиваем их у провайдера.
     */
    override fun getModels(filter: ModelsFilter): Flow<List<LlmModel>> =
        settingsRepository.observeProviders().flatMapLatest { config ->
            dao.getModels(
                searchQuery = filter.query,
                onlyFree = filter.isFreeOnly,
                onlyFavorites = filter.isFavoritesOnly,
                onlyAvailable = filter.isAvailableOnly,
                sortBy = filter.sortType.name
            ).map { entities ->
                val names = providerDisplayNames(config)
                entities.map { entity ->
                    entity.toDomain().copy(providerName = names[entity.providerId] ?: entity.providerId)
                }
            }
                .map { models ->
                    filter.providerId?.let { wanted -> models.filter { it.providerId == wanted } } ?: models
                }
                .flatMapLatest { stored ->
                    if (stored.isNotEmpty() || config.active.id == "openrouter") {
                        kotlinx.coroutines.flow.flowOf(stored)
                    } else kotlinx.coroutines.flow.flow {
                        val ids = providerGateway?.models(config.active.validated()).orEmpty()
                        emit(ids.filter { it.contains(filter.query, ignoreCase = true) }
                            .map { profileModel(it, config.active.id).copy(isSelected = it == config.active.modelId) })
                    }
                }
        }

    /** Имена профилей с различающим суффиксом при совпадении, чтобы источники были различимы. */
    private fun providerDisplayNames(config: com.arny.promptcontract.ProviderConfig): Map<String, String> {
        val seen = mutableMapOf<String, Int>()
        return config.profiles.associate { profile ->
            val count = (seen[profile.name] ?: 0) + 1
            seen[profile.name] = count
            profile.id to if (count == 1) profile.name else "${profile.name} #$count"
        }
    }

    /** Провайдеры, у которых есть сохранённые модели. */
    override fun observeProviderOptions(): Flow<List<com.arny.aipromptmaster.domain.repositories.ProviderOption>> =
        settingsRepository.observeProviders().flatMapLatest { config ->
            dao.observeProviderIds().map { ids ->
                val names = providerDisplayNames(config)
                ids.map { id ->
                    com.arny.aipromptmaster.domain.repositories.ProviderOption(
                        id = id,
                        name = names[id] ?: id.ifBlank { "OpenRouter" },
                    )
                }
            }
        }

    private fun profileModel(id: String, providerId: String = "") = LlmModel(
        id = id, providerId = providerId, name = id,
        description = "OpenAI-совместимая модель", created = 0,
        contextLength = "", pricingPrompt = "unknown", pricingCompletion = "unknown",
        pricingImage = null, inputModalities = listOf("text"), outputModalities = listOf("text"),
        isSelected = true,
    )

    override suspend fun refreshModels(): Result<Unit> {
        if (settingsRepository.loadProviders().active.id != "openrouter") return try {
            val active = settingsRepository.loadProviders().active.validated()
            val ids = providerGateway?.models(active).orEmpty()
            // Persist the provider list, otherwise the models screen stays empty for custom profiles.
            val now = System.currentTimeMillis()
            dao.deleteStaleProviderModels(active.id, ids)
            dao.syncModels(ids.map { id ->
                com.arny.aipromptmaster.data.db.entities.ModelEntity(
                    id = id,
                    providerId = active.id,
                    name = id,
                    description = "OpenAI-совместимая модель",
                    contextLength = 0,
                    pricingPrompt = "0",
                    pricingCompletion = "0",
                    pricingImage = null,
                    inputModalities = "[\"text\"]",
                    outputModalities = "[\"text\"]",
                    isMultimodal = false,
                    isFree = true,
                    sortPriority = 0,
                    lastUpdated = now,
                )
            })
            syncMetadata.setLastSync(now)
            Result.success(Unit)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (http: com.arny.promptcontract.ModelProbeHttpError) {
            Result.failure(IllegalStateException(
                com.arny.aipromptmaster.domain.models.errors.providerHttpMessage(http.status)
            ))
        }
        catch (error: Exception) { Result.failure(error) }
        val lastSync = syncMetadata.getLastSync()
        val hasModels = dao.getModelsCount() > 0
        Timber.tag("ModelRepo")
            .d("Last sync: $lastSync, ago: ${lastSync / 1000}s, cached: ${lastSync < 24 * 60 * 60_000L}")
        if (hasModels && System.currentTimeMillis() - lastSync < 24 * 60 * 60_000L) {
            Timber.tag("ModelRepo").d("Using cache, skipping API call")
            return Result.success(Unit)
        }

        return try {
            val response = api.getModels()
            Timber.tag("ModelRepo")
                .d("API response: ${response.code()}, body: ${response.body()?.models?.size}")
            if (!response.isSuccessful) {
                val body = runCatching { response.errorBody()?.string() }.getOrNull()
                throw IllegalStateException(
                    com.arny.aipromptmaster.domain.models.errors.providerHttpMessage(response.code(), body)
                )
            }
            val dtos = response.body()?.models ?: emptyList()
            val entities = dtos.map(ModelDTO::toEntity)
            // '' — владелец у записей, созданных до появления providerId: это тоже OpenRouter.
            dao.deleteStaleProviderModels("openrouter", entities.map { it.id })
            dao.deleteStaleProviderModels("", entities.map { it.id })
            dao.syncModels(entities)
            syncMetadata.setLastSync(System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Возвращает новое состояние isFavorite после переключения
     */
    override suspend fun toggleFavorite(modelId: String): Boolean =
        toggleFavorite(settingsRepository.loadProviders().activeId, modelId)

    override suspend fun toggleFavorite(providerId: String, modelId: String): Boolean {
        return favoriteMutex.withLock {
            val owner = providerId.ifBlank { "openrouter" }
            // Читаем текущее состояние
            val currentModel = dao.getModelById(owner, modelId)
                ?: throw DomainError.Local(StringHolder.Text("Model $modelId not found"))

            val newState = !currentModel.isFavorite

            // Обновляем в БД
            val rowsAffected = dao.updateFavoriteStatus(owner, modelId, newState)
            if (rowsAffected == 0) {
                throw DomainError.Local(StringHolder.Text("Failed to update model $modelId"))
            }

            newState
        }
    }

    override suspend fun selectModel(modelId: String) = selectModel("", modelId)

    /**
     * Выбирает модель вместе с её провайдером: активным профилем становится владелец модели.
     * Пустой [providerId] означает активный профиль (совместимость со старым вызовом).
     */
    override suspend fun selectModel(providerId: String, modelId: String) {
        val config = settingsRepository.loadProviders()
        val targetId = providerId.ifBlank { config.activeId }
        val target = config.profiles.firstOrNull { it.id == targetId } ?: config.active
        settingsRepository.saveProviders(
            config
                .copy(profiles = config.profiles.map { if (it.id == target.id) it.copy(modelId = modelId) else it })
                .let { updated -> runCatching { updated.select(target.id) }.getOrDefault(updated) }
        )
        dao.selectModel(target.id, modelId)
    }

/**
     * Проверяет доступность модели, отправляя тестовый запрос.
     * Использует простой промпт "test" для минимальной нагрузки.
     * Timeout: 15 секунд на модель.
     */
    override suspend fun checkModelAvailability(modelId: String): Boolean {
        return checkModelAvailabilityWithTiming(modelId).isAvailable
    }

    /**
     * Проверяет доступность модели с замером времени отклика.
     * Возвращает AvailabilityResult с временем для расчёта рейтинга.
     */
    suspend fun checkModelAvailabilityWithTiming(modelId: String): AvailabilityResult {
        val isOpenRouter = settingsRepository.loadProviders().activeId == "openrouter"
        val owner = settingsRepository.loadProviders().active.id
        return try {
            val startTime = System.currentTimeMillis()
            val request = ChatCompletionRequestDTO(
                model = modelId,
                messages = listOf(MessageDTO.fromText("user", "test")),
                maxTokens = 1,
                stream = false
            )
            val apiKey = getApiKey()
            val response = withTimeoutOrNull(15_000) {
                api.getChatCompletion(
                    authorization = "Bearer $apiKey",
                    request = request
                )
            }
            val endTime = System.currentTimeMillis()
            val responseTimeMs = endTime - startTime
            val isSuccess = response?.isSuccessful == true
            if (!isOpenRouter) return AvailabilityResult(isSuccess, responseTimeMs)

            // Получаем данные модели для расчёта рейтинга (учитывая провайдера)
            val model = dao.getModelById(owner, modelId)
            val rating = if (model != null) {
                calculateRating(
                    isAvailable = isSuccess,
                    responseTimeMs = responseTimeMs,
                    contextLength = model.contextLength,
                    isFree = model.isFree,
                    pricingPrompt = model.pricingPrompt
                )
            } else 50f

            // Сохраняем с рейтингом
            dao.updateModelRating(
                providerId = owner,
                modelId = modelId,
                isAvailable = isSuccess,
                responseTimeMs = responseTimeMs,
                rating = rating,
                checkTime = System.currentTimeMillis()
            )

            Timber.tag("ModelRepo").d("Model $modelId availability: $isSuccess, time: ${responseTimeMs}ms, rating: $rating")
            AvailabilityResult(isSuccess, responseTimeMs)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            if (!isOpenRouter) return AvailabilityResult(false, 15000)
            Timber.tag("ModelRepo").e(e, "Error checking model $modelId availability")
            // Сохраняем с рейтингом 0 при ошибке
            dao.updateModelRating(
                providerId = owner,
                modelId = modelId,
                isAvailable = false,
                responseTimeMs = 15000,
                rating = 0f,
                checkTime = System.currentTimeMillis()
            )
            AvailabilityResult(false, 15000)
        }
    }

/**
     * Проверяет доступность всех free моделей последовательно.
     * Возвращает Flow для отслеживания прогресса.
     */
    override suspend fun checkFreeModelsAvailability(
        onProgress: ((modelId: String, checked: Int, total: Int) -> Unit)?
    ): Flow<Pair<String, Boolean>> = flow {
        if (settingsRepository.loadProviders().activeId != "openrouter") return@flow
        val freeModels = dao.getFreeModels()
        val total = freeModels.size
        Timber.tag("ModelRepo").d("Checking availability for $total free models")

        var checked = 0
        for (model in freeModels) {
            checked++
            onProgress?.invoke(model.id, checked, total)
            val result = checkModelAvailabilityWithTiming(model.id)
            emit(model.id to result.isAvailable)
            // Небольшая задержка между запросами
            delay(300)
        }
    }
}
