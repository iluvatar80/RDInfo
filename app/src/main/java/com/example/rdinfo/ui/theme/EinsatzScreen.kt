// File: app/src/main/java/com/example/rdinfo/ui/theme/EinsatzScreen.kt
package com.example.rdinfo.ui.theme

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

/**
 * Einsatz-Screen mit datengetriebener Berechnungsbox (CalcCard) im **ui.theme**-Paket.
 * Achtung: Paketname bewusst `com.example.rdinfo.ui.theme`, damit dieser Screen
 * exakt an der Stelle eingebunden wird, an der dein Projekt ihn referenziert.
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
            // Optional: vorhandene Eingabe-UI oberhalb einhängen
            contentTop?.invoke()

            // Berechnungs-Box (rechnet automatisch mit 1:10 bei Reanimation, wenn in den Daten/Regeln hinterlegt)
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
