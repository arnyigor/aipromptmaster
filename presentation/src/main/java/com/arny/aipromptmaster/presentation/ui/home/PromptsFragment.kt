package com.arny.aipromptmaster.presentation.ui.home

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.SearchView
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
import com.arny.aipromptmaster.domain.models.strings.StringHolder
import com.arny.aipromptmaster.domain.models.strings.StringHolder.*
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.domain.R as domainR
import com.arny.aipromptmaster.presentation.databinding.FragmentHomeBinding
import com.arny.aipromptmaster.presentation.utils.asString
import com.arny.aipromptmaster.presentation.utils.autoClean
import com.arny.aipromptmaster.presentation.utils.getParcelableCompat
import com.arny.aipromptmaster.presentation.utils.hideKeyboard
import com.arny.aipromptmaster.presentation.utils.launchWhenCreated
import com.arny.aipromptmaster.presentation.utils.toastMessage
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import dagger.android.support.AndroidSupportInjection
import dagger.assisted.AssistedFactory
import es.dmoral.toasty.Toasty
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
        observeViewModel()
        listenForFilterResults()
        setupViews()
    }

    private fun setupViews() {
        with(binding) {
            recyclerView.apply {
                adapter = promptsAdapter
            }

            swipeRefresh.setOnRefreshListener {
                promptsAdapter.refresh()
            }

            fabAdd.setOnClickListener {
                findNavController().navigate(
                    PromptsFragmentDirections.actionNavHomeToAddPromptFragment()
                )
            }

            btnRetry.setOnClickListener {
                viewModel.synchronize()
            }

            btnSort.setOnClickListener {
                viewModel.onSortButtonClicked()
            }

            chipGroupFilters.setOnCheckedChangeListener { _, checkedId ->
                when (checkedId) {
                    R.id.chipAll -> viewModel.setStatusFilter(null) // Статус null = "Все"
                    R.id.chipFavorites -> viewModel.setStatusFilter("favorite")
                }
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
            viewModel.sortDataState.collect { sortData ->
                binding.btnSort.isVisible = sortData != null
            }
        }
        launchWhenCreated {
            viewModel.event.collect { event ->
                updateEvent(event)
            }
        }
        launchWhenCreated {
            viewModel.feedbackResult.collect { result ->
                result.fold(
                    onSuccess = {
                        Toasty.success(
                            requireContext(),
                            R.string.feedback_sent_successfully,
                            Toast.LENGTH_LONG
                        ).show()
                    },
                    onFailure = { error ->
                        Toasty.error(
                            requireContext(),
                            getString(R.string.feedback_send_error, error.message),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        }

        launchWhenCreated {
            viewModel.searchState.collect { state ->
                updateDynamicFilterChips(state)
                updateStaticFilterChips(state)
            }
        }
    }

// PromptsFragment.kt

    private fun updateDynamicFilterChips(state: SearchState) {
        binding.chipGroupDynamicFilters.removeAllViews()

        // Добавляем чип для категории, если она выбрана
        state.category?.let { category ->
            val chip = createFilterChip(category)
            binding.chipGroupDynamicFilters.addView(chip)
        }
        // Добавляем чипы для каждого тега
        state.tags.forEach { tag ->
            val chip = createFilterChip(tag)
            binding.chipGroupDynamicFilters.addView(chip)
        }
    }


    // НОВЫЙ МЕТОД для создания одного чипа
    private fun createFilterChip(text: String): Chip {
        return Chip(requireContext()).apply {
            this.text = text
            isCloseIconVisible = true
            setOnCloseIconClickListener {
                viewModel.removeFilter(text)
            }
        }
    }

    // НОВЫЙ МЕТОД для синхронизации состояния статических чипов
    private fun updateStaticFilterChips(state: SearchState) {
        // Временно отключаем слушатель, чтобы программное изменение не вызывало его снова
        binding.chipGroupFilters.setOnCheckedChangeListener(null)

        when {
            // Если выбран статус "Избранное"
            state.status == "favorite" -> {
                binding.chipGroupFilters.check(R.id.chipFavorites)
            }
            // Если нет НИКАКИХ фильтров (ни статуса, ни кастомных) - выбраны "Все"
            state.status == null && state.category == null && state.tags.isEmpty() -> {
                binding.chipGroupFilters.check(R.id.chipAll)
            }
            // Если есть кастомные фильтры (категория или теги), то ни "Все", ни "Избранное" не выбраны
            else -> {
                binding.chipGroupFilters.clearCheck()
            }
        }

        // Возвращаем слушатель обратно
        binding.chipGroupFilters.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.chipAll -> viewModel.setStatusFilter(null)
                R.id.chipFavorites -> viewModel.setStatusFilter("favorite")
            }
        }
    }

    private fun updateEvent(event: PromptsUiEvent) {
        with(binding) {
            when (event) {
                is PromptsUiEvent.SyncConflicts -> {
                    recyclerView.isVisible = true
                    showSyncConflictsDialog(event.conflicts)
                    requireActivity().invalidateOptionsMenu()
                }

                is PromptsUiEvent.ShowError -> {
                    errorView.isVisible = true
                    tvError.text = event.stringHolder.asString(requireContext())
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
                    toastMessage(
                        Formatted(
                            R.string.sync_success_updated_count,
                            listOf(event.updatedCount)
                        )
                    )
                }

                is PromptsUiEvent.OpenSortScreenEvent -> {
                    PromptsFilterSettingsBottomSheet.newInstance(
                        event.sortData,
                        event.currentFilters
                    ).show(childFragmentManager, PromptsFilterSettingsBottomSheet.TAG)
                }

                is PromptsUiEvent.PromptUpdated -> {
                    adapterRefresh()
                }

                is PromptsUiEvent.ShowInfoMessage ->{
                    toastMessage(event.stringHolder)
                }

                PromptsUiEvent.SyncFinished -> {
                    progressSync.isVisible = false
                    requireActivity().invalidateOptionsMenu()
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
        val dialogView =
            LayoutInflater.from(requireContext()).inflate(R.layout.dialog_feedback, null)
        val editText = dialogView.findViewById<TextInputEditText>(R.id.et_feedback)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.send_feedback_question))
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_send) { _, _ ->
                val feedbackText = editText.text.toString().trim()
                if (feedbackText.isNotEmpty()) {
                    viewModel.sendFeedback(feedbackText)
                } else {
                    Toasty.warning(
                        requireContext(),
                        getString(android.R.string.cancel),
                        Toast.LENGTH_SHORT
                    ).show()
                }
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

    private fun showError(stringHolder: StringHolder) {
        val errorMessage = stringHolder.asString(requireContext())
        with(binding) {
            // При показе ошибки скрываем все остальные состояния
            recyclerView.isVisible = false
            tvEmpty.isVisible = false
            errorView.isVisible = true
            tvError.text = errorMessage
        }
        Snackbar.make(
            binding.root,
            errorMessage,
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

    private fun listenForFilterResults() {
        childFragmentManager.setFragmentResultListener(
            PromptsFilterSettingsBottomSheet.REQUEST_KEY,
            viewLifecycleOwner
        ) { requestKey, bundle ->
            val result =
                bundle.getParcelableCompat<FilterSettings>(PromptsFilterSettingsBottomSheet.BUNDLE_KEY)
            result?.let {
                viewModel.applyFiltersFromDialog(
                    category = it.category,
                    tags = it.tags,
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
