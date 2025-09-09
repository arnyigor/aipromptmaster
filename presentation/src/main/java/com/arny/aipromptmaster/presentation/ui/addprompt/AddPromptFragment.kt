package com.arny.aipromptmaster.presentation.ui.addprompt

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.arny.aipromptmaster.core.di.scopes.viewModelFactory
import com.arny.aipromptmaster.domain.models.PromptContent
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.databinding.FragmentAddPromptBinding
import com.arny.aipromptmaster.presentation.ui.home.PromptsFragment
import com.arny.aipromptmaster.presentation.ui.home.PromptsViewModel
import com.arny.aipromptmaster.presentation.utils.asString
import com.arny.aipromptmaster.presentation.utils.hideKeyboard
import com.arny.aipromptmaster.presentation.utils.launchWhenCreated
import com.arny.aipromptmaster.presentation.utils.toastMessage
import com.google.android.material.snackbar.Snackbar
import dagger.android.support.AndroidSupportInjection
import dagger.assisted.AssistedFactory
import kotlinx.coroutines.launch
import javax.inject.Inject

class AddPromptFragment : Fragment() {

    private var _binding: FragmentAddPromptBinding? = null
    private val binding get() = _binding!!

    @AssistedFactory
    internal interface ViewModelFactory {
        fun create(): AddPromptViewModel
    }


    @Inject
    internal lateinit var viewModelFactory: ViewModelFactory
    private val viewModel: AddPromptViewModel by viewModelFactory { viewModelFactory.create() }

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
        _binding = FragmentAddPromptBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        observeViewModel()
    }

    private fun setupViews() {
        with(binding) {
            // Setup toolbar navigation
            toolbar.setNavigationOnClickListener {
                findNavController().navigateUp()
            }

            // Setup category dropdown
            setupCategoryDropdown()

            // Setup save button
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
        (binding.dropdownCategory as? AutoCompleteTextView)?.setAdapter(categoryAdapter)
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
    }

    private fun updateUiState(state: AddPromptUiState) {
        with(binding) {
            when (state) {
                is AddPromptUiState.Loading -> {
                    fabSave.isVisible = false
                    // Show loading indicator if needed
                }
                is AddPromptUiState.Content -> {
                    fabSave.isVisible = true
                }
                is AddPromptUiState.Error -> {
                    fabSave.isVisible = true
                    showError(state.error.asString(requireContext()))
                }
            }
        }
    }

    private fun handleEvent(event: AddPromptUiEvent) {
        when (event) {
            is AddPromptUiEvent.PromptSaved -> {
                toastMessage(getString(R.string.prompt_saved_successfully))
                findNavController().navigateUp()
            }
            is AddPromptUiEvent.ValidationError -> {
                showValidationError(event.field, event.message)
            }
        }
    }

    private fun savePrompt() {
        with(binding) {
            val title = etTitle.text.toString().trim()
            val category = dropdownCategory.text.toString().trim()
            val description = etDescription.text.toString().trim()
            val contentRu = etContentRu.text.toString().trim()
            val contentEn = etContentEn.text.toString().trim()

            val promptContent = PromptContent(
                ru = contentRu,
                en = contentEn
            )

            viewModel.savePrompt(title, description, category, promptContent)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}