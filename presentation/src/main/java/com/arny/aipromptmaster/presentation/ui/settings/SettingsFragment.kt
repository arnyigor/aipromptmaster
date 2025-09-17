package com.arny.aipromptmaster.presentation.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.arny.aipromptmaster.core.di.scopes.viewModelFactory
import com.arny.aipromptmaster.presentation.databinding.FragmentSettingsBinding
import com.arny.aipromptmaster.presentation.utils.launchWhenStarted
import com.arny.aipromptmaster.presentation.utils.toastMessage
import dagger.android.support.AndroidSupportInjection
import dagger.assisted.AssistedFactory
import javax.inject.Inject
import androidx.core.net.toUri

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    // URL для получения ключей OpenRouter
    private companion object {
        const val OPEN_ROUTER_KEYS_URL = "https://openrouter.ai/keys"
    }

    @AssistedFactory
    internal interface ViewModelFactory {
        fun create(): SettingsViewModel
    }

    @Inject
    internal lateinit var viewModelFactory: ViewModelFactory
    private val viewModel: SettingsViewModel by viewModelFactory { viewModelFactory.create() }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        observeViewModel()
        viewModel.loadApiKey()
    }

    private fun setupViews() {
        binding.saveButton.setOnClickListener {
            val apiKey = binding.apiKeyEditText.text?.toString()?.trim()
            viewModel.saveApiKey(apiKey)
        }
        binding.getApiKeyLink.setOnClickListener {
            openUrlInBrowser()
        }
    }

    private fun openUrlInBrowser() {
        val intent = Intent(Intent.ACTION_VIEW, OPEN_ROUTER_KEYS_URL.toUri())
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            e.printStackTrace()
            // Это редкий случай, когда на устройстве нет браузера.
            // Сообщаем пользователю об ошибке.
            Toast.makeText(requireContext(), "Не удалось найти браузер для открытия ссылки", Toast.LENGTH_LONG).show()
        }
    }

    private fun observeViewModel() {
        launchWhenStarted {
            viewModel.uiState.collect { state ->
                when (state) {
                    is SettingsUIState.Idle -> {}
                    is SettingsUIState.Success -> {
                        binding.apiKeyEditText.setText(state.apiKey)
                    }

                    is SettingsUIState.Error -> {
                        toastMessage(state.stringHolder)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}