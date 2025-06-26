package com.arny.aipromptmaster.di

import com.arny.aipromptmaster.core.di.scopes.FragmentScope
import com.arny.aipromptmaster.presentation.ui.chat.ChatFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
interface LibraryFragmentModule {
    @FragmentScope
    @ContributesAndroidInjector
    fun contributeFragmentInjector(): ChatFragment
}