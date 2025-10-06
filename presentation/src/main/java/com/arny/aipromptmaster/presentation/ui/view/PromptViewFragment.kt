package com.arny.aipromptmaster.presentation.ui.view

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.arny.aipromptmaster.core.di.scopes.viewModelFactory
import com.arny.aipromptmaster.domain.models.AppConstants
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.databinding.FragmentPromptViewBinding
import com.arny.aipromptmaster.presentation.utils.asString
import com.arny.aipromptmaster.presentation.utils.launchWhenCreated
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
    private val viewModel: PromptViewViewModel by viewModelFactory {
        viewModelFactory.create(args.promptId)
    }

    private var deleteMenuItem: MenuItem? = null
    private var editMenuItem: MenuItem? = null
    private var isDeleteMenuVisible = false
    private var isEditMenuVisible = false

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
        setupMenu()
        setupViews()
        observeViewModel()
        viewModel.loadPrompt()
    }

    private fun setupToolbar() {
        binding.btnFavorite.setOnClickListener {
            viewModel.toggleFavorite()
        }
    }

    private fun setupMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.prompt_view_menu, menu)
                deleteMenuItem = menu.findItem(R.id.action_delete)
                editMenuItem = menu.findItem(R.id.action_edit)
                updateMenuVisibility()
            }

            override fun onPrepareMenu(menu: Menu) {
                super.onPrepareMenu(menu)
                updateMenuVisibility()
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_edit -> {
                        val action =
                            PromptViewFragmentDirections.actionPromptViewFragmentToAddPromptFragment(
                                args.promptId
                            )
                        findNavController().navigate(action)
                        true
                    }

                    R.id.action_delete -> {
                        viewModel.showDeleteConfirmation()
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun updateMenuVisibility() {
        deleteMenuItem?.isVisible = isDeleteMenuVisible
        editMenuItem?.isVisible = isEditMenuVisible
    }

    private fun setupViews() {
        with(binding) {
            btnCopyId.setOnClickListener {
                val currentState = viewModel.uiState.value
                if (currentState is PromptViewUiState.Content) {
                    viewModel.copyContent(currentState.prompt.id, "ID промпта скопирован")
                }
            }

            btnCopyRu.setOnClickListener {
                val content = tvPromptRu.text.toString()
                viewModel.copyContent(content, "Русский промпт скопирован")
            }

            btnCopyEn.setOnClickListener {
                val content = tvPromptEn.text.toString()
                viewModel.copyContent(content, "Английский промпт скопирован")
            }

            fabCopy.setOnClickListener {
                viewModel.copyContent(getFullContent(), "Полный промпт скопирован")
            }

            chipGroupVariants.setOnCheckedStateChangeListener { group, checkedIds ->
                val selectedChip = checkedIds.firstOrNull()
                val variantIndex = when (selectedChip) {
                    R.id.chipMain -> -1
                    else -> {
                        val chip = group.findViewById<Chip>(
                            selectedChip ?: return@setOnCheckedStateChangeListener
                        )
                        chip.tag as? Int ?: -1
                    }
                }
                viewModel.selectVariant(variantIndex)
            }
        }
    }

    private fun getFullContent(): String {
        val ruText = binding.tvPromptRu.text.toString().trim()
        val enText = binding.tvPromptEn.text.toString().trim()
        return buildString {
            when {
                enText.isNotEmpty() && ruText.isNotEmpty() -> {
                    append("🇷🇺 Русский:\n")
                    append(ruText)
                    append("\n\n")
                    append("🇬🇧 English:\n")
                    append(enText)
                }
                enText.isNotEmpty() -> append(enText)
                ruText.isNotEmpty() -> append(ruText)
                else -> append("")
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
                    isDeleteMenuVisible = state.isLocal
                    isEditMenuVisible = state.isLocal
                    requireActivity().invalidateMenu()
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

            // Основной контент
            val mainChip = Chip(requireContext()).apply {
                id = R.id.chipMain
                text = getString(R.string.main_variant)
                isCheckable = true
                isChecked = state.selectedVariantIndex == -1

                // Используем Material Design 3 цвета
                setChipBackgroundColorResource(R.color.chip_background_selector)
                setTextColor(
                    ContextCompat.getColorStateList(requireContext(), R.color.chip_text_selector)
                )
            }
            addView(mainChip)

            // Варианты
            state.availableVariants.forEachIndexed { index, variant ->
                val variantChip = Chip(requireContext()).apply {
                    id = View.generateViewId()
                    text = getVariantDisplayName(variant.variantId)
                    isCheckable = true
                    isChecked = state.selectedVariantIndex == index
                    tag = index

                    setChipBackgroundColorResource(R.color.chip_background_selector)
                    setTextColor(
                        ContextCompat.getColorStateList(requireContext(), R.color.chip_text_selector)
                    )
                }
                addView(variantChip)
            }

            binding.cardVariants.visibility =
                if (state.availableVariants.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun updateTagsAndModels(prompt: com.arny.aipromptmaster.domain.models.Prompt) {
        with(binding) {
            // Теги
            chipGroupTags.removeAllViews()
            if (prompt.tags.isNotEmpty()) {
                prompt.tags.forEach { tag ->
                    val chip = Chip(requireContext()).apply {
                        text = tag
                        isClickable = false

                        // Используем secondaryContainer цвет для вторичных элементов
                        setChipBackgroundColorResource(R.color.md_theme_light_secondaryContainer)
                        setTextColor(
                            ContextCompat.getColor(
                                requireContext(),
                                R.color.md_theme_light_onSecondaryContainer
                            )
                        )
                        chipStrokeWidth = 0f
                    }
                    chipGroupTags.addView(chip)
                }
                chipGroupTags.visibility = View.VISIBLE
            } else {
                chipGroupTags.visibility = View.GONE
            }

            // Модели
            chipGroupModels.removeAllViews()
            prompt.compatibleModels.forEach { model ->
                val chip = Chip(requireContext()).apply {
                    text = model
                    isClickable = false

                    setChipBackgroundColorResource(R.color.md_theme_light_secondaryContainer)
                    setTextColor(
                        ContextCompat.getColor(
                            requireContext(),
                            R.color.md_theme_light_onSecondaryContainer
                        )
                    )
                }
                chipGroupModels.addView(chip)
            }

            modelsSection.isVisible = prompt.compatibleModels.any { it.isNotBlank() }
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
                showMessage("Вариант изменен")
            }

            is PromptViewUiEvent.ShowDeleteConfirmation -> {
                showDeleteConfirmationDialog()
            }

            is PromptViewUiEvent.PromptDeleted -> {
                val resultBundle = bundleOf(AppConstants.REQ_KEY_PROMPT_ID to event.id)
                setFragmentResult(AppConstants.REQ_KEY_PROMPT_DELETED, resultBundle)
                showMessage(getString(R.string.prompt_deleted))
            }

            is PromptViewUiEvent.NavigateBack -> {
                findNavController().navigateUp()
            }
        }
    }

    private fun getVariantDisplayName(
        variantId: com.arny.aipromptmaster.domain.models.DomainVariantId
    ): String {
        return when (variantId.type) {
            "style" -> "Стиль ${variantId.id}"
            "length" -> "Длина ${variantId.id}"
            "complexity" -> "Сложность ${variantId.id}"
            else -> "${variantId.type} ${variantId.id}"
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard =
            requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("prompt", text)
        clipboard.setPrimaryClip(clip)
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
            .setAnchorView(binding.fabCopy)
            .show()
    }

    private fun showDeleteConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_prompt_title)
            .setMessage(R.string.delete_prompt_message)
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton(R.string.delete) { dialog, _ ->
                viewModel.deletePrompt()
                dialog.dismiss()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
