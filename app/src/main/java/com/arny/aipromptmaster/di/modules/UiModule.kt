package com.arny.aipromptmaster.di.modules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.arny.aipromptmaster.di.AppViewModelFactory
import dagger.Module
import dagger.Provides
import javax.inject.Provider

@Module(includes = [ActivitiesModule::class])
class UiModule {
    @Provides
    fun provideViewModelFactory(
        providers: Map<Class<out ViewModel>, @JvmSuppressWildcards Provider<ViewModel>>
    ): ViewModelProvider.Factory = AppViewModelFactory(providers)
}