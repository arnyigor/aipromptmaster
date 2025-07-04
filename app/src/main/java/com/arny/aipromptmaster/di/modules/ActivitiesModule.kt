package com.arny.aipromptmaster.di.modules

import com.arny.aipromptmaster.core.di.scopes.ActivityScope
import com.arny.aipromptmaster.di.HistoryFragmentModule
import com.arny.aipromptmaster.di.HomeFragmentModule
import com.arny.aipromptmaster.di.LibraryFragmentModule
import com.arny.aipromptmaster.di.PromptViewFragmentModule
import com.arny.aipromptmaster.di.SettingsFragmentModule
import com.arny.aipromptmaster.presentation.MainActivity
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
abstract class ActivitiesModule {
    @ActivityScope
    @ContributesAndroidInjector(
        modules = [
            HomeFragmentModule::class,
            PromptViewFragmentModule::class,
            HistoryFragmentModule::class,
            LibraryFragmentModule::class,
            SettingsFragmentModule::class,
        ]
    )
    abstract fun bindMainActivity(): MainActivity
}
