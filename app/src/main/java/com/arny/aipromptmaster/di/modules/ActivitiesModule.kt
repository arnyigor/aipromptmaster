package com.arny.aipromptmaster.di.modules

import com.arny.aipromptmaster.core.di.scopes.ActivityScope
import com.arny.aipromptmaster.di.AddPromptFragmentModule
import com.arny.aipromptmaster.di.EditSystemPromptFragmentModule
import com.arny.aipromptmaster.di.HistoryFragmentModule
import com.arny.aipromptmaster.di.PromptsFragmentModule
import com.arny.aipromptmaster.di.LibraryFragmentModule
import com.arny.aipromptmaster.di.ModelsFragmentModule
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
            PromptsFragmentModule::class,
            PromptViewFragmentModule::class,
            AddPromptFragmentModule::class,
            EditSystemPromptFragmentModule::class,
            HistoryFragmentModule::class,
            LibraryFragmentModule::class,
            SettingsFragmentModule::class,
            ModelsFragmentModule::class,
        ]
    )
    abstract fun bindMainActivity(): MainActivity
}
