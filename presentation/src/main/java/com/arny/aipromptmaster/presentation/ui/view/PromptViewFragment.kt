package com.arny.aipromptmaster.presentation.ui.view

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.navArgs
import com.arny.aipromptmaster.core.di.scopes.viewModelFactory
import com.arny.aipromptmaster.domain.models.AppConstants
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.databinding.FragmentPromptViewBinding
import com.arny.aipromptmaster.presentation.utils.asString
import com.arny.aipromptmaster.presentation.utils.launchWhenCreated
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialFadeThrough
import dagger.android.support.AndroidSupportInjection
import dagger.assisted.AssistedFactory
import io.noties.markwon.Markwon
import javax.inject.Inject

class PromptViewFragment : Fragment() {
    private var _binding: FragmentPromptViewBinding? = null
    private val binding get() = _binding!!

    private val args: PromptViewFragmentArgs by navArgs()

    @AssistedFactory
    internal interface ViewModelFactory {
        fun create(id: String): PromptViewViewModel
    }

    @Inject
    lateinit var markwon: Markwon

    @Inject
    internal lateinit var viewModelFactory: ViewModelFactory
    private val viewModel: PromptViewViewModel by viewModelFactory { viewModelFactory.create(args.promptId) }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialFadeThrough()
        exitTransition = MaterialFadeThrough()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPromptViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupViews()
        observeViewModel()
        viewModel.loadPrompt()
    }

    private fun setupToolbar() {
        binding.btnFavorite.setOnClickListener {
            viewModel.toggleFavorite()
        }
    }

    private fun setupViews() {
        with(binding) {
            // Копирование ID промпта
            btnCopyId.setOnClickListener {
                val currentState = viewModel.uiState.value
                if (currentState is PromptViewUiState.Content) {
                    viewModel.copyContent(currentState.prompt.id, "ID промпта скопирован")
                }
            }

            // Копирование русского текста
            btnCopyRu.setOnClickListener {
                val content = tvPromptRu.text.toString()
                viewModel.copyContent(content, "Русский промпт скопирован")
            }

            // Копирование английского текста
            btnCopyEn.setOnClickListener {
                val content = tvPromptEn.text.toString()
                viewModel.copyContent(content, "Английский промпт скопирован")
            }

            // Копирование всего контента
            fabCopy.setOnClickListener {
                val fullContent = buildString {
                    append("🇷🇺 Русский:\n")
                    append(tvPromptRu.text)
                    append("\n\n")
                    append("🇬🇧 English:\n")
                    append(tvPromptEn.text)
                }
                viewModel.copyContent(fullContent, "Полный промпт скопирован")
            }

            // Обработка выбора варианта
            chipGroupVariants.setOnCheckedStateChangeListener { group, checkedIds ->
                val selectedChip = checkedIds.firstOrNull()
                val variantIndex = when (selectedChip) {
                    R.id.chipMain -> -1
                    else -> {
                        // Найти индекс выбранного варианта по ID чипа
                        val chip = group.findViewById<Chip>(selectedChip ?: return@setOnCheckedStateChangeListener)
                        chip.tag as? Int ?: -1
                    }
                }
                viewModel.selectVariant(variantIndex)
            }
        }
    }

    private fun observeViewModel() {
        launchWhenCreated {
            viewModel.uiState.collect { state ->
                updateUiState(state)
            }
        }

        launchWhenCreated {
            viewModel.uiEvent.collect { event ->
                handleUiEvent(event)
            }
        }
    }

    private fun updateUiState(state: PromptViewUiState) {
        with(binding) {
            when (state) {
                is PromptViewUiState.Content -> {
                    updatePromptContent(state)
                    updateVariants(state)
                    updateTagsAndModels(state.prompt)
                }

                is PromptViewUiState.Error -> {
                    showMessage(state.stringHolder.asString(requireContext()))
                }

                else -> Unit
            }
        }
    }

    private fun updatePromptContent(state: PromptViewUiState.Content) {
        with(binding) {
            tvTitle.text = state.prompt.title
            tvPromptRu.text = markwon.toMarkdown(state.currentContent.ru)
            tvPromptEn.text = markwon.toMarkdown(state.currentContent.en)
            tvPromptId.text = state.prompt.id
            btnFavorite.isSelected = state.prompt.isFavorite
        }
    }

    private fun updateVariants(state: PromptViewUiState.Content) {
        with(binding.chipGroupVariants) {
            removeAllViews()

            // Добавляем чип для основного контента
            val mainChip = Chip(requireContext()).apply {
                id = R.id.chipMain
                text = getString(R.string.main_variant)
                isCheckable = true
                isChecked = state.selectedVariantIndex == -1
                setChipBackgroundColorResource(R.color.chip_background_selector)
                setTextColor(resources.getColorStateList(R.color.chip_text_selector, null))
            }
            addView(mainChip)

            // Добавляем чипы для каждого варианта
            state.availableVariants.forEachIndexed { index, variant ->
                val variantChip = Chip(requireContext()).apply {
                    id = View.generateViewId()
                    text = getVariantDisplayName(variant.variantId)
                    isCheckable = true
                    isChecked = state.selectedVariantIndex == index
                    tag = index
                    setChipBackgroundColorResource(R.color.chip_background_selector)
                    setTextColor(resources.getColorStateList(R.color.chip_text_selector, null))
                }
                addView(variantChip)
            }

            // Показываем группу вариантов только если есть варианты
            binding.cardVariants.visibility = if (state.availableVariants.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun updateTagsAndModels(prompt: com.arny.aipromptmaster.domain.models.Prompt) {
        with(binding) {
            // Обновляем теги
            chipGroupTags.removeAllViews()
            if (prompt.tags.isNotEmpty()) {
                prompt.tags.forEach { tag ->
                    val chip = Chip(requireContext()).apply {
                        text = tag
                        isClickable = false
                        setChipBackgroundColorResource(R.color.chip_background_secondary)
                        setTextColor(resources.getColorStateList(R.color.chip_text_secondary, null))
                        chipStrokeWidth = 0f
                    }
                    chipGroupTags.addView(chip)
                }
                chipGroupTags.visibility = View.VISIBLE
            } else {
                chipGroupTags.visibility = View.GONE
            }

            // Обновляем модели
            chipGroupModels.removeAllViews()
            prompt.compatibleModels.forEach { model ->
                val chip = Chip(requireContext()).apply {
                    text = model
                    isClickable = false
                    setChipBackgroundColorResource(R.color.chip_background_secondary)
                    setTextColor(resources.getColorStateList(R.color.chip_text_secondary, null))
                }
                chipGroupModels.addView(chip)
            }

            // Скрываем секцию совместимых моделей, если список пустой
            modelsSection.visibility = if (prompt.compatibleModels.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun handleUiEvent(event: PromptViewUiEvent) {
        when (event) {
            is PromptViewUiEvent.PromptUpdated -> {
                val resultBundle = bundleOf(AppConstants.REQ_KEY_PROMPT_ID to event.id)
                setFragmentResult(AppConstants.REQ_KEY_PROMPT_VIEW_FAV, resultBundle)
            }

            is PromptViewUiEvent.ShowError -> {
                showMessage(event.stringHolder?.asString(requireContext()).orEmpty())
            }

            is PromptViewUiEvent.CopyContent -> {
                copyToClipboard(event.content)
                showMessage(event.label)
            }

            is PromptViewUiEvent.VariantSelected -> {
                // Можно добавить анимацию или другие эффекты при выборе варианта
                showMessage("Вариант изменен")
            }
        }
    }

    private fun getVariantDisplayName(variantId: com.arny.aipromptmaster.domain.models.DomainVariantId): String {
        return when (variantId.type) {
            "style" -> "Стиль ${variantId.id}"
            "length" -> "Длина ${variantId.id}"
            "complexity" -> "Сложность ${variantId.id}"
            else -> "${variantId.type} ${variantId.id}"
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("prompt", text)
        clipboard.setPrimaryClip(clip)
    }

    private fun showMessage(message: String) {
        Snackbar.make(
            binding.root,
            message,
            Snackbar.LENGTH_SHORT
        ).setAnchorView(binding.fabCopy)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}