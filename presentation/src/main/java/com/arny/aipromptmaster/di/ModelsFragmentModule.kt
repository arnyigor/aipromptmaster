package com.arny.aipromptmaster.di

import com.arny.aipromptmaster.core.di.scopes.FragmentScope
import com.arny.aipromptmaster.presentation.ui.chat.ChatFragment
import com.arny.aipromptmaster.presentation.ui.models.ModelsFragment
import com.arny.aipromptmaster.presentation.ui.settings.SettingsFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
interface ModelsFragmentModule {
    @FragmentScope
    @ContributesAndroidInjector
    fun contributeFragmentInjector(): ModelsFragment
}