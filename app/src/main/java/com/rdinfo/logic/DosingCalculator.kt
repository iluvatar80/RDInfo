// File: app/src/main/java/com/rdinfo/logic/DosingCalculator.kt
package com.rdinfo.logic

import com.rdinfo.data.DosingRule
import com.rdinfo.data.MedicationRepository
import com.rdinfo.data.UseCaseKey
import com.rdinfo.data.useCaseKeyFromLabel
import kotlin.math.min
import kotlin.math.round

/** Ergebniscontainer für die Berechnung (UI konsumiert diese Felder). */
data class DosingResult(
    val mg: Double?,
    val hint: String,
    val recommendedConcMgPerMl: Double?,
    val solutionText: String?,
    val totalPreparedMl: Double?,
    val maxDoseText: String? = null
)

/** Konfig: Sollen berechnete mg-Werte hart auf die Maximaldosis gekappt werden (Clamp)? */
private const val CLAMP_TO_MAX_DOSE: Boolean = false

/**
 * Berechnung OHNE feste Route – wählt die passendste Regel nur nach Med + Use-Case.
 */
fun computeDoseFor(
    medication: String,
    useCase: String,
    weightKg: Double,
    ageYears: Int
): DosingResult {
    val rule = selectBestRule(
        medication = medication,
        ucLabel = useCase,
        routeDisplayName = null,
        weightKg = weightKg,
        ageYears = ageYears
    )
    return buildResultFromRule(rule, medication, useCase, null, weightKg, ageYears)
}

/**
 * Berechnung MIT gewählter Route. **Kein Fallback** auf andere Routen.
 */
fun computeDoseFor(
    medication: String,
    useCase: String,
    weightKg: Double,
    ageYears: Int,
    routeDisplayName: String
): DosingResult {
    val rule = selectBestRule(
        medication = medication,
        ucLabel = useCase,
        routeDisplayName = routeDisplayName,
        weightKg = weightKg,
        ageYears = ageYears
    )
    return buildResultFromRule(rule, medication, useCase, routeDisplayName, weightKg, ageYears)
}

/** Interne Auswahl der "besten" passenden Regel (Route optional streng). */
private fun selectBestRule(
    medication: String,
    ucLabel: String,
    routeDisplayName: String?,
    weightKg: Double,
    ageYears: Int
): DosingRule? {
    val med = MedicationRepository.getMedicationByName(medication) ?: return null
    val ucKey: UseCaseKey = useCaseKeyFromLabel(ucLabel) ?: return null

    return med.dosing
        .asSequence()
        .filter { it.useCase == ucKey }
        .filter { routeDisplayName == null || it.route?.equals(routeDisplayName, ignoreCase = true) == true }
        .filter { matches(it, weightKg, ageYears) }
        .sortedByDescending { specificity(it) }
        .firstOrNull()
}

// --- Auswahl-/Bewertungslogik ------------------------------------------------
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

    // 1) Dosis (mg) berechnen
    val rawMg = when {
        rule.doseMgPerKg != null -> rule.doseMgPerKg * weightKg
        rule.fixedDoseMg != null -> rule.fixedDoseMg
        else -> null
    }

    // 2) Hinweis aufbauen (ohne maxDoseText hier – das zeigt die MainActivity separat an)
    var finalMg = rawMg
    val hintParts = mutableListOf<String>()
    rule.note?.let { if (it.isNotBlank()) hintParts += it }

    val max = rule.maxDoseMg
    if (rawMg != null && max != null && rawMg > max) {
        if (CLAMP_TO_MAX_DOSE) {
            finalMg = min(rawMg, max)
            hintParts += "Berechnete Dosis (${fmt2(rawMg)} mg) > Maximaldosis (${fmt2(max)} mg) – gekürzt auf Maximaldosis."
        } else {
            hintParts += "Berechnete Dosis (${fmt2(rawMg)} mg) überschreitet die Maximaldosis (${fmt2(max)} mg)."
        }
    }

    val hint = hintParts.joinToString("\n")

    return DosingResult(
        mg = finalMg,
        hint = hint,
        recommendedConcMgPerMl = rule.recommendedConcMgPerMl,
        solutionText = rule.solutionText,
        totalPreparedMl = rule.totalPreparedMl,
        maxDoseText = rule.maxDoseText // << MainActivity hängt „Maximaldosis: …“ EINMAL an.
    )
}

/**
 * Volumen in ml aus Dosis (mg) und Konzentration (mg/ml).
 * → Jetzt **in der Logik** auf 0,1 ml gerundet.
 */
fun computeVolumeMl(doseMg: Double?, concentrationMgPerMl: Double?): Double? {
    if (doseMg == null || concentrationMgPerMl == null) return null
    if (concentrationMgPerMl <= 0.0) return null
    val v = doseMg / concentrationMgPerMl
    return round(v * 10.0) / 10.0
}

private fun fmt1(v: Double): String = String.format(java.util.Locale.GERMANY, "%.1f", v)
private fun fmt2(v: Double): String = String.format(java.util.Locale.GERMANY, "%.2f", v)
