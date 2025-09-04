package com.example.rdinfo.domain

// File: app/src/main/java/com/example/rdinfo/domain/DoseMath.kt
package com.example.rdinfo.domain

import com.example.rdinfo.data.local.DoseRuleEntity
import kotlin.math.roundToInt

/** Ergebnis der Dosis-/Volumenberechnung. */
data class DoseResult(
    val doseMg: Double?,
    val volumeMl: Double?,
    val concentrationUsedMgPerMl: Double?,
    val cappedByMax: Boolean
)

/**
 * Reine Fachlogik ohne UI – nutzt ausschließlich Daten aus der Datenbank/Regel.
 * Keine medikamentspezifischen Hardcodes: Verdünnungen kommen über `dilutionFactor` aus der Rule.
 */
object DoseMath {

    /**
     * Dosis in mg nach Regel bestimmen.
     * Priorität: mgPerKg (falls Gewicht vorhanden) > flatMg. Maximaldosis wird angewandt.
     * @return Pair(doseMg, cappedByMax)
     */
    fun computeDoseMg(rule: DoseRuleEntity, weightKg: Double?): Pair<Double?, Boolean> {
        val dose = when {
            rule.mgPerKg != null && weightKg != null -> rule.mgPerKg * weightKg
            rule.flatMg != null -> rule.flatMg
            else -> null
        }
        if (dose == null) return null to false
        val max = rule.maxSingleMg
        return if (max != null && dose > max) max to true else dose to false
    }

    /** Effektive Konzentration (mg/mL) unter Berücksichtigung einer Verdünnung. */
    fun effectiveConcentrationMgPerMl(
        stockConcMgPerMl: Double?,
        overrideConcMgPerMl: Double?,
        rule: DoseRuleEntity
    ): Double? {
        val base = overrideConcMgPerMl ?: stockConcMgPerMl ?: return null
        val factor = rule.dilutionFactor
        return if (factor != null && factor > 0.0) base / factor else base
    }

    /** Auf nächste Schrittweite runden (z. B. 0.1 mL). */
    private fun roundToStep(value: Double, step: Double): Double {
        if (step <= 0.0) return value
        val n = (value / step).roundToInt()
        return n * step
    }

    /**
     * Komplettberechnung: Dosis (mg) + Volumen (mL).
     * - Konzentration: zuerst manuelle Vorgabe, sonst Formulierungswert.
     * - Verdünnungen: ausschließlich über `rule.dilutionFactor` (z. B. 10.0 für 1:10).
     */
    fun compute(
        rule: DoseRuleEntity,
        weightKg: Double?,
        stockConcMgPerMl: Double?,
        overrideConcMgPerMl: Double?
    ): DoseResult {
        val (doseMg, capped) = computeDoseMg(rule, weightKg)
        val effConc = effectiveConcentrationMgPerMl(stockConcMgPerMl, overrideConcMgPerMl, rule)
        val volRaw = if (doseMg != null && effConc != null && effConc > 0.0) doseMg / effConc else null
        val volRounded = volRaw?.let { roundToStep(it, rule.roundingMl) }
        return DoseResult(
            doseMg = doseMg,
            volumeMl = volRounded,
            concentrationUsedMgPerMl = effConc,
            cappedByMax = capped
        )
    }
}
