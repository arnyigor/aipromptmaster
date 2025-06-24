package com.arny.aipromptmaster.presentation.ui.library

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.core.net.toUri
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import com.arny.aipromptmaster.core.di.scopes.viewModelFactory
import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.models.SyncConflict
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.databinding.FragmentHomeBinding
import com.arny.aipromptmaster.presentation.databinding.FragmentLibraryBinding
import com.arny.aipromptmaster.presentation.ui.home.HomeFragmentDirections
import com.arny.aipromptmaster.presentation.ui.home.HomeViewModel
import com.arny.aipromptmaster.presentation.utils.hideKeyboard
import com.arny.aipromptmaster.presentation.utils.launchWhenCreated
import com.arny.aipromptmaster.presentation.utils.strings.IWrappedString
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.android.support.AndroidSupportInjection
import dagger.assisted.AssistedFactory
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject
import kotlin.getValue

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    @AssistedFactory
    internal interface ViewModelFactory {
        fun create(): LibraryViewModel
    }

    @Inject
    internal lateinit var viewModelFactory: ViewModelFactory
    private val viewModel: LibraryViewModel by viewModelFactory { viewModelFactory.create() }

    private var searchView: SearchView? = null
    private var searchMenuItem: MenuItem? = null

    private val promptsAdapter = PromptsAdapter(
        onPromptClick = { showPromptDetails(it) },
        onPromptLongClick = { showPromptOptions(it) },
        onFavoriteClick = { prompt ->
            viewModel.toggleFavorite(prompt.id)
        }
    )

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
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initMenu()
        setupViews()
        observeViewModel()
    }

    private fun setupViews() {
        with(binding) {
            recyclerView.apply {
                adapter = promptsAdapter
            }

            // Setup SwipeRefreshLayout
            swipeRefresh.setOnRefreshListener {
                promptsAdapter.refresh()
            }

            chipGroupFilters.setOnCheckedChangeListener { _, checkedId ->
                when (checkedId) {
                    R.id.chipAll -> viewModel.search()
                    R.id.chipFavorites -> viewModel.search(status = "favorite")
                }
            }

            fabAdd.setOnClickListener {
                // TODO: Navigate to add prompt screen
            }

            btnRetry.setOnClickListener {
                viewModel.synchronize()
            }
        }

        // Handle adapter load states
        promptsAdapter.addLoadStateListener { loadStates ->
            viewModel.handleLoadStates(loadStates, promptsAdapter.itemCount)
        }
    }

    private fun observeViewModel() {
        launchWhenCreated {
            viewModel.promptsFlow.collectLatest { pagingData ->
                promptsAdapter.submitData(pagingData)
            }
        }

        launchWhenCreated {
            viewModel.uiState.collect { state ->
                updateUiState(state)
            }
        }

        launchWhenCreated {
            viewModel.error.collect { error ->
                showError(error)
            }
        }
    }

    private fun initMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onPrepareMenu(menu: Menu) {
                // Всегда показываем поиск
                menu.findItem(R.id.action_search)?.isVisible = true
            }

            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.home_menu, menu)
                searchMenuItem = menu.findItem(R.id.action_search)
                searchView = searchMenuItem?.actionView as? SearchView
                searchView?.let { searchView ->
                    searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                        override fun onQueryTextSubmit(query: String?): Boolean {
                            viewModel.loadPrompts(query.orEmpty())
                            return true
                        }

                        override fun onQueryTextChange(newText: String?): Boolean {
                            viewModel.loadPrompts(newText.orEmpty())
                            return true
                        }
                    })

                    searchMenuItem?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
                        override fun onMenuItemActionExpand(item: MenuItem): Boolean = true

                        override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                            viewModel.loadPrompts(resetAll = true)
                            requireActivity().hideKeyboard()
                            return true
                        }
                    })
                }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                when (menuItem.itemId) {
                    R.id.action_search -> true
                    R.id.action_sync -> {
                        viewModel.synchronize()
                        true
                    }
                    R.id.action_feedback -> {
                        showFeedBackDialog()
                        true
                    }
                    else -> false
                }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun showFeedBackDialog() {
        MaterialAlertDialogBuilder(requireActivity())
            .setTitle(getString(R.string.send_feedback_question))
            .setMessage(getString(R.string.review_current_issues_on_github))
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = getString(R.string.github_issues_link).toUri()
                }
                startActivity(intent)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun updateUiState(state: HomeViewModel.UiState) {
        with(binding) {
            // Сначала скрываем все состояния
            recyclerView.isVisible = false
            errorView.isVisible = false
            tvEmpty.isVisible = false
            progressSync.isVisible = false
            swipeRefresh.isRefreshing = false

            when (state) {
                is HomeViewModel.UiState.Initial -> {
                    recyclerView.isVisible = true
                }

                is HomeViewModel.UiState.Loading -> {
                    recyclerView.isVisible = true
                    swipeRefresh.isRefreshing = true
                }

                is HomeViewModel.UiState.Content -> {
                    recyclerView.isVisible = true
                }

                is HomeViewModel.UiState.Empty -> {
                    tvEmpty.isVisible = true
                }

                is HomeViewModel.UiState.Error -> {
                    errorView.isVisible = true
                    tvError.text = state.error.message
                }

                is HomeViewModel.UiState.SyncInProgress -> {
                    recyclerView.isVisible = true
                    progressSync.isVisible = true
                    requireActivity().invalidateOptionsMenu()
                }

                is HomeViewModel.UiState.SyncError -> {
                    errorView.isVisible = true
                    tvError.text = getString(R.string.sync_error)
                    requireActivity().invalidateOptionsMenu()
                }

                is HomeViewModel.UiState.SyncSuccess -> {
                    recyclerView.isVisible = true
                    showMessage(getString(R.string.sync_success, state.updatedCount))
                    requireActivity().invalidateOptionsMenu()
                }

                is HomeViewModel.UiState.SyncConflicts -> {
                    recyclerView.isVisible = true
                    showSyncConflictsDialog(state.conflicts)
                    requireActivity().invalidateOptionsMenu()
                }
            }
        }
    }

    private fun showPromptDetails(prompt: Prompt) {
        findNavController().navigate(
            HomeFragmentDirections.actionNavHomeToPromptViewFragment(prompt.id)
        )
    }

    private fun showPromptOptions(prompt: Prompt) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(prompt.title)
            .setItems(R.array.prompt_options) { _, which ->
                when (which) {
                    0 -> { /* TODO: Edit */
                    }

                    1 -> viewModel.deletePrompt(prompt.id)
                }
            }
            .show()
    }

    private fun showError(error: IWrappedString) {
        val errorMessage = error.toString(requireContext())
        with(binding) {
            // При показе ошибки скрываем все остальные состояния
            recyclerView.isVisible = false
            tvEmpty.isVisible = false
            errorView.isVisible = true
            tvError.text = errorMessage
        }
        Snackbar.make(
            binding.root,
            errorMessage.orEmpty(),
            Snackbar.LENGTH_LONG
        ).setAction(R.string.retry) {
            viewModel.synchronize()
        }.show()
    }

    private fun showMessage(message: String) {
        Snackbar.make(
            binding.root,
            message,
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun showSyncConflictsDialog(conflicts: List<SyncConflict>) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.sync_conflicts_title)
            .setMessage(getString(R.string.sync_conflicts_message, conflicts.size))
            .setPositiveButton(R.string.resolve_conflicts) { _, _ ->
                // TODO: Navigate to conflicts resolution screen
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
