package com.arny.aipromptmaster.presentation.ui.models

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arny.aipromptmaster.domain.models.LLMModel
import com.arny.aipromptmaster.presentation.R

// Предположим, что у вас есть R.color.selected_background и R.layout.item_llm_model

class LlmAdapter(
    private val onItemClick: (LLMModel) -> Unit
) : ListAdapter<LLMModel, LlmAdapter.LlmViewHolder>(LlmDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LlmViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_llm_model, parent, false) // Замените на ваш layout
        return LlmViewHolder(view)
    }

    // Этот метод вызывается для полной привязки данных
    override fun onBindViewHolder(holder: LlmViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    // ЭТОТ МЕТОД ВЫЗЫВАЕТСЯ ДЛЯ ЧАСТИЧНОГО ОБНОВЛЕНИЯ
    override fun onBindViewHolder(holder: LlmViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            // Если payloads пустой, значит, DiffUtil не нашел частичных изменений.
            // Вызываем полную привязку.
            super.onBindViewHolder(holder, position, payloads)
        } else {
            // Если payloads не пустой, значит, есть частичные изменения.
            // Применяем их.
            val bundle = payloads[0] as Bundle
            holder.updateSelection(bundle)
        }
    }

    inner class LlmViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Замените на ваши View из layout'а
        private val nameTextView: TextView = itemView.findViewById(R.id.nameTextView)
        private val descriptionTextView: TextView = itemView.findViewById(R.id.descriptionTextView)
        private val container: View = itemView.findViewById(R.id.container)

        init {
            itemView.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(adapterPosition))
                }
            }
        }

        // Полная привязка данных к View
        fun bind(model: LLMModel) {
            nameTextView.text = model.name
            descriptionTextView.text = model.description
            updateSelectionState(model.isSelected)
        }

        // Частичное обновление: только состояние выбора
        fun updateSelection(bundle: Bundle) {
            if (bundle.containsKey(AdapterConstants.PAYLOAD_IS_SELECTED)) {
                val isSelected = bundle.getBoolean(AdapterConstants.PAYLOAD_IS_SELECTED)
                updateSelectionState(isSelected)
            }
        }

        private fun updateSelectionState(isSelected: Boolean) {
            val backgroundColor = if (isSelected) {
                ContextCompat.getColor(itemView.context, R.color.selected_background)
            } else {
                android.R.color.transparent
            }
            container.setBackgroundColor(backgroundColor)
        }
    }

    // DiffUtil.ItemCallback для вычисления изменений
    class LlmDiffCallback : DiffUtil.ItemCallback<LLMModel>() {
        // Проверяем, один и тот же ли это элемент (по ID)
        override fun areItemsTheSame(oldItem: LLMModel, newItem: LLMModel): Boolean {
            return oldItem.id == newItem.id
        }

        // Проверяем, изменилось ли содержимое элемента
        override fun areContentsTheSame(oldItem: LLMModel, newItem: LLMModel): Boolean {
            // data class сам сравнит все поля
            return oldItem == newItem
        }

        // Создаем Bundle с информацией о том, ЧТО ИМЕННО изменилось
        override fun getChangePayload(oldItem: LLMModel, newItem: LLMModel): Any? {
            val diffBundle = Bundle()

            // Если изменилось только состояние выбора, добавляем его в Bundle
            if (oldItem.isSelected != newItem.isSelected) {
                diffBundle.putBoolean(AdapterConstants.PAYLOAD_IS_SELECTED, newItem.isSelected)
            }

            // Если Bundle пустой, значит, изменилось что-то другое (или ничего)
            // В этом случае вернется null, и вызовется полная перерисовка (onBindViewHolder(holder, position))
            return if (diffBundle.isEmpty) null else diffBundle
        }
    }
}
