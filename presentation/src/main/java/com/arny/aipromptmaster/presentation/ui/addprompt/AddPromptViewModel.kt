package com.arny.aipromptmaster.presentation.ui.addprompt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.aipromptmaster.domain.interactors.IPromptsInteractor
import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.models.PromptContent
import com.arny.aipromptmaster.domain.models.strings.StringHolder
import com.arny.aipromptmaster.presentation.R
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AddPromptViewModel @AssistedInject constructor(
    private val interactor: IPromptsInteractor
) : ViewModel() {

    private val _uiState = MutableStateFlow<AddPromptUiState>(AddPromptUiState.Content)
    val uiState: StateFlow<AddPromptUiState> = _uiState.asStateFlow()

    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    private val _event = MutableSharedFlow<AddPromptUiEvent>()
    val event: SharedFlow<AddPromptUiEvent> = _event.asSharedFlow()

    init {
        loadCategories()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            try {
                val categories = interactor.getPromptsSortData().categories
                _categories.value = categories
            } catch (e: Exception) {
                // Handle error silently or show error state
            }
        }
    }

    fun savePrompt(
        title: String,
        description: String?,
        category: String,
        content: PromptContent
    ) {
        viewModelScope.launch {
            try {
                _uiState.value = AddPromptUiState.Loading

                // Validate input
                val validationResult = validateInput(title, category, content)
                if (validationResult != null) {
                    _event.emit(validationResult)
                    _uiState.value = AddPromptUiState.Content
                    return@launch
                }

                // Create prompt
                val prompt = Prompt(
                    title = title,
                    description = description,
                    content = content,
                    category = category,
                    compatibleModels = emptyList(), // Default empty list
                    status = "active"
                )
                interactor.savePrompt(prompt)

                _event.emit(AddPromptUiEvent.PromptSaved)
            } catch (e: Exception) {
                _uiState.value = AddPromptUiState.Error(
                    StringHolder.Resource(R.string.error_saving_prompt)
                )
            }
        }
    }

    private fun validateInput(
        title: String,
        category: String,
        content: PromptContent
    ): AddPromptUiEvent.ValidationError? = when {
        title.isBlank() -> AddPromptUiEvent.ValidationError(
            ValidationField.TITLE,
            "Название промпта не может быть пустым"
        )

        category.isBlank() -> AddPromptUiEvent.ValidationError(
            ValidationField.CATEGORY,
            "Категория не может быть пустой"
        )

        content.ru.isBlank() && content.en.isBlank() -> AddPromptUiEvent.ValidationError(
            ValidationField.CONTENT_RU,
            "Необходимо заполнить хотя бы одно поле содержимого"
        )

        else -> null
    }
}