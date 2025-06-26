package com.arny.aipromptmaster.presentation.ui.chathistory

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DefaultItemAnimator
import com.arny.aipromptmaster.core.di.scopes.viewModelFactory
import com.arny.aipromptmaster.domain.models.Chat
import com.arny.aipromptmaster.domain.results.DataResult
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.databinding.FragmentHistoryBinding
import com.arny.aipromptmaster.presentation.utils.autoClean
import com.arny.aipromptmaster.presentation.utils.strings.SimpleString
import com.arny.aipromptmaster.presentation.utils.toastMessage
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
        requireActivity().title = getString(R.string.title_history)
        initUI()
        observeData()
        setupRefresh()
        viewModel.loadChats()
    }

    private fun initUI() {
        binding.chatRecyclerView.itemAnimator = DefaultItemAnimator().apply {
            addDuration = 250
            removeDuration = 250
        }
        val fabMargin = resources.getDimensionPixelSize(R.dimen.fab_margin_bottom)
        binding.newChatFab.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = fabMargin
        }
        binding.chatRecyclerView.adapter = adapter
        Log.d("ChatHistory", "Adapter set: $adapter")
        binding.chatRecyclerView.setPadding(
            /* left = */ 0,
            /* top = */ 0,
            /* right = */ 0,
            /* bottom = */ resources.getDimensionPixelSize(R.dimen.bottom_nav_height)
        )
        binding.newChatFab.setOnClickListener {
            findNavController().navigate(
                ChatHistoryFragmentDirections.actionNavHistoryToNavChat(null)
            )
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is ChatHistoryUIState.Loading -> {
                            binding.swipeRefresh.isRefreshing = true
                        }

                        is ChatHistoryUIState.Success -> {
                            binding.swipeRefresh.isRefreshing = false
                            binding.emptyView.visibility =
                                if (state.chats.isEmpty()) View.VISIBLE else View.GONE
                            binding.chatRecyclerView.visibility =
                                if (state.chats.isNotEmpty()) View.VISIBLE else View.GONE
                            adapter.updateAsync(createItems(state.chats))
                        }

                        is ChatHistoryUIState.Error -> {
                            binding.swipeRefresh.isRefreshing = false
                            toastMessage(state.message)
                            binding.emptyView.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }

    private fun createItems(chats: List<Chat>): List<ChatItem> {
        return chats.map { chat ->
            ChatItem(chat) { clickedChat ->
                toastMessage(SimpleString(clickedChat.name))
            }
        }
    }

    private fun setupRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadChats()
        }
        binding.swipeRefresh.setColorSchemeResources(
            R.color.colorPrimary,
            R.color.colorPrimaryDark,
            R.color.colorAccent
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
