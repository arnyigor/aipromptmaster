package com.arny.aipromptmaster.presentation.ui.llm

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.arny.aipromptmaster.core.di.scopes.viewModelFactory
import com.arny.aipromptmaster.presentation.databinding.FragmentLlmInteractionBinding
import com.arny.aipromptmaster.presentation.utils.autoClean
import com.arny.aipromptmaster.presentation.utils.launchWhenCreated
import com.xwray.groupie.GroupieAdapter
import dagger.android.support.AndroidSupportInjection
import dagger.assisted.AssistedFactory
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

class LLMInteractionFragment : Fragment() {
    private var _binding: FragmentLlmInteractionBinding? = null
    private val binding get() = _binding!!

    @AssistedFactory
    internal interface ViewModelFactory {
        fun create(): LLMInteractionViewModel
    }

    private val groupAdapter by autoClean { GroupieAdapter() }

    @Inject
    internal lateinit var viewModelFactory: ViewModelFactory
    private val viewModel: LLMInteractionViewModel by viewModelFactory { viewModelFactory.create() }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLlmInteractionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        observeViewModel()
    }

    private fun setupViews() {
        binding.rvChat.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = groupAdapter
        }

        binding.btnSend.setOnClickListener {
            val message = binding.etUserInput.text.toString()
            if (message.isNotBlank()) {
                // Добавляем сообщение пользователя
                groupAdapter.add(UserMessageItem(message))
                // Прокручиваем к последнему сообщению
                binding.rvChat.smoothScrollToPosition(groupAdapter.itemCount - 1)
                // Очищаем поле ввода
                binding.etUserInput.text.clear()
                // Отправляем сообщение в ViewModel
                viewModel.sendMessage("deepseek/deepseek-r1:free", message)
            }
        }
    }

    private fun observeViewModel() {
        launchWhenCreated {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is LLMUIState.Content -> {
                        state.messages.lastOrNull()?.let { message ->
                            groupAdapter.add(LLMMessageItem(message))
                            binding.rvChat.smoothScrollToPosition(groupAdapter.itemCount - 1)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}