package com.arny.aipromptmaster.domain.di

import com.arny.aipromptmaster.domain.interactors.ILLMInteractor
import com.arny.aipromptmaster.domain.interactors.IPromptsInteractor
import com.arny.aipromptmaster.domain.interactors.ISettingsInteractor
import com.arny.aipromptmaster.domain.interactors.LLMInteractor
import com.arny.aipromptmaster.domain.interactors.PromptsInteractorImpl
import com.arny.aipromptmaster.domain.interactors.SettingsInteractorImpl
import dagger.Binds
import dagger.Module
import javax.inject.Singleton

@Module
interface DomainModule {
    @Binds
    @Singleton
    fun bindMoviesInteractor(impl: PromptsInteractorImpl): IPromptsInteractor

    @Binds
    @Singleton
    fun bindLLMInteractor(impl: LLMInteractor): ILLMInteractor

    @Binds
    @Singleton
    fun bindSettingsInteractor(impl: SettingsInteractorImpl): ISettingsInteractor
}
