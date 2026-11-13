package com.arny.aiprompts.domain.strings

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

sealed interface StringHolder {

    @JvmInline
    value class Resource(val resource: StringResource) : StringHolder

    @JvmInline
    value class Text(val value: String?) : StringHolder

    data class Formatted(
        val resource: StringResource,
        val formatArgs: List<Any>
    ) : StringHolder

    data class Plural(
        val resource: PluralStringResource,
        val quantity: Int,
        val formatArgs: List<Any> = emptyList()
    ) : StringHolder
}


@OptIn(ExperimentalResourceApi::class)
@Composable
fun StringHolder.asString(): String {
    return when (this) {
        is StringHolder.Resource -> stringResource(resource = resource)
        is StringHolder.Text -> value ?: ""
        is StringHolder.Formatted -> stringResource(resource = resource, formatArgs = formatArgs.toTypedArray())
        is StringHolder.Plural -> pluralStringResource(
            resource = resource,
            quantity = quantity,
            formatArgs = formatArgs.toTypedArray()
        )
    }
}