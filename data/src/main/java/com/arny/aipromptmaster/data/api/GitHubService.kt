package com.arny.aipromptmaster.data.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Streaming
import retrofit2.http.Url

interface GitHubService {
    @GET
    @Streaming
    suspend fun downloadFile(@Url url: String): Response<ResponseBody>
}
