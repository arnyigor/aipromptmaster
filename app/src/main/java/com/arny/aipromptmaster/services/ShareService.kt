package com.arny.aipromptmaster.services

interface ShareService {
    fun shareText(text: String)
    fun exportFile(fileName: String, content: String)
    fun copyToClipboard(content: String)
}