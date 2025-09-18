// File: app/src/main/java/com/rdinfo/logic/DosingCalculator.kt
package com.rdinfo.logic

import com.rdinfo.data.MedicationRepository
import com.rdinfo.data.DosingRule
import com.rdinfo.data.useCaseKeyFromLabel

/** Ergebniscontainer für die Berechnung (UI konsumiert diese Felder). */
data class DosingResult(
    val mg: Double?,
    val hint: String,
    val recommendedConcMgPerMl: Double?,
    val solutionText: String?,
    val totalPreparedMl: Double?,
    val maxDoseText: String? = null
)

/**
 * Berechnung OHNE feste Route – wählt die passendste Regel nur nach Med + Use‑Case.
 */
fun computeDoseFor(
    medication: String,
    useCase: String,
    weightKg: Double,
    ageYears: Int
): DosingResult {
    val med = MedicationRepository.getMedicationByName(medication)
    val ucKey = useCaseKeyFromLabel(useCase)
    val rule = med?.dosing
        ?.filter { it.useCase == ucKey }
        ?.filter { matches(it, weightKg, ageYears) }
        ?.maxByOrNull { specificity(it) }

    return buildResultFromRule(rule, medication, useCase, null, weightKg, ageYears)
}

/**
 * Berechnung MIT gewählter Route. **Kein Fallback** auf andere Routen.
 * Wenn für die gewählte Route keine Regel passt → leerer DosingResult mit Hinweis.
 */
fun computeDoseFor(
    medication: String,
    useCase: String,
    weightKg: Double,
    ageYears: Int,
    routeDisplayName: String
): DosingResult {
    val med = MedicationRepository.getMedicationByName(medication)
    val ucKey = useCaseKeyFromLabel(useCase)
    val rule = med?.dosing
        ?.filter { it.useCase == ucKey && it.route?.equals(routeDisplayName, ignoreCase = true) == true }
        ?.filter { matches(it, weightKg, ageYears) }
        ?.maxByOrNull { specificity(it) }

    return buildResultFromRule(rule, medication, useCase, routeDisplayName, weightKg, ageYears)
}

// --- Auswahl-Logik ---------------------------------------------------------
private fun matches(r: DosingRule, weightKg: Double, ageYears: Int): Boolean {
    r.ageMinYears?.let { if (ageYears < it) return false }
    r.ageMaxYears?.let { if (ageYears > it) return false }
    r.weightMinKg?.let { if (weightKg < it) return false }
    r.weightMaxKg?.let { if (weightKg > it) return false }
    return true
}

// „Spezifischere“ Regeln bevorzugen: mehr gesetzte Grenzen → höherer Score
private fun specificity(r: DosingRule): Int {
    var s = 0
    if (r.ageMinYears != null) s++
    if (r.ageMaxYears != null) s++
    if (r.weightMinKg != null) s++
    if (r.weightMaxKg != null) s++
    if (r.route != null) s++
    return s
}

private fun buildResultFromRule(
    rule: DosingRule?,
    medication: String,
    useCase: String,
    routeDisplayName: String?,
    weightKg: Double,
    ageYears: Int
): DosingResult {
    if (rule == null) {
        val routePart = routeDisplayName?.let { " / $it" } ?: ""
        val msg = "Keine passende Regel für $medication / $useCase$routePart bei Alter ${ageYears} J, Gewicht ${fmt1(weightKg)} kg."
        return DosingResult(
            mg = null,
            hint = msg,
            recommendedConcMgPerMl = null,
            solutionText = null,
            totalPreparedMl = null,
            maxDoseText = null
        )
    }

    // Dosis (mg)
    val mg = when {
        rule.doseMgPerKg != null -> rule.doseMgPerKg * weightKg
        rule.fixedDoseMg != null -> rule.fixedDoseMg
        else -> null
    }

    val hint = rule.note ?: ""

    return DosingResult(
        mg = mg,
        hint = hint,
        recommendedConcMgPerMl = rule.recommendedConcMgPerMl,
        solutionText = rule.solutionText,
        totalPreparedMl = rule.totalPreparedMl,
        maxDoseText = rule.maxDoseText
    )
}

/**
 * Hilfsfunktion: Volumen in ml aus Dosis (mg) und Konzentration (mg/ml).
 */
fun computeVolumeMl(doseMg: Double?, concentrationMgPerMl: Double?): Double? {
    if (doseMg == null || concentrationMgPerMl == null) return null
    if (concentrationMgPerMl <= 0.0) return null
    return doseMg / concentrationMgPerMl
}

private fun fmt1(v: Double): String = String.format(java.util.Locale.GERMANY, "%.1f", v)
