package com.arny.aipromptmaster.data.models

import com.google.gson.annotations.SerializedName

// Внешний объект запроса
data class FeedbackRequestDto(
    @SerializedName("appname")
    val appInfo: AppInfoDto,

    @SerializedName("content")
    val content: String
)

// Вложенный объект с информацией о приложении
data class AppInfoDto(
    @SerializedName("name")
    val name: String,

    @SerializedName("id")
    val id: String,

    @SerializedName("version")
    val version: String,

    @SerializedName("packagename")
    val packageName: String
)
