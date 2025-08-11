package com.arny.aiprompts.data.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.arny.aiprompts.data.db.daos.PromptDao
import com.arny.aiprompts.data.db.entities.PromptEntity
import kotlinx.coroutines.Dispatchers
import java.io.File

@Database(
    entities = [PromptEntity::class], // Перечисляем все наши Entity (пока одна)
    version = 1,                      // Версия БД. Увеличивать при изменении схемы
    exportSchema = true               // Рекомендуется для отслеживания истории миграций
)
abstract class AppDatabase : RoomDatabase() {

    // Абстрактная функция, которая вернет нам наш DAO
    abstract fun promptDao(): PromptDao

    companion object {
        const val DBNAME = "AiPromptDB.db" // Название БД
        // Volatile гарантирует, что значение INSTANCE всегда будет актуальным
        // для всех потоков.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(): AppDatabase {
            // Если INSTANCE не null, возвращаем его.
            // Если null, то создаем базу данных в синхронизированном блоке.
            return INSTANCE ?: synchronized(this) {
                val instance = buildDatabase()
                INSTANCE = instance
                instance
            }
        }

        // ИСПРАВЛЕННЫЙ МЕТОД
        private fun buildDatabase(): AppDatabase {
            val dbFile = File(System.getProperty("user.home"), ".aiprompts/$DBNAME")
            dbFile.parentFile.mkdirs()

            // Создаем builder
            val builder = Room.databaseBuilder<AppDatabase>(
                name = dbFile.absolutePath,
            )

            // --- ВОТ КЛЮЧЕВОЕ ИСПРАВЛЕНИЕ ---
            // Применяем настройки, которые раньше были в getRoomDatabase()
            return builder
                .setDriver(BundledSQLiteDriver()) // Явно указываем драйвер
                .setQueryCoroutineContext(Dispatchers.IO) // Явно указываем диспатчер для запросов
                .build()
        }
    }
}