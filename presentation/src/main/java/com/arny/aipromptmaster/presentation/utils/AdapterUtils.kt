package com.arny.aipromptmaster.presentation.utils

import androidx.recyclerview.widget.DiffUtil

object AdapterUtils {
    fun <T : Any> diffItemCallback(
        itemsTheSame: (T, T) -> Boolean,
        contentsTheSame: (T, T) -> Boolean = { item1, item2 -> item1 == item2 }
    ): DiffUtil.ItemCallback<T> = object : DiffUtil.ItemCallback<T>() {
        override fun areItemsTheSame(oldItem: T, newItem: T): Boolean = itemsTheSame(oldItem, newItem)
        override fun areContentsTheSame(oldItem: T, newItem: T): Boolean = contentsTheSame(oldItem, newItem)
    }
}