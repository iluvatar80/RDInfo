// File: app/src/main/java/com/example/rdinfo/data/InfoRepository.kt
package com.example.rdinfo.data

import com.example.rdinfo.data.local.AppDatabase
import com.example.rdinfo.data.local.UseCaseEntity
import com.example.rdinfo.data.local.FormulationEntity
import kotlinx.coroutines.flow.Flow

class InfoRepository(val db: AppDatabase) {

    // Use-Cases nur nach Drug filtern (KEIN Routen-Filter)
    fun observeUseCasesByDrug(drugId: Long): Flow<List<UseCaseEntity>> {
        return db.useCaseDao().observeByDrug(drugId)
    }

    // Passende Formulierung für (Drug, UseCase) wählen
    suspend fun pickPreferredFormulationForUseCase(
        drugId: Long,
        useCaseId: Long
    ): FormulationEntity? {
        val rules = db.doseRuleDao().getByDrugAndUseCase(drugId, useCaseId)
        val formId = rules.firstOrNull { it.formulationId != null }?.formulationId ?: return null
        return db.formulationDao().getById(formId!!)
    }
}
