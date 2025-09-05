package com.example.rdinfo.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FormulationDao {

    @Query("SELECT * FROM formulation WHERE drugId = :drugId ORDER BY isDefault DESC, route, id")
    fun observeByDrug(drugId: Long): Flow<List<FormulationEntity>>

    @Query("SELECT * FROM formulation WHERE drugId = :drugId ORDER BY isDefault DESC, route, id")
    suspend fun getByDrug(drugId: Long): List<FormulationEntity>

    @Upsert
    suspend fun upsertAll(items: List<FormulationEntity>)

    @Query("DELETE FROM formulation WHERE drugId = :drugId")
    suspend fun deleteByDrug(drugId: Long)

    @Query("DELETE FROM formulation")
    suspend fun deleteAll()

    // + in app/src/main/java/com/example/rdinfo/data/local/FormulationDao.kt
    @Query("SELECT * FROM formulation WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): FormulationEntity?

}
