package com.arny.aipromptmaster.data.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Streaming

interface GitHubService {
    @GET("repos/{owner}/{repo}/zipball/{ref}")
    @Streaming
    @Headers("Accept: application/vnd.github.v3+json")
    suspend fun downloadArchive(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("ref") ref: String
    ): Response<ResponseBody>
}