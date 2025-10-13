package com.arny.aipromptmaster.presentation.ui.chathistory

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuProvider
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DefaultItemAnimator
import com.arny.aipromptmaster.core.di.scopes.viewModelFactory
import com.arny.aipromptmaster.domain.models.Chat
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.databinding.FragmentHistoryBinding
import com.arny.aipromptmaster.presentation.utils.asString
import com.arny.aipromptmaster.presentation.utils.autoClean
import com.arny.aipromptmaster.presentation.utils.toastMessage
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.xwray.groupie.GroupieAdapter
import dagger.android.support.AndroidSupportInjection
import dagger.assisted.AssistedFactory
import kotlinx.coroutines.launch
import javax.inject.Inject

class ChatHistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val adapter by autoClean {
        GroupieAdapter()
    }

    @AssistedFactory
    internal interface ViewModelFactory {
        fun create(): ChatHistoryViewModel
    }

    @Inject
    internal lateinit var viewModelFactory: ViewModelFactory
    private val viewModel: ChatHistoryViewModel by viewModelFactory { viewModelFactory.create() }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initMenu()
        initUI()
        observeData()
    }

    private fun initMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onPrepareMenu(menu: Menu) {
                // Обновляем заголовок toolbar
                requireActivity().title = getString(R.string.title_history)
            }

            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // Для истории чата меню не требуется
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun initUI() {
        // ✅ Добавляем проверки на null
        _binding?.let { binding ->
            binding.chatRecyclerView.itemAnimator = DefaultItemAnimator().apply {
                addDuration = 250
                removeDuration = 250
            }
            val fabMargin = resources.getDimensionPixelSize(R.dimen.fab_margin_bottom)
            binding.newChatFab.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = fabMargin
            }
            binding.chatRecyclerView.adapter = adapter
            binding.chatRecyclerView.setPadding(
                /* left = */ 0,
                /* top = */ 0,
                /* right = */ 0,
                /* bottom = */ resources.getDimensionPixelSize(R.dimen.bottom_nav_height)
            )
            binding.newChatFab.setOnClickListener {
                findNavController().navigate(
                    ChatHistoryFragmentDirections.actionChatHistoryFragmentToNavChat(null)
                )
            }
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is ChatHistoryUIState.Loading -> {
                        }

                        is ChatHistoryUIState.Success -> {
                            // ✅ Добавляем проверки на null
                            _binding?.let { binding ->
                                binding.emptyView.visibility =
                                    if (state.chats.isEmpty()) View.VISIBLE else View.GONE
                                binding.chatRecyclerView.visibility =
                                    if (state.chats.isNotEmpty()) View.VISIBLE else View.GONE
                                adapter.updateAsync(createItems(state.chats))
                            }
                        }

                        is ChatHistoryUIState.Error -> {
                            toastMessage(
                                state.stringHolder?.asString(requireContext()) ?: "Ошибка загрузки чатов"
                            )
                            // ✅ Добавляем проверки на null
                            _binding?.emptyView?.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }

    private fun createItems(chats: List<Chat>): List<ChatItem> {
        return chats.map { chat ->
            ChatItem(
                chat = chat,
                onClick = { clickedChat ->
                    findNavController().navigate(
                        ChatHistoryFragmentDirections.actionChatHistoryFragmentToNavChat(clickedChat.conversationId)
                    )
                },
                // ПЕРЕДАЕМ РЕАЛИЗАЦИЮ ДЛЯ ДОЛГОГО НАЖАТИЯ
                onLongClick = { longClickedChat ->
                    showDeleteConfirmationDialog(longClickedChat)
                }
            )
        }
    }

    private fun showDeleteConfirmationDialog(chat: Chat) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_chat_dialog_title)
            .setMessage(getString(R.string.delete_chat_dialog_message, chat.name))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.deleteConversation(chat.conversationId)
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
