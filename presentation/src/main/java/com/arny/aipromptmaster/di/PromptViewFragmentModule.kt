package com.arny.aipromptmaster.di

import com.arny.aipromptmaster.core.di.scopes.FragmentScope
import com.arny.aipromptmaster.presentation.ui.view.PromptViewFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
interface PromptViewFragmentModule {
    @FragmentScope
    @ContributesAndroidInjector
    fun contributeFragmentInjector(): PromptViewFragment
}