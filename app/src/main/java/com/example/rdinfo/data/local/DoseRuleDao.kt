package com.example.rdinfo.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface DoseRuleDao {

    @Upsert
    suspend fun upsertAll(items: List<DoseRuleEntity>)

    @Query("DELETE FROM dose_rule")
    suspend fun deleteAll()

    @Query(
        """
        SELECT * FROM dose_rule
        WHERE drugId = :drugId AND useCaseId = :useCaseId
        ORDER BY id
        """
    )
    suspend fun getByDrugAndUseCase(
        drugId: Long,
        useCaseId: Long
    ): List<DoseRuleEntity>

    @Query(
        """
        SELECT * FROM dose_rule
        WHERE drugId = :drugId
          AND useCaseId = :useCaseId
          AND (:formulationId IS NULL OR formulationId = :formulationId)
          AND (ageMinMonths IS NULL OR :ageMonths >= ageMinMonths)
          AND (ageMaxMonths IS NULL OR :ageMonths <= ageMaxMonths)
        ORDER BY
          CASE WHEN formulationId = :formulationId THEN 0 ELSE 1 END,
          CASE WHEN ageMinMonths IS NOT NULL OR ageMaxMonths IS NOT NULL THEN 0 ELSE 1 END,
          id
        LIMIT 1
        """
    )
    suspend fun pickByDrugUseCaseFormulationAndAge(
        drugId: Long,
        useCaseId: Long,
        formulationId: Long?,
        ageMonths: Int
    ): DoseRuleEntity?
}
