package com.arny.aipromptmaster.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.arny.aipromptmaster.data.db.AppDatabase
import com.arny.aipromptmaster.data.db.daos.ChatDao
import com.arny.aipromptmaster.data.db.daos.PromptDao
import com.arny.aipromptmaster.data.models.GitHubConfig
import com.arny.aipromptmaster.data.openrouter.OpenRouterRepositoryImpl
import com.arny.aipromptmaster.data.prefs.Prefs
import com.arny.aipromptmaster.data.repositories.ChatHistoryRepositoryImpl
import com.arny.aipromptmaster.data.repositories.FeedbackRepositoryImpl
import com.arny.aipromptmaster.data.repositories.FileRepositoryImpl
import com.arny.aipromptmaster.data.repositories.PromptsRepositoryImpl
import com.arny.aipromptmaster.data.repositories.SettingsRepositoryImpl
import com.arny.aipromptmaster.data.sync.PromptSynchronizerImpl
import com.arny.aipromptmaster.data.utils.TokenEstimator
import com.arny.aipromptmaster.domain.repositories.IChatHistoryRepository
import com.arny.aipromptmaster.domain.repositories.IFeedbackRepository
import com.arny.aipromptmaster.domain.repositories.IFileRepository
import com.arny.aipromptmaster.domain.repositories.IOpenRouterRepository
import com.arny.aipromptmaster.domain.repositories.IPromptSynchronizer
import com.arny.aipromptmaster.domain.repositories.IPromptsRepository
import com.arny.aipromptmaster.domain.repositories.ISettingsRepository
import com.arny.aipromptmaster.domain.services.FileProcessingService
import com.arny.aipromptmaster.domain.utils.ITokenEstimator
import dagger.Binds
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
interface DataModule {

    companion object {
        @Provides
        @Singleton
        fun providePreferences(context: Context): Prefs = Prefs.getInstance(context)

        @Provides
        @Singleton
        fun providesIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

        @Provides
        @Singleton
        fun provideGitHubConfig(): GitHubConfig = GitHubConfig(
            owner = "arnyigor",
            repo = "aiprompts",
            branch = "master",
            promptsPath = "prompts"
        )

        @Provides
        @Singleton
        fun provideDb(context: Context): AppDatabase = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            AppDatabase.DBNAME
        )
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .addMigrations(AppDatabase.MIGRATION_2_3)
            .addMigrations(AppDatabase.MIGRATION_3_4)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    db.execSQL("PRAGMA encoding='UTF-8';")
                }
            }).build()

        @Provides
        @Singleton
        fun providePromptDao(db: AppDatabase): PromptDao = db.promptDao()

        @Provides
        @Singleton
        fun provideChatDao(db: AppDatabase): ChatDao = db.chatDao()

        @Provides
        @Singleton
        fun provideFileProcessingService(context: Context): FileProcessingService = FileProcessingService(context)

        @Provides
        @Singleton
        fun provideFileRepository(
            context: Context,
            fileProcessingService: FileProcessingService
        ): FileRepositoryImpl = FileRepositoryImpl(context, fileProcessingService)
    }

    @Binds
    @Singleton
    fun bindTokenEstimator(impl: TokenEstimator): ITokenEstimator

    @Binds
    @Singleton
    fun bindPromptsRepository(impl: PromptsRepositoryImpl): IPromptsRepository

    @Binds
    @Singleton
    fun bindFilesRepository(impl: FileRepositoryImpl): IFileRepository

    @Binds
    @Singleton
    fun bindHistoryRepository(impl: ChatHistoryRepositoryImpl): IChatHistoryRepository

    @Binds
    @Singleton
    fun bindFeedbackInteractor(impl: FeedbackRepositoryImpl): IFeedbackRepository

    @Binds
    @Singleton
    fun bindOpenRouterRepository(impl: OpenRouterRepositoryImpl): IOpenRouterRepository

    @Binds
    @Singleton
    fun bindPromptSynchronizer(impl: PromptSynchronizerImpl): IPromptSynchronizer

    @Binds
    @Singleton
    fun bindSettingsRepository(impl: SettingsRepositoryImpl): ISettingsRepository
}