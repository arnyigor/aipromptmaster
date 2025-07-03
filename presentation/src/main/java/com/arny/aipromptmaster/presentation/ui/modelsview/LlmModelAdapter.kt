package com.arny.aipromptmaster.presentation.ui.modelsview

// LlmModelAdapter.kt
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.presentation.databinding.ItemModelBinding
import com.arny.aipromptmaster.presentation.ui.models.formatDescription
import com.arny.aipromptmaster.presentation.ui.models.getModelIconUrlWithFallback
import com.arny.aipromptmaster.presentation.ui.modelsview.LlmModelAdapter.LlmModelViewHolder
import com.arny.aipromptmaster.presentation.utils.AdapterUtils
import com.arny.aipromptmaster.presentation.utils.setImgFromUrl

class LlmModelAdapter(
    private val onItemClick: (modelId: String) -> Unit
) : ListAdapter<LlmModel, LlmModelViewHolder>(
    AdapterUtils.diffItemCallback(
        itemsTheSame = { old, new -> old.id == new.id },
        payloadsSame = { old, new ->
            isPayloadsSame(old, new)
        },
        payloadData = { newData ->
            Bundle().apply {
                putBoolean(ARG_SELECTED, newData.isSelected)
            }
        }
    )
) {

    private companion object {
        const val ARG_SELECTED = "ARG_ANIM"
        fun isPayloadsSame(
            old: LlmModel,
            aNew: LlmModel
        ): Boolean = old.id == aNew.id &&
                old.isSelected == aNew.isSelected
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
            bundle?.getBoolean(ARG_SELECTED)?.let { isSelected ->
                binding.switchModelActive.isChecked = isSelected
            }
        }

        fun bind(payloads: LlmModel? = null) {
            val item: LlmModel = payloads ?: getItem(bindingAdapterPosition)
            with(binding) {
                tvModelName.text = item.name
                tvModelProvider.text = item.formatDescription(binding.root.context)
                switchModelActive.isChecked = item.isSelected
                ivProviderIcon.setImgFromUrl(getModelIconUrlWithFallback(item.id))
                clContainer.setOnClickListener {
                    onItemClick(item.id)
                }
            }
        }
    }
}
