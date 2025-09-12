package com.arny.aipromptmaster.di

import com.arny.aipromptmaster.core.di.scopes.FragmentScope
import com.arny.aipromptmaster.presentation.ui.addprompt.EditPromptFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
interface AddPromptFragmentModule {
    @FragmentScope
    @ContributesAndroidInjector
    fun contributeFragmentInjector(): EditPromptFragment
}