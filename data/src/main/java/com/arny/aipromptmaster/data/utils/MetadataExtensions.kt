package com.arny.aipromptmaster.data.utils

import com.arny.aipromptmaster.domain.models.PromptMetadata
import com.google.gson.Gson

private val gson = Gson()

fun PromptMetadata.toJson(): String = gson.toJson(this)

fun String?.toPromptMetadata(): PromptMetadata = try {
    gson.fromJson(this, PromptMetadata::class.java)
} catch (e: Exception) {
    PromptMetadata()
}