package com.arny.aipromptmaster.data.repositories

import com.arny.aipromptmaster.data.db.daos.PromptDao
import com.arny.aipromptmaster.data.mappers.*
import com.arny.promptcontract.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidPersonalVaultLocal(private val dao: PromptDao) : PersonalVaultLocal {
    override suspend fun snapshot() = withContext(Dispatchers.IO) {
        PersonalVaultSnapshot(prompts = dao.getAllPrompts().filter { it.isLocal }.map { it.toDomain().toExportJson() }.sortedBy { it.id }).checked()
    }
    override suspend fun validate(expected: PersonalVaultSnapshot, result: PersonalVaultSnapshot) = withContext(Dispatchers.IO) {
        require(snapshot() == expected) { "Локальные промпты изменились. Проверьте изменения снова" }
        val publicIds = dao.getAllPrompts().filterNot { it.isLocal }.map { it.id }.toSet()
        require(result.checked().prompts.none { it.id in publicIds }) { "ID личного промпта совпадает с публичным каталогом" }
    }
    override suspend fun apply(expected: PersonalVaultSnapshot, result: PersonalVaultSnapshot) {
        dao.applyPersonalVault(expected, result)
    }
}
