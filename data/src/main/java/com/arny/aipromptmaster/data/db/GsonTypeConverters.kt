package com.arny.aipromptmaster.data.db

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class GsonTypeConverters {
    @TypeConverter
    fun fromMapToString(map: Map<String, Any>?): String? {
        return Gson().toJson(map)
    }

    @TypeConverter
    fun fromStringToMap(value: String?): Map<String, Any>? {
        return Gson().fromJson(value, object : TypeToken<Map<String, Any?>?>() {}.type)
    }
}