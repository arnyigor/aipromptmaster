package com.arny.aiprompts.domain.model

import kotlinx.serialization.*
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@Serializable
data class ParserConfig(
    val postContainer: String,
    @Serializable(with = SelectorsMapSerializer::class)
    val selectors: Map<String, SelectorConfig>
)

@Serializable
data class SelectorConfig(
    val selector: String,
    val attribute: String? = null,
    val regex: String? = null
)

// --- ФИНАЛЬНЫЙ РАБОЧИЙ ВАРИАНТ ---
object SelectorsMapSerializer : KSerializer<Map<String, SelectorConfig>> {

    // Используем by lazy, чтобы избежать проблем с порядком инициализации
    private val delegateSerializer by lazy {
        MapSerializer(String.serializer(), SelectorConfig.serializer())
    }

    override val descriptor: SerialDescriptor by lazy {
        delegateSerializer.descriptor
    }

    override fun deserialize(decoder: Decoder): Map<String, SelectorConfig> {
        val jsonDecoder = decoder as? JsonDecoder ?: error("Only JSON format is supported")
        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject

        return jsonObject.mapValues { (_, value) ->
            when (value) {
                is JsonPrimitive -> {
                    if (!value.isString) error("Selector config must be a string or an object")
                    SelectorConfig(selector = value.content)
                }
                is JsonObject -> {
                    Json.decodeFromJsonElement(SelectorConfig.serializer(), value)
                }
                else -> error("Unexpected type for selector config")
            }
        }
    }

    override fun serialize(encoder: Encoder, value: Map<String, SelectorConfig>) {
        // Теперь encoder и value передаются в наш лениво инициализированный делегат
        encoder.encodeSerializableValue(delegateSerializer, value)
    }
}