package com.arny.aiprompts.data.db

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.arny.aiprompts.AndroidPlatform
import com.arny.aiprompts.data.db.migrations.ALL_MIGRATIONS
import kotlinx.coroutines.Dispatchers

/**
 * Синглтон инстанс базы данных для Android.
 */
private var INSTANCE: AppDatabase? = null

/**
 * Returns the database instance for Android platform.
 * This is used by Koin and other dependency injection frameworks.
 */
fun AppDatabase.Companion.getInstance(): AppDatabase {
    return INSTANCE ?: synchronized(this) {
        val context = AndroidPlatform.applicationContext
        val instance = Room.databaseBuilder<AppDatabase>(
            context = context,
            name = context.getDatabasePath(DB_NAME).absolutePath
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
    val context = AndroidPlatform.applicationContext
    return Room.databaseBuilder<AppDatabase>(
        context = context,
        name = context.getDatabasePath(AppDatabase.DB_NAME).absolutePath
    )
        .addMigrations(*ALL_MIGRATIONS)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
}
