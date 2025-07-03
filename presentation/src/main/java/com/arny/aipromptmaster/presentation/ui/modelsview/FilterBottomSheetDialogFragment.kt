package com.arny.aipromptmaster.presentation.ui.modelsview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.arny.aipromptmaster.presentation.R
import com.arny.aipromptmaster.presentation.databinding.BottomSheetFiltersBinding
import com.arny.aipromptmaster.presentation.ui.models.FilterState
import com.arny.aipromptmaster.presentation.ui.models.SortOption
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class FilterBottomSheetDialogFragment : BottomSheetDialogFragment() {

    // Используем ViewBinding для безопасного доступа к View
    private var _binding: BottomSheetFiltersBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetFiltersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Получаем начальное состояние фильтров из аргументов
        val initialFilters = arguments?.getParcelable<FilterState>(ARG_FILTERS)
        if (initialFilters != null) {
            setupInitialState(initialFilters)
        }

        // Обработчик кнопки "Применить"
        binding.buttonApply.setOnClickListener {
            val newFilters = buildFilterStateFromUi()
            // Отправляем результат обратно вызывающему фрагменту
            setFragmentResult(REQUEST_KEY, bundleOf(RESULT_KEY to newFilters))
            dismiss()
        }

        // Обработчик кнопки "Сбросить"
        binding.buttonReset.setOnClickListener {
            // Отправляем пустой FilterState
            setFragmentResult(REQUEST_KEY, bundleOf(RESULT_KEY to FilterState()))
            dismiss()
        }
    }

    private fun setupInitialState(filters: FilterState) {
        binding.switchOnlySelected.isChecked = filters.showOnlySelected

        when (filters.sortBy) {
            SortOption.NONE -> binding.radioGroupSort.check(R.id.radio_sort_default)
            SortOption.BY_NAME_ASC -> binding.radioGroupSort.check(R.id.radio_sort_asc)
            SortOption.BY_NAME_DESC -> binding.radioGroupSort.check(R.id.radio_sort_desc)
            SortOption.NEWEST -> TODO()
        }
    }

    private fun buildFilterStateFromUi(): FilterState {
        val selectedSortOption = when (binding.radioGroupSort.checkedRadioButtonId) {
            R.id.radio_sort_asc -> SortOption.BY_NAME_ASC
            R.id.radio_sort_desc -> SortOption.BY_NAME_DESC
            else -> SortOption.NONE
        }

        val showOnlySelected = binding.switchOnlySelected.isChecked

        // Важно: мы сохраняем поисковый запрос, если он был
        val initialQuery = arguments?.getParcelable<FilterState>(ARG_FILTERS)?.searchQuery ?: ""

        return FilterState(
            searchQuery = initialQuery,
            sortBy = selectedSortOption,
            showOnlySelected = showOnlySelected
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Очищаем ссылку на binding
    }

    companion object {
        const val TAG = "FilterBottomSheet"
        const val REQUEST_KEY = "filter_request"
        const val RESULT_KEY = "filters"
        private const val ARG_FILTERS = "arg_filters"

        // Фабричный метод для создания инстанса с передачей аргументов
        fun newInstance(currentFilters: FilterState): FilterBottomSheetDialogFragment {
            return FilterBottomSheetDialogFragment().apply {
                arguments = bundleOf(ARG_FILTERS to currentFilters)
            }
        }
    }
}
