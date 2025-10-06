package com.arny.aipromptmaster.domain.repositories

import android.net.Uri
import com.arny.aipromptmaster.domain.models.FileAttachment
import com.arny.aipromptmaster.domain.services.FileProcessingResult
import kotlinx.coroutines.flow.Flow

/**
 * Репозиторий для управления временными файлами, используемыми в чате.
 *
 * Отвечает за сохранение, получение, обновление и удаление временных файлов,
 * а также за обработку файлов из URI (например, выбранных пользователем через системный файл-менеджер).
 */
interface IFileRepository {

    /**
     * Сохраняет временный файл в локальное хранилище.
     *
     * @param fileAttachment данные файла для сохранения
     * @return уникальный идентификатор сохранённого файла
     */
    suspend fun saveTemporaryFile(fileAttachment: FileAttachment): String

    /**
     * Получает временный файл по его идентификатору.
     *
     * @param id уникальный идентификатор файла
     * @return [FileAttachment], если файл найден, иначе `null`
     */
    suspend fun getTemporaryFile(id: String): FileAttachment?

    /**
     * Обновляет содержимое временного файла по его идентификатору.
     *
     * @param id идентификатор файла
     * @param updatedContent новое содержимое файла
     * @return `true`, если обновление успешно, иначе `false`
     */
    suspend fun updateTemporaryFile(id: String, updatedContent: String): Boolean

    /**
     * Удаляет временный файл по его идентификатору.
     *
     * @param id идентификатор файла для удаления
     */
    suspend fun deleteTemporaryFile(id: String)

    /**
     * Возвращает поток всех временных файлов.
     *
     * @return [Flow] списка всех временных файлов, который обновляется при изменениях
     */
    fun getAllTemporaryFiles(): Flow<List<FileAttachment>>

    /**
     * Обрабатывает файл из URI (например, из ContentResolver) и возвращает результат в виде потока.
     *
     * Используется для пошаговой обработки (чтение, парсинг, превью), с поддержкой прогресса.
     *
     * @param uri URI файла, полученный от системы (например, через Intent)
     * @return [Flow] результатов обработки: начало, прогресс, завершение или ошибка
     */
    fun processFileFromUri(uri: Uri): Flow<FileProcessingResult>
}