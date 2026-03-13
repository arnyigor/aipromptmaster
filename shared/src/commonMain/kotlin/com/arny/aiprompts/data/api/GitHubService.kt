package com.arny.aiprompts.data.api

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes

interface GitHubService {
    suspend fun downloadFile(url: String): ByteArray
}

class GitHubServiceImpl(private val httpClient: HttpClient) : GitHubService {
    override suspend fun downloadFile(url: String): ByteArray {
        return httpClient.get(url).bodyAsBytes()
    }
}