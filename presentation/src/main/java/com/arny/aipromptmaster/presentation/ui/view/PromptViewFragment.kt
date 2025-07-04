package com.arny.aipromptmaster.presentation.ui.view

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.navArgs
import com.arny.aipromptmaster.core.di.scopes.viewModelFactory
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.databinding.FragmentPromptViewBinding
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import dagger.android.support.AndroidSupportInjection
import dagger.assisted.AssistedFactory
import io.noties.markwon.Markwon
import kotlinx.coroutines.launch
import javax.inject.Inject

class PromptViewFragment : Fragment() {
    private var _binding: FragmentPromptViewBinding? = null
    private val binding get() = _binding!!
    private val args: PromptViewFragmentArgs by navArgs()

    @AssistedFactory
    internal interface ViewModelFactory {
        fun create(id: String): PromptViewViewModel
    }

    // Инжектируем наш синглтон Markwon
    @Inject
    lateinit var markwon: Markwon

    @Inject
    internal lateinit var viewModelFactory: ViewModelFactory
    private val viewModel: PromptViewViewModel by viewModelFactory { viewModelFactory.create(args.promptId) }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
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
        requireActivity().setTitle(R.string.prompt_details)
        setupViews()
        observeViewModel()
        viewModel.loadPrompt()
    }

    private fun setupViews() {
        with(binding) {
            // Копирование русского текста
            btnCopyRu.setOnClickListener {
                copyToClipboard(tvPromptRu.text.toString())
            }

            // Копирование английского текста
            btnCopyEn.setOnClickListener {
                copyToClipboard(tvPromptEn.text.toString())
            }

            // Копирование обоих текстов
            fabCopy.setOnClickListener {
                val fullText = buildString {
                    append("🇷🇺 Русский:\n")
                    append(tvPromptRu.text)
                    append("\n\n")
                    append("🇬🇧 English:\n")
                    append(tvPromptEn.text)
                }
                copyToClipboard(fullText)
            }

            // Избранное
            btnFavorite.setOnClickListener {
                viewModel.toggleFavorite()
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUiState(state)
                }
            }
        }
    }

    private fun updateUiState(state: PromptViewUiState) {
        with(binding) {
            when (state) {
                is PromptViewUiState.Content -> {
                    val prompt = state.prompt
                    tvTitle.text = prompt.title
                    tvPromptRu.text = markwon.toMarkdown(prompt.content.ru)
                    tvPromptEn.text = markwon.toMarkdown(prompt.content.en)
                    btnFavorite.isSelected = prompt.isFavorite

                    // Очищаем и добавляем теги
                    chipGroupTags.removeAllViews()
                    prompt.tags.forEach { tag ->
                        val chip = Chip(requireContext()).apply {
                            text = tag
                            isClickable = false
                            setTextColor(ContextCompat.getColor(context, R.color.textColorPrimary))
                        }
                        chipGroupTags.addView(chip)
                    }

                    // Очищаем и добавляем модели
                    chipGroupModels.removeAllViews()
                    prompt.compatibleModels.forEach { model ->
                        val chip = Chip(requireContext()).apply {
                            text = model
                            isClickable = false
                            setTextColor(ContextCompat.getColor(context, R.color.textColorPrimary))
                        }
                        chipGroupModels.addView(chip)
                    }
                }

                is PromptViewUiState.Error -> {
                    showError(state.message)
                }

                else -> Unit
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard =
            requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("prompt", text)
        clipboard.setPrimaryClip(clip)
        showMessage(getString(R.string.prompt_copied))
    }

    private fun showMessage(message: String) {
        Snackbar.make(
            binding.root,
            message,
            Snackbar.LENGTH_SHORT
        ).setAnchorView(binding.fabCopy)
            .show()
    }

    private fun showError(message: String) {
        showMessage(message)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}