import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox

class ModelsFilterBottomSheetDialog : BottomSheetDialogFragment() {

    private var _binding: ModelsBottomSheetFiltersBinding? = null
    private val binding get() = _binding!!

    // Состояние направлений сортировки
    private val sortDirections = mutableMapOf<SortType, SortDirection>().apply {
        put(SortType.BY_NAME, SortDirection.ASC)
        put(SortType.BY_PRICE, SortDirection.ASC)
        put(SortType.BY_DATE, SortDirection.DESC) // По умолчанию новые сверху
        put(SortType.BY_CONTEXT, SortDirection.DESC) // По умолчанию большие сверху
    }

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

        setupInitialState()
        setupClickListeners()
        setupSortCards()
    }

    private fun setupInitialState() {
        val initialFilters = arguments?.getParcelable<FilterState>(ARG_FILTERS)
        initialFilters?.let { filters ->
            // Настраиваем переключатели
            binding.switchOnlySelected.isChecked = filters.showOnlySelected
            binding.switchOnlyFavorite.isChecked = filters.showOnlyFavorites
            binding.switchOnlyFree.isChecked = filters.showOnlyFree
            binding.switchSupportsImages.isChecked =
                ModalityType.IMAGE in filters.requiredModalities

            // Настраиваем сортировки
            val activeSorts = filters.sortOptions.associateBy { it.type }

            binding.checkboxSortName.isChecked = activeSorts.containsKey(SortType.BY_NAME)
            binding.checkboxSortPrice.isChecked = activeSorts.containsKey(SortType.BY_PRICE)
            binding.checkboxSortDate.isChecked = activeSorts.containsKey(SortType.BY_DATE)
            binding.checkboxSortContext.isChecked = activeSorts.containsKey(SortType.BY_CONTEXT)

            // Обновляем направления из сохраненного состояния
            activeSorts.forEach { (type, criteria) ->
                sortDirections[type] = criteria.direction
            }

            // Обновляем UI направлений
            updateDirectionButton(binding.buttonNameDirection, sortDirections[SortType.BY_NAME]!!)
            updateDirectionButton(binding.buttonPriceDirection, sortDirections[SortType.BY_PRICE]!!)
            updateDirectionButton(binding.buttonDateDirection, sortDirections[SortType.BY_DATE]!!)
            updateDirectionButton(
                binding.buttonContextDirection,
                sortDirections[SortType.BY_CONTEXT]!!
            )

            // Обновляем визуальное состояние карточек
            updateCardState(binding.cardSortName, binding.checkboxSortName.isChecked)
            updateCardState(binding.cardSortPrice, binding.checkboxSortPrice.isChecked)
            updateCardState(binding.cardSortDate, binding.checkboxSortDate.isChecked)
            updateCardState(binding.cardSortContext, binding.checkboxSortContext.isChecked)
        }
    }

    private fun setupSortCards() {
        // Настраиваем карточки сортировок
        setupSortCard(
            binding.cardSortName,
            binding.checkboxSortName,
            binding.buttonNameDirection,
            SortType.BY_NAME
        )
        setupSortCard(
            binding.cardSortPrice,
            binding.checkboxSortPrice,
            binding.buttonPriceDirection,
            SortType.BY_PRICE
        )
        setupSortCard(
            binding.cardSortDate,
            binding.checkboxSortDate,
            binding.buttonDateDirection,
            SortType.BY_DATE
        )
        setupSortCard(
            binding.cardSortContext,
            binding.checkboxSortContext,
            binding.buttonContextDirection,
            SortType.BY_CONTEXT
        )
    }

    private fun setupSortCard(
        card: MaterialCardView,
        checkbox: MaterialCheckBox,
        directionButton: MaterialButton,
        sortType: SortType
    ) {
        // Клик по карточке или чекбоксу переключает активность
        val toggleListener = View.OnClickListener {
            checkbox.isChecked = !checkbox.isChecked
            updateCardState(card, checkbox.isChecked)
        }

        card.setOnClickListener(toggleListener)
        checkbox.setOnCheckedChangeListener { _, isChecked ->
            updateCardState(card, isChecked)
        }

        // Клик по кнопке направления меняет направление
        directionButton.setOnClickListener {
            if (checkbox.isChecked) {
                val currentDirection = sortDirections[sortType]!!
                val newDirection = if (currentDirection == SortDirection.ASC) {
                    SortDirection.DESC
                } else {
                    SortDirection.ASC
                }
                sortDirections[sortType] = newDirection
                updateDirectionButton(directionButton, newDirection)
            }
        }
    }

    private fun updateCardState(card: MaterialCardView, isActive: Boolean) {
        // Устанавливаем состояние для автоматического применения селекторов
        card.isSelected = isActive

        // Применяем цвета через селекторы
        card.strokeColor = ContextCompat.getColor(requireContext(), R.color.filter_card_stroke_color)
        card.setCardBackgroundColor(ContextCompat.getColorStateList(requireContext(), R.color.filter_card_background_color))

        // Устанавливаем ширину обводки в зависимости от состояния
        card.strokeWidth = resources.getDimensionPixelSize(
            if (isActive) R.dimen.active_card_stroke_width
            else R.dimen.default_card_stroke_width
        )

        // Анимированное изменение elevation
        val targetElevation = if (isActive) {
            resources.getDimension(R.dimen.filter_card_elevation)
        } else {
            0f
        }

        card.animate()
            .translationZ(targetElevation)
            .setDuration(150)
            .start()
    }


    private fun updateDirectionButton(button: MaterialButton, direction: SortDirection) {
        val iconRes = when (direction) {
            SortDirection.ASC -> android.R.drawable.arrow_up_float
            SortDirection.DESC -> android.R.drawable.arrow_down_float
        }
        button.setIconResource(iconRes)
        button.contentDescription = when (direction) {
            SortDirection.ASC -> getString(R.string.sort_direction_ascending)
            SortDirection.DESC -> getString(R.string.sort_direction_descending)
        }
    }

    private fun setupClickListeners() {
        binding.buttonApply.setOnClickListener {
            val newFilters = buildFiltersFromUi()
            setFragmentResult(REQUEST_KEY, bundleOf(RESULT_KEY to newFilters))
            dismiss()
        }
    }

    private fun buildFiltersFromUi(): FilterState {
        val currentFilters = arguments?.getParcelable(ARG_FILTERS) ?: FilterState()

        // Собираем активные сортировки
        val activeSortOptions = mutableListOf<SortCriteria>()

        if (binding.checkboxSortName.isChecked) {
            activeSortOptions.add(
                SortCriteria(
                    SortType.BY_NAME,
                    sortDirections[SortType.BY_NAME]!!
                )
            )
        }
        if (binding.checkboxSortPrice.isChecked) {
            activeSortOptions.add(
                SortCriteria(
                    SortType.BY_PRICE,
                    sortDirections[SortType.BY_PRICE]!!
                )
            )
        }
        if (binding.checkboxSortDate.isChecked) {
            activeSortOptions.add(
                SortCriteria(
                    SortType.BY_DATE,
                    sortDirections[SortType.BY_DATE]!!
                )
            )
        }
        if (binding.checkboxSortContext.isChecked) {
            activeSortOptions.add(
                SortCriteria(
                    SortType.BY_CONTEXT,
                    sortDirections[SortType.BY_CONTEXT]!!
                )
            )
        }

        // Если ничего не выбрано, используем дефолтную сортировку
        if (activeSortOptions.isEmpty()) {
            activeSortOptions.add(SortCriteria(SortType.BY_DATE, SortDirection.DESC))
            activeSortOptions.add(SortCriteria(SortType.BY_PRICE, SortDirection.ASC))
        }

        // Собираем модальности
        val modalities = mutableSetOf<ModalityType>().apply {
            add(ModalityType.TEXT) // Всегда включен
            if (binding.switchSupportsImages.isChecked) add(ModalityType.IMAGE)
        }

        return currentFilters.copy(
            sortOptions = activeSortOptions,
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
        const val TAG = "SmartFilterBottomSheet"
        const val REQUEST_KEY = "smart_filter_request"
        const val RESULT_KEY = "filters"
        private const val ARG_FILTERS = "arg_filters"

        fun newInstance(currentFilters: FilterState): ModelsFilterBottomSheetDialog {
            return ModelsFilterBottomSheetDialog().apply {
                arguments = bundleOf(ARG_FILTERS to currentFilters)
            }
        }
    }
}

