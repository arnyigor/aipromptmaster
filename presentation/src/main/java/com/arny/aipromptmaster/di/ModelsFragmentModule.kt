package com.arny.aipromptmaster.di

import com.arny.aipromptmaster.core.di.scopes.FragmentScope
import com.arny.aipromptmaster.presentation.ui.modelsview.ModelsFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
interface ModelsFragmentModule {
    @FragmentScope
    @ContributesAndroidInjector
    fun contributeFragmentInjector(): ModelsFragment
}