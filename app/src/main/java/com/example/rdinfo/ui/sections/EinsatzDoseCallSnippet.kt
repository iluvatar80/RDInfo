// File: app/src/main/java/com/example/rdinfo/ui/sections/EinsatzDoseCallSnippet.kt
package com.example.rdinfo.ui.sections

import androidx.compose.runtime.Composable
import com.example.rdinfo.data.local.DoseRuleEntity
import com.example.rdinfo.data.local.FormulationEntity

/**
 * Kopiere die AUFRUF-ZEILE aus dieser Funktion in deinen EinsatzScreen.kt
 * (dort, wo Dosis/Volumen angezeigt werden).
 */
@Composable
fun CallDoseSectionInYourScreen(
    appliedRule: DoseRuleEntity?,
    selectedFormulation: FormulationEntity?,
    manualConcMgPerMl: Double?,
    weightKg: Double?
) {
    // ⬇⬇⬇ DIESE ZEILE in deinen Screen kopieren ⬇⬇⬇
    EinsatzDoseSectionHost(
        appliedRule = appliedRule,
        selectedFormulation = selectedFormulation,
        manualConcMgPerMl = manualConcMgPerMl,
        weightKg = weightKg,
    )
    // ⬆⬆⬆ DIESE ZEILE in deinen Screen kopieren ⬆⬆⬆
}
