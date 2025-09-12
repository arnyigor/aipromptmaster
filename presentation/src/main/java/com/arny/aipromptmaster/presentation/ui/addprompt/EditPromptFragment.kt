package com.arny.aipromptmaster.presentation.ui.addprompt

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.arny.aipromptmaster.core.di.scopes.viewModelFactory
import com.arny.aipromptmaster.domain.models.AppConstants
import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.models.PromptContent
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.databinding.FragmentEditPromptBinding
import com.arny.aipromptmaster.presentation.utils.asString
import com.arny.aipromptmaster.presentation.utils.launchWhenCreated
import com.arny.aipromptmaster.presentation.utils.toastMessage
import com.google.android.material.snackbar.Snackbar
import dagger.android.support.AndroidSupportInjection
import dagger.assisted.AssistedFactory
import javax.inject.Inject

class EditPromptFragment : Fragment() {

    private var _binding: FragmentEditPromptBinding? = null
    private val binding get() = _binding!!

    @AssistedFactory
    internal interface ViewModelFactory {
        fun create(): EditPromptViewModel
    }


    @Inject
    internal lateinit var viewModelFactory: ViewModelFactory
    private val viewModel: EditPromptViewModel by viewModelFactory { viewModelFactory.create() }

    private val args: EditPromptFragmentArgs by navArgs()
    private lateinit var categoryAdapter: ArrayAdapter<String>

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditPromptBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        observeViewModel()
        args.promptId?.let { promptId ->
            viewModel.loadPrompt(promptId)
        }
    }


    private fun setupViews() {
        with(binding) {
            setupCategoryDropdown()

            fabSave.setOnClickListener {
                savePrompt()
            }
        }
    }

    private fun setupCategoryDropdown() {
        categoryAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            mutableListOf()
        )
        binding.dropdownCategory.setAdapter(categoryAdapter)
    }

    private fun observeViewModel() {
        launchWhenCreated {
            viewModel.uiState.collect { state ->
                updateUiState(state)
            }
        }

        launchWhenCreated {
            viewModel.categories.collect { categories ->
                categoryAdapter.clear()
                categoryAdapter.addAll(categories)
                categoryAdapter.notifyDataSetChanged()
            }
        }

        launchWhenCreated {
            viewModel.event.collect { event ->
                handleEvent(event)
            }
        }

        launchWhenCreated {
            viewModel.editingPrompt.collect { prompt ->
                prompt?.let { fillFields(it) }
            }
        }
    }

    private fun updateUiState(state: EditPromptUiState) {
        with(binding) {
            when (state) {
                is EditPromptUiState.Loading -> {
                    fabSave.isVisible = false
                }

                is EditPromptUiState.Content -> {
                    fabSave.isVisible = true
                }

                is EditPromptUiState.Error -> {
                    fabSave.isVisible = true
                    showError(state.error.asString(requireContext()))
                }
            }
        }
    }

    private fun handleEvent(event: EditPromptUiEvent) {
        when (event) {
            is EditPromptUiEvent.PromptSaved -> {
                toastMessage(getString(R.string.prompt_saved_successfully))
                val resultBundle = bundleOf(AppConstants.REQ_KEY_PROMPT_ID to true)
                setFragmentResult(AppConstants.REQ_KEY_PROMPT_ADDED, resultBundle)
                findNavController().navigateUp()
            }

            is EditPromptUiEvent.ValidationError -> {
                showValidationError(event.field, event.message)
            }
        }
    }

    private fun savePrompt() {
        with(binding) {
            val title = etTitle.text.toString().trim()
            val category = dropdownCategory.text.toString().trim()
            val description = etDescription.text.toString().trim()
            val tagsText = etTags.text.toString().trim()
            val contentRu = etContentRu.text.toString().trim()
            val contentEn = etContentEn.text.toString().trim()

            val tags = if (tagsText.isNotEmpty()) {
                tagsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            } else {
                emptyList()
            }

            val promptContent = PromptContent(
                ru = contentRu,
                en = contentEn
            )

            viewModel.savePrompt(title, description, category, promptContent, tags)
        }
    }

    private fun showValidationError(field: ValidationField, message: String) {
        val view = when (field) {
            ValidationField.TITLE -> binding.etTitle
            ValidationField.CATEGORY -> binding.dropdownCategory
            ValidationField.CONTENT_RU -> binding.etContentRu
            ValidationField.CONTENT_EN -> binding.etContentEn
        }
        view.error = message
        view.requestFocus()
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun fillFields(prompt: Prompt) {
        with(binding) {
            etTitle.setText(prompt.title)
            etDescription.setText(prompt.description)
            dropdownCategory.setText(prompt.category, false)
            etContentRu.setText(prompt.content.ru)
            etContentEn.setText(prompt.content.en)
            etTags.setText(prompt.tags.joinToString(", "))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}