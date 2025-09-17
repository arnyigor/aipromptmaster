package com.arny.aipromptmaster.presentation.ui.modelsview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.databinding.ModelsBottomSheetFiltersBinding
import com.arny.aipromptmaster.presentation.ui.models.FilterState
import com.arny.aipromptmaster.presentation.ui.models.ModalityType
import com.arny.aipromptmaster.presentation.ui.models.SortCriteria
import com.arny.aipromptmaster.presentation.ui.models.SortDirection
import com.arny.aipromptmaster.presentation.ui.models.SortType
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ModelsFilterBottomSheetDialog : BottomSheetDialogFragment() {

    private var _binding: ModelsBottomSheetFiltersBinding? = null
    private val binding get() = _binding!!

    // Текущее состояние сортировки
    private var currentSortType = SortType.BY_PRICE
    private var currentSortDirection = SortDirection.ASC

    // Опции сортировки для dropdown
    private data class SortOption(val type: SortType, val displayName: String)

    private lateinit var sortOptions: List<SortOption>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ModelsBottomSheetFiltersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeSortOptions()
        setupInitialState() // Сначала устанавливаем состояние
        setupDropdown() // Потом настраиваем dropdown
        setupClickListeners()
    }

    private fun initializeSortOptions() {
        sortOptions = listOf(
            SortOption(SortType.BY_NAME, getString(R.string.sorting_by_name)),
            SortOption(SortType.BY_PRICE, getString(R.string.sorting_by_pricing)),
            SortOption(SortType.BY_DATE, getString(R.string.sorting_by_date)),
            SortOption(SortType.BY_CONTEXT, getString(R.string.sorting_by_context_size))
        )
    }

    private fun setupInitialState() {
        val initialFilters = arguments?.getParcelable<FilterState>(ARG_FILTERS)

        if (initialFilters != null) {
            // Настраиваем фильтры-переключатели
            binding.switchOnlySelected.isChecked = initialFilters.showOnlySelected
            binding.switchOnlyFavorite.isChecked = initialFilters.showOnlyFavorites
            binding.switchOnlyFree.isChecked = initialFilters.showOnlyFree
            binding.switchSupportsImages.isChecked =
                ModalityType.IMAGE in initialFilters.requiredModalities

            // Определяем текущую активную сортировку
            if (initialFilters.sortOptions.isNotEmpty()) {
                val activeSortCriteria = initialFilters.sortOptions.first()
                currentSortType = activeSortCriteria.type
                currentSortDirection = activeSortCriteria.direction
            } else {
                // Дефолтные значения, если сортировка не задана
                currentSortType = SortType.BY_PRICE
                currentSortDirection = SortDirection.ASC
            }
        } else {
            // Если нет переданных фильтров, используем дефолтные значения
            currentSortType = SortType.BY_PRICE
            currentSortDirection = SortDirection.ASC
        }
    }

    private fun setupDropdown() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            sortOptions.map { it.displayName }
        )

        binding.sortTypeDropdown.setAdapter(adapter)

        // Устанавливаем текущий выбранный элемент в dropdown
        val selectedIndex = sortOptions.indexOfFirst { it.type == currentSortType }
        if (selectedIndex >= 0) {
            binding.sortTypeDropdown.setText(sortOptions[selectedIndex].displayName, false)
        }

        // Обновляем UI кнопки направления
        updateSortDirectionButton()

        // Обработчик выбора элемента из dropdown
        binding.sortTypeDropdown.setOnItemClickListener { _, _, position, _ ->
            val selectedOption = sortOptions[position]
            currentSortType = selectedOption.type
            updateSortDirectionButton() // Обновляем UI после изменения
        }
    }

    private fun setupClickListeners() {
        // Кнопка изменения направления
        binding.buttonSortDirection.setOnClickListener {
            toggleSortDirection()
        }

        // Кнопка применения фильтров
        binding.buttonApply.setOnClickListener {
            val newFilters = buildFiltersFromUi()
            setFragmentResult(REQUEST_KEY, bundleOf(RESULT_KEY to newFilters))
            dismiss()
        }

        // Кнопка сброса фильтров (опционально)
        binding.buttonReset.setOnClickListener {
            resetFilters()
        }
    }

    private fun toggleSortDirection() {
        currentSortDirection = if (currentSortDirection == SortDirection.ASC) {
            SortDirection.DESC
        } else {
            SortDirection.ASC
        }

        updateSortDirectionButton()
    }

    private fun updateSortDirectionButton() {
        val iconRes = when (currentSortDirection) {
            SortDirection.ASC -> android.R.drawable.arrow_up_float
            SortDirection.DESC -> android.R.drawable.arrow_down_float
        }

        binding.buttonSortDirection.setIconResource(iconRes)

        // Обновляем текст на кнопке для ясности
        val directionText = when (currentSortDirection) {
            SortDirection.ASC -> getString(R.string.sort_direction_ascending)
            SortDirection.DESC -> getString(R.string.sort_direction_descending)
        }

        // Если у кнопки есть текст, обновляем его
        binding.buttonSortDirection.text = directionText
        binding.buttonSortDirection.contentDescription = directionText
    }

    private fun resetFilters() {
        // Сбрасываем все переключатели
        binding.switchOnlySelected.isChecked = false
        binding.switchOnlyFavorite.isChecked = false
        binding.switchOnlyFree.isChecked = false
        binding.switchSupportsImages.isChecked = false

        // Сбрасываем сортировку на дефолтную
        currentSortType = SortType.BY_PRICE
        currentSortDirection = SortDirection.ASC

        // Обновляем dropdown
        val defaultIndex = sortOptions.indexOfFirst { it.type == currentSortType }
        if (defaultIndex >= 0) {
            binding.sortTypeDropdown.setText(sortOptions[defaultIndex].displayName, false)
        }

        // Обновляем UI кнопки направления
        updateSortDirectionButton()
    }

    private fun buildFiltersFromUi(): FilterState {
        val currentFilters = arguments?.getParcelable(ARG_FILTERS) ?: FilterState()

        // Создаем критерий сортировки с текущими значениями
        val sortCriteria = SortCriteria(currentSortType, currentSortDirection)

        // Собираем модальности
        val modalities = mutableSetOf<ModalityType>().apply {
            add(ModalityType.TEXT) // Всегда включен
            if (binding.switchSupportsImages.isChecked) add(ModalityType.IMAGE)
        }

        return currentFilters.copy(
            sortOptions = listOf(sortCriteria), // Текущий критерий сортировки
            showOnlySelected = binding.switchOnlySelected.isChecked,
            showOnlyFavorites = binding.switchOnlyFavorite.isChecked,
            showOnlyFree = binding.switchOnlyFree.isChecked,
            requiredModalities = modalities
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ModelsFilterBottomSheet"
        const val REQUEST_KEY = "models_filter_request"
        const val RESULT_KEY = "filters"
        private const val ARG_FILTERS = "arg_filters"

        fun newInstance(currentFilters: FilterState): ModelsFilterBottomSheetDialog {
            return ModelsFilterBottomSheetDialog().apply {
                arguments = bundleOf(ARG_FILTERS to currentFilters)
            }
        }
    }
}
