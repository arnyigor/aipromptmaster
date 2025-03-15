package com.arny.aipromptmaster.data.models

import com.google.gson.annotations.SerializedName

data class GitHubContent(
    val name: String,
    val path: String,
    val sha: String,
    val size: Int,
    val type: String,
    @SerializedName("download_url")
    val downloadUrl: String?,
    @SerializedName("html_url")
    val htmlUrl: String
)

data class GitHubCommit(
    val sha: String,
    val commit: CommitInfo,
    @SerializedName("html_url")
    val htmlUrl: String
)

data class CommitInfo(
    val author: CommitAuthor,
    val message: String,
    @SerializedName("comment_count")
    val commentCount: Int
)

data class CommitAuthor(
    val name: String,
    val email: String,
    val date: String
) 