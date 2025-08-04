package com.arny.aipromptmaster.data.di

import com.arny.aipromptmaster.data.BuildConfig
import com.arny.aipromptmaster.data.api.FeedbackApiService
import com.arny.aipromptmaster.data.api.OpenRouterService
import com.arny.aipromptmaster.data.api.VercelApiService
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import javax.inject.Qualifier
import javax.inject.Singleton

@Module
object NetworkModule {
    private const val OPEN_ROUTER_BASE_URL = "https://openrouter.ai/api/v1/"
    private const val VERCEL_BASE_URL = "https://aipromptsapi.vercel.app/"

    @Provides
    @Singleton
    fun provideJsonParser(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Provides
    @Singleton
    @DefaultOkHttpClient
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    @VercelOkHttpClient
    fun provideVercelOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }

        // Создаем Interceptor, который будет добавлять наш ключ в заголовок
        val apiKeyInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val newRequest = originalRequest.newBuilder()
                .header("X-API-Key", BuildConfig.API_SECRET_KEY)
                .build()
            chain.proceed(newRequest)
        }

        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(apiKeyInterceptor)
            .build()
    }

    @Provides
    @Singleton
    @OpenRouterRetrofit
    fun provideOpenRouterRetrofit(@DefaultOkHttpClient okHttpClient: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(OPEN_ROUTER_BASE_URL)
            .client(okHttpClient) // Используем ОБЩИЙ клиент
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    @VercelRetrofit
    fun provideVercelApiRetrofit(json: Json, @VercelOkHttpClient okHttpClient: OkHttpClient): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(VERCEL_BASE_URL)
            .client(okHttpClient) // Используем ЗАЩИЩЕННЫЙ клиент
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    // --- СЕРВИСЫ ---

    @Provides
    @Singleton
    fun provideOpenRouterService(@OpenRouterRetrofit retrofit: Retrofit): OpenRouterService =
        retrofit.create(OpenRouterService::class.java)

    // VercelApiService и FeedbackApiService теперь используют один и тот же инстанс Retrofit
    @Provides
    @Singleton
    fun provideVercelApiService(@VercelRetrofit retrofit: Retrofit): VercelApiService =
        retrofit.create(VercelApiService::class.java)

    @Provides
    @Singleton
    fun provideFeedbackApiService(@VercelRetrofit retrofit: Retrofit): FeedbackApiService =
        retrofit.create(FeedbackApiService::class.java)
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OpenRouterRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class VercelRetrofit

// Квалификаторы для разных OkHttpClient
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultOkHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class VercelOkHttpClient