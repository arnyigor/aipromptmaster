package com.arny.aipromptmaster.presentation.utils

import android.os.Bundle
import androidx.recyclerview.widget.DiffUtil

object AdapterUtils {

    /**
     * Функия помогает расчитать разницу в полях данных
     * @param itemsTheSame Разные ли элементы, как правило это id
     * @param contentsTheSame Разные ли данные элементов, как правило сравнение всех данных
     * @param payloadsSame Одинаковые ли полезные данные,при изменении которых не нужно перезагружать view
     * @param payloadData Бандл для передачи полезных данных
     */
    fun <T : Any> diffItemCallback(
        itemsTheSame: (T, T) -> Boolean = { first, second -> first == second },
        contentsTheSame: (T, T) -> Boolean = { first, second -> first == second },
        payloadsSame: (T, T) -> Boolean = { first, second -> itemsTheSame(first, second) },
        payloadData: (new: T) -> Bundle? = { _ -> null },
    ): DiffUtil.ItemCallback<T> =
        object : DiffUtil.ItemCallback<T>() {
            override fun areItemsTheSame(oldItem: T, newItem: T) = itemsTheSame(oldItem, newItem)

            override fun areContentsTheSame(oldItem: T, newItem: T) =
                contentsTheSame(oldItem, newItem)

            override fun getChangePayload(oldItem: T, newItem: T): Any? =
                if (itemsTheSame(oldItem, newItem)) {
                    if (payloadsSame(oldItem, newItem)) {
                        super.getChangePayload(oldItem, newItem)
                    } else {
                        payloadData(newItem)
                    }
                } else {
                    super.getChangePayload(oldItem, newItem)
                }
        }
}