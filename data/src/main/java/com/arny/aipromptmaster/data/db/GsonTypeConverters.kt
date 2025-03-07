package com.arny.aipromptmaster.data.db

import androidx.room.TypeConverter
import com.arny.aipromptmaster.domain.models.PromptCategory
import com.arny.aipromptmaster.domain.models.PromptStatus
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Date

class GsonTypeConverters {
    private val gson = Gson()
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? = date?.time

    @TypeConverter
    fun fromStringList(value: String?): List<String> =
        value?.split(",")?.map { it.trim() } ?: emptyList()

    @TypeConverter
    fun toStringList(list: List<String>): String =
        list.joinToString(",")

    @TypeConverter
    fun mapToString(map: Map<String, Any>?): String =
        gson.toJson(map ?: emptyMap<String, Any>())

    @TypeConverter
    fun stringToMap(value: String): Map<String, Any> {
        val type = object : TypeToken<Map<String, Any>>() {}.type
        return try {
            gson.fromJson(value, type)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    @TypeConverter
    fun promptStatusToString(status: PromptStatus): String = status.name

    @TypeConverter
    fun stringToPromptStatus(value: String): PromptStatus =
        PromptStatus.valueOf(value)

    @TypeConverter
    fun promptCategoryToString(category: PromptCategory): String = category.name

    @TypeConverter
    fun stringToPromptCategory(value: String): PromptCategory =
        PromptCategory.valueOf(value)
}