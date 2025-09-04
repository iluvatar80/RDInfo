package com.example.rdinfo.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Konkreter Einsatzfall für ein Medikament.
 * Beispiel: "Anaphylaxie", "Reanimation", "Sedierung", …
 */
@Entity(
    tableName = "use_case",
    indices = [Index(value = ["drugId"])]
)
data class UseCaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val drugId: Long,           // FK → DrugEntity.id
    val name: String,           // z.B. "Anaphylaxie"
    val description: String = "", // optional: Freitext-Hinweis
    val priority: Int = 0,      // für Sortierung (niedriger Wert = wichtiger)
    val updatedAt: Long = System.currentTimeMillis()
)
