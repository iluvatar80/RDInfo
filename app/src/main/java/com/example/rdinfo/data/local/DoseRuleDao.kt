package com.example.rdinfo.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface DoseRuleDao {

    @Upsert
    suspend fun upsertAll(items: List<DoseRuleEntity>)

    // Wenn deine Entity ein eigenes tableName hat (z. B. "dose_rules"),
    // dann hier "DoseRuleEntity" durch diesen Namen ersetzen.
    @Query("DELETE FROM DoseRuleEntity")
    suspend fun deleteAll()
}
