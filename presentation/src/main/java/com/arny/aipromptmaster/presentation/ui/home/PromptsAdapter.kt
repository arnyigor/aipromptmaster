package com.arny.aipromptmaster.presentation.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.presentation.databinding.ItemPromptBinding
import com.arny.aipromptmaster.presentation.utils.AdapterUtils
import com.arny.aipromptmaster.presentation.utils.addTags

class PromptsAdapter(
    private val onPromptClick: (Prompt) -> Unit,
    private val onPromptLongClick: (Prompt) -> Unit,
    private val onFavoriteClick: (Prompt) -> Unit,
) : PagingDataAdapter<Prompt, PromptsAdapter.PromptViewHolder>(
    AdapterUtils.diffItemCallback(
        itemsTheSame = { old, new -> old.id == new.id },
        payloadsSame = { old, new -> old.isFavorite == new.isFavorite },
        payloadData = { newData ->
            Bundle().apply {
                putBoolean(PARAM_FAVORITE, newData.isFavorite)
            }
        }
    )
) {

    private companion object {
        const val PARAM_FAVORITE = "PARAM_FAVORITE"
    }

    override fun onBindViewHolder(holder: PromptViewHolder, position: Int) {
        onBindViewHolder(holder, position, mutableListOf())
    }

    override fun onBindViewHolder(
        holder: PromptViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        val item = getItem(position) ?: return

        if (payloads.isEmpty()) {
            holder.bind(item)
        } else {
            val bundle = payloads[0] as? Bundle
            if (bundle != null) {
                holder.update(bundle)
            } else {
                // На случай, если payload пришел некорректный
                holder.bind(item)
            }
        }
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
            onFavoriteClick,
        )
    }

    class PromptViewHolder(
        private val binding: ItemPromptBinding,
        private val onPromptClick: (Prompt) -> Unit,
        private val onPromptLongClick: (Prompt) -> Unit,
        private val onFavoriteClick: (Prompt) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun update(bundle: Bundle) {
            if (bundle.containsKey(PARAM_FAVORITE)) {
                binding.ivFavorite.isSelected = bundle.getBoolean(PARAM_FAVORITE)
            }
        }

        fun bind(item: Prompt) {
            with(binding) {
                tvTitle.text = item.title
                chipGroupTags.addTags(item.tags)
                ivFavorite.isSelected = item.isFavorite
                ivFavorite.setOnClickListener {
                    onFavoriteClick(item)
                }
                root.setOnClickListener { onPromptClick(item) }
                root.setOnLongClickListener {
                    onPromptLongClick(item)
                    true
                }
            }
        }
    }
}
