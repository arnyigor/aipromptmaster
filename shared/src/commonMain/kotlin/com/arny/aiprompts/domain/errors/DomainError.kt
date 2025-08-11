package com.arny.aiprompts.domain.errors

import androidx.annotation.StringRes
import com.arny.aiprompts.domain.strings.StringHolder

/**
 * Базовый класс для всех доменных ошибок в приложении.
 * Предоставляет унифицированный способ хранения пользовательского сообщения об ошибке.
 *
 * @property stringHolder Объект, содержащий пользовательское сообщение об ошибке.
 *                        Может быть как строковым ресурсом, так и обычным текстом.
 */
sealed class DomainError(open val stringHolder: StringHolder) : Exception() {

    /**
     * Ошибка API - используется для представления ошибок, полученных от сервера.
     *
     * @property code Код ошибки, возвращенный API.
     * @property stringHolder Объект, содержащий пользовательское сообщение об ошибке.
     * @property detailedMessage Детализированное техническое сообщение об ошибке.
     */
    data class Api(
        val code: Int,
        override val stringHolder: StringHolder,
        val detailedMessage: String
    ) : DomainError(stringHolder) {
        /**
         * Вторичный конструктор для обратной совместимости.
         * Преобразует строку в StringHolder.Text.
         *
         * @param code Код ошибки, возвращенный API.
         * @param userFriendlyMessage Пользовательское сообщение об ошибке.
         * @param detailedMessage Детализированное техническое сообщение об ошибке.
         */
        constructor(code: Int, userFriendlyMessage: String, detailedMessage: String) : this(
            code = code,
            stringHolder = StringHolder.Text(userFriendlyMessage),
            detailedMessage = detailedMessage
        )
    }

    /**
     * Ошибка сети - используется для представления ошибок сетевого соединения.
     *
     * @property stringHolder Объект, содержащий пользовательское сообщение об ошибке.
     */
    data class Network(
        override val stringHolder: StringHolder
    ) : DomainError(stringHolder) {
        /**
         * Вторичный конструктор для создания ошибки из строки.
         *
         * @param message Пользовательское сообщение об ошибке.
         */
        constructor(message: String) : this(StringHolder.Text(message))
    }

    /**
     * Локальная ошибка - используется для представления ошибок, возникающих на стороне клиента.
     *
     * @property stringHolder Объект, содержащий пользовательское сообщение об ошибке.
     */
    data class Local(
        override val stringHolder: StringHolder
    ) : DomainError(stringHolder) {
        /**
         * Вторичный конструктор для создания ошибки из строки.
         *
         * @param message Пользовательское сообщение об ошибке.
         */
        constructor(message: String) : this(StringHolder.Text(message))
    }

    /**
     * Общая ошибка - используется для представления ошибок общего назначения.
     *
     * @property stringHolder Объект, содержащий пользовательское сообщение об ошибке.
     */
    data class Generic(
        override val stringHolder: StringHolder
    ) : DomainError(stringHolder) {
        /**
         * Вторичный конструктор для создания ошибки из строки.
         * Обрабатывает null-значения, преобразуя их в пустую строку.
         *
         * @param message Пользовательское сообщение об ошибке. Может быть null.
         */
        constructor(message: String?) : this(StringHolder.Text(message ?: ""))
    }

    /**
     * Фабричные методы для создания ошибок с использованием строковых ресурсов.
     */
    companion object {
        /**
         * Создает локальную ошибку с использованием строкового ресурса.
         *
         * @param resId Идентификатор строкового ресурса.
         * @return Экземпляр Local ошибки.
         */
        fun local(@StringRes resId: Int) = Local(StringHolder.Resource(resId))

        /**
         * Создает сетевую ошибку с использованием строкового ресурса.
         *
         * @param resId Идентификатор строкового ресурса.
         * @return Экземпляр Network ошибки.
         */
        fun network(@StringRes resId: Int) = Network(StringHolder.Resource(resId))

        /**
         * Создает общую ошибку с использованием строкового ресурса.
         *
         * @param resId Идентификатор строкового ресурса.
         * @return Экземпляр Generic ошибки.
         */
        fun generic(@StringRes resId: Int) = Generic(StringHolder.Resource(resId))

        /**
         * Создает API ошибку с использованием строкового ресурса.
         *
         * @param resId Идентификатор строкового ресурса.
         * @param code Код ошибки, возвращенный API.
         * @param detailed Детализированное техническое сообщение об ошибке.
         * @return Экземпляр Api ошибки.
         */
        fun api(@StringRes resId: Int, code: Int, detailed: String) =
            Api(code, StringHolder.Resource(resId), detailed)
    }
}