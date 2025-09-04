// File: src/main/java/com/example/rdinfo/data/local/DoseRuleEntity.kt
package com.example.rdinfo.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dose_rule")
data class DoseRuleEntity(
    @PrimaryKey val id: Long,
    val drugId: Long,
    val useCaseId: Long,
    val formulationId: Long?,

    // Dosis-Definition
    val mode: String,            // z. B. "flat" oder "mg_per_kg"
    val mgPerKg: Double?,        // falls gewichtsabhängig
    val flatMg: Double?,         // falls feste Dosis
    @ColumnInfo(name = "maxSingleMg")
    val maxSingleMg: Double?,    // optionale Obergrenze pro Gabe

    // Volumen-/Anzeige-Feintuning
    @ColumnInfo(name = "roundingMl", defaultValue = "0.1")
    val roundingMl: Double = 0.1,
    @ColumnInfo(name = "displayHint", defaultValue = "")
    val displayHint: String = "",

    // Alters-/Gewichts-Geltungsbereich
    @ColumnInfo(name = "ageMinMonths")
    val ageMinMonths: Int?,
    @ColumnInfo(name = "ageMaxMonths")
    val ageMaxMonths: Int?,
    @ColumnInfo(name = "weightMinKg")
    val weightMinKg: Double?,
    @ColumnInfo(name = "weightMaxKg")
    val weightMaxKg: Double?,

    // Datengetriebene Verdünnung (z. B. 10.0 für 1:10). Null = keine Verdünnung.
    @ColumnInfo(name = "dilutionFactor", defaultValue = "NULL")
    val dilutionFactor: Double? = null
)
