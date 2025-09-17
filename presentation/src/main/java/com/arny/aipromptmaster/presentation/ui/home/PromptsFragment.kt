package com.arny.aipromptmaster.presentation.ui.home

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
import com.arny.aipromptmaster.domain.models.strings.StringHolder.Formatted
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.databinding.FragmentHomeBinding
import com.arny.aipromptmaster.presentation.utils.asString
import com.arny.aipromptmaster.presentation.utils.autoClean
import com.arny.aipromptmaster.presentation.utils.getParcelableCompat
import com.arny.aipromptmaster.presentation.utils.hideKeyboard
import com.arny.aipromptmaster.presentation.utils.launchWhenCreated
import com.arny.aipromptmaster.presentation.utils.toastMessage
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import dagger.android.support.AndroidSupportInjection
import dagger.assisted.AssistedFactory
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.flow.collectLatest
import java.util.Locale
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
            viewModel.omPromptUpdated(!promptId.isNullOrBlank())
        }
        setFragmentResultListener(AppConstants.REQ_KEY_PROMPT_DELETED) { key, bundle ->
            val promptId = bundle.getString(AppConstants.REQ_KEY_PROMPT_ID)
            viewModel.omPromptUpdated(!promptId.isNullOrBlank())
        }
        setFragmentResultListener(AppConstants.REQ_KEY_PROMPT_ADDED) { key, bundle ->
            val added = bundle.getBoolean(AppConstants.REQ_KEY_PROMPT_ID, false)
            viewModel.omPromptUpdated(added)
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
                    PromptsFragmentDirections.actionNavHomeToAddPromptFragment(null)
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

    private fun createFilterChip(text: String): Chip {
        return Chip(requireContext()).apply {
            this.text = text
            isCloseIconVisible = true
            setOnCloseIconClickListener {
                viewModel.removeFilter(text)
            }
        }
    }

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

                is PromptsUiEvent.ShowInfoMessage -> {
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
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_feedback, null)

        val optionsContainer = dialogView.findViewById<LinearLayout>(R.id.options_container)
        val feedbackForm = dialogView.findViewById<LinearLayout>(R.id.feedback_form)
        val cardQuickFeedback = dialogView.findViewById<MaterialCardView>(R.id.card_quick_feedback)
        val cardGithubIssue = dialogView.findViewById<MaterialCardView>(R.id.card_github_issue)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.send_feedback_question))
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        // Обработчик быстрого фидбека
        cardQuickFeedback.setOnClickListener {
            dialog.dismiss() // Сначала закрываем текущий диалог
            showQuickFeedbackForm() // Затем показываем новый
        }

        // Обработчик GitHub Issues
        cardGithubIssue.setOnClickListener {
            dialog.dismiss()
            openGithubIssues()
        }

        dialog.show()
    }

    private fun showQuickFeedbackForm() {
        // Создаем НОВЫЙ dialogView для формы фидбека
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_feedback, null)

        val optionsContainer = dialogView.findViewById<LinearLayout>(R.id.options_container)
        val feedbackForm = dialogView.findViewById<LinearLayout>(R.id.feedback_form)
        val feedbackTypeSpinner = dialogView.findViewById<AutoCompleteTextView>(R.id.feedback_type_spinner)
        val editText = dialogView.findViewById<TextInputEditText>(R.id.et_feedback)
        val cbIncludeDeviceInfo = dialogView.findViewById<MaterialCheckBox>(R.id.cb_include_device_info)

        // Скрываем опции и показываем форму
        optionsContainer.visibility = View.GONE
        feedbackForm.visibility = View.VISIBLE

        // Настройка спиннера
        setupFeedbackTypeSpinner(feedbackTypeSpinner)

        // Создаем новый диалог с кнопками
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.quick_feedback))
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.action_back) { _, _ ->
                // Возвращаемся к выбору опций
                showFeedBackDialog()
            }
            .setPositiveButton(R.string.action_send) { _, _ ->
                sendQuickFeedback(editText, feedbackTypeSpinner, cbIncludeDeviceInfo)
            }
            .show()
    }

    private fun setupFeedbackTypeSpinner(spinner: AutoCompleteTextView) {
        val feedbackTypes = arrayOf(
            getString(R.string.feedback_type_suggestion),
            getString(R.string.feedback_type_bug_simple),
            getString(R.string.feedback_type_question),
            getString(R.string.feedback_type_other)
        )

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, feedbackTypes)
        spinner.setAdapter(adapter)
        spinner.setText(feedbackTypes[0], false)
    }

    private fun sendQuickFeedback(
        editText: TextInputEditText,
        feedbackTypeSpinner: AutoCompleteTextView,
        cbIncludeDeviceInfo: MaterialCheckBox
    ) {
        val feedbackText = editText.text.toString().trim()
        val feedbackType = feedbackTypeSpinner.text.toString()

        if (feedbackText.isNotEmpty()) {
            val fullFeedback = buildString {
                appendLine("Тип: $feedbackType")
                appendLine()
                appendLine(feedbackText)

                if (cbIncludeDeviceInfo.isChecked) {
                    appendLine()
                    appendLine("--- Информация об устройстве ---")
                    appendLine(getDeviceInfo())
                }
            }

            viewModel.sendFeedback(fullFeedback)

            Toasty.success(
                requireContext(),
                getString(R.string.feedback_sent_success),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toasty.warning(
                requireContext(),
                getString(R.string.feedback_empty_message),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun openGithubIssues() {
        val intent = Intent(Intent.ACTION_VIEW, getString(R.string.github_issues_link).toUri())
        try {
            startActivity(intent)

            // Показываем подсказку
            Toasty.info(
                requireContext(),
                getString(R.string.github_issue_tip),
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            // Копируем ссылку в буфер обмена как fallback
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("GitHub Issues", getString(R.string.github_issues_link))
            clipboard.setPrimaryClip(clip)

            Toasty.error(
                requireContext(),
                getString(R.string.error_opening_browser_link_copied),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun getAppVersion(): String {
        return try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            "${packageInfo.versionName} (${packageInfo.versionCode})"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun getDeviceInfo(): String {
        return buildString {
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("App Version: ${getAppVersion()}")
            appendLine("Locale: ${Locale.getDefault()}")

            try {
                appendLine("Screen: ${getScreenInfo()}")
                appendLine("RAM: ${getAvailableMemory()}")
            } catch (e: Exception) {
                appendLine("Additional info: unavailable")
            }
        }
    }

    private fun getScreenInfo(): String {
        val displayMetrics = resources.displayMetrics
        return "${displayMetrics.widthPixels}x${displayMetrics.heightPixels} (${displayMetrics.densityDpi}dpi)"
    }

    private fun getAvailableMemory(): String {
        val activityManager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return "${memoryInfo.availMem / (1024 * 1024)}MB available"
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
