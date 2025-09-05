// app/src/main/java/com/example/rdinfo/data/local/UseCaseDao.kt
package com.example.rdinfo.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UseCaseDao {
    @Query("SELECT * FROM use_case WHERE drugId = :drugId ORDER BY priority, name")
    fun observeByDrug(drugId: Long): Flow<List<UseCaseEntity>>

    @Query("SELECT * FROM use_case WHERE drugId = :drugId ORDER BY priority, name")
    suspend fun getByDrug(drugId: Long): List<UseCaseEntity>

    @Upsert
    suspend fun upsertAll(items: List<UseCaseEntity>)

    @Query("DELETE FROM use_case WHERE drugId = :drugId")
    suspend fun deleteByDrug(drugId: Long)

    @Query("DELETE FROM use_case")
    suspend fun deleteAll()
}
