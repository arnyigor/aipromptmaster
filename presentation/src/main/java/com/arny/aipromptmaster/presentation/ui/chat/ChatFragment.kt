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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.arny.aipromptmaster.core.di.scopes.viewModelFactory
import com.arny.aipromptmaster.domain.models.errors.DomainError
import com.arny.aipromptmaster.domain.services.FileProcessingResult
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.ui.editprompt.EditSystemPromptFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.android.support.AndroidSupportInjection
import dagger.assisted.AssistedFactory
import es.dmoral.toasty.Toasty
import io.noties.markwon.Markwon
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

class ChatFragment : Fragment() {
    private lateinit var filePickerLauncher: ActivityResultLauncher<String>
    private var uploadJob: Job? = null
    private val args: ChatFragmentArgs by navArgs()

    @AssistedFactory
    internal interface ViewModelFactory {
        fun create(chatid: String?): ChatViewModel
    }

    @Inject
    lateinit var markwon: Markwon

    @Inject
    internal lateinit var viewModelFactory: ViewModelFactory

    private val viewModel: ChatViewModel by viewModelFactory {
        viewModelFactory.create(args.chatid)
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        filePickerLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let { processFile(it) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )

            setContent {
                ChatComposeScreen(
                    viewModel = viewModel,
                    markwon = markwon,
                    onNavigateToFileViewer = { fileId ->
                        val action = ChatFragmentDirections
                            .actionChatFragmentToFileViewerFragment(fileId)
                        findNavController().navigate(action)
                    },
                    onAttachFileClick = {
                        filePickerLauncher.launch("*/*")
                    },
                    onNavigateToSettings = {
                        findNavController().navigate(
                            ChatFragmentDirections.actionNavChatToNavSettings()
                        )
                    },
                    onCopyToClipboard = { text ->
                        copyToClipboard(text)
                    }
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initMenu()
        setupFragmentResultListener()
        observeViewModelEvents()
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
                        viewModel.onClearChatClicked()
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

    private fun observeViewModelEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.newConversationIdEvent.collect { newId ->
                navigateToEditSystemPrompt(newId)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiEvents.collect { event ->
                when (event) {
                    is ChatUiEvent.ShowError -> { }
                    is ChatUiEvent.ShareChat -> shareChatContent(event.content)
                    ChatUiEvent.RequestClearChat -> {}
                }
            }
        }
    }

    private fun processFile(uri: Uri) {
        uploadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                viewModel.processFileFromUri(uri).collect { result ->
                    when (result) {
                        is FileProcessingResult.Complete -> {
                            Toasty.success(
                                requireContext(),
                                "Файл ${result.fileAttachment.fileName} добавлен к чату",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        is FileProcessingResult.Error -> {
                            Toasty.error(
                                requireContext(),
                                result.message,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                Toasty.error(
                    requireContext(),
                    e.message ?: "Неизвестная ошибка",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun shareChatContent(content: String) {
        try {
            val fileName = "chat_export_${System.currentTimeMillis()}.md"
            val cacheDir = requireContext().cacheDir
            val file = java.io.File(cacheDir, fileName)

            file.writer().use { it.write(content) }

            val fileUri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )

            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, fileUri)
                type = "text/markdown"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(sendIntent, "Поделиться чатом"))
        } catch (e: Exception) {
            Toasty.error(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("AI Response", text))
        Toasty.success(requireContext(), "Скопировано", Toast.LENGTH_SHORT).show()
    }

    private fun navigateToEditSystemPrompt(conversationId: String) {
        val action = ChatFragmentDirections.actionNavChatToEditSystemPromptFragment(
            viewModel.uiState.value.systemPrompt,
            conversationId
        )
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        uploadJob?.cancel()
        super.onDestroyView()
    }
}
