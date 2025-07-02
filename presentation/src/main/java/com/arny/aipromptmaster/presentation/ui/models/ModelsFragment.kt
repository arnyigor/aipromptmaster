package com.arny.aipromptmaster.presentation.ui.models

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.arny.aipromptmaster.core.di.scopes.viewModelFactory
import com.arny.aipromptmaster.domain.models.LLMModel
import com.arny.aipromptmaster.domain.results.DataResult
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.databinding.FragmentModelsBinding
import com.arny.aipromptmaster.presentation.ui.chat.ChatFragmentDirections
import com.arny.aipromptmaster.presentation.utils.autoClean
import com.arny.aipromptmaster.presentation.utils.launchWhenCreated
import com.arny.aipromptmaster.presentation.utils.strings.ResourceString
import com.arny.aipromptmaster.presentation.utils.strings.SimpleString
import com.arny.aipromptmaster.presentation.utils.toastMessage
import com.xwray.groupie.GroupieAdapter
import dagger.android.support.AndroidSupportInjection
import dagger.assisted.AssistedFactory
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

class ModelsFragment : Fragment() {

    private var _binding: FragmentModelsBinding? = null
    private val binding get() = _binding!!

    @AssistedFactory
    internal interface ViewModelFactory {
        fun create(): ModelsViewModel
    }

    private val modelsAdapter by autoClean { GroupieAdapter() }

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
        setupViews()
        observeViewModel()
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
                            ModelsFragmentDirections.actionModelsFragmentToSettingsFragment()
                        )
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
                        when {
                            state.exception != null -> {
                                toastMessage(SimpleString(state.exception!!.message))
                            }

                            state.messageRes != null -> {
                                toastMessage(ResourceString(state.messageRes!!))
                            }
                        }
                    }

                    DataResult.Loading -> {
                        binding.progressBar.isVisible = true
                    }

                    is DataResult.Success<List<LLMModel>> -> {
                        binding.progressBar.isVisible = false
                        modelsAdapter.updateAsync(createItems(state.data))
                    }
                }
            }
        }
    }

    private fun createItems(data: List<LLMModel>): List<ModelItem> {
       return data.map { model ->
            ModelItem(model) { m ->
                viewModel.selectModel(m)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}