# Универсальная архитектура для работы с LLM-провайдерами

## Обзор
Архитектура позволяет подключать различные LLM-провайдеры (OpenRouter, HuggingFace и др.) через единый интерфейс.

## Компоненты

### 1. Интерфейсы
#### `ILLmRepository.kt`
```kotlin
interface ILLmRepository {
    suspend fun sendMessage(
        messages: List<ChatMessage>,
        model: String,
        temperature: Float
    ): Result<LLMResponse>
    
    suspend fun sendStreamingMessage(
        messages: List<ChatMessage>,
        model: String,
        temperature: Float
    ): Flow<Result<LLMResponse>>
}
```

#### `ILLMInteractor.kt`
```kotlin
interface ILLMInteractor {
    suspend fun generateResponse(
        prompt: String,
        systemMessage: String? = null,
        provider: String = "default",
        model: String? = null
    ): Result<String>
    
    fun streamResponse(
        prompt: String,
        systemMessage: String? = null,
        provider: String = "default",
        model: String? = null
    ): Flow<Result<String>>
}
```

### 2. Реализация
#### Базовый класс провайдера
```kotlin
abstract class BaseLlmProvider(
    protected val httpClient: HttpClient,
    protected val apiKey: String,
    val providerName: String
) {
    abstract suspend fun sendRequest(request: LLMRequest): Result<LLMResponse>
    abstract fun streamRequest(request: LLMRequest): Flow<Result<LLMResponse>>
}
```

#### Пример реализации для OpenRouter
```kotlin
class OpenRouterProvider(
    httpClient: HttpClient,
    apiKey: String
) : BaseLlmProvider(httpClient, apiKey, "openrouter") {
    
    override suspend fun sendRequest(request: LLMRequest): Result<LLMResponse> {
        // Реализация специфичная для OpenRouter
    }
    
    override fun streamRequest(request: LLMRequest): Flow<Result<LLMResponse>> {
        // Реализация потокового запроса
    }
}
```

### 3. Добавление нового провайдера
1. Создать новый класс, наследуемый от `BaseLlmProvider`
2. Реализовать методы `sendRequest` и `streamRequest`
3. Зарегистрировать провайдер в DI-контейнере
4. Обновить конфигурацию API ключей

## DI-настройка
```kotlin
val llmModule = module {
    single<ILLmRepository> {
        LLmRepositoryImpl(
            providers = mapOf(
                "openrouter" to OpenRouterProvider(
                    httpClient = get(),
                    apiKey = getProperty("OPENROUTER_KEY")
                ),
                "huggingface" to HuggingFaceProvider(
                    httpClient = get(),
                    apiKey = getProperty("HF_KEY")
                )
            ),
            defaultProvider = "openrouter"
        )
    }
    
    single<ILLMInteractor> { LLMInteractorImpl(get()) }
}