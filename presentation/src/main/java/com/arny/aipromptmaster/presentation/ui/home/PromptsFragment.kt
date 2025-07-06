package com.arny.aipromptmaster.presentation.ui.home

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
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import com.arny.aipromptmaster.core.di.scopes.viewModelFactory
import com.arny.aipromptmaster.domain.models.AppConstants
import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.models.SyncConflict
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.databinding.FragmentHomeBinding
import com.arny.aipromptmaster.presentation.utils.autoClean
import com.arny.aipromptmaster.presentation.utils.hideKeyboard
import com.arny.aipromptmaster.presentation.utils.launchWhenCreated
import com.arny.aipromptmaster.presentation.utils.strings.IWrappedString
import com.arny.aipromptmaster.presentation.utils.strings.ResourceString
import com.arny.aipromptmaster.presentation.utils.toastMessage
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.android.support.AndroidSupportInjection
import dagger.assisted.AssistedFactory
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

class PromptsFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    @AssistedFactory
    internal interface ViewModelFactory {
        fun create(): PromptsViewModel
    }

    @Inject
    internal lateinit var viewModelFactory: ViewModelFactory
    private val viewModel: PromptsViewModel by viewModelFactory { viewModelFactory.create() }

    private var searchView: SearchView? = null
    private var searchMenuItem: MenuItem? = null

    private val promptsAdapter by autoClean {
        PromptsAdapter(
            onPromptClick = { showPromptDetails(it) },
            onPromptLongClick = { showPromptOptions(it) },
            onFavoriteClick = { prompt ->
                viewModel.toggleFavorite(prompt.id)
            },
        )
    }

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
        setFragmentResultListener(AppConstants.REQ_KEY_PROMPT_VIEW_FAV) { key, bundle ->
            val promptId = bundle.getString(AppConstants.REQ_KEY_PROMPT_ID)
            viewModel.updateFavorite(promptId)
        }
        initMenu()
        setupViews()
        observeViewModel()
    }

    private fun setupViews() {
        with(binding) {
            recyclerView.apply {
                adapter = promptsAdapter
            }

            swipeRefresh.setOnRefreshListener {
                promptsAdapter.refresh()
            }

            updateChipGroup()

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

    private fun FragmentHomeBinding.updateChipGroup() {
        chipGroupFilters.setOnCheckedChangeListener { group, checkedId ->
            // Получаем текущий поисковый запрос, чтобы не потерять его
            // Это не самый лучший подход, лучше, чтобы ViewModel сама его хранила,
            // но для быстрого исправления подойдет. В идеале UI не должен знать о деталях состояния.
            // В нашей новой ViewModel это уже так, поэтому мы можем просто вызвать applyFilters.

            when (checkedId) {
                // Если выбраны "Все", мы сбрасываем фильтр по статусу
                R.id.chipAll -> viewModel.applyFilters(
                    category = viewModel.searchState.value.category, // Сохраняем другие фильтры
                    status = null, // Сбрасываем только статус
                    tags = viewModel.searchState.value.tags
                )
                // Если выбраны "Избранные", устанавливаем фильтр по статусу
                R.id.chipFavorites -> viewModel.applyFilters(
                    category = viewModel.searchState.value.category,
                    status = "favorite", // Устанавливаем статус
                    tags = viewModel.searchState.value.tags
                )
                // Если ни один чип не выбран (пользователь снял выбор)
                View.NO_ID -> viewModel.applyFilters(
                    category = viewModel.searchState.value.category,
                    status = null,
                    tags = viewModel.searchState.value.tags
                )
            }
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
            viewModel.event.collect { event ->
                updateEvent(event)
            }
        }
    }

    private fun updateEvent(event: PromptsUiEvent) {
        with(binding) {
            when (event) {
                is PromptsUiEvent.ShowError -> {
                    showError(event.message)
                }

                is PromptsUiEvent.SyncConflicts -> {
                    recyclerView.isVisible = true
                    showSyncConflictsDialog(event.conflicts)
                    requireActivity().invalidateOptionsMenu()
                }

                PromptsUiEvent.SyncError -> {
                    errorView.isVisible = true
                    tvError.text = getString(R.string.sync_error)
                    requireActivity().invalidateOptionsMenu()
                }

                PromptsUiEvent.SyncInProgress -> {
                    recyclerView.isVisible = true
                    progressSync.isVisible = true
                    requireActivity().invalidateOptionsMenu()
                }

                is PromptsUiEvent.SyncSuccess -> {
                    recyclerView.isVisible = true
                    progressSync.isVisible = false
                    showMessage(getString(R.string.sync_success, event.updatedCount))
                    requireActivity().invalidateOptionsMenu()
                    toastMessage(ResourceString(R.string.sync_success_updated_count, event.updatedCount))
                }

                is PromptsUiEvent.PromptUpdated -> {
                    adapterRefresh()
                }
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
                            // Поиск уже происходит в onQueryTextChange.
                            // Здесь можно просто скрыть клавиатуру для лучшего UX.
                            requireActivity().hideKeyboard()
                            return false // Возвращаем false, чтобы система сама скрыла клавиатуру
                        }

                        override fun onQueryTextChange(newText: String?): Boolean {
                            // Используем новый, сфокусированный метод.
                            // Он просто обновляет состояние в ViewModel, а Flow реагирует на это.
                            viewModel.search(newText.orEmpty())
                            return true
                        }
                    })

                    searchMenuItem?.setOnActionExpandListener(object :
                        MenuItem.OnActionExpandListener {
                        override fun onMenuItemActionExpand(item: MenuItem): Boolean = true

                        override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                            // Используем новый метод для полного сброса поиска и фильтров.
                            // ViewModel сбросит SearchState, и Flow автоматически загрузит полный список.
                            viewModel.resetSearchAndFilters()
                            // Прятать клавиатуру здесь тоже полезно, хотя система часто делает это сама.
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

    private fun updateUiState(state: PromptsUiState) {
        with(binding) {
            // Сначала скрываем все состояния
            recyclerView.isVisible = false
            errorView.isVisible = false
            tvEmpty.isVisible = false
            progressSync.isVisible = false
            swipeRefresh.isRefreshing = false

            when (state) {
                is PromptsUiState.Initial -> {
                    recyclerView.isVisible = true
                }

                is PromptsUiState.Loading -> {
                    recyclerView.isVisible = true
                    swipeRefresh.isRefreshing = true
                }

                is PromptsUiState.Content -> {
                    recyclerView.isVisible = true
                }

                is PromptsUiState.Empty -> {
                    tvEmpty.isVisible = true
                }

                is PromptsUiState.Error -> {
                    errorView.isVisible = true
                    tvError.text = state.error.message
                }
            }
        }
    }

    private fun showPromptDetails(prompt: Prompt) {
        findNavController().navigate(
            PromptsFragmentDirections.actionNavHomeToPromptViewFragment(prompt.id)
        )
    }

    private fun showPromptOptions(prompt: Prompt) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(prompt.title)
            .setItems(R.array.prompt_options) { _, which ->
                when (which) {
                    0 -> {
                    }

                    1 -> {
                        viewModel.deletePrompt(prompt.id)
                        adapterRefresh()
                    }
                }
            }
            .show()
    }

    private fun adapterRefresh() {
        promptsAdapter.refresh()
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
