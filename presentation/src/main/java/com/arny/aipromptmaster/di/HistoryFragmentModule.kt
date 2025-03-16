package com.arny.aipromptmaster.di

import com.arny.aipromptmaster.core.di.scopes.FragmentScope
import com.arny.aipromptmaster.presentation.ui.history.HistoryFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
interface HistoryFragmentModule {
    @FragmentScope
    @ContributesAndroidInjector
    fun contributeFragmentInjector(): HistoryFragment
} 