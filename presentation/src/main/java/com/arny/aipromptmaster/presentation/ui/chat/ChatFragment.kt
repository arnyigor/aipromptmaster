package com.arny.aipromptmaster.presentation.ui.chat

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
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
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.databinding.FragmentChatBinding
import com.arny.aipromptmaster.presentation.ui.editprompt.EditSystemPromptDialogFragment
import com.arny.aipromptmaster.presentation.utils.AnimationUtils
import com.arny.aipromptmaster.presentation.utils.asString
import com.arny.aipromptmaster.presentation.utils.autoClean
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.xwray.groupie.GroupieAdapter
import dagger.android.support.AndroidSupportInjection
import dagger.assisted.AssistedFactory
import es.dmoral.toasty.Toasty
import io.noties.markwon.Markwon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

class ChatFragment : Fragment() {
    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var filePickerLauncher: ActivityResultLauncher<String>


    // Получаем аргументы навигации с помощью Safe Args
    private val args: ChatFragmentArgs by navArgs()

    @AssistedFactory
    internal interface ViewModelFactory {
        fun create(chatid: String?): ChatViewModel
    }

    @Inject
    lateinit var markwon: Markwon

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
        // Инициализация лаунчера
        filePickerLauncher =
            registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                uri?.let {
                    if (isTxtFile(it)) {
                        readTextFile(it) { text ->
                            binding.etUserInput.setText(text)
                        }
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Пожалуйста, выберите TXT-файл",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
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
        setupFragmentResultListener()
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
                            ChatFragmentDirections.actionNavChatToNavSettings()
                        )
                        true
                    }

                    R.id.action_model_select -> {
                        findNavController().navigate(
                            ChatFragmentDirections.actionNavChatToNavModels()
                        )
                        true
                    }

                    R.id.action_export_chat -> {
                        viewModel.onExportChatClicked()
                        true
                    }

                    R.id.action_clear_chat -> {
                        showClearDialog()
                        true
                    }

                    R.id.action_system_prompt -> {
                        val currentPrompt = viewModel.uiState.value.systemPrompt
                        val action = ChatFragmentDirections.actionNavChatToEditPromptDialogFragment(
                            currentPrompt
                        )
                        Log.i(this::class.java.simpleName, "onMenuItemSelected: currentPrompt:$currentPrompt")
                        findNavController().navigate(action)
                        true
                    }

                    else -> false
                }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupFragmentResultListener() {
        setFragmentResultListener(EditSystemPromptDialogFragment.REQUEST_KEY) { _, bundle ->
            val newPrompt = bundle.getString(EditSystemPromptDialogFragment.BUNDLE_KEY)
            if (newPrompt != null) {
                viewModel.setSystemPrompt(newPrompt)
            }
        }
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
                binding.errorCard.isVisible = false
                binding.tvErrorMessage.text = ""
            }
        }

        binding.btnDismissError.setOnClickListener {
            binding.tvErrorMessage.text = ""
            binding.errorCard.isVisible = false
        }

        binding.btnAttachFile.setOnClickListener {
            filePickerLauncher.launch("text/plain")
        }
    }

    private fun readTextFile(uri: Uri, callback: (String) -> Unit) {
        lifecycleScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.bufferedReader().use { reader -> reader.readText() }
                    } ?: throw IOException("Не удалось открыть файл")
                }
                withContext(Dispatchers.Main) {
                    callback(text)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "Не удалось прочитать файл: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun isTxtFile(uri: Uri): Boolean {
        val mimeType = requireContext().contentResolver.getType(uri)
        return mimeType == "text/plain"
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
                                is ChatUiEvent.ShareChat -> shareChatContent(event.content)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun shareChatContent(content: String) {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, content)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, null)
        startActivity(shareIntent)
    }

    private fun updateToolbarTitle(modelName: String?) {
        val actionBar = (activity as? AppCompatActivity)?.supportActionBar
        val title = modelName ?: getString(R.string.title_llm_interaction_model_not_selected)
        actionBar?.title = title
    }

    private fun updateLoadingState(isLoading: Boolean) {
        binding.progressBarSend.isVisible = isLoading
        binding.etUserInput.isEnabled = !isLoading
        binding.btnSend.isEnabled = !isLoading
        binding.btnSend.visibility = if (isLoading) View.INVISIBLE else View.VISIBLE
    }

    private fun handleError(error: Throwable) {
        error.printStackTrace()
        when (error) {
            is DomainError.Api -> {
                // Для API ошибок можно показывать как диалог, так и карточку
                // в зависимости от критичности
                when (error.code) {
                    401, 403 -> showApiErrorDialog(error) // Критические ошибки авторизации
                    429 -> showErrorCard( // Rate limit - можно показать в карточке
                        "Превышен лимит запросов. ${error.detailedMessage}"
                    )

                    else -> showApiErrorDialog(error) // Остальные API ошибки через диалог
                }
            }

            is DomainError.Local -> {
                val message = error.stringHolder.asString(requireContext())
                    .takeIf { it.isNotBlank() } ?: error.message ?: "Локальная ошибка"
                showErrorCard(message)
            }

            is DomainError.Generic -> {
                val message = error.stringHolder.asString(requireContext())
                    .takeIf { it.isNotBlank() } ?: error.message ?: "Неизвестная ошибка"
                showErrorCard(message)
            }

            else -> {
                val message = error.message ?: "Неизвестная ошибка"
                showErrorCard(message)
            }
        }
    }

    /**
     * Показать ошибку в анимированной карточке
     */
    private fun showErrorCard(message: String) {
        binding.tvErrorMessage.text = message

        // Используем анимацию вместо прямого изменения visibility
        if (!binding.errorCard.isVisible) {
            AnimationUtils.showWithSlideDown(binding.errorCard)
        } else {
            // Если карточка уже видна, просто обновляем текст с fade эффектом
            updateErrorMessage(message)
        }
    }

    /**
     * Обновить сообщение в уже видимой карточке
     */
    private fun updateErrorMessage(newMessage: String) {
        println("newMessage:$newMessage")
        val fadeOut = ObjectAnimator.ofFloat(binding.tvErrorMessage, "alpha", 1f, 0f)
        fadeOut.duration = 150

        fadeOut.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                binding.tvErrorMessage.text = newMessage
                val fadeIn = ObjectAnimator.ofFloat(binding.tvErrorMessage, "alpha", 0f, 1f)
                fadeIn.duration = 150
                fadeIn.start()
            }
        })

        fadeOut.start()
    }

    /**
     * Показать критичную API ошибку в диалоге
     */
    private fun showApiErrorDialog(error: DomainError.Api) {
        val title = when (error.code) {
            400 -> "Ошибка сервера"
            401, 403 -> "Ошибка авторизации"
            500, 502, 503 -> "Ошибка сервера"
            else -> "Ошибка API"
        }

        val holderMessage = error.stringHolder.asString(requireContext())
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(error.detailedMessage.takeIf { it.isNotBlank() } ?: holderMessage)
            .setPositiveButton("ОК") { dialog, _ -> dialog.dismiss() }
            .apply {
                // Для ошибок авторизации добавляем кнопку настроек
                if (error.code in listOf(401, 403)) {
                    setNegativeButton("Настройки") { dialog, _ ->
                        // Перейти к настройкам API ключа
                        findNavController().navigate(ChatFragmentDirections.actionNavChatToNavSettings())
                        dialog.dismiss()
                    }
                }
            }
            .show()
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
                    markwon = markwon,
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
//        if (isError) {
//            val redColor = ContextCompat.getColor(requireContext(), R.color.red_error)
//            binding.btnModelSettings.setColorFilter(redColor, PorterDuff.Mode.SRC_IN)
//        } else {
//            binding.btnModelSettings.clearColorFilter()
//        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}