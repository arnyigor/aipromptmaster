package com.arny.aipromptmaster.data.repositories

import com.arny.aipromptmaster.R
import com.arny.aipromptmaster.data.models.ApiErrorResponse
import com.arny.aipromptmaster.data.models.toDomainError
import com.arny.aipromptmaster.domain.models.errors.DomainError
import com.arny.aipromptmaster.domain.models.strings.StringHolder
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Единственная точка маппинга `Throwable → DomainError`.
 *
 * Внутри делегируем конкретным объектам:
 * - NetworkErrorMapper – сетевые исключения
 * - HttpErrorParser   – ошибки HTTP (4xx/5xx)
 * - LocalErrorMapper  – всё остальное
 */
object ErrorMapper {

    /** Публичный API для маппинга Throwable → DomainError */
    fun map(t: Throwable): DomainError = when (t) {
        is CancellationException -> NetworkErrorMapper.cancelled()
        is UnknownHostException,
        is ConnectException,
        is SocketTimeoutException,
        is IOException ->
            NetworkErrorMapper.network(t)

        is HttpException -> HttpErrorParser.parse(t)
        else -> LocalErrorMapper.local(t)
    }

    /** Расширение, удобное в репозитории */
    fun Throwable.toDomainError(): DomainError = map(this)

    /* ------------------------------------------------------------------ */
    /*  Сетевые ошибки                                                    */
    /* ------------------------------------------------------------------ */
    private object NetworkErrorMapper {
        private const val DEFAULT_MSG = "Сетевая ошибка"

        fun cancelled() =
            DomainError.Network(StringHolder.Text("Запрос отменён"))

        fun network(t: Throwable) =
            DomainError.Network(
                StringHolder.Text(t.localizedMessage ?: DEFAULT_MSG)
            )
    }

    /* ------------------------------------------------------------------ */
    /*  Локальные ошибки                                                  */
    /* ------------------------------------------------------------------ */
    private object LocalErrorMapper {
        fun local(t: Throwable) = DomainError.Local(
            StringHolder.Text(t.localizedMessage ?: "Неизвестная ошибка")
        )
    }

    /* ------------------------------------------------------------------ */
    /*  HTTP‑парсер                                                        */
    /* ------------------------------------------------------------------ */
    private object HttpErrorParser {

        /** JSON‑парсер, совместимый с любыми полями */
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true          // не падаем при «не‑приведенных» JSON
        }

        fun parse(e: HttpException): DomainError {
            val bodyStr = e.response()?.errorBody()?.string()
            return if (bodyStr.isNullOrBlank()) {
                fallback(e)
            } else {
                tryParse(bodyStr, e)
            }
        }

        /** Попытка распарсить ApiErrorResponse */
        private fun tryParse(body: String, exception: HttpException): DomainError =
            try {
                val err = json.decodeFromString<ApiErrorResponse>(body)
                err.error.toDomainError()
            } catch (e: Exception) {
                fallback(exception)
            }

        /** Базовый вариант – «Ошибка сервера» */
        private fun fallback(e: HttpException): DomainError =
            DomainError.Api(
                e.code(),
                StringHolder.Text("Ошибка сервера (${e.code()})"),
                e.message() ?: "HTTP ${e.code()}"
            )
    }
}
