package com.arny.aiprompts.data.db

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.arny.aiprompts.data.db.migrations.ALL_MIGRATIONS
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * Синглтон инстанс базы данных для Desktop.
 */
private var INSTANCE: AppDatabase? = null

/**
 * Returns the database instance for Desktop platform.
 * This is used by Koin and other dependency injection frameworks.
 */
fun AppDatabase.Companion.getInstance(): AppDatabase {
    return INSTANCE ?: synchronized(this) {
        val dbFile = File(System.getProperty("user.home"), ".aiprompts/${DB_NAME}")
        dbFile.parentFile?.mkdirs()

        val instance = Room.databaseBuilder<AppDatabase>(
            name = dbFile.absolutePath
        )
            .addMigrations(*ALL_MIGRATIONS)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        INSTANCE = instance
        instance
    }
}

/**
 * Platform-specific implementation of getAppDatabase.
 */
actual fun getAppDatabase(): AppDatabase = AppDatabase.getInstance()

/**
 * Kept for backward compatibility.
 * @deprecated Use AppDatabase.getInstance() instead.
 */
actual fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val dbFile = File(System.getProperty("user.home"), ".aiprompts/${AppDatabase.DB_NAME}")
    dbFile.parentFile?.mkdirs()

    return Room.databaseBuilder<AppDatabase>(
        name = dbFile.absolutePath
    )
        .addMigrations(*ALL_MIGRATIONS)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
}
