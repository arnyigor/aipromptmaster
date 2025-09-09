package com.arny.aipromptmaster.di

import com.arny.aipromptmaster.core.di.scopes.FragmentScope
import com.arny.aipromptmaster.presentation.ui.addprompt.AddPromptFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
interface AddPromptFragmentModule {
    @FragmentScope
    @ContributesAndroidInjector
    fun contributeFragmentInjector(): AddPromptFragment
}