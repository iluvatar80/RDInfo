package com.example.rdinfo.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Darreichung/Konzentration eines Medikaments.
 * Beispiel: i.v. 1 mg in 10 ml (0.1 mg/ml), i.m. 1 mg/ml, inhalativ 1 mg/ml …
 */
@Entity(
    tableName = "formulation",
    indices = [
        Index(value = ["drugId"]),              // schneller JOIN auf Drug
        Index(value = ["drugId", "route"])      // häufige Filterkombination
    ]
)
data class FormulationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val drugId: Long,                           // FK → DrugEntity.id
    val route: String,                          // "i.v.", "i.m.", "inhal.", "bukkal", ...
    val concentrationMgPerMl: Double,           // mg/ml
    val label: String = "",                     // z.B. "1 mg in 10 ml (1:10)"
    val isDefault: Boolean = false,             // Standard-Formulierung für schnellen Einsatz
    val dilutionHint: String = "",              // Freitext-Hinweis zur Verdünnung
    val updatedAt: Long = System.currentTimeMillis()
)
