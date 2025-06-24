package com.arny.aipromptmaster.presentation.ui.library

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.presentation.databinding.ItemPromptBinding
import com.arny.aipromptmaster.presentation.utils.addTags

class PromptsAdapter(
    private val onPromptClick: (Prompt) -> Unit,
    private val onPromptLongClick: (Prompt) -> Unit,
    private val onFavoriteClick: (Prompt) -> Unit
) : PagingDataAdapter<Prompt, PromptsAdapter.PromptViewHolder>(PromptComparator) {

    override fun onBindViewHolder(holder: PromptViewHolder, position: Int) {
        getItem(position)?.let { holder.bind(it) }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PromptViewHolder {
        return PromptViewHolder(
            ItemPromptBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ),
            onPromptClick,
            onPromptLongClick,
            onFavoriteClick
        )
    }

    class PromptViewHolder(
        private val binding: ItemPromptBinding,
        private val onPromptClick: (Prompt) -> Unit,
        private val onPromptLongClick: (Prompt) -> Unit,
        private val onFavoriteClick: (Prompt) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(prompt: Prompt) {
            with(binding) {
                tvTitle.text = prompt.title
                chipGroupTags.addTags(root.context, prompt.tags)
                ivFavorite.isSelected = prompt.isFavorite
                ivFavorite.setOnClickListener { onFavoriteClick(prompt) }
                root.setOnClickListener { onPromptClick(prompt) }
                root.setOnLongClickListener { 
                    onPromptLongClick(prompt)
                    true
                }
            }
        }
    }

    object PromptComparator : DiffUtil.ItemCallback<Prompt>() {
        override fun areItemsTheSame(oldItem: Prompt, newItem: Prompt): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Prompt, newItem: Prompt): Boolean {
            return oldItem == newItem
        }
    }
} 