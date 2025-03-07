package com.arny.aipromptmaster.presentation.ui.home

import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.MultiAutoCompleteTextView.CommaTokenizer
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.arny.aipromptmaster.domain.PromptBlock
import com.arny.aipromptmaster.domain.PromptBlock.ParamType
import com.arny.aipromptmaster.domain.PromptBlock.ParameterBlock
import com.arny.aipromptmaster.domain.PromptBlock.TextBlock
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.databinding.FragmentHomeBinding
import com.google.android.material.snackbar.Snackbar

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initUI()
        initListeners()
        observeData()
    }

    private fun initUI() {
        initAutoCompletePrompts()
    }

    private fun initListeners() {
        binding.fabCreate.setOnClickListener {
            Snackbar.make(it, "Add new Prompt", Snackbar.LENGTH_LONG)
                .setAction("Action") {
                    Toast.makeText(requireContext(), "Action Callback", Toast.LENGTH_SHORT).show()
                }
                .apply {
                    anchorView = binding.fabCreate
                    show()
                }
        }
    }

    private fun observeData() {
        viewModel.text.observe(viewLifecycleOwner) { data ->
            Toast.makeText(requireContext(), data, Toast.LENGTH_SHORT).show()
        }
    }

    private fun initAutoCompletePrompts() {
        val baseList = arrayOf(
            "Изображение",
            "Текст",
            "Видео",
            "Генерация",
            "Редактирование",
            "Идеи",
            "Шаблоны",
            "История",
            "Избранное",
            "Сообщество"
        )
        val modelsList = arrayOf(
            "MidJourney",
            "Stable Diffusion",
            "ChatGPT",
            "DALL·E",
            "SDXL",
            "Pixar-стиль",
            "3D-моделирование",
        )
        val requestsList = arrayOf(
            "Сценарий",
            "Резюме",
            "Стихи",
            "Бизнес-идеи",
            "Обучение",
            "Перевод",
            "Создать диалог",
            "Генерация заголовков"
        )
        val adapter =
            ArrayAdapter(
                requireContext(),
                R.layout.prompts_item_dropdown,
                baseList + modelsList + requestsList
            )
        binding.multiAutoCompleteTextView.setAdapter(adapter)
        // установка запятой в качестве разделителя
        binding.multiAutoCompleteTextView.setTokenizer(CommaTokenizer())
    }

    fun generatePrompt(blocks: List<PromptBlock>): CharSequence {
        val spannable = SpannableStringBuilder()
        blocks.forEach { block ->
            when (block) {
                is TextBlock -> spannable.append("${block.content} ")
                is ParameterBlock -> {
                    val param = "--${block.key} ${block.value}"
                    spannable.append(param)
                    spannable.setSpan(
                        ForegroundColorSpan(Color.BLUE),
                        spannable.length - param.length,
                        spannable.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
        }
        return spannable
    }

    fun validateBlock(block: PromptBlock): Boolean {
        return when (block) {
            is TextBlock -> block.content.isNotBlank()
            is ParameterBlock -> when (block.type) {
                ParamType.TEXT -> block.value.isNotBlank()
                ParamType.NUMBER -> block.value.toIntOrNull() != null && block.value.toInt() in 1..100
                ParamType.RATIO -> Regex("^\\d+:\\d+$").matches(block.value)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
