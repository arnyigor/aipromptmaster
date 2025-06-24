package com.arny.aipromptmaster.di

import com.arny.aipromptmaster.core.di.scopes.FragmentScope
import com.arny.aipromptmaster.presentation.ui.llm.LLMInteractionFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
interface LLMInteractionFragmentModule {
    @FragmentScope
    @ContributesAndroidInjector
    fun contributeFragmentInjector(): LLMInteractionFragment
}