package com.arny.aiprompts.presentation.navigation

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.io.File

@Serializable
sealed interface ScreenConfig {
    @Serializable
    data object PromptList : ScreenConfig

    @Serializable
    data class PromptDetails(
        val promptId: String
    ) : ScreenConfig

    @Serializable
    data object Scraper : ScreenConfig

    @Serializable
    data class Importer(
        // Передаем список путей к файлам.
        // Используем кастомный сериализатор для поддержки List<File> в Decompose.
        @Serializable(with = FilePathListSerializer::class)
        val files: List<File>
    ) : ScreenConfig

    @Serializable
    data object LLM : ScreenConfig
}

// MainComponent navigation configuration
@Serializable
sealed interface MainConfig {
    @Serializable
    data object Prompts : MainConfig

    @Serializable
    data class PromptDetails(val promptId: String) : MainConfig

    @Serializable
    data object Chat : MainConfig

    @Serializable
    data object Import : MainConfig

    @Serializable
    data object Settings : MainConfig
}

/**
 * Кастомный сериализатор для List<File>, который преобразует
 * список файлов в список их путей (строк) и обратно.
 * Это необходимо для сохранения состояния навигации.
 */
object FilePathListSerializer : KSerializer<List<File>> {
    private val delegate = ListSerializer(String.serializer())

    override val descriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: List<File>) {
        encoder.encodeSerializableValue(delegate, value.map { it.absolutePath })
    }

    override fun deserialize(decoder: Decoder): List<File> {
        return decoder.decodeSerializableValue(delegate).map { File(it) }
    }
}