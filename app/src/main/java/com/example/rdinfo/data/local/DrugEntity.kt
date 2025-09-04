package com.example.rdinfo.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stammdaten eines Medikaments (ohne Darreichung/Regeln).
 */
@Entity(
    tableName = "drug",
    indices = [Index(value = ["name"], unique = true)]
)
data class DrugEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val indications: String = "",
    val contraindications: String = "",
    val effects: String = "",
    val adverseEffects: String = "",
    val notes: String = "",
    val isFavorite: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
