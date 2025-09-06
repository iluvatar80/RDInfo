// app/src/main/java/com/rdinfo/logic/RuleDosingCalculator.kt
package com.rdinfo.logic

import com.rdinfo.data.model.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * Reiner Interpreter für regelgetriebene Dosierungen.
 *
 * Auswahl-Logik:
 *  1) Medication → UseCase (by id) → Route (exact match) → passende DosingRule(n)
 *  2) Regeln werden nach Alter/Gewicht/Conditions gefiltert und nach priority absteigend sortiert.
 *  3) Ersttreffer wird verwendet.
 *
 * Berechnung:
 *  - Dosis (mg): perKg oder fixed; anschließend minMg/maxMg-Kappungen.
 *  - Effektive Konzentration (mg/ml):
 *      * Basis = manuelle Ampulle (falls vorhanden), sonst Standard-Ampulle des Medikaments.
 *      * Wenn rule.dilution.totalVolumeMl gesetzt → mg / totalVolumeMl
 *        (unabhängig vom Ampullen-Volumen, da vor Applikation aufgezogen/verdünnt wird).
 *      * Sonst → mg / ml aus der Ampullendefinition.
 *  - Volumen (ml) = Dosis(mg) / Konzentration(mg/ml).
 *  - "Gesamt" (ml) = rule.dilution.totalVolumeMl (falls vorhanden), sonst null.
 *
 * Runden:
 *  - mgStep / mlStep aus rule.rounding werden auf Ergebnis angewandt (kaufmännisch gerundet).
 */
object RuleDosingCalculator {

    data class Input(
        val medication: Medication,
        val useCaseId: String,
        val route: String,
        val ageMonths: Int,
        val weightKg: Double?,                 // erforderlich bei perKg-Regeln
        val manualAmpoule: AmpouleStrength? = null // falls Nutzer manuelle mg/ml angibt
    )

    data class Result(
        val ok: Boolean,
        val error: String? = null,
        val doseMg: Double? = null,
        val concentrationMgPerMl: Double? = null,
        val volumeMl: Double? = null,
        val solutionText: String? = null,
        val totalVolumeMl: Double? = null,
        val appliedRuleId: String? = null,
        val appliedRoute: String? = null,
        val ruleHint: String? = null
    )

    fun calculate(input: Input): Result {
        val useCase = input.medication.useCases.firstOrNull { it.id == input.useCaseId }
            ?: return Result(false, "Use-Case nicht gefunden: ${input.useCaseId}")

        val routeSpec = useCase.routes.firstOrNull { it.route == input.route }
            ?: return Result(false, "Route nicht gefunden: ${input.route}")

        val rule = selectRule(
            rules = routeSpec.rules,
            ageMonths = input.ageMonths,
            weightKg = input.weightKg,
            hasManualAmpoule = input.manualAmpoule != null
        ) ?: return Result(false, "Keine passende Regel gefunden")

        // Dosis berechnen
        val doseMg = when (rule.calc.type) {
            "perKg" -> {
                val w = input.weightKg
                    ?: return Result(false, "Gewicht erforderlich für perKg-Regel")
                val raw = (rule.calc.mgPerKg ?: 0.0) * w
                applyClamp(raw, rule.calc.minMg, rule.calc.maxMg)
            }
            "fixed" -> applyClamp(rule.calc.fixedMg ?: 0.0, rule.calc.minMg, rule.calc.maxMg)
            else -> return Result(false, "Unbekannter calc.type: ${rule.calc.type}")
        }

        // Effektive Konzentration bestimmen
        val base = input.manualAmpoule ?: input.medication.ampoule
        val concentration = when (val tv = rule.dilution?.totalVolumeMl) {
            null -> safeDiv(base.mg, base.ml)
            else -> safeDiv(base.mg, tv)
        }
        if (concentration == null || concentration <= 0.0) {
            return Result(false, "Ungültige Konzentration (Division durch 0?)")
        }

        // Volumen
        var volumeMl = doseMg / concentration

        // Rundung
        val roundedDose = rule.rounding?.mgStep?.let { roundToStep(doseMg, it) } ?: doseMg
        volumeMl = rule.rounding?.mlStep?.let { roundToStep(volumeMl, it) } ?: volumeMl

        return Result(
            ok = true,
            doseMg = roundedDose,
            concentrationMgPerMl = concentration,
            volumeMl = volumeMl,
            solutionText = rule.dilution?.solutionText,
            totalVolumeMl = rule.dilution?.totalVolumeMl,
            appliedRuleId = rule.id,
            appliedRoute = routeSpec.route,
            ruleHint = rule.hint
        )
    }

    // --- Auswahl-Helfer ------------------------------------------------------------------

    private fun selectRule(
        rules: List<DosingRule>,
        ageMonths: Int,
        weightKg: Double?,
        hasManualAmpoule: Boolean
    ): DosingRule? {
        return rules
            .asSequence()
            .filter { matchesAge(it.age, ageMonths) }
            .filter { matchesWeight(it.weight, weightKg) }
            .filter { matchesConditions(it.conditions, hasManualAmpoule) }
            .sortedByDescending { it.priority }
            .firstOrNull()
    }

    private fun matchesAge(range: AgeRange?, ageMonths: Int): Boolean {
        val minOk = range?.minMonths?.let { ageMonths >= it } ?: true
        val maxOk = range?.maxMonthsExclusive?.let { ageMonths < it } ?: true
        return minOk && maxOk
    }

    private fun matchesWeight(range: WeightRange?, weightKg: Double?): Boolean {
        if (range == null) return true
        val w = weightKg ?: return false
        val minOk = range.minKg?.let { w >= it } ?: true
        val maxOk = range.maxKgExclusive?.let { w < it } ?: true
        return minOk && maxOk
    }

    private fun matchesConditions(cond: Conditions?, hasManualAmpoule: Boolean): Boolean {
        val manualOk = cond?.requiresManualAmpoule?.let { req ->
            if (req) hasManualAmpoule else true
        } ?: true
        return manualOk
    }

    // --- Mathe/Helfer --------------------------------------------------------------------

    private fun applyClamp(value: Double, minMg: Double?, maxMg: Double?): Double {
        var v = value
        if (minMg != null) v = max(v, minMg)
        if (maxMg != null) v = min(v, maxMg)
        return v
    }

    private fun roundToStep(value: Double, step: Double): Double {
        if (step <= 0.0) return value
        val n = value / step
        return round(n) * step
    }

    private fun safeDiv(numerator: Double, denominator: Double?): Double? {
        val d = denominator ?: return null
        if (d == 0.0) return null
        return numerator / d
    }
}
