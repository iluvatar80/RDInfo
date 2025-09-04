// File: app/src/main/java/com/example/rdinfo/ui/sections/EinsatzDoseSectionHost.kt
package com.example.rdinfo.ui.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.rdinfo.data.local.DoseRuleEntity
import com.example.rdinfo.data.local.FormulationEntity

/**
 * Schlanker Host, um die Dosis-/Volumenanzeige ohne Umbauten im Screen zu integrieren.
 *
 * Übergib einfach deine aktuellen Zustandswerte (Regel, Formulierung, Gewicht, manuelle Ampullenkonzentration).
 * Die Berechnung berücksichtigt automatisch die **manuelle** Konzentration (falls != null), sonst die der Formulierung,
 * und – datengetrieben – den `dilutionFactor` aus der ausgewählten Regel (z. B. 10 bei Reanimation).
 */
@Composable
fun EinsatzDoseSectionHost(
    appliedRule: DoseRuleEntity?,
    selectedFormulation: FormulationEntity?,
    manualConcMgPerMl: Double?,
    weightKg: Double?,
    modifier: Modifier = Modifier
) {
    EinsatzDoseSection(
        rule = appliedRule,
        weightKg = weightKg,
        stockConcMgPerMl = selectedFormulation?.concentrationMgPerMl,
        manualConcMgPerMl = manualConcMgPerMl,
        modifier = modifier
    )
}
