package com.arny.aipromptmaster.ui.screens.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.aipromptmaster.domain.interactors.IPromptsInteractor
import com.arny.aipromptmaster.domain.models.DomainPromptVariant
import com.arny.aipromptmaster.domain.models.DomainVariantId
import com.arny.aipromptmaster.domain.models.Prompt
import com.arny.aipromptmaster.domain.models.PromptContent
import com.arny.aipromptmaster.domain.models.PromptMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Date
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/**
 * View-Model responsible for all business logic of prompt editing.
 */
class PromptEditViewModel(
    promptId: String?,
    private val interactor: IPromptsInteractor,
) : ViewModel() {

    /* ---------------------------------------------------------------------*
     *  Private mutable holders
     * ---------------------------------------------------------------------*/
    private val _uiState = MutableStateFlow(EditUiState())
    private val _validation = MutableStateFlow(ValidationState())
    private val _saveResult = MutableStateFlow<SaveResult>(SaveResult.Idle)
    /** Holds the list of categories loaded from DB */
    private val _categories = MutableStateFlow<List<String>>(emptyList())

    /* ---------------------------------------------------------------------*
     *  Public state flows
     * ---------------------------------------------------------------------*/
    /** UI state - all form fields including variants */
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()

    /** Validation errors for main fields and variants */
    val validation: StateFlow<ValidationState> = _validation.asStateFlow()

    /** Current save operation status */
    val saveResult: StateFlow<SaveResult> = _saveResult.asStateFlow()

    /** Exposed flow of categories for UI consumption if needed */
    val categories: StateFlow<List<String>> get() = _categories.asStateFlow()

    /** Maximum title length (200 characters) */
    private val MAX_TITLE_LENGTH = 200

    init {
        if (promptId != null) {
            loadPrompt(promptId)
        }
        // Load categories from DB during initialization
        loadCategories()
    }

    /* ---------------------------------------------------------------------*
     *  UI updates (called from Composable)
     * ---------------------------------------------------------------------*/

    fun updateTitle(t: String) {
        _uiState.update { it.copy(title = t) }
        validateTitle(t)
    }

    fun updateCategory(c: String) {
        _uiState.update { it.copy(category = c) }
        validateCategory(c)
    }

    fun updateDescription(d: String?) {
        _uiState.update { it.copy(description = d?.takeIf(String::isNotBlank)) }
    }

    fun updateTags(tags: List<String>) {
        val uniq = tags.map(String::trim).filter(String::isNotBlank).distinct()
        _uiState.update { it.copy(tags = uniq) }
    }

    fun updateContentRu(c: String) {
        _uiState.update { it.copy(contentRu = c) }
    }

    fun updateContentEn(c: String) {
        _uiState.update { it.copy(contentEn = c) }
    }

    fun updateExpandedVariantIndex(index: Int) {
        _uiState.update { it.copy(expandedVariantIndex = if (it.expandedVariantIndex == index) -1 else index) }
    }

    /* ---------------------------------------------------------------------*
     *  Variants management
     * ---------------------------------------------------------------------*/

    /**
     * Adds a new empty variant to the prompt.
     * Sets default values for variant ID and priority.
     */
    fun addVariant() {
        val currentState = _uiState.value
        val newVariant = DomainPromptVariant(
            variantId = DomainVariantId(
                type = "custom",
                id = UUID.randomUUID().toString(),
                priority = currentState.variants.size + 1
            ),
            content = PromptContent()
        )

        _uiState.update { state ->
            state.copy(variants = state.variants + newVariant)
        }
    }

    /**
     * Updates an existing variant at the specified index.
     */
    fun updateVariant(index: Int, updatedVariant: DomainPromptVariant) {
        if (index < 0 || index >= _uiState.value.variants.size) return

        _uiState.update { state ->
            val updatedVariants = state.variants.toMutableList().apply { set(index, updatedVariant) }
            state.copy(variants = updatedVariants)
        }
    }

    /**
     * Deletes a variant at the specified index and reorders priorities.
     */
    fun deleteVariant(index: Int) {
        if (index < 0 || index >= _uiState.value.variants.size) return

        _uiState.update { state ->
            val updatedVariants = state.variants.toMutableList().apply { removeAt(index) }
                .mapIndexed { i, variant -> variant.copy(variantId = variant.variantId.copy(priority = i + 1)) }

            // Reset expanded index if it was the deleted variant or higher
            val newExpandedIndex = if (state.expandedVariantIndex >= index) -1 else state.expandedVariantIndex

            state.copy(
                variants = updatedVariants,
                expandedVariantIndex = newExpandedIndex
            )
        }
    }

    /* ---------------------------------------------------------------------*
     *  Save logic
     * ---------------------------------------------------------------------*/
    fun onSaveClicked() {
        viewModelScope.launch {
            if (!validate()) return@launch

            _saveResult.value = SaveResult.Loading
            try {
                val state = _uiState.value
                val prompt = Prompt(
                    id = state.id ?: UUID.randomUUID().toString(),
                    title = state.title.trim(),
                    description = state.description,
                    content = PromptContent(state.contentRu, state.contentEn),
                    variables = emptyMap(),
                    promptVariants = state.variants,
                    compatibleModels = state.compatibleModels,
                    category = state.category,
                    tags = state.tags,
                    isLocal = true,
                    rating = 0f,
                    ratingVotes = 0,
                    metadata = state.metadata,
                    createdAt = if (state.isNew) {
                        Date()
                    } else {
                        interactor.getPrompt(state.id!!)?.createdAt ?: Date()
                    },
                    modifiedAt = Date()
                )

                val ok = if (state.isNew) interactor.insertPrompt(prompt)
                else interactor.updatePrompt(prompt)

                _saveResult.value = if (ok) {
                    SaveResult.Success(prompt.id)
                } else {
                    SaveResult.Error("Не удалось сохранить промпт")
                }
            } catch (e: Exception) {
                _saveResult.value = SaveResult.Error(e.localizedMessage ?: "Ошибка при сохранении")
            }
        }
    }

    /* ---------------------------------------------------------------------*
     *  Loading existing prompt
     * ---------------------------------------------------------------------*/
    private fun loadPrompt(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val prompt = interactor.getPrompt(id)
                    .also { Timber.tag("EditViewModel").d("getPrompt(%s) → %s", id, it?.id ?: "null") }

                if (prompt != null) {
                    _uiState.value = EditUiState(
                        title = prompt.title,
                        description = prompt.description,
                        tags = prompt.tags,
                        contentRu = prompt.content.ru,
                        contentEn = prompt.content.en,
                        variants = prompt.promptVariants,
                        isNew = false,
                        id = prompt.id,
                        category = prompt.category,
                        compatibleModels = prompt.compatibleModels,
                        metadata = prompt.metadata,
                        isLoading = false
                    )
                    // Trigger validation after UI state is set
                    validate()
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }

            } catch (e: CancellationException) {
                Timber.tag("EditViewModel").d(
                    "loadPrompt(%s) → coroutine cancelled", id
                )
                throw e
            } catch (e: Exception) {
                Timber.tag("EditViewModel").e(e, "loadPrompt(%s) → unexpected failure", id)
                _saveResult.value = SaveResult.Error(e.localizedMessage ?: "Unknown error")
            }

            Timber.tag("EditViewModel").d(
                "loadPrompt(%s) → exit", id,
            )
        }
    }

    /* ---------------------------------------------------------------------*
     *  Validation logic
     * ---------------------------------------------------------------------*/
    private fun validate(): Boolean {
        var isValid: Boolean

        // Validate title
        isValid = validateTitle(_uiState.value.title)

        // Validate category
        val categoryIsValid = validateCategory(_uiState.value.category)
        isValid = categoryIsValid && isValid

        // Validate main content - at least one language must be filled
        val state = _uiState.value
        if (state.contentRu.isBlank() && state.contentEn.isBlank()) {
            _validation.update {
                it.copy(
                    contentError = "Основное содержимое не может быть пустым (заполните хотя бы один язык)"
                )
            }
            isValid = false
        } else {
            _validation.update { it.copy(contentError = null) }
        }

        // Validate variants
        val variantsValidation = mutableListOf<ValidationState.VariantValidation>()
        state.variants.forEachIndexed { index, variant ->
            val isVariantValid = validateVariant(variant, index, variantsValidation)
            if (!isVariantValid) isValid = false
        }

        _validation.update {
            it.copy(variantsValidation = variantsValidation)
        }

        return isValid
    }

    private fun validateTitle(title: String): Boolean {
        return if (title.isBlank() || title.length > MAX_TITLE_LENGTH) {
            _validation.update {
                it.copy(
                    isTitleValid = false,
                    titleError = if (title.isBlank()) "Заголовок не может быть пустым"
                    else "Заголовок слишком длинный (макс. $MAX_TITLE_LENGTH символов)"
                )
            }
            false
        } else {
            _validation.update { it.copy(isTitleValid = true, titleError = null) }
            true
        }
    }

    /** Category validation – non‑blank */
    private fun validateCategory(category: String): Boolean {
        return if (category.isBlank()) {
            _validation.update {
                it.copy(
                    isCategoryValid = false,
                    categoryError = "Категория обязательна"
                )
            }
            false
        } else {
            _validation.update { it.copy(isCategoryValid = true, categoryError = null) }
            true
        }
    }

    private fun validateVariant(
        variant: DomainPromptVariant,
        index: Int,
        validationResults: MutableList<ValidationState.VariantValidation>
    ): Boolean {
        val errors = mutableListOf<String>()

        // Check if at least one language content is provided
        if (variant.content.ru.isBlank() && variant.content.en.isBlank()) {
            errors.add("Содержимое не может быть пустым (заполните хотя бы один язык)")
        }

        // Check variant type
        if (variant.variantId.type.isBlank()) {
            errors.add("Тип варианта не может быть пустым")
        }

        val isValid = errors.isEmpty()
        validationResults.add(
            ValidationState.VariantValidation(
                index = index,
                isValid = isValid,
                errors = errors
            )
        )

        return isValid
    }

    /* ---------------------------------------------------------------------*
     *  Immutable data classes
     * ---------------------------------------------------------------------*/
    data class EditUiState(
        val title: String = "",
        val description: String? = null,
        val tags: List<String> = emptyList(),
        val contentRu: String = "",
        val contentEn: String = "",
        val variants: List<DomainPromptVariant> = emptyList(),
        val expandedVariantIndex: Int = -1,
        val isNew: Boolean = true,
        val id: String? = null,
        val category: String = "",
        val compatibleModels: List<String> = emptyList(),
        val metadata: PromptMetadata = PromptMetadata(),
        val isLoading: Boolean = false
    )

    data class ValidationState(
        val isTitleValid: Boolean = false,
        val isCategoryValid: Boolean = false,
        val titleError: String? = null,
        val categoryError: String? = null,
        val contentError: String? = null,
        val variantsValidation: List<VariantValidation> = emptyList()
    ) {
        data class VariantValidation(val index: Int, val isValid: Boolean, val errors: List<String>)
    }

    sealed class SaveResult {
        object Idle : SaveResult()
        object Loading : SaveResult()
        data class Success(val promptId: String) : SaveResult()
        data class Error(val message: String) : SaveResult()
    }

    /** Loads categories from repository and logs them */
    private fun loadCategories() {
        viewModelScope.launch {
            try {
                val categories = interactor.getUniqueCategories()
                _categories.value = categories
                Timber.tag("EditViewModel").i("Loaded categories: %s", categories)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load categories")
            }
        }
    }
}
