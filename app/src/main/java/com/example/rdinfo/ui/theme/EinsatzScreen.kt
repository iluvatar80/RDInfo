// File: app/src/main/java/com/example/rdinfo/ui/EinsatzScreen.kt
package com.example.rdinfo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.rdinfo.data.local.DoseRuleEntity
import com.example.rdinfo.data.local.FormulationEntity
import com.example.rdinfo.ui.theme.CalcCard

/**
 * Einsatz-Screen, der die Berechnungsbox über CalcCard rendert.
 * Keine Hardcodes – Logik über DoseMath/CalcFacade; Daten (inkl. Verdünnung) kommen aus der DB.
 */
@Composable
fun EinsatzScreen(
    appliedRule: DoseRuleEntity?,
    selectedFormulation: FormulationEntity?,
    manualConcMgPerMl: Double?,
    weightKg: Double?,
    contentTop: (@Composable () -> Unit)? = null,
    contentBottom: (@Composable () -> Unit)? = null
) {
    Scaffold { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Optional: bestehende Eingabe-UI
            contentTop?.invoke()

            // Berechnungs-Box
            CalcCard(
                appliedRule = appliedRule,
                selectedFormulation = selectedFormulation,
                manualConcMgPerMl = manualConcMgPerMl,
                weightKg = weightKg
            )

            // Optional: restliche Inhalte
            contentBottom?.invoke()
        }
    }
}
