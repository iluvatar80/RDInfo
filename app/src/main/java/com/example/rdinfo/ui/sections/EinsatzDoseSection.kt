// File: app/src/main/java/com/example/rdinfo/ui/sections/EinsatzDoseSection.kt
package com.example.rdinfo.ui.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.rdinfo.data.local.DoseRuleEntity
import com.example.rdinfo.ui.components.DoseSummary

/**
 * Schlanker UI-Baustein für die Dosis-/Volumenanzeige.
 * Nutzt automatisch manuelle Ampullenkonzentration (falls gesetzt) oder die aus der Formulierung
 * sowie den dilutionFactor aus der Datenbank (z. B. 10 für Reanimation 1:10).
 */
@Composable
fun EinsatzDoseSection(
    rule: DoseRuleEntity?,
    weightKg: Double?,
    stockConcMgPerMl: Double?,    // z. B. selectedFormulation?.concentrationMgPerMl
    manualConcMgPerMl: Double?,   // manuelle Eingabe (hat Vorrang)
    modifier: Modifier = Modifier
) {
    DoseSummary(
        rule = rule,
        weightKg = weightKg,
        stockConcMgPerMl = stockConcMgPerMl,
        manualConcMgPerMl = manualConcMgPerMl,
        modifier = modifier
    )
}
