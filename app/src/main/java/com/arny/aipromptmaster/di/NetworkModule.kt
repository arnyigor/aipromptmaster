package com.arny.aipromptmaster.di


import com.arny.aipromptmaster.BuildConfig
import com.arny.aipromptmaster.data.api.FeedbackApiService
import com.arny.aipromptmaster.data.api.GitHubService
import com.arny.aipromptmaster.data.api.OpenRouterService
import com.arny.aipromptmaster.data.api.VercelApiService
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit

val networkModule = module {

    // Constants (можно вынести в buildConfig или оставить тут)
    val GITHUB_BASE_URL = "https://api.github.com/"
    val OPEN_ROUTER_BASE_URL = "https://openrouter.ai/api/v1/"
    val VERCEL_BASE_URL = "https://aipromptsapi.vercel.app/"

    // 1. JSON Parser
    single {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    // 2. Logging Interceptor
    single {
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }
    }

    // 3. Default OkHttpClient (Named "Default")
    single(named("DefaultOkHttp")) {
        OkHttpClient.Builder()
            .addInterceptor(get<HttpLoggingInterceptor>())
            .build()
    }

    // 4. Vercel OkHttpClient (Named "Vercel")
    single(named("VercelOkHttp")) {
        val apiKeyInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val newRequest = originalRequest.newBuilder()
                .header("X-API-Key", BuildConfig.API_SECRET_KEY)
                .header("Origin", "android-app://com.arny.aipromptmaster")
                .build()
            chain.proceed(newRequest)
        }

        OkHttpClient.Builder()
            .addInterceptor(apiKeyInterceptor)
            .addInterceptor(get<HttpLoggingInterceptor>())
            .build()
    }

    // 5. OpenRouter Retrofit (Uses Default Client)
    single(named("OpenRouterRetrofit")) {
        val contentType = "application/json".toMediaType()
        Retrofit.Builder()
            .baseUrl(OPEN_ROUTER_BASE_URL)
            .client(get(named("DefaultOkHttp"))) // 👈 Инъекция по имени
            .addConverterFactory(get<Json>().asConverterFactory(contentType))
            .build()
    }

    // 6. GitHub Retrofit
    single(named("GitHubRetrofit")) {
        val contentType = "application/json".toMediaType()
        Retrofit.Builder()
            .baseUrl(GITHUB_BASE_URL)
            .client(get(named("DefaultOkHttp")))
            .addConverterFactory(get<Json>().asConverterFactory(contentType))
            .build()
    }

    // 7. Vercel Retrofit (Uses Vercel Client)
    single(named("VercelRetrofit")) {
        val contentType = "application/json".toMediaType()
        Retrofit.Builder()
            .baseUrl(VERCEL_BASE_URL)
            .client(get(named("VercelOkHttp"))) // 👈 Инъекция защищенного клиента
            .addConverterFactory(get<Json>().asConverterFactory(contentType))
            .build()
    }

    // 8. Services (API Interfaces)
    single<OpenRouterService> {
        get<Retrofit>(named("OpenRouterRetrofit")).create(OpenRouterService::class.java)
    }

    single<VercelApiService> {
        get<Retrofit>(named("VercelRetrofit")).create(VercelApiService::class.java)
    }

    single<FeedbackApiService> {
        get<Retrofit>(named("VercelRetrofit")).create(FeedbackApiService::class.java)
    }

    single<GitHubService> {
        get<Retrofit>(named("GitHubRetrofit")).create(GitHubService::class.java)
    }
}