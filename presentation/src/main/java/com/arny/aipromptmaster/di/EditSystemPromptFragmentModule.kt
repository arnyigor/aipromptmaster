package com.arny.aipromptmaster.di

import com.arny.aipromptmaster.core.di.scopes.FragmentScope
import com.arny.aipromptmaster.presentation.ui.editprompt.EditSystemPromptFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
interface EditSystemPromptFragmentModule {
    @FragmentScope
    @ContributesAndroidInjector
    fun contributeFragmentInjector(): EditSystemPromptFragment
}