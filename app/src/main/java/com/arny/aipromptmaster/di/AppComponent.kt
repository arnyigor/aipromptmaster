package com.arny.aipromptmaster.di

import com.arny.aipromptmaster.AiPromptMasterApp
import com.arny.aipromptmaster.core.di.CoreModule
import com.arny.aipromptmaster.data.di.DataModule
import com.arny.aipromptmaster.data.di.NetworkModule
import com.arny.aipromptmaster.di.modules.AppModule
import com.arny.aipromptmaster.di.modules.UiModule
import dagger.BindsInstance
import dagger.Component
import dagger.android.AndroidInjector
import dagger.android.support.AndroidSupportInjectionModule
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        AndroidSupportInjectionModule::class,
        AppModule::class,
        UiModule::class,
        CoreModule::class,
        DataModule::class,
        NetworkModule::class,
    ]
)
interface AppComponent : AndroidInjector<AiPromptMasterApp> {
    override fun inject(application: AiPromptMasterApp)

    @Component.Builder
    interface Builder {
        @BindsInstance
        fun application(application: AiPromptMasterApp): Builder

        fun build(): AppComponent
    }
}