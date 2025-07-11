package com.arny.aipromptmaster.presentation.ui.home

import android.os.Bundle
import android.util.Log
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
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
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
        val data = availableSortData ?: return
        val filters = currentFilters ?: return

        populateCategoryChipGroup(
            chipGroup = binding.categoryChipGroup,
            items = data.categories,
            selected = filters.category
        )

        populateTagChipGroup(
            chipGroup = binding.tagsChipGroup,
            items = data.tags,
            selected = filters.tags
        )
    }

    private fun setupListeners() {
        binding.applyButton.setOnClickListener {
            val selectedCategoryId = binding.categoryChipGroup.checkedChipId
            val selectedCategory = if (selectedCategoryId != View.NO_ID) {
                binding.categoryChipGroup.findViewById<Chip>(selectedCategoryId)?.text?.toString()
            } else {
                null
            }

            val settings = FilterSettings(
                category = selectedCategory,
                tags = binding.tagsChipGroup.getMultiSelectedChipTexts()
            )
            setFragmentResult(REQUEST_KEY, bundleOf(BUNDLE_KEY to settings))
            dismiss()
        }

        binding.resetButton.setOnClickListener {
            val settings = FilterSettings(category = null, tags = emptyList())
            setFragmentResult(REQUEST_KEY, bundleOf(BUNDLE_KEY to settings))
            dismiss()
        }
    }

    private fun populateCategoryChipGroup(
        chipGroup: ChipGroup,
        items: List<String>,
        selected: String?
    ) {
        chipGroup.isSingleSelection = true
        chipGroup.removeAllViews()

        items.forEach { item ->
            val chip = Chip(requireContext()).apply {
                text = item
                isCheckable = true
                isChecked = (item == selected)
            }
            chipGroup.addView(chip)
        }
    }

    private fun populateTagChipGroup(
        chipGroup: ChipGroup,
        items: List<String>,
        selected: List<String>?
    ) {
        chipGroup.isSingleSelection = false
        chipGroup.removeAllViews()

        items.forEach { item ->
            val chip = Chip(requireContext()).apply {
                text = item
                isCheckable = true
                isChecked = selected?.contains(item) ?: false
            }
            chipGroup.addView(chip)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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
