package com.arny.aiprompts.data.db

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import java.io.File

actual fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val dbFile = File(System.getProperty("user.home"), ".aiprompts/${AppDatabase.DBNAME}")
    dbFile.parentFile?.mkdirs()

    return Room.databaseBuilder<AppDatabase>(
        name = dbFile.absolutePath
    ).setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
}
