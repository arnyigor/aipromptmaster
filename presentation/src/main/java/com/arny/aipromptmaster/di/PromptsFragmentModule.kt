package com.arny.aipromptmaster.di

import com.arny.aipromptmaster.core.di.scopes.FragmentScope
import com.arny.aipromptmaster.presentation.ui.home.PromptsFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
interface PromptsFragmentModule {
    @FragmentScope
    @ContributesAndroidInjector
    fun contributeFragmentInjector(): PromptsFragment
}