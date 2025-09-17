package com.arny.aipromptmaster.presentation.ui.modelsview

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.appcompat.widget.SearchView
import androidx.core.os.BundleCompat
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arny.aipromptmaster.core.di.scopes.viewModelFactory
import com.arny.aipromptmaster.domain.models.LlmModel
import com.arny.aipromptmaster.domain.models.strings.StringHolder
import com.arny.aipromptmaster.domain.results.DataResult
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.databinding.FragmentModelsBinding
import com.arny.aipromptmaster.presentation.ui.models.FilterState
import com.arny.aipromptmaster.presentation.ui.modelsview.ModelsFilterBottomSheetDialog
import com.arny.aipromptmaster.presentation.utils.autoClean
import com.arny.aipromptmaster.presentation.utils.hideKeyboard
import com.arny.aipromptmaster.presentation.utils.launchWhenCreated
import com.arny.aipromptmaster.presentation.utils.toastMessage
import dagger.android.support.AndroidSupportInjection
import dagger.assisted.AssistedFactory
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

class ModelsFragment : Fragment() {

    private var _binding: FragmentModelsBinding? = null
    private val binding get() = _binding!!

    private var searchView: SearchView? = null
    private var searchMenuItem: MenuItem? = null

    @AssistedFactory
    internal interface ViewModelFactory {
        fun create(): ModelsViewModel
    }

    private val modelsAdapter by autoClean {
        LlmModelAdapter(
            onItemClick = { modelId ->
                viewModel.selectModel(modelId)
            },
            onFavoriteClick = { modelId ->
                viewModel.toggleFavorite(modelId)
            }
        )
    }

    @Inject
    internal lateinit var viewModelFactory: ViewModelFactory
    private val viewModel: ModelsViewModel by viewModelFactory { viewModelFactory.create() }

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
        _binding = FragmentModelsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initMenu()
        setupFragmentResultListener()
        observeViewModel()
        setupViews()
    }

    private fun initMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onPrepareMenu(menu: Menu) {
                // Всегда показываем поиск
                menu.findItem(R.id.action_search)?.isVisible = true
            }

            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.models_menu, menu)
                searchMenuItem = menu.findItem(R.id.action_search)
                searchView = searchMenuItem?.actionView as? SearchView
                searchView?.let { searchView ->
                    searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                        override fun onQueryTextSubmit(query: String?): Boolean {
                            // Используем метод для обновления поискового запроса
                            viewModel.updateSearchQuery(query.orEmpty())
                            // Скрываем клавиатуру после поиска
                            searchView.clearFocus()
                            requireActivity().hideKeyboard()
                            return true
                        }

                        override fun onQueryTextChange(newText: String?): Boolean {
                            // Обновляем поисковый запрос в реальном времени
                            viewModel.updateSearchQuery(newText.orEmpty())
                            return true
                        }
                    })

                    searchMenuItem?.setOnActionExpandListener(object :
                        MenuItem.OnActionExpandListener {
                        override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                            return true
                        }

                        override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                            // Очищаем поисковый запрос при сворачивании
                            viewModel.updateSearchQuery("")
                            requireActivity().hideKeyboard()
                            return true
                        }
                    })
                }

            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                when (menuItem.itemId) {
                    R.id.action_search -> true

                    R.id.action_filter -> {
                        showFilterDialog()
                        true
                    }

                    else -> false
                }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupViews() {
        binding.rvModels.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = modelsAdapter
        }
    }

    private fun observeViewModel() {
        launchWhenCreated {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is DataResult.Error<*> -> {
                        binding.progressBar.isVisible = false
                        val exception = state.exception
                        val messageRes = state.messageRes
                        when {
                            exception != null -> {
                                toastMessage(StringHolder.Text(exception.message))
                            }

                            messageRes != null -> {
                                toastMessage(StringHolder.Resource(messageRes))
                            }
                        }
                    }

                    DataResult.Loading -> {
                        binding.progressBar.isVisible = true
                    }

                    is DataResult.Success<List<LlmModel>> -> {
                        updateAdapter(state)
                    }
                }
            }
        }
    }

    private fun updateAdapter(state: DataResult.Success<List<LlmModel>>) {
        // Скрываем прогресс-бар
        binding.progressBar.isVisible = false

        // Обновляем данные
        modelsAdapter.submitList(state.data)

        if (state.data.isNotEmpty()) {
            binding.rvModels.post {
                if (modelsAdapter.itemCount > 0) {
                    binding.rvModels.scrollToPosition(0)
                }
            }
        }
    }

    private fun setupFragmentResultListener() {
        childFragmentManager.setFragmentResultListener(/* requestKey = */
            ModelsFilterBottomSheetDialog.REQUEST_KEY,/* lifecycleOwner = */
            viewLifecycleOwner) { _, bundle ->
            val result = BundleCompat.getParcelable(bundle,ModelsFilterBottomSheetDialog.RESULT_KEY,FilterState::class.java)
            if (result != null) {
                viewModel.applyFilters(result)
            }
        }
    }

    private fun showFilterDialog() {
        val currentFilters = FilterState()
        val dialog = ModelsFilterBottomSheetDialog.newInstance(currentFilters)
        dialog.show(childFragmentManager, ModelsFilterBottomSheetDialog.TAG)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}