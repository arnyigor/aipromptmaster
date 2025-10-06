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
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.arny.aipromptmaster.core.di.scopes.viewModelFactory
import com.arny.aipromptmaster.domain.models.ChatMessage
import com.arny.aipromptmaster.domain.models.ChatRole
import com.arny.aipromptmaster.domain.models.FileAttachmentMetadata
import com.arny.aipromptmaster.domain.models.errors.DomainError
import com.arny.aipromptmaster.domain.results.DataResult
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.databinding.FragmentChatBinding
import com.arny.aipromptmaster.presentation.ui.editprompt.EditSystemPromptFragment
import com.arny.aipromptmaster.presentation.utils.asString
import com.arny.aipromptmaster.presentation.utils.autoClean
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Item
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

class ChatFragment : Fragment(R.layout.fragment_chat) {
    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var filePickerLauncher: ActivityResultLauncher<String>
    private var modelName = ""
    private val args: ChatFragmentArgs by navArgs()

    // Флаги состояния
    private var isKeyboardVisible = false
    private var isStreaming = false

    // ✅ Храним ссылку на callback
    private var backPressedCallback: OnBackPressedCallback? = null

    @AssistedFactory
    internal interface ViewModelFactory {
        fun create(chatid: String?): ChatViewModel
    }

    @Inject
    lateinit var markwon: Markwon

    private val groupAdapter by autoClean { GroupAdapter<GroupieViewHolder>() }

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
                uri?.let { handleFileSelection(it) }
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
        setupWindowInsets()
        initMenu()
        setupViews()
        setupKeyboardHandling()
        setupBackPressHandling()
        observeViewModel()
        setupFragmentResultListener()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            // Применяем padding к input_card
            val bottomPadding = maxOf(systemBars.bottom, ime.bottom)
            binding.inputCard.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = bottomPadding + 12 // 12dp базовый margin
            }

            // Применяем padding к RecyclerView
            binding.messagesRecyclerView.updatePadding(bottom = bottomPadding + 16)

            // Обновляем флаг видимости клавиатуры
            val wasVisible = isKeyboardVisible
            isKeyboardVisible = ime.bottom > systemBars.bottom

            if (wasVisible != isKeyboardVisible) {
                onKeyboardVisibilityChanged(isKeyboardVisible)
            }

            insets
        }
    }


    private fun setupKeyboardHandling() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val wasVisible = isKeyboardVisible
            isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())

            if (wasVisible != isKeyboardVisible) {
                onKeyboardVisibilityChanged(isKeyboardVisible)
            }

            insets
        }
    }

    private fun setupBackPressHandling() {
        // Создаем и сохраняем callback
        backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    isKeyboardVisible -> {
                        hideKeyboard()
                    }

                    isStreaming -> {
                        viewModel.cancelCurrentRequest()
                        // После отмены выйти
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }

                    else -> {
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        }

        // Регистрируем callback
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            backPressedCallback!!
        )
    }

    // Обновляем isEnabled напрямую через сохраненную ссылку
    private fun onKeyboardVisibilityChanged(visible: Boolean) {
        backPressedCallback?.isEnabled = visible || isStreaming
    }

    private fun hideKeyboard() {
        val view = requireActivity().currentFocus ?: binding.root
        ViewCompat.getWindowInsetsController(view)?.hide(WindowInsetsCompat.Type.ime())
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
                            ChatFragmentDirections.actionNavChatToModelsFragment()
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
        // Основной список сообщений
        binding.messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(context).apply {
                stackFromEnd = true
                reverseLayout = false
            }
            adapter = groupAdapter

            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
            setItemViewCacheSize(20)
            setHasFixedSize(false)

            groupAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                    smoothScrollToPosition(groupAdapter.itemCount - 1)
                }
            })
        }

        // Кнопка прикрепления файла
        binding.btnAttachFile.setOnClickListener {
            if (viewModel.attachments.value.size >= 5) {
                showErrorCard("Максимум 5 файлов")
                return@setOnClickListener
            }
            filePickerLauncher.launch("*/*")
        }

        // Кнопка отправки
        binding.btnSend.setOnClickListener {
            sendMessage()
        }

        // Отслеживание текста для активации кнопки
        binding.etUserInput.doAfterTextChanged { text ->
            updateSendButtonState()
            updateTokenInfo(text?.toString() ?: "")
        }

        // Обработка ошибок
        binding.btnDismissError.setOnClickListener {
            hideErrorCard()
        }

        // Кнопка отмены генерации
        binding.btnCancel.setOnClickListener {
            viewModel.cancelCurrentRequest()
        }
    }

    /**
     * Обрабатывает выбор файла пользователем
     */
    private fun handleFileSelection(uri: Uri) {
        lifecycleScope.launch {
            try {
                // Вызываем метод ViewModel для добавления файла
                viewModel.addAttachmentFromUri(uri)
            } catch (e: Exception) {
                showErrorCard("Ошибка при добавлении файла: ${e.message}")
            }
        }
    }

    /**
     * Отправляет сообщение
     */
    private fun sendMessage() {
        // ✅ Добавляем проверки на null
        _binding?.let { binding ->
            val message = binding.etUserInput.text?.toString()?.trim() ?: ""

            // Проверяем наличие текста или файлов
            if (message.isEmpty() && viewModel.attachments.value.isEmpty()) {
                showErrorCard("Введите сообщение или прикрепите файл")
                return
            }

            // Проверяем, есть ли загружающиеся файлы
            if (viewModel.hasUploadingFiles()) {
                showErrorCard("Дождитесь завершения загрузки файлов")
                return
            }

            // Отправляем сообщение через ViewModel
            viewModel.sendMessage(message)

            // Очищаем поле ввода
            binding.etUserInput.text?.clear()
        }
    }

    /**
     * Обновляет состояние кнопки отправки
     */
    private fun updateSendButtonState() {
        // ✅ Добавляем проверки на null
        _binding?.let { binding ->
            val hasText = binding.etUserInput.text?.isNotBlank() == true
            val hasAttachments = viewModel.attachments.value.isNotEmpty()
            val hasUploadingFiles = viewModel.hasUploadingFiles()

            binding.btnSend.isEnabled = (hasText || hasAttachments) && !hasUploadingFiles
        }
    }

    /**
      * Обновляет информацию о токенах
      */
    private fun updateTokenInfo(text: String) {
        // ✅ Добавляем проверки на null
        _binding?.let { binding ->
            if (text.isBlank() && viewModel.attachments.value.isEmpty()) {
                binding.tokenInfoContainer.isVisible = false
                return
            }

            // Примерный подсчет токенов
            val estimatedTokens = estimateTokenCount(text)

            if (estimatedTokens > 0) {
                binding.tvTokenInfo.text = "~$estimatedTokens"
                binding.tvTokenAccuracy.text = "tokens"

                if (!binding.tokenInfoContainer.isVisible) {
                    binding.tokenInfoContainer.animateVisibility(true)
                }
            }
        }
    }

    /**
     * Примерная оценка количества токенов
     */
    private fun estimateTokenCount(text: String): Int {
        val words = text.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val punctuation = text.count { it in ".,!?;:\"'()[]{}—-" }
        val fileTokens = viewModel.attachments.value.size * 100

        return words.size + punctuation + fileTokens
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                supervisorScope {
                    // UI State
                    launch {
                        viewModel.chatState.collect { state ->
                            handleChatUiState(state)
                        }
                    }

                    // Chat items для RecyclerView
                    launch {
                        viewModel.chatData.collect { data ->
                            // Обновляем заголовок
                            modelName = data.selectedModel?.name.orEmpty()
                            updateToolbarTitle(data.selectedModel?.name)
                            val items = data.messages.map { message ->
                                createMessageItem(message, modelName)
                            }
                            groupAdapter.updateAsync(items)
                        }
                    }

                    // Attachments для chips
                    launch {
                        viewModel.attachments.collect { attachments ->
                            updateAttachmentChips(attachments)
                            updateSendButtonState()
                        }
                    }

                    // Модель для заголовка
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

                    // События UI
                    launch {
                        viewModel.uiEvents.collect { event ->
                            when (event) {
                                is ChatUiEvent.ShowError -> handleError(event.error)
                                is ChatUiEvent.ShareChat -> shareChatContent(event.content)
                            }
                        }
                    }

                    // События навигации
                    launch {
                        viewModel.newConversationIdEvent.collect { newId ->
                            navigateToEditSystemPrompt(newId)
                        }
                    }
                }
            }
        }
    }


    private fun createMessageItem(message: ChatMessage, modelName: String): Item<*> {
        return when (message.role) {
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
                    }
                )
            }

            else -> throw IllegalArgumentException("Unknown role: ${message.role}")
        }
    }


    private fun handleChatState(state: ChatUiState) {
        when (state) {
            is ChatUiState.Idle -> {
                isStreaming = false
                showInputMode()
            }

            is ChatUiState.Streaming -> {
                isStreaming = true
                showGeneratingMode("Генерация ответа...")
            }

            is ChatUiState.BackgroundStreaming -> {
                isStreaming = true
                showGeneratingMode("Генерация в фоне...")
            }

            is ChatUiState.Completed -> {
                isStreaming = false
                showInputMode()
            }

            is ChatUiState.Cancelled -> {
                isStreaming = false
                showInputMode()
                Toasty.info(requireContext(), "Генерация отменена", Toast.LENGTH_SHORT).show()
            }

            is ChatUiState.RestoredFromBackground -> {
                isStreaming = true
                showGeneratingMode("Восстановление...")
            }

            is ChatUiState.Error -> {
                isStreaming = false
                showInputMode()
                showErrorCard(state.message)
            }
        }
    }

    private fun updateTokenInfo(tokens: Int) {
        if (tokens > 0) {
            binding.tvTokenInfo.text = "~$tokens"
            binding.tvTokenAccuracy.text = "tokens"
            binding.tokenInfoContainer.isVisible = true
        } else {
            binding.tokenInfoContainer.isVisible = false
        }
    }

    private fun openFileViewer(fileAttachment: FileAttachmentMetadata) {
        try {
            val action = ChatFragmentDirections.actionChatFragmentToFileViewerFragment(
                fileAttachment.fileId
            )
            findNavController().navigate(action)
        } catch (e: Exception) {
            Toasty.error(
                requireContext(),
                "Ошибка при открытии файла: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
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


    /**
     * Обновляет chips на основе списка вложений
     */
    private fun updateAttachmentChips(attachments: List<UiAttachment>) {
        val hasAttachments = attachments.isNotEmpty()
        val hasUploading = attachments.any { it.uploadStatus == UploadStatus.UPLOADING }

        // Показываем/скрываем контейнер
        binding.attachmentsScrollView.isVisible = hasAttachments

        // Показываем loader только если есть загружающиеся файлы
        binding.fileLoadIndicator.isVisible = hasUploading

        if (!hasAttachments) {
            binding.attachmentsChipGroup.removeAllViews()
            return
        }

        val chipGroup = binding.attachmentsChipGroup

        // Удаляем chips, которых нет в новом списке
        val existingChipIds = chipGroup.children
            .filterIsInstance<Chip>()
            .mapNotNull { it.tag as? String }
            .toSet()

        val newAttachmentIds = attachments.map { it.id }.toSet()

        chipGroup.children
            .filterIsInstance<Chip>()
            .filter { (it.tag as? String) !in newAttachmentIds }
            .forEach { chipGroup.removeView(it) }

        // Добавляем новые chips или обновляем существующие
        attachments.forEach { attachment ->
            val existingChip = chipGroup.children
                .filterIsInstance<Chip>()
                .find { it.tag == attachment.id }

            if (existingChip != null) {
                updateChip(existingChip, attachment)
            } else {
                val newChip = createAttachmentChip(attachment)
                chipGroup.addView(newChip)
            }
        }
    }

    /**
     * Создает новый Chip для вложения
     */
    private fun createAttachmentChip(attachment: UiAttachment): Chip {
        return Chip(requireContext()).apply {
            id = View.generateViewId()
            tag = attachment.id

            // Применяем стиль Material 3 Input Chip
            setChipBackgroundColorResource(R.color.md_theme_light_secondaryContainer)
            setTextColor(
                ContextCompat.getColor(
                    context,
                    R.color.md_theme_light_onSecondaryContainer
                )
            )
            chipStrokeWidth = 0f

            // Устанавливаем контент
            updateChip(this, attachment)

            // Обработка удаления
            setOnCloseIconClickListener {
                viewModel.removeAttachment(attachment.id)
            }

            // Обработка клика - показать детали
            setOnClickListener {
                showAttachmentDetails(attachment)
            }
        }
    }

    /**
     * Обновляет содержимое Chip
     */
    private fun updateChip(chip: Chip, attachment: UiAttachment) {
        // Текст с названием и размером
        chip.text = buildString { "${attachment.displayName} • ${formatFileSize(attachment.size)}" }

        // Иконка файла
        chip.chipIcon = AppCompatResources.getDrawable(
            requireContext(),
            R.drawable.ic_file_text
        )

        // Цвет иконки в зависимости от статуса
        chip.setChipIconTintResource(
            when (attachment.uploadStatus) {
                UploadStatus.UPLOADING -> R.color.md_theme_light_primary
                UploadStatus.UPLOADED -> R.color.success
                UploadStatus.FAILED -> R.color.md_theme_light_error
                else -> R.color.md_theme_light_onSecondaryContainer
            }
        )

        // Кнопка закрытия видна только если файл не загружается
        chip.isCloseIconVisible = attachment.uploadStatus != UploadStatus.UPLOADING
        chip.closeIcon = AppCompatResources.getDrawable(
            requireContext(),
            R.drawable.outline_close_24
        )
        chip.setCloseIconTintResource(R.color.md_theme_light_onSecondaryContainer)

        // Прозрачность для failed статуса
        chip.alpha = if (attachment.uploadStatus == UploadStatus.FAILED) 0.6f else 1f

        // Отключаем chip при загрузке
        chip.isEnabled = attachment.uploadStatus != UploadStatus.UPLOADING
    }

    /**
     * Показывает детали вложения в диалоге
     */
    private fun showAttachmentDetails(attachment: UiAttachment) {
        val statusText = when (attachment.uploadStatus) {
            UploadStatus.PENDING -> "Ожидает загрузки"
            UploadStatus.UPLOADING -> "Загружается..."
            UploadStatus.UPLOADED -> "Загружено"
            UploadStatus.FAILED -> "Ошибка загрузки"
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(attachment.displayName)
            .setMessage(
                "Размер: ${formatFileSize(attachment.size)}\n" +
                        "Тип: ${attachment.mimeType ?: "неизвестно"}\n" +
                        "Статус: $statusText"
            )
            .setPositiveButton("OK", null)
            .apply {
                if (attachment.uploadStatus != UploadStatus.UPLOADING) {
                    setNeutralButton("Удалить") { _, _ ->
                        viewModel.removeAttachment(attachment.id)
                    }
                }
            }
            .show()
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    /**
     * Обрабатывает состояния чата
     */
    private fun handleChatUiState(state: ChatUiState) {
        when (state) {
            is ChatUiState.Idle -> {
                isStreaming = false
                showInputMode()
            }

            is ChatUiState.Streaming -> {
                isStreaming = true
                showGeneratingMode("Генерация ответа...")
            }

            is ChatUiState.BackgroundStreaming -> {
                isStreaming = true
                showGeneratingMode("Генерация в фоне...")
            }

            is ChatUiState.Completed -> {
                isStreaming = false
                showInputMode()
            }

            is ChatUiState.Cancelled -> {
                isStreaming = false
                showInputMode()
                Toasty.info(requireContext(), "Генерация отменена", Toast.LENGTH_SHORT).show()
            }

            is ChatUiState.RestoredFromBackground -> {
                isStreaming = true
                showGeneratingMode("Восстановление...")
            }

            is ChatUiState.Error -> {
                isStreaming = false
                showInputMode()
                showErrorCard(state.message)
            }
        }
    }

    /**
     * Показывает режим ввода
     */
    private fun showInputMode() {
        // ✅ Добавляем проверки на null
        _binding?.let { binding ->
            binding.cancelCard.animateVisibility(false)
            binding.inputCard.animateVisibility(true)

            binding.btnSend.isEnabled = true
            binding.btnAttachFile.isEnabled = true
            binding.etUserInput.isEnabled = true
        }
    }

    /**
      * Показывает режим генерации
      */
    private fun showGeneratingMode(statusText: String) {
        // ✅ Добавляем проверки на null
        _binding?.let { binding ->
            binding.inputCard.animateVisibility(false)
            binding.tokenInfoContainer.animateVisibility(false)

            binding.cancelCard.animateVisibility(true)
            binding.tvGeneratingStatus.text = statusText
        }
    }

    /**
     * Анимация появления/исчезновения View
     */
    private fun View.animateVisibility(visible: Boolean, duration: Long = 200) {
        if (visible && isVisible) return
        if (!visible && !isVisible) return

        if (visible) {
            isVisible = true
            alpha = 0f
            scaleX = 0.95f
            scaleY = 0.95f

            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(duration)
                .start()
        } else {
            animate()
                .alpha(0f)
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(duration)
                .withEndAction { isVisible = false }
                .start()
        }
    }

    private fun showErrorCard(message: String) {
        binding.tvErrorMessage.text = message
        binding.errorCard.animateVisibility(true)

        // Автоматически скрываем через 5 секунд
        binding.errorCard.postDelayed({
            hideErrorCard()
        }, 5000)
    }

    private fun hideErrorCard() {
        // ✅ Добавляем проверку на null для предотвращения NPE
        _binding?.errorCard?.animateVisibility(false)
    }

    private fun shareChatContent(content: String) {
        try {
            val fileName = "chat_export_${System.currentTimeMillis()}.md"
            val cacheDir = requireContext().cacheDir
            val file = java.io.File(cacheDir, fileName)

            file.writer().use { writer ->
                writer.write(content)
            }

            val fileUri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )

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
            Toasty.error(
                requireContext(),
                "Ошибка при экспорте: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun updateToolbarTitle(modelName: String?) {
        val actionBar = (activity as? AppCompatActivity)?.supportActionBar
        val title = modelName ?: getString(R.string.title_llm_interaction_model_not_selected)
        actionBar?.title = title
    }

    private fun handleError(error: Throwable) {
        error.printStackTrace()
        when (error) {
            is DomainError.Api -> {
                when (error.code) {
                    401, 403 -> showApiErrorDialog(error)
                    429 -> showErrorCard("Превышен лимит запросов. ${error.detailedMessage}")
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
                showErrorCard(error.message ?: "Неизвестная ошибка")
            }
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
                        findNavController().navigate(
                            ChatFragmentDirections.actionNavChatToNavSettings()
                        )
                        dialog.dismiss()
                    }
                }
            }
            .show()
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

    private fun navigateToEditSystemPrompt(conversationId: String) {
        val currentPrompt = viewModel.chatData.value.systemPrompt
        val action = ChatFragmentDirections.actionNavChatToEditSystemPromptFragment(
            currentPrompt,
            conversationId
        )
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
