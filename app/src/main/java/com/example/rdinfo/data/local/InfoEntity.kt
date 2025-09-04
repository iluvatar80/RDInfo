package com.example.rdinfo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "info")
data class InfoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val detail: String,
    val timestamp: Long = System.currentTimeMillis()
)
