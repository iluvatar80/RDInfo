// File: app/src/main/java/com/example/rdinfo/ui/EinsatzScreen.kt
package com.example.rdinfo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.rdinfo.data.local.DoseRuleEntity
import com.example.rdinfo.data.local.FormulationEntity
import com.example.rdinfo.ui.sections.EinsatzDoseSectionHost

/**
 * Minimaler, aber vollständiger Einsatz-Screen.
 *
 * - Keine medikamentspezifischen Hardcodes.
 * - Berechnung/Anzeige erfolgt über EinsatzDoseSectionHost → CalcFacade/DoseMath → DB-Regeln (inkl. dilutionFactor).
 * - Übergib deinen aktuellen Zustand als Parameter (Rule, Formulierung, manuelle Ampullenkonzentration, Gewicht).
 */
@Composable
fun EinsatzScreen(
    appliedRule: DoseRuleEntity?,
    selectedFormulation: FormulationEntity?,
    manualConcMgPerMl: Double?,
    weightKg: Double?,
    contentTop: (@Composable () -> Unit)? = null,   // optional: deine vorhandenen Eingabefelder
    contentBottom: (@Composable () -> Unit)? = null // optional: weitere Abschnitte (Tabs etc.)
) {
    Scaffold { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Optional: existierende UI-Teile oben/unten einhängen
            contentTop?.invoke()

            Text("Berechnung", style = MaterialTheme.typography.titleMedium)
            EinsatzDoseSectionHost(
                appliedRule = appliedRule,
                selectedFormulation = selectedFormulation,
                manualConcMgPerMl = manualConcMgPerMl,
                weightKg = weightKg
            )

            contentBottom?.invoke()
        }
    }
}