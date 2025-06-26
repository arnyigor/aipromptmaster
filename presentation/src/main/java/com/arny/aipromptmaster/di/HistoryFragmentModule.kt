package com.arny.aipromptmaster.di

import com.arny.aipromptmaster.core.di.scopes.FragmentScope
import com.arny.aipromptmaster.presentation.ui.chathistory.ChatHistoryFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
interface HistoryFragmentModule {
    @FragmentScope
    @ContributesAndroidInjector
    fun contributeFragmentInjector(): ChatHistoryFragment
} 