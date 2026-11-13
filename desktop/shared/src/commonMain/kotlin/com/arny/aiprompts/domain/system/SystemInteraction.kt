package com.arny.aiprompts.domain.system

// Этот интерфейс будет инжектироваться в DefaultImporterComponent
interface SystemInteraction {
    fun showNotification(title: String, message: String)
}