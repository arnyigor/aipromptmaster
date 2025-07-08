package com.arny.aipromptmaster.presentation.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.arny.aipromptmaster.presentation.databinding.PromptsBottomSheetFiltersBinding
import com.arny.aipromptmaster.presentation.utils.getMultiSelectedChipTexts
import com.arny.aipromptmaster.presentation.utils.getParcelableCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class PromptsFilterSettingsBottomSheet : BottomSheetDialogFragment() {
    private var _binding: PromptsBottomSheetFiltersBinding? = null
    private val binding get() = _binding!!

    private var availableSortData: SortData? = null
    private var currentFilters: CurrentFilters? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            availableSortData = it.getParcelableCompat(ARG_SORT_DATA)
            currentFilters = it.getParcelableCompat(ARG_CURRENT_FILTERS)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = PromptsBottomSheetFiltersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        populateUi()
        setupListeners()
    }

    private fun populateUi() {
        availableSortData?.let { data ->
            populateChipGroup(binding.categoryChipGroup, data.categories, currentFilters?.category)
            populateChipGroup(binding.tagsChipGroup, data.tags, currentFilters?.tags)
        }
    }

    private fun setupListeners() {
        binding.applyButton.setOnClickListener {
            val settings = FilterSettings(
                categories = binding.categoryChipGroup.getMultiSelectedChipTexts(),
                tags = binding.tagsChipGroup.getMultiSelectedChipTexts()
            )
            setFragmentResult(REQUEST_KEY, bundleOf(BUNDLE_KEY to settings))
            dismiss()
        }
    }

    private fun populateChipGroup(chipGroup: ChipGroup, items: List<String>, selected: Any?) {
        chipGroup.isSingleSelection = selected is String?
        items.forEach { item ->
            val chip = Chip(context).apply {
                text = item
                isCheckable = true
                isChecked =
                    if (selected is String?) item == selected else (selected as? List<*>)?.contains(
                        item
                    ) ?: false
            }
            chipGroup.addView(chip)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Очищаем ссылку на binding
    }

    companion object {
        const val TAG = "SortBottomSheetFragment"
        const val REQUEST_KEY = "filter_settings_request"
        const val BUNDLE_KEY = "filter_settings_bundle"

        private const val ARG_SORT_DATA = "arg_sort_data"
        private const val ARG_CURRENT_FILTERS = "arg_current_filters"

        fun newInstance(
            sortData: SortData,
            currentFilters: CurrentFilters
        ): PromptsFilterSettingsBottomSheet {
            return PromptsFilterSettingsBottomSheet().apply {
                arguments = bundleOf(
                    ARG_SORT_DATA to sortData,
                    ARG_CURRENT_FILTERS to currentFilters
                )
            }
        }
    }
}