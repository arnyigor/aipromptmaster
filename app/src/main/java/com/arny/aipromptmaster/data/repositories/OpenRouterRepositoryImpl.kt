package com.arny.aipromptmaster.data.repositories

import android.util.Base64
import android.util.Log
import com.arny.aipromptmaster.data.api.OpenRouterService
import com.arny.aipromptmaster.data.mappers.toDomain
import com.arny.aipromptmaster.data.models.ChatCompletionRequestDTO
import com.arny.aipromptmaster.data.models.ChatCompletionResponseDTO
import com.arny.aipromptmaster.data.models.ContentItemDTO
import com.arny.aipromptmaster.data.models.MessageDTO
import com.arny.aipromptmaster.data.models.StreamChunk
import com.arny.aipromptmaster.data.repositories.ErrorMapper.toDomainError
import com.arny.aipromptmaster.domain.models.ApiRequestWithFiles
import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.domain.models.ChatRole
import com.arny.aipromptmaster.domain.models.DataResult
import com.arny.aipromptmaster.domain.models.FileAttachment
import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.domain.models.errors.DomainError
import com.arny.aipromptmaster.domain.models.StreamResult
import com.arny.aipromptmaster.domain.repositories.IFileRepository
import com.arny.aipromptmaster.domain.repositories.IOpenRouterRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException

/**
 * Реализация репозитория для OpenRouter.
 *
 * - Ошибки обрабатываются через `ErrorMapper.map()`/`toDomainError()`.
 * - Отмена запросов реализуется через обычный `Job.cancel()`
 *   (флаг `isRequestCancelled` удалён).
 */
class OpenRouterRepositoryImpl(
    private val service: OpenRouterService,
    private val jsonParser: Json,
    private val fileRepository: IFileRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : IOpenRouterRepository {

    /* --------------------------------------------------------------- */
    /*  Модели                                                      */
    /* --------------------------------------------------------------- */
    private val _modelsCache = MutableStateFlow<List<LlmModel>>(emptyList())
    private val refreshMutex = Mutex()

    /**
     * Определяет, нужно ли использовать multimodal формат для изображений.
     * Текстовые файлы всегда используем как plain text.
     */
    private fun shouldUseMultimodal(file: FileAttachment?, model: LlmModel?): Boolean {
        // Только для изображений проверяем поддержку модели
        if (file?.isImage != true) return false
        
        // Проверяем, что модель поддерживает image вход
        val inputModalities = model?.inputModalities ?: emptyList()
        return inputModalities.contains("image")
    }

    /**
     * Создает простой текстовый формат - объединяем текст и файл.
     */
    private fun createSimpleTextMessage(
        content: String,
        file: FileAttachment?
    ): MessageDTO {
        val textContent = buildString {
            append(content)
            
            file?.let { f ->
                if (f.originalContent.isNotBlank()) {
                    if (isNotBlank()) append("\n\n")
                    append("[Файл: ${f.fileName}]\n\n")
                    append(f.originalContent)
                }
            }
        }
        
        return MessageDTO.fromText("", textContent)
    }

    /**
     * Создает multimodal формат для изображений.
     */
    private fun createMultimodalMessage(
        content: String,
        file: FileAttachment
    ): MessageDTO {
        val contentItems = mutableListOf<ContentItemDTO>()
        
        // Добавляем текст сообщения
        if (content.isNotBlank()) {
            contentItems.add(ContentItemDTO.text(content))
        }
        
        // Добавляем изображение в base64
        file.imageData?.let { imageData ->
            val base64Image = Base64.encodeToString(imageData, Base64.NO_WRAP)
            contentItems.add(
                ContentItemDTO.image("data:${file.mimeType};base64,$base64Image")
            )
        }
        
        return if (contentItems.size == 1 && content.isNotBlank()) {
            MessageDTO.fromText("", content)
        } else {
            MessageDTO(
                role = "",
                content = null,
                contentList = contentItems
            )
        }
    }

    /**
     * Преобразует ChatMessage в MessageDTO с учетом возможностей модели.
     * Текстовые файлы → простой формат.
     * Изображения → multimodal только если модель поддерживает image input.
     */
    private fun ChatMessage.toMessageDTOWithModel(
        file: FileAttachment?,
        model: LlmModel?
    ): MessageDTO {
        // Текстовые файлы всегда используем как plain text
        if (file?.isImage != true) {
            return createSimpleTextMessage(content, file)
        }
        
        // Изображения - проверяем поддержку моделью
        return if (shouldUseMultimodal(file, model)) {
            createMultimodalMessage(content, file)
        } else {
            // Модель не поддерживает изображения - используем простой формат
            createSimpleTextMessage(content, file)
        }
    }

    /**
     * Преобразует ChatMessage в MessageDTO.
     * @deprecated Используйте toMessageDTOWithModel с передачей LlmModel
     */
    private suspend fun ChatMessage.toMessageDTOWithFile(
        fileAttachment: FileAttachment?
    ): MessageDTO = toMessageDTOWithModel(fileAttachment, null)

    override fun getModelsFlow(): Flow<List<LlmModel>> =
        _modelsCache.asStateFlow()

    /** Обновляем список моделей из сети (одновременно только один поток) */
    override suspend fun refreshModels(): Result<Unit> = withContext(dispatcher) {
        refreshMutex.withLock {
            try {
                val response = service.getModels()
                if (response.isSuccessful && response.body() != null) {
                    _modelsCache.value = response.body()!!.models.map { it.toDomain() }
                    Result.success(Unit)
                } else {
                    // Retrofit‑помощник: создаём HttpException и отдаем в ErrorMapper
                    val httpEx = HttpException(response)
                    Result.failure(ErrorMapper.map(httpEx))
                }
            } catch (e: Exception) {
                Result.failure(e.toDomainError())
            }
        }
    }

    override fun getChatCompletionStream(
        model: String,
        messages: List<ChatMessage>,
        apiKey: String,
        attachedFiles: List<FileAttachment>,
        llmModel: LlmModel?
    ): Flow<DataResult<StreamResult>> = flow {
        // Предварительно загружаем полный контент всех файлов из сообщений
        val messageFiles = withContext(dispatcher) {
            messages.mapNotNull { message ->
                message.fileAttachment?.let { meta ->
                    fileRepository.getTemporaryFile(meta.fileId)?.let { fullFile ->
                        message.id to fullFile
                    }
                }
            }.toMap()
        }

        // Подготавливаем сообщения с файлами с учетом возможностей модели
        val messagesWithFiles = messages.map { message ->
            val file = messageFiles[message.id]
            message.toMessageDTOWithModel(file, llmModel).copy(role = message.role.toApiRole())
        }

        val request = ChatCompletionRequestDTO(
            model = model,
            messages = messagesWithFiles,
            stream = true
        )

        try {
            val response = service.getChatCompletionStream("Bearer $apiKey", request = request)
            if (!response.isSuccessful) {
                emit(DataResult.Error(ErrorMapper.map(HttpException(response))))
                return@flow
            }

            val source = response.body()?.source() ?: throw DomainError.Generic("Empty body")

            // Используем use для авто-закрытия
            source.use { bufferedSource ->
                while (!bufferedSource.exhausted()) {
                    // Читаем до перевода строки
                    val line = bufferedSource.readUtf8Line() ?: break

                    if (line.startsWith("data:")) {
                        val jsonPart = line.removePrefix("data:").trim()
                        if (jsonPart == "[DONE]") break

                        try {
                            val chunk = jsonParser.decodeFromString<StreamChunk>(jsonPart)
                            val content = chunk.choices.firstOrNull()?.delta?.content
                            val actualModelId = chunk.model ?: model
                            if (!content.isNullOrEmpty()) {
                                emit(DataResult.Success(StreamResult(content = content, modelId = actualModelId)))
                            }
                        } catch (e: Exception) {
                            // Логируем, но не крашим поток из-за одного битого чанка
                            Timber.w(e, "Failed to parse chunk: $jsonPart")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emit(DataResult.Error(e.toDomainError()))
        }
    }.flowOn(dispatcher)

    /* --------------------------------------------------------------- */
    /*  Chat‑completion с файлами                                     */
    /* --------------------------------------------------------------- */
    override fun getChatCompletionStreamWithFiles(
        request: ApiRequestWithFiles,
        apiKey: String
    ): Flow<DataResult<StreamResult>> = flow {
        emit(DataResult.Loading)

        try {
            // Преобразуем ApiMessage в MessageDTO с поддержкой файлов
            val messagesWithFiles = request.messages.mapIndexed { index, apiMessage ->
                // Находим соответствующий файл
                val fileRef = request.files.getOrNull(index)
                val fileAttachment = fileRef?.let { ref ->
                    FileAttachment(
                        id = ref.id,
                        fileName = ref.name,
                        fileExtension = ref.name.substringAfterLast('.', ""),
                        fileSize = ref.content.length.toLong(),
                        mimeType = ref.mimeType,
                        originalContent = if (ref.mimeType.startsWith("text/")) ref.content else "",
                        imageData = null,
                        isEditable = false
                    )
                }

                // Создаем MessageDTO с файлами
                val contentItems = mutableListOf<ContentItemDTO>()

                if (apiMessage.content.isNotBlank()) {
                    contentItems.add(ContentItemDTO.text(apiMessage.content))
                }

                fileAttachment?.let { file ->
                    if (file.isImage && file.originalContent.isNotBlank()) {
                        // Изображение в base64
                        contentItems.add(
                            ContentItemDTO.image("data:${file.mimeType};base64,${file.originalContent}")
                        )
                    }
                }

                if (contentItems.size == 1 && contentItems[0].type == "text") {
                    MessageDTO.fromText(apiMessage.role, apiMessage.content)
                } else {
                    MessageDTO(
                        role = apiMessage.role,
                        content = null,
                        contentList = contentItems
                    )
                }
            }

            val apiRequest = ChatCompletionRequestDTO(
                model = request.model,
                messages = messagesWithFiles,
                stream = true
            )

            val response = service.getChatCompletionStream("Bearer $apiKey", request = apiRequest)

            if (!response.isSuccessful) {
                emit(DataResult.Error(ErrorMapper.map(HttpException(response))))
                return@flow
            }

            response.body()?.source()?.use { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line()
                    if (line?.startsWith("data: ") == true) {
                        val json = line.substring(6).trim()
                        if (json != "[DONE]") {
                            try {
                                val streamResponse =
                                    jsonParser.decodeFromString<ChatCompletionResponseDTO>(json)
                                val content =
                                    streamResponse.choices.firstOrNull()?.delta?.content
                                if (!content.isNullOrEmpty()) {
                                    emit(DataResult.Success(StreamResult(content = content, modelId = request.model)))
                                }
                            } catch (e: Exception) {
                                Timber.e("Error parsing chunk: $json", e.stackTraceToString())
                            }
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            emit(DataResult.Error(e.toDomainError()))
        }
    }

    /* --------------------------------------------------------------- */
    /*  Отмена запроса – теперь просто прерываем Job, флаг не нужен   */
    /* --------------------------------------------------------------- */
    override fun cancelCurrentRequest() {
        // В репозитории можно хранить ссылку на текущий Job и вызвать `cancel()` там.
        Timber.d("Запрос отменен (Job.cancel())")
    }
}
