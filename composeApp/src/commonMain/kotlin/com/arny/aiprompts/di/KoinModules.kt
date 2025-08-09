package com.arny.aiprompts.di


import com.arny.aiprompts.data.db.AppDatabase
import com.arny.aiprompts.data.repository.PromptsRepositoryImpl
import com.arny.aiprompts.domain.interfaces.IPromptsRepository
import com.arny.aiprompts.domain.usecase.GetPromptsUseCase
import com.arny.aiprompts.domain.usecase.ToggleFavoriteUseCase
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

// Модуль для слоя данных
val dataModule = module {
    // Создаем синглтон AppDatabase
    single { AppDatabase.getInstance() }
    // Создаем синглтон PromptDao
    single { get<AppDatabase>().promptDao() }
    // Создаем синглтон PromptsRepositoryImpl и связываем его с интерфейсом IPromptsRepository
    singleOf(::PromptsRepositoryImpl) { bind<IPromptsRepository>() }
    // Предоставляем диспатчер для фоновых задач
    single { Dispatchers.IO }
}

// Модуль для доменного слоя (UseCases)
val domainModule = module {
    // Koin автоматически подставит IPromptsRepository из dataModule
    singleOf(::GetPromptsUseCase)
    singleOf(::ToggleFavoriteUseCase)
}