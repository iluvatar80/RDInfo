package com.example.rdinfo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InfoDao {
    @Query("SELECT * FROM info ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<InfoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: InfoEntity)

    @Query("DELETE FROM info WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM info")
    suspend fun deleteAll()
}
