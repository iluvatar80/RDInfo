package com.example.rdinfo.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DrugDao {

    @Query("SELECT * FROM drug ORDER BY name")
    fun observeAll(): Flow<List<DrugEntity>>

    @Query("SELECT * FROM drug ORDER BY name")
    suspend fun getAll(): List<DrugEntity>

    @Upsert
    suspend fun upsert(item: DrugEntity)

    @Upsert
    suspend fun upsertAll(items: List<DrugEntity>)

    @Query("DELETE FROM drug")
    suspend fun deleteAll()

    @Query("SELECT * FROM drug WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): DrugEntity?
}
