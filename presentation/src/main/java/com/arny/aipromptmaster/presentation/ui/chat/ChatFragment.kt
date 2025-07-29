package com.arny.aipromptmaster.presentation.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arny.aipromptmaster.core.di.scopes.viewModelFactory
import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.domain.models.ChatRole
import com.arny.aipromptmaster.domain.models.errors.DomainError
import com.arny.aipromptmaster.domain.results.DataResult
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.databinding.FragmentChatBinding
import com.arny.aipromptmaster.presentation.utils.autoClean
import com.arny.aipromptmaster.presentation.utils.launchWhenCreated
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.xwray.groupie.GroupieAdapter
import dagger.android.support.AndroidSupportInjection
import dagger.assisted.AssistedFactory
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject
import com.arny.aipromptmaster.presentation.utils.showInputTextDialog

class ChatFragment : Fragment() {
    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    // Получаем аргументы навигации с помощью Safe Args
    private val args: ChatFragmentArgs by navArgs()

    @AssistedFactory
    internal interface ViewModelFactory {
        fun create(chatid: String?): ChatViewModel
    }

    private val groupAdapter by autoClean { GroupieAdapter() }

    @Inject
    internal lateinit var viewModelFactory: ViewModelFactory
    private val viewModel: ChatViewModel by viewModelFactory { viewModelFactory.create(args.chatid) }

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
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initMenu()
        setupViews()
        observeViewModel()
    }

    private fun initMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onPrepareMenu(menu: Menu) {}

            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.chat_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                when (menuItem.itemId) {
                    R.id.action_settings -> {
                        findNavController().navigate(
                            ChatFragmentDirections.actionNavChatToSettingsFragment()
                        )
                        true
                    }

                    R.id.action_clear_chat -> {
                        showClearDialog()
                        true
                    }

                    R.id.action_system_prompt -> {
                        // Получаем текущее значение из последнего собранного state
                        // Это уже лучше, так как мы берем его из "реактивного" контекста
                        val currentPrompt = viewModel.uiState.value.systemPrompt
                        showSystemPromptDialog(currentPrompt)
                        true
                    }

                    else -> false
                }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun showSystemPromptDialog(initialPrompt: String?) {
        requireContext().showInputTextDialog(
            title = getString(R.string.system_prompt_dialog_title),
            hint = getString(R.string.system_prompt_dialog_message),
            prefillText = initialPrompt,
            positiveButtonText = getString(android.R.string.ok),
            negativeButtonText = getString(android.R.string.cancel),
            onResult = { name ->
                viewModel.setSystemPrompt(name)
            }
        )
    }

    private fun setupViews() {
        binding.rvChat.apply {
            layoutManager = LinearLayoutManager(context).apply {
                stackFromEnd = true
            }
            adapter = groupAdapter
            adapter?.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                    smoothScrollToPosition(groupAdapter.itemCount - 1)
                }
            })
        }

        binding.btnSend.setOnClickListener {
            val message = binding.etUserInput.text.toString().trim()
            if (message.isNotBlank()) {
                viewModel.sendMessage(message)
                binding.etUserInput.text?.clear()
            }
        }

        binding.btnModelSettings.setOnClickListener {
            findNavController().navigate(
                ChatFragmentDirections.actionNavChatToModelsFragment()
            )
        }
    }

    private fun observeViewModel() {
        // Единая точка входа для сбора потоков
        viewLifecycleOwner.lifecycleScope.launch {
            // Гарантирует, что корутины внутри будут работать только между ON_START и ON_STOP
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // Создаем scope-супервизор. Он будет управлять дочерними корутинами.
                // Если один из launch ниже упадет, остальные продолжат работать.
                // Сам supervisorScope пробросит ошибку дальше, если ее не обработать в CoroutineExceptionHandler,
                // но он не отменит "соседей".
                supervisorScope {
                    // --- Корутина №1: Сбор основного состояния UI ---
                    launch {
                        viewModel.uiState.collect { state ->
                            // Вызываем функции, которые должны реагировать на ЛЮБОЕ изменение state
                            updateChatList(state.messages)
                            updateLoadingState(state.isLoading)
                        }
                    }

                    // --- Корутина №2 (ВОССТАНОВЛЕННАЯ): Сбор названия модели для заголовка ---
                    launch {
                        viewModel.uiState
                            .map { it.selectedModel?.name } // Извлекаем только имя
                            .distinctUntilChanged() // Пропускаем дубликаты
                            .collect { modelName ->
                                // Этот блок выполнится ТОЛЬКО при смене имени модели
                                updateToolbarTitle(modelName)
                            }
                    }

                    // --- Корутина №3: Сбор состояния ошибки модели ---
                    // Точно так же, как и с заголовком, выносим в отдельный поток,
                    // чтобы не дергать UI лишний раз.
                    launch {
                        viewModel.uiState
                            .map { it.selectedModel == null && !it.isLoading }
                            .distinctUntilChanged()
                            .collect { isError ->
                                updateModelErrorState(isError)
                            }
                    }

                    // --- Корутина №4: Сбор одноразовых событий ---
                    launch {
                        viewModel.uiEvents.collect { event ->
                            when (event) {
                                is ChatUiEvent.ShowError -> handleError(event.error)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateToolbarTitle(modelName: String?) {
        val actionBar = (activity as? AppCompatActivity)?.supportActionBar
        val title = modelName ?: getString(R.string.title_llm_interaction_model_not_selected)
        actionBar?.title = title
    }

    private fun updateLoadingState(isLoading: Boolean) {
        binding.progressBarSend.isVisible = isLoading
        binding.btnModelSettings.isEnabled = !isLoading
        binding.etUserInput.isEnabled = !isLoading
        binding.btnSend.isEnabled = !isLoading
        binding.btnSend.visibility = if (isLoading) View.INVISIBLE else View.VISIBLE
    }

    private fun handleError(error: Throwable) {
        when (error) {
            is DomainError.Api -> showApiErrorDialog(error)
            is DomainError.Local -> Toasty.error(requireContext(), error.message.orEmpty(), Toast.LENGTH_LONG)
                .show()

            is DomainError.Generic -> Toasty.warning(
                requireContext(),
                error.message ?: "Произошла неизвестная ошибка",
                Toast.LENGTH_LONG
            ).show()

            else -> Toasty.error(
                requireContext(),
                error.message ?: "Неизвестная ошибка",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun updateModelErrorState(isError: Boolean) {
        setErrorColor(isError)
        binding.btnSend.isEnabled = !isError
    }

    private fun updateChatList(messages: List<ChatMessage>) {
        val items = messages.map { message ->
            when (message.role) {
                ChatRole.USER -> UserMessageItem(
                    message = message,
                    onCopyClicked = { textToCopy ->
                        copyToClipboard(textToCopy)
                    },
                    onRegenerateClicked = { textToCopy ->
                        binding.etUserInput.setText(textToCopy)
                    }
                )

                ChatRole.ASSISTANT -> AiMessageItem(
                    message = message,
                    onCopyClicked = { textToCopy ->
                        copyToClipboard(textToCopy)
                    },
                )

                else -> throw IllegalArgumentException("Unknown message role: ${message.role})")
            }
        }
        groupAdapter.update(items)
        val layoutManager = binding.rvChat.layoutManager as LinearLayoutManager
        if (layoutManager.findLastVisibleItemPosition() == groupAdapter.itemCount - 2) {
            binding.rvChat.smoothScrollToPosition(groupAdapter.itemCount - 1)
        }
    }

    /**
     * Показывает детализированный диалог для ошибок API.
     */
    private fun showApiErrorDialog(apiError: DomainError.Api) {
        MaterialAlertDialogBuilder(requireActivity())
            .setTitle(apiError.message)
            .setMessage(apiError.detailedMessage)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton(R.string.action_settings) { dialog, _ ->
                findNavController().navigate(ChatFragmentDirections.actionNavChatToSettingsFragment())
                dialog.dismiss()
            }.show()
    }

    private fun showClearDialog() {
        MaterialAlertDialogBuilder(requireActivity())
            .setTitle("Очистить историю чата?")
            .setMessage("При удалении истории чата удалится история разговора и ИИ модель не будет помнить о чем был разговор.")
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                viewModel.onRemoveChatHistory()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }.show()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("AI Response", text)
        clipboard?.setPrimaryClip(clip)
        Toasty.success(
            requireContext(),
            getString(R.string.copied_to_clipboard),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun setErrorColor(isError: Boolean) {
        if (isError) {
            val redColor = ContextCompat.getColor(requireContext(), R.color.red_error)
            binding.btnModelSettings.setColorFilter(redColor, PorterDuff.Mode.SRC_IN)
        } else {
            binding.btnModelSettings.clearColorFilter()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}