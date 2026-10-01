package com.arny.aipromptmaster.data

import com.arny.aipromptmaster.domain.repositories.ISettingsRepository
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl

class ProviderRoutingInterceptor(private val settings: ISettingsRepository) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val profile = settings.loadProviders().active
        val request = chain.request()
        val suffix = request.url.encodedPath.removePrefix("/api/v1/")
        val target = (profile.baseUrl.trimEnd('/') + "/" + suffix).toHttpUrl()
        val builder = request.newBuilder().url(target).removeHeader("Authorization")
        if (profile.apiKey.isNotBlank()) builder.header("Authorization", "Bearer ${profile.apiKey}")
        if (profile.id != "openrouter") builder.removeHeader("HTTP-Referer").removeHeader("X-Title")
        return chain.proceed(builder.build())
    }
}
