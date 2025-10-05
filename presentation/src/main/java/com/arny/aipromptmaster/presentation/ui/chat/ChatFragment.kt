package com.arny.aipromptmaster.presentation.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import androidx.core.content.ContextCompat
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
import com.arny.aipromptmaster.domain.models.FileAttachment
import com.arny.aipromptmaster.domain.models.FileAttachmentMetadata
import com.arny.aipromptmaster.domain.models.errors.DomainError
import com.arny.aipromptmaster.domain.results.DataResult
import com.arny.aipromptmaster.domain.services.FileProcessingResult
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.databinding.FragmentChatBinding
import com.arny.aipromptmaster.presentation.ui.editprompt.EditSystemPromptFragment
import com.arny.aipromptmaster.presentation.utils.AnimationUtils
import com.arny.aipromptmaster.presentation.utils.asString
import com.arny.aipromptmaster.presentation.utils.autoClean
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.xwray.groupie.GroupieAdapter
import dagger.android.support.AndroidSupportInjection
import dagger.assisted.AssistedFactory
import es.dmoral.toasty.Toasty
import io.noties.markwon.Markwon
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import java.util.Locale
import javax.inject.Inject

class ChatFragment : Fragment() {
    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var filePickerLauncher: ActivityResultLauncher<String>
    private var uploadJob: kotlinx.coroutines.Job? = null // Для отмены загрузки
    private var modelName = ""
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

        filePickerLauncher =
            registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                uri?.let {
                    // ✅ fileProcessingService вызывается через репозиторий для проверки типа файла
                    processFile(it)
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
                        viewModel.onSystemPromptMenuClicked()
                        true
                    }

                    else -> false
                }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupFragmentResultListener() {
        setFragmentResultListener(EditSystemPromptFragment.REQUEST_KEY) { _, bundle ->
            val newPrompt = bundle.getString(EditSystemPromptFragment.BUNDLE_KEY)
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
                viewModel.updateInputText("")
                binding.errorCard.isVisible = false
                binding.tvErrorMessage.text = ""
            }
        }

        // Добавляем слушатель для обновления токенов при изменении текста
        binding.etUserInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                viewModel.updateInputText(s.toString())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.btnDismissError.setOnClickListener {
            binding.tvErrorMessage.text = ""
            binding.errorCard.isVisible = false
        }

        binding.btnAttachFile.setOnClickListener {
            filePickerLauncher.launch("*/*")
        }

        binding.btnCancelUpload.setOnClickListener {
            cancelFileUpload()
        }

        binding.btnCancel.setOnClickListener {
            cancelMessageRequest()
        }
    }

    /**
     * ✅ Обработка файла через fileProcessingService репозитория со встроенным UI прогресса
     */
    private fun processFile(uri: Uri) {

        uploadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                viewModel.processFileFromUri(uri)
                    .collect { result ->
                        when (result) {
                            is FileProcessingResult.Started -> {
                                // Показываем карточку прогресса при начале обработки
                                showUploadProgress(result.fileName, result.fileSize)
                            }

                            is FileProcessingResult.Progress -> {
                                // Обновляем прогресс обработки
                                updateUploadProgress(
                                    progress = result.progress,
                                    bytesRead = result.bytesRead,
                                    totalBytes = result.totalBytes,
                                    previewText = result.previewText
                                )
                            }

                            is FileProcessingResult.Complete -> {
                                // Скрываем прогресс и обрабатываем завершение
                                hideUploadProgress()
                                onFileUploadComplete(result)
                            }

                            is FileProcessingResult.Error -> {
                                // Скрываем прогресс и показываем ошибку
                                hideUploadProgress()
                                showErrorCard(result.message)
                            }
                        }
                    }
            } catch (e: Exception) {
                hideUploadProgress()
                showErrorCard(e.message ?: "Неизвестная ошибка")
            }
        }
    }

    /**
     * Показать карточку прогресса (вызывается один раз)
     */
    private fun showUploadProgress(fileName: String, fileSize: Long) {
        binding.fileUploadProgressCard.isVisible = true
        binding.tvUploadFileName.text = fileName
        binding.tvUploadFileSize.text = formatFileSize(fileSize)
        binding.progressUpload.progress = 0
        binding.tvUploadProgress.text = "Подготовка..."
        binding.tvUploadPreview.isVisible = false

        // Анимация появления
        binding.fileUploadProgressCard.alpha = 0f
        binding.fileUploadProgressCard.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
    }

    /**
     * Обновить прогресс загрузки (вызывается многократно)
     */
    private fun updateUploadProgress(
        progress: Int,
        bytesRead: Long,
        totalBytes: Long,
        previewText: String? = null
    ) {
        binding.progressUpload.setProgressCompat(progress, true) // Анимированный прогресс

        val progressText = when {
            totalBytes > 0 -> {
                val mbRead = bytesRead / (1024.0 * 1024.0)
                val mbTotal = totalBytes / (1024.0 * 1024.0)
                "Обработано $progress% (${
                    String.format(
                        "%.1f",
                        mbRead
                    )
                } MB / ${String.format("%.1f", mbTotal)} MB)"
            }

            else -> "Обработано $progress%"
        }
        binding.tvUploadProgress.text = progressText

        // Показываем превью, если есть
        previewText?.let {
            binding.tvUploadPreview.isVisible = true
            binding.tvUploadPreview.text = it.take(200) + if (it.length > 200) "..." else ""
        }
    }

    /**
     * Скрыть карточку прогресса
     */
    private fun hideUploadProgress() {
        binding.fileUploadProgressCard.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                binding.fileUploadProgressCard.isVisible = false
            }
            .start()
    }

    /**
     * Отменить загрузку файла
     */
    private fun cancelFileUpload() {
        uploadJob?.cancel()
        hideUploadProgress()
        Toasty.info(requireContext(), "Загрузка отменена", Toast.LENGTH_SHORT).show()
    }

    /**
     * Отменить текущий запрос к LLM
     */
    private fun cancelMessageRequest() {
        viewModel.cancelCurrentRequest()
        Toasty.info(requireContext(), "Запрос отменен", Toast.LENGTH_SHORT).show()
    }

    /**
     * Обработать завершение загрузки
     */
    private fun onFileUploadComplete(result: FileProcessingResult.Complete) {
        // Добавляем файл для расчета токенов
        viewModel.addAttachedFile(result.fileAttachment)

        sendMessageWithFile(result.fileAttachment)

        Toasty.success(
            requireContext(),
            "Файл ${result.fileAttachment.fileName} загружен",
            Toast.LENGTH_SHORT
        ).show()
    }

    /**
     * Единая и единственная точка добавления сообщения
     * Только при добавлении файла к чату
     */
    private fun sendMessageWithFile(fileAttachment: FileAttachment) {
        lifecycleScope.launch {
            try {
                // ✅ ИСПОЛЬЗУЕМ ПУБЛИЧНЫЙ МЕТОД ViewModel
                viewModel.addMessageWithFile(
                    conversationId = args.chatid ?: "",
                    userMessage = "", // Пустое сообщение - файл говорит сам за себя
                    fileAttachment = fileAttachment
                )

                // Автоматическая отправка сообщения на анализ отключена пользователем

                Toasty.success(
                    requireContext(),
                    "Файл ${fileAttachment.fileName} добавлен",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toasty.error(
                    requireContext(),
                    "Ошибка при добавлении файла: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }


    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    private fun openFileViewer(fileAttachment: FileAttachmentMetadata) {
        try {
            val action =
                ChatFragmentDirections.actionChatFragmentToFileViewerFragment(fileAttachment.fileId)
            findNavController().navigate(action)
        } catch (e: Exception) {
            Toasty.error(
                requireContext(),
                "Ошибка при открытии файла: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                supervisorScope {
                    launch {
                        viewModel.uiState.collect { state ->
                            updateChatList(state.messages, state.isStreamingResponse)
                            updateLoadingState(state.isLoading)
                        }
                    }

                    launch {
                        viewModel.selectedModelResult
                            .map { result ->
                                when (result) {
                                    is DataResult.Success -> result.data.name
                                    else -> null
                                }
                            }
                            .distinctUntilChanged()
                            .collect { modelName ->
                                this@ChatFragment.modelName = modelName.orEmpty()
                                updateToolbarTitle(modelName)
                            }
                    }

                    launch {
                        viewModel.selectedModelResult
                            .map { result ->
                                result !is DataResult.Success && result !is DataResult.Loading
                            }
                            .distinctUntilChanged()
                            .collect { isError ->
                                updateModelErrorState(isError)
                            }
                    }

                    launch {
                        viewModel.uiEvents.collect { event ->
                            when (event) {
                                is ChatUiEvent.ShowError -> handleError(event.error)
                                is ChatUiEvent.ShareChat -> shareChatContent(event.content)
                            }
                        }
                    }

                    launch {
                        viewModel.newConversationIdEvent.collect { newId ->
                            navigateToEditSystemPrompt(newId)
                        }
                    }

                    launch {
                        viewModel.estimatedTokens.collect { tokenCount ->
                            updateTokenInfo(tokenCount)
                        }
                    }

                    launch {
                        viewModel.isAccurate.collect { isAccurate ->
                            updateTokenAccuracy(isAccurate)
                        }
                    }
                }
            }
        }
    }

    private fun shareChatContent(content: String) {
        try {
            // Создаем временный MD файл
            val fileName = "chat_export_${System.currentTimeMillis()}.md"
            val cacheDir = requireContext().cacheDir
            val file = java.io.File(cacheDir, fileName)

            file.writer().use { writer ->
                writer.write(content)
            }

            // Создаем URI для файла
            val fileUri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )

            // Создаем Intent для отправки файла
            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, fileUri)
                putExtra(Intent.EXTRA_SUBJECT, "Экспорт чата AiPromptMaster")
                type = "text/markdown"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val shareIntent = Intent.createChooser(sendIntent, "Поделиться чатом")
            startActivity(shareIntent)

        } catch (e: Exception) {
            // Fallback: если не удается создать файл, отправляем как текст
            Toasty.error(
                requireContext(),
                "Ошибка при создании файла: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()

            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, content)
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, "Поделиться чатом")
            startActivity(shareIntent)
        }
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
        binding.btnCancel.isVisible = isLoading
    }

    private fun handleError(error: Throwable) {
        error.printStackTrace()
        when (error) {
            is DomainError.Api -> {
                when (error.code) {
                    401, 403 -> showApiErrorDialog(error)
                    429 -> showErrorCard(
                        "Превышен лимит запросов. ${error.detailedMessage}"
                    )

                    else -> showApiErrorDialog(error)
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

    private fun showErrorCard(message: String) {
        binding.tvErrorMessage.text = message

        if (!binding.errorCard.isVisible) {
            AnimationUtils.showWithSlideDown(binding.errorCard)
        } else {
            updateErrorMessage(message)
        }
    }

    private fun updateErrorMessage(newMessage: String) {
        android.animation.ObjectAnimator.ofFloat(binding.tvErrorMessage, "alpha", 1f, 0f).apply {
            duration = 150
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    binding.tvErrorMessage.text = newMessage
                    android.animation.ObjectAnimator.ofFloat(
                        binding.tvErrorMessage,
                        "alpha",
                        0f,
                        1f
                    ).apply {
                        duration = 150
                        start()
                    }
                }
            })
            start()
        }
    }

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
                if (error.code in listOf(401, 403)) {
                    setNegativeButton("Настройки") { dialog, _ ->
                        findNavController().navigate(ChatFragmentDirections.actionNavChatToNavSettings())
                        dialog.dismiss()
                    }
                }
            }
            .show()
    }

    private fun updateModelErrorState(isError: Boolean) {
        binding.btnSend.isEnabled = !isError
    }

    private fun updateTokenInfo(tokenCount: Int) {
        if (tokenCount > 0) {
            binding.tvTokenInfo.text = buildString { "📊 ~$tokenCount" }
            binding.tokenInfoContainer.visibility = View.VISIBLE
        } else {
            binding.tokenInfoContainer.visibility = View.GONE
        }
    }

    private fun updateTokenAccuracy(isAccurate: Boolean) {
        if (isAccurate) {
            binding.tvTokenAccuracy.text = "✓"
            binding.tvTokenAccuracy.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    android.R.color.holo_green_dark
                )
            )
        } else {
            binding.tvTokenAccuracy.text = "≈"
            binding.tvTokenAccuracy.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    android.R.color.darker_gray
                )
            )
        }
    }

    private fun updateChatList(messages: List<ChatMessage>, isStreamingResponse: Boolean = false) {
        val items = messages.map { message ->
            when (message.role) {
                ChatRole.USER -> {
                    message.fileAttachment?.let { fileAttachment ->
                        FileMessageItem(
                            message = message,
                            onViewFile = { file ->
                                openFileViewer(file)
                            }
                        )
                    } ?: UserMessageItem(
                        markwon = markwon,
                        message = message,
                        onCopyClicked = { textToCopy ->
                            copyToClipboard(textToCopy)
                        },
                        onRegenerateClicked = { textToCopy ->
                            binding.etUserInput.setText(textToCopy)
                        }
                    )
                }

                ChatRole.ASSISTANT -> {
                    AiMessageItem(
                        markwon = markwon,
                        message = message,
                        modelName = modelName,
                        onCopyClicked = { textToCopy ->
                            copyToClipboard(textToCopy)
                        },
                    )
                }

                else -> throw IllegalArgumentException("Unknown message role: ${message.role})")
            }
        }
        groupAdapter.update(items)

        if (!isStreamingResponse) {
            val layoutManager = binding.rvChat.layoutManager as LinearLayoutManager
            if (layoutManager.findLastVisibleItemPosition() == groupAdapter.itemCount - 2) {
                binding.rvChat.smoothScrollToPosition(groupAdapter.itemCount - 1)
            }
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

    private fun navigateToEditSystemPrompt(conversationId: String) {
        val currentPrompt = viewModel.uiState.value.systemPrompt
        val action = ChatFragmentDirections.actionNavChatToEditSystemPromptFragment(
            currentPrompt,
            conversationId
        )
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        uploadJob?.cancel() // Отменяем загрузку при уничтожении view -
        // тут нужно продумать,чтобы можно было завершить разговор,даже если ушел или хотя бы текущий ответ дождаться
        super.onDestroyView()
        _binding = null
    }
}
