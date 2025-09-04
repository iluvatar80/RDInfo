// File: app/src/main/java/com/example/rdinfo/ui/components/DoseSummary.kt
package com.example.rdinfo.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.rdinfo.data.local.DoseRuleEntity
import com.example.rdinfo.domain.CalcFacade

/**
 * Kompakte Ausgabe der Dosis-/Volumenberechnung.
 * Berechnung erfolgt voll datengetrieben (Ampullen-Konz. + dilutionFactor aus Rule).
 */
@Composable
fun DoseSummary(
    rule: DoseRuleEntity?,
    weightKg: Double?,
    stockConcMgPerMl: Double?,    // z. B. selectedFormulation?.concentrationMgPerMl
    manualConcMgPerMl: Double?,   // manuelle Eingabe (hat Vorrang, falls != null)
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            if (rule == null) {
                Text("Keine Regel ausgewählt")
                return@Column
            }
            val ui = CalcFacade.computeUi(
                rule = rule,
                weightKg = weightKg,
                stockConcMgPerMl = stockConcMgPerMl,
                manualConcMgPerMl = manualConcMgPerMl
            )
            Text("Gesamtdosis: ${'$'}{ui.doseText}")
            Spacer(Modifier.height(6.dp))
            Text("Volumen: ${'$'}{ui.volumeText}")
            Spacer(Modifier.height(6.dp))
            Text("Konzentration (effektiv): ${'$'}{ui.concentrationText}")
            if (ui.cappedByMax) {
                Spacer(Modifier.height(6.dp))
                Text("Maximaldosis angewandt", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

