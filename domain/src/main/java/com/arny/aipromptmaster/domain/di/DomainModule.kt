package com.arny.aipromptmaster.domain.di

import com.arny.aipromptmaster.domain.interactors.IPromptsInteractor
import com.arny.aipromptmaster.domain.interactors.PromptsInteractorImpl
import dagger.Binds
import dagger.Module
import javax.inject.Singleton

@Module
interface DomainModule {
    @Binds
    @Singleton
    fun bindMoviesInteractor(impl: PromptsInteractorImpl): IPromptsInteractor

}
