package com.arny.aipromptmaster.presentation.ui.editprompt

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.arny.aipromptmaster.core.di.scopes.viewModelFactory
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.databinding.FragmentEditSystemPromptBinding
import com.arny.aipromptmaster.presentation.utils.launchWhenCreated
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.android.support.AndroidSupportInjection
import dagger.assisted.AssistedFactory
import javax.inject.Inject

class EditSystemPromptFragment : Fragment() {

    private var _binding: FragmentEditSystemPromptBinding? = null
    private val binding get() = _binding!!

    @AssistedFactory
    internal interface ViewModelFactory {
        fun create(): EditSystemPromptViewModel
    }

    @Inject
    internal lateinit var viewModelFactory: ViewModelFactory
    private val viewModel: EditSystemPromptViewModel by viewModelFactory { viewModelFactory.create() }

    private val args: EditSystemPromptFragmentArgs by navArgs()

    // ✅ НОВЫЙ ФЛАГ: отслеживает, был ли текст установлен пользователем
    private var isUserEdited = false

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditSystemPromptBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✅ ПОРЯДОК ВАЖЕН: сначала setupViews, потом observeViewModel
        setupViews()
        observeViewModel()

        // ✅ Устанавливаем conversationId для загрузки промпта
        viewModel.setConversationId(args.conversationId)

        android.util.Log.d("EditSystemPrompt", "ConversationId: ${args.conversationId}")
    }

    private fun setupViews() {
        with(binding) {

            // Кнопка сохранения
            btnSave.setOnClickListener {
                saveSystemPrompt()
            }

            // Кнопка очистки
            btnClear.setOnClickListener {
                showClearConfirmationDialog()
            }

            // ✅ TextWatcher для отслеживания изменений пользователем
            etSystemPrompt.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val length = s?.length ?: 0
                    updateCounterColor(length)

                    // ✅ Помечаем, что пользователь редактировал текст
                    if (etSystemPrompt.isFocused) {
                        isUserEdited = true
                    }
                }

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })

            // Примеры промптов
            setupExamples()
        }
    }

    private fun setupExamples() {
        binding.example1.setOnClickListener {
            val prompt = getString(R.string.example_prompt_android_developer)
            showInsertPromptDialog(prompt)
        }

        binding.example2.setOnClickListener {
            val prompt = getString(R.string.example_prompt_code_reviewer)
            showInsertPromptDialog(prompt)
        }

        binding.example3.setOnClickListener {
            val prompt = getString(R.string.example_prompt_programming_tutor)
            showInsertPromptDialog(prompt)
        }
    }

    private fun showInsertPromptDialog(prompt: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.insert_example_prompt_title)
            .setMessage(R.string.insert_example_prompt_message)
            .setPositiveButton(R.string.action_replace) { _, _ ->
                binding.etSystemPrompt.setText(prompt)
                binding.etSystemPrompt.setSelection(prompt.length)
                isUserEdited = true
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateCounterColor(length: Int) {
        val textInputLayout = binding.textInputLayout
        val color = when {
            length > MAX_CHARACTERS -> ContextCompat.getColor(requireContext(), R.color.red_error)
            length > MAX_CHARACTERS * 0.9 -> ContextCompat.getColor(requireContext(), R.color.warning)
            else -> ContextCompat.getColor(requireContext(), com.google.android.material.R.color.material_on_surface_emphasis_medium)
        }
        textInputLayout.setCounterTextColor(android.content.res.ColorStateList.valueOf(color))
    }

    private fun observeViewModel() {
        launchWhenCreated {
            viewModel.uiState.collect { state ->
                updateUiState(state)
            }
        }

        launchWhenCreated {
            viewModel.event.collect { event ->
                handleEvent(event)
            }
        }

        // ✅ ИСПРАВЛЕНО: упрощенная логика обновления текста
        launchWhenCreated {
            viewModel.currentPrompt.collect { prompt ->
                // Обновляем текст только если пользователь еще не редактировал поле
                if (!isUserEdited && binding.etSystemPrompt.text.toString() != prompt) {
                    android.util.Log.d("EditSystemPrompt", "Загружен промпт: $prompt")
                    binding.etSystemPrompt.setText(prompt)
                    if (prompt.isNotEmpty()) {
                        binding.etSystemPrompt.setSelection(prompt.length)
                    }
                }
            }
        }
    }

    private fun updateUiState(state: EditSystemPromptUiState) {
        with(binding) {
            when (state) {
                is EditSystemPromptUiState.Loading -> {
                    btnSave.isEnabled = false
                    btnClear.isEnabled = false
                }

                is EditSystemPromptUiState.Content -> {
                    btnSave.isEnabled = true
                    btnClear.isEnabled = true
                }

                is EditSystemPromptUiState.Error -> {
                    btnSave.isEnabled = true
                    btnClear.isEnabled = true
                    showError(state.message)
                }
            }
        }
    }

    private fun handleEvent(event: EditSystemPromptUiEvent) {
        when (event) {
            is EditSystemPromptUiEvent.PromptSaved -> {
                Snackbar.make(
                    binding.root,
                    getString(R.string.system_prompt_saved_successfully),
                    Snackbar.LENGTH_SHORT
                ).show()

                val resultBundle = bundleOf(BUNDLE_KEY to binding.etSystemPrompt.text.toString())
                setFragmentResult(REQUEST_KEY, resultBundle)
                findNavController().navigateUp()
            }

            is EditSystemPromptUiEvent.ValidationError -> {
                showValidationError(event.message)
            }
        }
    }

    private fun saveSystemPrompt() {
        val promptText = binding.etSystemPrompt.text.toString().trim()

        if (promptText.length > MAX_CHARACTERS) {
            showValidationError(getString(R.string.error_prompt_too_long, MAX_CHARACTERS))
            return
        }

        viewModel.saveSystemPrompt(promptText)
    }

    private fun showClearConfirmationDialog() {
        if (binding.etSystemPrompt.text.toString().isBlank()) {
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.clear_prompt_title)
            .setMessage(R.string.clear_prompt_message)
            .setPositiveButton(R.string.action_clear) { _, _ ->
                binding.etSystemPrompt.setText("")
                isUserEdited = true
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showValidationError(message: String) {
        binding.textInputLayout.error = message
        binding.etSystemPrompt.requestFocus()

        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction(R.string.action_ok) { }
            .show()
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.red_error))
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val REQUEST_KEY = "edit_system_prompt_request"
        const val BUNDLE_KEY = "new_system_prompt_text"
        private const val MAX_CHARACTERS = 2000
    }
}
