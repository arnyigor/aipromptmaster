package com.arny.aipromptmaster.di

import com.arny.aipromptmaster.domain.interactors.ILLMInteractor
import com.arny.aipromptmaster.domain.interactors.IPromptsInteractor
import com.arny.aipromptmaster.domain.interactors.ISettingsInteractor
import com.arny.aipromptmaster.domain.interactors.LLMInteractor
import com.arny.aipromptmaster.domain.interactors.PromptsInteractorImpl
import com.arny.aipromptmaster.domain.interactors.SettingsInteractorImpl
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val domainModule = module {
    // Koin сам найдет зависимости для конструктора Interactor'а
    // factory - создаем каждый раз новый (безопаснее для stateful интеракторов)
    // single - один на все приложение
    factoryOf(::PromptsInteractorImpl) { bind<IPromptsInteractor>() }
    factoryOf(::LLMInteractor) { bind<ILLMInteractor>() }
    factoryOf(::SettingsInteractorImpl) { bind<ISettingsInteractor>() }
}