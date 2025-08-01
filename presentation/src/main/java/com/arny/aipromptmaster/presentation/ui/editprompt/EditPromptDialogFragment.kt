package com.arny.aipromptmaster.presentation.ui.editprompt

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.databinding.FragmentEditPromptBinding

class EditPromptDialogFragment : DialogFragment() {

    private var _binding: FragmentEditPromptBinding? = null
    private val binding get() = _binding!!

    // Получаем аргументы через Safe Args
    private val args: EditPromptDialogFragmentArgs by navArgs()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_App_FullScreenDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditPromptBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Устанавливаем начальный текст из аргументов
        binding.etSystemPrompt.setText(args.initialPrompt)

        // Закрытие диалога
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // Сохранение и возврат результата
        binding.fabSave.setOnClickListener {
            val newPrompt = binding.etSystemPrompt.text.toString()
            // Используем Fragment Result API для возврата данных
            setFragmentResult(REQUEST_KEY, bundleOf(BUNDLE_KEY to newPrompt))
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val REQUEST_KEY = "edit_prompt_request"
        const val BUNDLE_KEY = "new_prompt_text"
    }
}