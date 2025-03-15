package com.arny.aipromptmaster.presentation.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.presentation.databinding.ItemHistoryBinding
import com.arny.aipromptmaster.presentation.utils.AdapterUtils
import java.text.SimpleDateFormat
import java.util.Locale

class PromptsAdapter(
    private val onItemClick: (Prompt) -> Unit,
    private val onRestoreClick: (Prompt) -> Unit
) : PagingDataAdapter<Prompt, PromptsAdapter.ViewHolder>(
    AdapterUtils.diffItemCallback(
        itemsTheSame = { old, new -> old.id == new.id && old.modifiedAt == new.modifiedAt }
    )
) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemHistoryBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        getItem(position)?.let { prompt ->
            holder.bind(prompt)
        }
    }

    inner class ViewHolder(
        private val binding: ItemHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

        fun bind(item: Prompt) {
            binding.apply {
                // Основная информация
                tvTitle.text = item.title
                tvContent.text = when {
                    item.content.ru.isNotBlank() -> item.content.ru
                    item.content.en.isNotBlank() -> item.content.en
                    else -> ""
                }
                tvDate.text = dateFormat.format(item.modifiedAt)

                // Чипы с категорией и статусом
                chipCategory.text = item.category
                chipStatus.text = item.status

                // Обработчики нажатий
                root.setOnClickListener { onItemClick(item) }
                buttonRestore.setOnClickListener { onRestoreClick(item) }
            }
        }
    }
} 