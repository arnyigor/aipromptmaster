package com.arny.aipromptmaster.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FeedbackRequestDto(
    @SerialName("appname")
    val appInfo: AppInfoDto,

    @SerialName("content")
    val content: String
)

@Serializable
data class AppInfoDto(
    @SerialName("name")
    val name: String,

    @SerialName("id")
    val id: String,

    @SerialName("version")
    val version: String,

    @SerialName("packagename")
    val packageName: String
)
