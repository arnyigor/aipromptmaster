package com.arny.aipromptmaster.di

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.arny.aipromptmaster.data.db.AppDatabase
import com.arny.aipromptmaster.data.db.daos.ChatDao
import com.arny.aipromptmaster.data.db.daos.ModelDao
import com.arny.aipromptmaster.data.db.daos.PromptDao
import com.arny.aipromptmaster.data.prefs.Prefs
import com.arny.aipromptmaster.data.prefs.SecurePrefs
import com.arny.aipromptmaster.data.repositories.ChatHistoryRepositoryImpl
import com.arny.aipromptmaster.data.repositories.FeedbackRepositoryImpl
import com.arny.aipromptmaster.data.repositories.FileRepositoryImpl
import com.arny.aipromptmaster.data.repositories.ModelRepositoryImpl
import com.arny.aipromptmaster.data.repositories.OpenRouterRepositoryImpl
import com.arny.aipromptmaster.data.repositories.PromptsRepositoryImpl
import com.arny.aipromptmaster.data.repositories.SettingsRepositoryImpl
import com.arny.aipromptmaster.data.repositories.SyncMetadata
import com.arny.aipromptmaster.data.services.ShareServiceImpl
import com.arny.aipromptmaster.data.sync.PromptSynchronizerImpl
import com.arny.aipromptmaster.domain.repositories.IChatHistoryRepository
import com.arny.aipromptmaster.domain.repositories.IFeedbackRepository
import com.arny.aipromptmaster.domain.repositories.IFileRepository
import com.arny.aipromptmaster.domain.repositories.IOpenRouterRepository
import com.arny.aipromptmaster.domain.repositories.IPromptSynchronizer
import com.arny.aipromptmaster.domain.repositories.IPromptsRepository
import com.arny.aipromptmaster.domain.repositories.ISettingsRepository
import com.arny.aipromptmaster.domain.repositories.ModelRepository
import com.arny.aipromptmaster.services.FileProcessing
import com.arny.aipromptmaster.services.ShareService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val dataModule = module {
    // ---------- Database ----------
    single {
        Room.databaseBuilder(
            get(),
            AppDatabase::class.java,
            AppDatabase.DBNAME
        )
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .addMigrations(AppDatabase.MIGRATION_2_3)
            .addMigrations(AppDatabase.MIGRATION_3_4)
            .addMigrations(AppDatabase.MIGRATION_5_6)
            .fallbackToDestructiveMigration()
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    db.execSQL("PRAGMA encoding='UTF-8';")
                }
            }).build()
    }

    // ---------- DAO‑ы ----------
    factory<PromptDao> { get<AppDatabase>().promptDao() }
    factory<ModelDao> { get<AppDatabase>().modelDao() }
    factory<ChatDao> { get<AppDatabase>().chatDao() }
    factory {
        FileProcessing(get())
    }
    single {
        Prefs.getInstance(get())
    }
    single {
        SyncMetadata(get())
    }
    single {
        SecurePrefs(get())
    }
    singleOf(::FileRepositoryImpl) { bind<IFileRepository>() }
    singleOf(::FeedbackRepositoryImpl) { bind<IFeedbackRepository>() }
    singleOf(::PromptsRepositoryImpl) { bind<IPromptsRepository>() }
    singleOf(::PromptSynchronizerImpl) { bind<IPromptSynchronizer>() }
    singleOf(::OpenRouterRepositoryImpl) { bind<IOpenRouterRepository>() }
    singleOf(::SettingsRepositoryImpl) { bind<ISettingsRepository>() }
    singleOf(::ChatHistoryRepositoryImpl) { bind<IChatHistoryRepository>() }
    singleOf(::ModelRepositoryImpl) { bind<ModelRepository>() }
    singleOf(::ShareServiceImpl) { bind<ShareService>() }
    // ---------- Диспатчер ----------
    single<CoroutineDispatcher> { Dispatchers.IO }   // можно вынести в отдельный модуль
}
