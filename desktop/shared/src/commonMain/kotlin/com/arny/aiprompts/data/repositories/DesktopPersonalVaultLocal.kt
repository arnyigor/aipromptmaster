package com.arny.aiprompts.data.repositories

import com.arny.aiprompts.data.db.daos.PromptDao
import com.arny.aiprompts.data.mappers.*
import com.arny.promptcontract.*

class DesktopPersonalVaultLocal(private val dao: PromptDao, private val settings: ISettingsRepository) : PersonalVaultLocal {
    override suspend fun snapshot() = PersonalVaultSnapshot(prompts = dao.getAllPrompts().filter { it.isLocal }.map { it.toDomain().toExportJson() }.sortedBy { it.id }).checked()
    override suspend fun validate(expected: PersonalVaultSnapshot, result: PersonalVaultSnapshot) {
        require(snapshot() == expected) { "Локальные промпты изменились. Проверьте изменения снова" }
        val publicIds = dao.getAllPrompts().filterNot { it.isLocal }.map { it.id }.toSet()
        require(result.checked().prompts.none { it.id in publicIds }) { "ID личного промпта совпадает с публичным каталогом" }
    }
    override suspend fun apply(expected: PersonalVaultSnapshot, result: PersonalVaultSnapshot) {
        // Old debug-import backups must never resurrect prompts deleted by vault sync.
        settings.markLegacyPersonalFilesLoaded()
        dao.applyPersonalVault(expected, result)
    }
}
