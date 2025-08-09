package com.arny.aiprompts.presentation.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DelicateDecomposeApi
import com.arkivanov.decompose.router.stack.*
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arny.aiprompts.domain.usecase.GetPromptsUseCase
import com.arny.aiprompts.domain.usecase.ToggleFavoriteUseCase
import com.arny.aiprompts.presentation.screens.DefaultPromptListComponent
import com.arny.aiprompts.presentation.screens.PromptListComponent
import org.koin.core.component.KoinComponent

interface RootComponent {
    val stack: Value<ChildStack<*, Child>>

    // Sealed-класс для дочерних компонентов, чтобы UI знал, какой экран рисовать
    sealed interface Child {
        data class List(val component: PromptListComponent) : Child
    }
}

class DefaultRootComponent(
    componentContext: ComponentContext,
    private val getPromptsUseCase: GetPromptsUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
) : RootComponent, ComponentContext by componentContext, KoinComponent {

    private val navigation = StackNavigation<ScreenConfig>()

    override val stack: Value<ChildStack<*, RootComponent.Child>> =
        childStack(
            source = navigation,
            serializer = ScreenConfig.serializer(), // Для сохранения стека
            initialConfiguration = ScreenConfig.PromptList, // Стартовый экран
            handleBackButton = true, // Автоматическая обработка кнопки "назад" на Android
            childFactory = ::createChild // Фабрика для создания дочерних компонентов
        )

    @OptIn(DelicateDecomposeApi::class)
    private fun createChild(
        config: ScreenConfig,
        context: ComponentContext
    ): RootComponent.Child {
        return when (config) {
            is ScreenConfig.PromptList -> RootComponent.Child.List(
                DefaultPromptListComponent(
                    componentContext = context,
                    getPromptsUseCase = getPromptsUseCase,
                    toggleFavoriteUseCase = toggleFavoriteUseCase,
                    onNavigateToDetails = {
//                        navigation.push(ScreenConfig.PromptDetails(it.id))
                    }
                )
            )
        }
    }
}
