package com.arny.aiprompts.data.db

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.arny.aiprompts.AndroidPlatform
import kotlinx.coroutines.Dispatchers

actual fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val context = AndroidPlatform.applicationContext
    return Room.databaseBuilder<AppDatabase>(
        context = context,
        name = context.getDatabasePath(AppDatabase.DBNAME).absolutePath
    ).setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
}
