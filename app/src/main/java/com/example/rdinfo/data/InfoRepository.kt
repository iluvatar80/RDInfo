package com.example.rdinfo.data

import com.example.rdinfo.data.local.InfoDao
import com.example.rdinfo.data.local.InfoEntity
import kotlinx.coroutines.flow.Flow

class InfoRepository(private val dao: InfoDao) {
    fun observeAll(): Flow<List<InfoEntity>> = dao.observeAll()
    suspend fun add(title: String, detail: String) =
        dao.insert(InfoEntity(title = title.trim(), detail = detail.trim()))
    suspend fun delete(id: Long) = dao.deleteById(id)
    suspend fun clear() = dao.deleteAll()
}
