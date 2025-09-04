// File: app/src/main/java/com/example/rdinfo/domain/DoseMath.kt
package com.example.rdinfo.domain

import com.example.rdinfo.data.local.DoseRuleEntity
import kotlin.math.roundToInt

data class DoseResult(
    val doseMg: Double?,
    val volumeMl: Double?,
    val concentrationUsedMgPerMl: Double?,
    val cappedByMax: Boolean
)

object DoseMath {
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

    fun effectiveConcentrationMgPerMl(
        stockConcMgPerMl: Double?,
        overrideConcMgPerMl: Double?,
        rule: DoseRuleEntity
    ): Double? {
        val base = overrideConcMgPerMl ?: stockConcMgPerMl ?: return null

        // 1) Expliziter Verdünnungsfaktor aus der Datenbank
        val explicit = rule.dilutionFactor
        if (explicit != null && explicit > 0.0) return base / explicit

        // 2) Fallback (datengetrieben): Erkenne "1:10" im displayHint der Regel
        val hint = rule.displayHint
        if (hint != null && containsOneToTen(hint)) return base / 10.0

        // 3) Keine Verdünnung
        return base
    }

    private fun containsOneToTen(text: String): Boolean {
        val compact = buildString {
            for (c in text) if (!c.isWhitespace()) append(c)
        }.lowercase()
        return ":" in compact && "1:10" in compact
    }

    private fun roundToStep(value: Double, step: Double): Double {
        if (step <= 0.0) return value
        val n = (value / step).roundToInt()
        return n * step
    }

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
