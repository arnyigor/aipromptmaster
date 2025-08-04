package com.arny.aipromptmaster.data.di

import com.arny.aipromptmaster.data.api.FeedbackApiService
import com.arny.aipromptmaster.data.api.GitHubService
import com.arny.aipromptmaster.data.api.OpenRouterService
import com.arny.aipromptmaster.data.api.VercelApiService
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Qualifier
import javax.inject.Singleton

@Module
object NetworkModule {
    private const val GITHUB_BASE_URL = "https://api.github.com/"
    private const val OPEN_ROUTER_BASE_URL = "https://openrouter.ai/api/v1/"
    private const val FEEDBACK_BASE_URL = "https://aipromptsapi.vercel.app/"

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideJsonParser(): Json = Json {
        ignoreUnknownKeys = true // Очень важная настройка для стабильности
        isLenient = true // Помогает с некоторыми нестрогими JSON
    }

    @Provides
    @Singleton
    @GitHubRetrofit
    fun provideGitHubRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(GITHUB_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    @OpenRouterRetrofit
    fun provideOpenRouterRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(OPEN_ROUTER_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    @VercelRetrofit
    fun provideVercelApiRetrofit(json: Json, okHttpClient: OkHttpClient): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl("https://aipromptsapi.vercel.app/") // Новый базовый URL
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideGitHubService(@GitHubRetrofit retrofit: Retrofit): GitHubService {
        return retrofit.create(GitHubService::class.java)
    }

    @Provides
    @Singleton
    fun provideOpenRouterService(@OpenRouterRetrofit retrofit: Retrofit): OpenRouterService {
        return retrofit.create(OpenRouterService::class.java)
    }

    @Provides
    @Singleton
    @FeedbackRetrofit
    fun provideFeedbackRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl(FEEDBACK_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideFeedbackApiService(@FeedbackRetrofit retrofit: Retrofit): FeedbackApiService {
        return retrofit.create(FeedbackApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideVercelApiService(@VercelRetrofit retrofit: Retrofit): VercelApiService {
        return retrofit.create(VercelApiService::class.java)
    }
}

// Квалификаторы для различения экземпляров Retrofit
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GitHubRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OpenRouterRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FeedbackRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class VercelRetrofit