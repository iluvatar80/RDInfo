// File: app/src/main/java/com/example/rdinfo/ui/theme/CalcCard.kt
package com.example.rdinfo.ui.theme

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
import com.example.rdinfo.data.local.FormulationEntity
import com.example.rdinfo.domain.CalcFacade

/**
 * Zeigt die Box "Berechnung" auf Basis der datengetriebenen Pipeline (DoseMath/CalcFacade).
 * – nutzt manuelle Ampullen-Konz. (falls gesetzt), sonst die der Formulierung
 * – berücksichtigt Rule.dilutionFactor (z. B. 10 für Reanimation 1:10)
 */
@Composable
fun CalcCard(
    appliedRule: DoseRuleEntity?,
    selectedFormulation: FormulationEntity?,
    manualConcMgPerMl: Double?,
    weightKg: Double?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Berechnung", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            if (appliedRule == null) {
                Text("Keine Regel ausgewählt")
                return@Column
            }

            val ui = CalcFacade.computeUi(
                rule = appliedRule,
                weightKg = weightKg,
                stockConcMgPerMl = selectedFormulation?.concentrationMgPerMl,
                manualConcMgPerMl = manualConcMgPerMl
            )

            Text("Gesamtdosis: ${'$'}{ui.doseText}")
            Spacer(Modifier.height(4.dp))
            Text("Volumen: ${'$'}{ui.volumeText}")
            Spacer(Modifier.height(4.dp))
            Text("Konzentration (effektiv): ${'$'}{ui.concentrationText}")
            if (ui.cappedByMax) {
                Spacer(Modifier.height(6.dp))
                Text("Maximaldosis angewandt", color = MaterialTheme.colorScheme.error)
            }

            val hint = appliedRule.displayHint
            if (!hint.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(hint, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
