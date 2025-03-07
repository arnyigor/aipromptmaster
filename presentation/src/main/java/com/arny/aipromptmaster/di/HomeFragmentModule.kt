package com.arny.aipromptmaster.di

import com.arny.aipromptmaster.core.di.scopes.FragmentScope
import com.arny.aipromptmaster.presentation.ui.home.HomeFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
interface HomeFragmentModule {
    @FragmentScope
    @ContributesAndroidInjector(
        modules = [
        ]
    )
    fun contributeFragmentInjector(): HomeFragment
}