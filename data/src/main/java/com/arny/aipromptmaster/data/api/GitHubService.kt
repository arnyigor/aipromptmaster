package com.arny.aipromptmaster.data.api

import com.arny.aipromptmaster.data.models.GitHubCommit
import com.arny.aipromptmaster.data.models.GitHubContent
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface GitHubService {
    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getContents(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Query("ref") ref: String = "main"
    ): Response<List<GitHubContent>>

    @GET("repos/{owner}/{repo}/commits")
    suspend fun getCommits(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Query("since") since: String? = null
    ): Response<List<GitHubCommit>>
} 