package com.arny.aipromptmaster.presentation.ui.chat

import android.content.Context
import android.graphics.PorterDuff
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.arny.aipromptmaster.core.di.scopes.viewModelFactory
import com.arny.aipromptmaster.domain.results.DataResult
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.databinding.FragmentChatBinding
import com.arny.aipromptmaster.presentation.utils.autoClean
import com.arny.aipromptmaster.presentation.utils.getColorFromAttr
import com.arny.aipromptmaster.presentation.utils.launchWhenCreated
import com.arny.aipromptmaster.presentation.utils.setTextColorRes
import com.xwray.groupie.GroupieAdapter
import dagger.android.support.AndroidSupportInjection
import dagger.assisted.AssistedFactory
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

class ChatFragment : Fragment() {
    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    @AssistedFactory
    internal interface ViewModelFactory {
        fun create(): ChatViewModel
    }

    private val groupAdapter by autoClean { GroupieAdapter() }

    @Inject
    internal lateinit var viewModelFactory: ViewModelFactory
    private val viewModel: ChatViewModel by viewModelFactory { viewModelFactory.create() }

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
        _binding = FragmentChatBinding.inflate(inflater, container, false)
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
                            ChatFragmentDirections.actionNavChatToSettingsFragment()
                        )
                        true
                    }

                    else -> false
                }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupViews() {
        binding.rvChat.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = groupAdapter
        }

        binding.btnSend.setOnClickListener {
            val message = binding.etUserInput.text.toString()
            if (message.isNotBlank()) {
                // Добавляем сообщение пользователя
                groupAdapter.add(UserMessageItem(message))
                // Прокручиваем к последнему сообщению
                binding.rvChat.smoothScrollToPosition(groupAdapter.itemCount - 1)
                // Очищаем поле ввода
                binding.etUserInput.text?.clear()
                // Отправляем сообщение в ViewModel
                viewModel.sendMessage(message)
                showSendingState(true)
            }
        }

        binding.btnModelSettings.setOnClickListener {
            findNavController().navigate(
                ChatFragmentDirections.actionNavChatToModelsFragment()
            )
        }
    }

    private fun showSendingState(isSending: Boolean) {
        Log.i(this::class.java.simpleName, "showSendingState: isSending:$isSending")
        if (isSending) {
            // Начало отправки
            binding.btnSend.isEnabled = false
            binding.btnSend.visibility = View.INVISIBLE // Скрываем, но оставляем занимаемое место
            binding.progressBarSend.visibility = View.VISIBLE
        } else {
            // Окончание отправки
            binding.btnSend.isEnabled = true
            binding.btnSend.visibility = View.VISIBLE
            binding.progressBarSend.visibility = View.GONE
        }
    }

    private fun observeViewModel() {
        launchWhenCreated {
            viewModel.uiState.collectLatest { state ->
                Log.d(this::class.java.simpleName, "observeViewModel: state=$state")
                // 1. Обновляем весь список целиком. Groupie/ListAdapter сам найдет разницу.
                // Это надежнее, чем groupAdapter.add()
                groupAdapter.update(state.messages.map { UserMessageItem(it.content) })
                showSendingState(false)

                // Прокручиваем к последнему сообщению, если список изменился
                if (groupAdapter.itemCount > 0) {
                    binding.rvChat.smoothScrollToPosition(groupAdapter.itemCount - 1)
                }

                // 2. Показываем/скрываем ProgressBar
                binding.progressBarSend.isVisible = state.isLoading

                // 3. Показываем ошибку (например, в SnackBar)
                state.error?.let { error ->
                    // Создаем сообщение для пользователя
                    val errorMessage = when (error) {
                        is IllegalArgumentException -> "Ошибка: Неверный API ключ. Проверьте настройки."
                        else -> error.message ?: "Произошла неизвестная ошибка"
                    }

                    Toasty.error(requireContext(), errorMessage, Toast.LENGTH_LONG).show()

                    // СРАЗУ ЖЕ сообщаем ViewModel, что ошибка показана.
                    // Это предотвратит повторный показ SnackBar при каждом незначительном обновлении стейта.
                    viewModel.errorShown()
                }
            }
        }

        launchWhenCreated {
            viewModel.selectedModelResult.collectLatest { modelResult ->
                Log.d(this::class.java.simpleName, "observeViewModel: modelResult:$modelResult")
                when (modelResult) {
                    is DataResult.Error<*> -> {
                        binding.btnSend.isEnabled = false
                        setErrorColor(true)
                    }

                    DataResult.Loading -> {}
                    is DataResult.Success<*> -> {
                        binding.btnSend.isEnabled = true
                        setErrorColor(false)
                        showSendingState(false)
                    }
                }
            }
        }
    }

    private fun setErrorColor(isError: Boolean) {
        if (isError) {
            binding.btnModelSettings.setTextColorRes(R.color.red_error)
        } else {
            val attr =
                requireContext().getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
            binding.btnModelSettings.setTextColor(attr)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}