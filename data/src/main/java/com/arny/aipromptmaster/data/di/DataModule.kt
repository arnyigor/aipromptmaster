package com.arny.aipromptmaster.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.arny.aipromptmaster.data.db.AppDatabase
import com.arny.aipromptmaster.data.db.daos.PromptDao
import com.arny.aipromptmaster.data.db.daos.PromptHistoryDao
import com.arny.aipromptmaster.data.models.GitHubConfig
import com.arny.aipromptmaster.data.prefs.Prefs
import com.arny.aipromptmaster.data.repositories.PromptsRepositoryImpl
import com.arny.aipromptmaster.data.sync.PromptSynchronizerImpl
import com.arny.aipromptmaster.domain.repositories.IPromptSynchronizer
import com.arny.aipromptmaster.domain.repositories.IPromptsRepository
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
        fun provideHistoryDao(db: AppDatabase): PromptHistoryDao = db.promptHistoryDao()

        @Provides
        @Singleton
        fun provideDispatcher(): CoroutineDispatcher = Dispatchers.IO
    }

    @Binds
    @Singleton
    fun bindRepository(impl: PromptsRepositoryImpl): IPromptsRepository

    @Binds
    @Singleton
    fun bindPromptSynchronizer(impl: PromptSynchronizerImpl): IPromptSynchronizer
}