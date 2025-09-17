package com.arny.aipromptmaster.presentation.ui.addprompt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.aipromptmaster.domain.interactors.IPromptsInteractor
import java.util.Date
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

class EditPromptViewModel @AssistedInject constructor(
    private val interactor: IPromptsInteractor
) : ViewModel() {

    private val _uiState = MutableStateFlow<EditPromptUiState>(EditPromptUiState.Content)
    val uiState: StateFlow<EditPromptUiState> = _uiState.asStateFlow()

    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    private val _event = MutableSharedFlow<EditPromptUiEvent>()
    val event: SharedFlow<EditPromptUiEvent> = _event.asSharedFlow()

    private val _editingPrompt = MutableStateFlow<Prompt?>(null)
    val editingPrompt: StateFlow<Prompt?> = _editingPrompt.asStateFlow()

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

    fun loadPrompt(promptId: String) {
        viewModelScope.launch {
            try {
                val prompt = interactor.getPromptById(promptId)
                _editingPrompt.value = prompt
            } catch (e: Exception) {
                _uiState.value = EditPromptUiState.Error(
                    StringHolder.Resource(R.string.error_loading_prompts)
                )
            }
        }
    }

    fun savePrompt(
        title: String,
        description: String?,
        category: String,
        content: PromptContent,
        tags: List<String>
    ) {
        viewModelScope.launch {
            try {
                _uiState.value = EditPromptUiState.Loading

                // Validate input
                val validationResult = validateInput(title, category, content)
                if (validationResult != null) {
                    _event.emit(validationResult)
                    _uiState.value = EditPromptUiState.Content
                    return@launch
                }

                val existingPrompt = _editingPrompt.value
                if (existingPrompt != null) {
                    // Update existing prompt
                    val updatedPrompt = existingPrompt.copy(
                        title = title,
                        description = description,
                        content = content,
                        category = category,
                        tags = tags,
                        modifiedAt = Date()
                    )
                    interactor.updatePrompt(updatedPrompt)
                } else {
                    // Create new prompt
                    val prompt = Prompt(
                        title = title,
                        description = description,
                        content = content,
                        category = category,
                        tags = tags,
                        compatibleModels = emptyList(), // Default empty list
                        status = "active"
                    )
                    interactor.savePrompt(prompt)
                }

                _event.emit(EditPromptUiEvent.PromptSaved)
            } catch (e: Exception) {
                _uiState.value = EditPromptUiState.Error(
                    StringHolder.Resource(R.string.error_saving_prompt)
                )
            }
        }
    }

    private fun validateInput(
        title: String,
        category: String,
        content: PromptContent
    ): EditPromptUiEvent.ValidationError? = when {
        title.isBlank() -> EditPromptUiEvent.ValidationError(
            ValidationField.TITLE,
            "Название промпта не может быть пустым"
        )

        category.isBlank() -> EditPromptUiEvent.ValidationError(
            ValidationField.CATEGORY,
            "Категория не может быть пустой"
        )

        content.ru.isBlank() && content.en.isBlank() -> EditPromptUiEvent.ValidationError(
            ValidationField.CONTENT_RU,
            "Необходимо заполнить хотя бы одно поле содержимого"
        )

        else -> null
    }

    fun addNewCategory(categoryName: String) {
        viewModelScope.launch {
            try {
                interactor.addCategory(categoryName)
                val categories = interactor.getPromptsSortData().categories
                _categories.value = categories
            } catch (e: Exception) {
                _uiState.value = EditPromptUiState.Error(
                    StringHolder.Resource(R.string.error_add_category)
                )
            }
        }
    }
}