// File: app/src/main/java/com/example/rdinfo/ui/EinsatzScreen.kt
package com.example.rdinfo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.rdinfo.data.local.DoseRuleEntity
import com.example.rdinfo.data.local.FormulationEntity
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Einsatz-Screen mit eigener Ergebnis-Box (ohne heuristische Verdünnungslogik).
 * Volumenanzeige wird hier lokal korrekt berechnet.
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

            // NEU: Ergebnis-Card mit korrekter Volumenberechnung (ohne "1:10"-Hint-Heuristik)
            ResultCardNoHeuristic(
                rule = appliedRule,
                stockConcMgPerMl = selectedFormulation?.concentrationMgPerMl, // Ampullen-Konzentration
                manualConcMgPerMl = manualConcMgPerMl,                         // manueller Override (hat Vorrang)
                weightKg = weightKg
            )

            // Optional: restliche Inhalte
            contentBottom?.invoke()
        }
    }
}

/* ---------------------- interne Logik (ohne Heuristik) ---------------------- */

private data class Ui(
    val doseText: String,
    val volumeText: String,
    val concentrationText: String,
    val cappedByMax: Boolean
)

@Composable
private fun ResultCardNoHeuristic(
    rule: DoseRuleEntity?,
    stockConcMgPerMl: Double?,
    manualConcMgPerMl: Double?,
    weightKg: Double?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Berechnung", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            if (rule == null) {
                Text("Keine Regel ausgewählt")
                return@Column
            }

            val ui = computeUiNoHeuristic(
                rule = rule,
                weightKg = weightKg,
                stockConcMgPerMl = stockConcMgPerMl,
                manualConcMgPerMl = manualConcMgPerMl
            )

            Text("Gesamtdosis: ${ui.doseText}")
            Spacer(Modifier.height(4.dp))
            Text("Volumen: ${ui.volumeText}")
            Spacer(Modifier.height(4.dp))
            Text("Konzentration (effektiv): ${ui.concentrationText}")

            if (ui.cappedByMax) {
                Spacer(Modifier.height(6.dp))
                Text("Maximaldosis angewandt", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun computeUiNoHeuristic(
    rule: DoseRuleEntity,
    weightKg: Double?,
    stockConcMgPerMl: Double?,
    manualConcMgPerMl: Double?
): Ui {
    val (doseMg, capped) = computeDoseMg(rule, weightKg)

    // Konzentration OHNE Hint-Heuristik:
    // 1) manueller Override
    // 2) Ampullen-Konzentration (Formulierung)
    // 3) optional: expliziter dilutionFactor aus der DB
    val base = manualConcMgPerMl ?: stockConcMgPerMl
    val effConc = base?.let { b ->
        val f = rule.dilutionFactor
        if (f != null && f > 0.0) b / f else b
    }

    val volumeRaw = if (doseMg != null && effConc != null && effConc > 0.0) doseMg / effConc else null
    val volumeRounded = volumeRaw?.let { roundToStep(it, rule.roundingMl) }

    val doseText = doseMg?.let { formatDe(it, "mg") } ?: "—"
    val volText = volumeRounded?.let { formatDe(it, "ml") } ?: "—"
    val concText = effConc?.let { formatDe(it, "mg/ml") } ?: "—"

    return Ui(
        doseText = doseText,
        volumeText = volText,
        concentrationText = concText,
        cappedByMax = capped
    )
}

private fun computeDoseMg(rule: DoseRuleEntity, weightKg: Double?): Pair<Double?, Boolean> {
    val dose = when {
        rule.mgPerKg != null && weightKg != null -> rule.mgPerKg * weightKg
        rule.flatMg != null -> rule.flatMg
        else -> null
    }
    if (dose == null) return null to false

    val max = rule.maxSingleMg
    return if (max != null && dose > max) max to true else dose to false
}

private fun roundToStep(value: Double, step: Double): Double {
    if (step <= 0.0) return value
    val n = (value / step).roundToInt()
    return n * step
}

private fun formatDe(value: Double, unit: String): String {
    val s = String.format(Locale.GERMANY, "%.1f", value)
    return "$s $unit"
}
