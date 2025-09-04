package com.example.rdinfo.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Dosierregel für ein Medikament in einem bestimmten UseCase und optional für eine bestimmte Formulation.
 * Hinweis:
 * - 'mode' steuert, welches der Werte (mgPerKg | ugPerKg | flatMg) verwendet wird.
 * - roundingMl hat nun einen Default (0.1 ml), damit UI-Rundungen reproduzierbar sind.
 * - zusätzlicher zusammengesetzter Index (drugId, useCaseId, formulationId) für schnellere Auswahl.
 */
@Entity(
    tableName = "dose_rule",
    indices = [
        Index(value = ["drugId"]),
        Index(value = ["useCaseId"]),
        Index(value = ["formulationId"]),
        Index(value = ["drugId", "useCaseId", "formulationId"])
    ]
)
data class DoseRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    val drugId: Long,                // FK → DrugEntity.id
    val useCaseId: Long?,            // FK → UseCaseEntity.id
    val formulationId: Long?,        // FK → FormulationEntity.id (optional)

    val mode: String,                // "MG_PER_KG", "UG_PER_KG", "FLAT_MG"
    val mgPerKg: Double? = null,     // wenn mode = MG_PER_KG
    val ugPerKg: Double? = null,     // wenn mode = UG_PER_KG
    val flatMg: Double? = null,      // wenn mode = FLAT_MG

    val ageMinMonths: Int? = null,   // optionale Altersgrenzen
    val ageMaxMonths: Int? = null,
    val weightMinKg: Double? = null, // optionale Gewichtslimits
    val weightMaxKg: Double? = null,

    val maxSingleMg: Double? = null, // Obergrenze pro Gabe
    val maxDailyMg: Double? = null,  // optionale Tagesgrenze

    val roundingMl: Double = 0.1,    // NEU: Standard-Rundung in ml
    val displayHint: String? = null, // z. B. "langsam titrieren"

    val updatedAt: Long = System.currentTimeMillis()
)
