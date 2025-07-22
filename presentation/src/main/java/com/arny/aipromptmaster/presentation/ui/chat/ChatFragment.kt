package com.arny.aipromptmaster.presentation.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PorterDuff
import android.os.Bundle
import android.util.Log
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
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arny.aipromptmaster.core.di.scopes.viewModelFactory
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
import javax.inject.Inject

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

                    else -> false
                }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
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
        launchWhenCreated {
            viewModel.uiState.collectLatest { state ->
                val items = state.messages.map { message ->
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
//                Log.d("ChatUI", "Updating adapter with ${items.size} items. Last message: ${state.messages.lastOrNull()}")
                // Обновляем адаптер. Groupie сам разберется с изменениями благодаря getId()
                groupAdapter.update(items)

                // Прокручиваем вниз, только если пользователь уже внизу списка
                // (чтобы не сбивать его, если он читает старые сообщения)
                val layoutManager = binding.rvChat.layoutManager as LinearLayoutManager
                if (layoutManager.findLastVisibleItemPosition() == groupAdapter.itemCount - 2) {
                    binding.rvChat.smoothScrollToPosition(groupAdapter.itemCount - 1)
                }
                // 2. Управляем состоянием отправки ИСКЛЮЧИТЕЛЬНО через state
                binding.progressBarSend.isVisible = state.isLoading
                binding.btnModelSettings.isEnabled = !state.isLoading
                binding.etUserInput.isEnabled = !state.isLoading
                binding.btnSend.isEnabled = !state.isLoading
                binding.btnSend.visibility = if (state.isLoading) View.INVISIBLE else View.VISIBLE

                // 3. Показываем ошибку. Теперь этот блок будет работать надежно.
                state.error?.let { error ->
                    Log.i(this::class.java.name, "observeViewModel: error = $error")
                    when (error) {
                        // Работаем с ошибкой доменного типа
                        is DomainError.Api -> {
                            showApiErrorDialog(error)
                        }
                        // Работаем с другой ошибкой доменного типа
                        is DomainError.Local -> {
                            Toasty.error(
                                requireContext(),
                                error.message.orEmpty(),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        // Обрабатываем все остальные случаи
                        is DomainError.Generic -> {
                            Toasty.warning(
                                requireContext(),
                                error.message ?: "Произошла неизвестная ошибка",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        // На случай, если прилетит обычный Exception
                        else -> {
                            Toasty.error(
                                requireContext(),
                                error.message ?: "Неизвестная ошибка",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    // СРАЗУ ЖЕ сообщаем ViewModel, что ошибка показана.
                    // Это предотвратит повторный показ SnackBar при каждом незначительном обновлении стейта.
                    viewModel.errorShown()
                }
            }
        }

        launchWhenCreated {
            viewModel.selectedModelResult.collectLatest { modelResult ->
                when (modelResult) {
                    is DataResult.Error<*> -> {
                        binding.btnSend.isEnabled = false
                        setErrorColor(true)
                    }

                    DataResult.Loading -> {}
                    is DataResult.Success<*> -> {
                        binding.btnSend.isEnabled = true
                        setErrorColor(false)
                    }
                }
            }
        }

        launchWhenCreated {
            viewModel.uiState
                // 1. Извлекаем только название модели. Если модели нет, будет null.
                .map { it.selectedModel?.name }
                // 2. Пропускаем одинаковые значения подряд. Заголовок обновится,
                //    только если название модели действительно изменилось.
                .distinctUntilChanged()
                // 3. Собираем результат
                .collect { modelName ->
                    // Получаем supportActionBar из Activity. Используем безопасное приведение.
                    val actionBar = (activity as? AppCompatActivity)?.supportActionBar
                    if (modelName != null) {
                        // Если имя модели есть, устанавливаем его
                        actionBar?.title = modelName
                    } else {
                        // Если модели нет (загрузка, ошибка), ставим заголовок по умолчанию
                        actionBar?.title =
                            getString(R.string.title_llm_interaction_model_not_selected)
                    }
                }
        }
    }

    /**
     * Показывает детализированный диалог для ошибок API.
     */
    private fun showApiErrorDialog(apiError: DomainError.Api) {
        MaterialAlertDialogBuilder(requireActivity())
            .setTitle(apiError.userFriendlyMessage)
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