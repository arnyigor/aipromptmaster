package com.arny.aipromptmaster.presentation.ui.modelsview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.databinding.ItemModelBinding
import com.arny.aipromptmaster.presentation.ui.models.formatDescription
import com.arny.aipromptmaster.presentation.ui.models.getModelIconUrlWithFallback
import com.arny.aipromptmaster.presentation.ui.modelsview.LlmModelAdapter.LlmModelViewHolder
import com.arny.aipromptmaster.presentation.utils.AdapterUtils
import com.arny.aipromptmaster.presentation.utils.setImgFromUrl

class LlmModelAdapter(
    private val onItemClick: (modelId: String) -> Unit,
    private val onFavoriteClick: (modelId: String) -> Unit // Новый callback
) : ListAdapter<LlmModel, LlmModelViewHolder>(
    AdapterUtils.diffItemCallback(
        itemsTheSame = { old, new -> old.id == new.id },
        payloadsSame = { old, new ->
            isPayloadsSame(old, new)
        },
        payloadData = { newData ->
            Bundle().apply {
                putBoolean(ARG_SELECTED, newData.isSelected)
                putBoolean(ARG_FAVORITE, newData.isFavorite)
            }
        }
    )
) {

    private companion object {
        const val ARG_SELECTED = "ARG_SELECTED"
        const val ARG_FAVORITE = "ARG_FAVORITE"

        fun isPayloadsSame(old: LlmModel, new: LlmModel): Boolean =
            old.id == new.id &&
                    old.isSelected == new.isSelected &&
                    old.isFavorite == new.isFavorite
    }

    override fun onBindViewHolder(holder: LlmModelViewHolder, position: Int) {
        holder.bind()
    }

    override fun onBindViewHolder(
        holder: LlmModelViewHolder,
        position: Int,
        payloads: MutableList<Any?>
    ) {
        val item = getItem(position)
        if (payloads.isEmpty() || payloads[0] !is Bundle) {
            holder.bind(item)
        } else {
            holder.update(payloads[0] as? Bundle)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LlmModelViewHolder =
        LlmModelViewHolder(
            ItemModelBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    inner class LlmModelViewHolder(private val binding: ItemModelBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun update(bundle: Bundle?) {
            bundle?.let { payload ->
                // Обновляем только измененные поля
                if (payload.containsKey(ARG_SELECTED)) {
                    val isSelected = payload.getBoolean(ARG_SELECTED)
                    binding.switchModelActive.isChecked = isSelected
                }

                if (payload.containsKey(ARG_FAVORITE)) {
                    val isFavorite = payload.getBoolean(ARG_FAVORITE)
                    updateFavoriteButton(isFavorite, animate = true)
                }
            }
        }

        fun bind(payloads: LlmModel? = null) {
            val item: LlmModel = payloads ?: getItem(bindingAdapterPosition)
            with(binding) {
                tvModelName.text = item.name
                tvModelProvider.text = item.formatDescription(binding.root.context)
                switchModelActive.isChecked = item.isSelected
                ivProviderIcon.setImgFromUrl(getModelIconUrlWithFallback(item.id))

                // Настраиваем избранное
                updateFavoriteButton(item.isFavorite, animate = false)

                // Обработчики кликов
                switchModelActive.setOnClickListener {
                    onItemClick(item.id)
                }

                btnFavorite.setOnClickListener {
                    // Добавляем анимацию нажатия
                    animateFavoriteClick()
                    onFavoriteClick(item.id)
                }
            }
        }

        private fun updateFavoriteButton(isFavorite: Boolean, animate: Boolean) {
            binding.btnFavorite.isSelected = isFavorite
            binding.btnFavorite.setIconResource(
                if (isFavorite) R.drawable.ic_favorite_filled
                else R.drawable.ic_favorite_border
            )

            // Анимация изменения состояния
            if (animate) {
                binding.btnFavorite.animate()
                    .scaleX(1.2f)
                    .scaleY(1.2f)
                    .setDuration(150)
                    .withEndAction {
                        binding.btnFavorite.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(150)
                            .start()
                    }
                    .start()
            }
        }

        private fun animateFavoriteClick() {
            // Haptic feedback
            binding.btnFavorite.performHapticFeedback(
                android.view.HapticFeedbackConstants.VIRTUAL_KEY
            )

            // Pulse анимация
            binding.btnFavorite.animate()
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(100)
                .withEndAction {
                    binding.btnFavorite.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                }
                .start()
        }
    }
}
