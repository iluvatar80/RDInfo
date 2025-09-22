// File: app/src/main/java/com/rdinfo/logic/DosingCalculator.kt
package com.rdinfo.logic

import com.rdinfo.data.DosingRule
import com.rdinfo.data.MedicationRepository
import kotlin.math.min
import kotlin.math.round

// Einheiten-Umrechnungen (alle zu mg als Basis)
private fun convertToMg(amount: Double, unit: String): Double = when (unit) {
    "g" -> amount * 1000.0
    "µg" -> amount / 1000.0
    "ng" -> amount / 1000000.0
    "mg" -> amount
    "I.E." -> amount // I.E. bleibt unverändert
    "mol" -> amount // mol bleibt unverändert
    "mmol" -> amount // mmol bleibt unverändert
    "%" -> amount // % bleibt unverändert
    else -> amount
}

private fun convertFromMg(amountMg: Double, targetUnit: String): Double = when (targetUnit) {
    "g" -> amountMg / 1000.0
    "µg" -> amountMg * 1000.0
    "ng" -> amountMg * 1000000.0
    "mg" -> amountMg
    "I.E." -> amountMg // I.E. bleibt unverändert
    "mol" -> amountMg // mol bleibt unverändert
    "mmol" -> amountMg // mmol bleibt unverändert
    "%" -> amountMg // % bleibt unverändert
    else -> amountMg
}

private fun canConvertUnits(unit: String): Boolean = when (unit) {
    "mg", "g", "µg", "ng" -> true
    else -> false
}

/** Ergebniscontainer für die Berechnung (UI konsumiert diese Felder). */
data class DosingResult(
    val amount: Double?,
    val unit: String,
    val hint: String,
    val recommendedConcAmountPerMl: Double?,
    val solutionText: String?,
    val totalPreparedMl: Double?,
    val maxDoseText: String? = null
)

/** Konfig: Sollen berechnete mg-Werte hart auf die Maximaldosis gekappt werden (Clamp)? */
private const val CLAMP_TO_MAX_DOSE: Boolean = true

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

    return med.dosing
        .asSequence()
        .filter { it.useCase == ucLabel }
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
            amount = null,
            unit = "mg",
            hint = msg,
            recommendedConcAmountPerMl = null,
            solutionText = null,
            totalPreparedMl = null,
            maxDoseText = null
        )
    }

    // 1) Dosis berechnen
    val med = MedicationRepository.getMedicationByName(medication)
    val unit = med?.unit ?: "mg"

    val rawAmount = when {
        rule.doseMgPerKg != null -> rule.doseMgPerKg * weightKg
        rule.fixedDoseMg != null -> rule.fixedDoseMg
        else -> null
    }

    // 2) Hinweis aufbauen (ohne maxDoseText hier — das zeigt die MainActivity separat an)
    var finalAmount = rawAmount
    val hintParts = mutableListOf<String>()
    rule.note?.let { if (it.isNotBlank()) hintParts += it }

    val max = rule.maxDoseMg
    if (rawAmount != null && max != null && rawAmount > max) {
        if (CLAMP_TO_MAX_DOSE) {
            finalAmount = min(rawAmount, max)
            hintParts += "Berechnete Dosis (${fmt2(rawAmount)} $unit) > Maximaldosis (${fmt2(max)} $unit) — gekürzt auf Maximaldosis."
        } else {
            hintParts += "Berechnete Dosis (${fmt2(rawAmount)} $unit) überschreitet die Maximaldosis (${fmt2(max)} $unit)."
        }
    }

    val hint = hintParts.joinToString("\n")

    return DosingResult(
        amount = finalAmount,
        unit = unit,
        hint = hint,
        recommendedConcAmountPerMl = rule.recommendedConcMgPerMl,
        solutionText = rule.solutionText,
        totalPreparedMl = rule.totalPreparedMl,
        maxDoseText = rule.maxDoseText
    )
}

/**
 * Volumen in ml aus Dosis und Konzentration (beide in gleicher Einheit).
 * → Auf 0,01 ml gerundet.
 */
fun computeVolumeMl(doseAmount: Double?, concentrationAmountPerMl: Double?): Double? {
    if (doseAmount == null || concentrationAmountPerMl == null) return null
    if (concentrationAmountPerMl <= 0.0) return null
    val v = doseAmount / concentrationAmountPerMl
    return round(v * 100.0) / 100.0  // Auf 0,01 ml runden
}

private fun fmt1(v: Double): String = String.format(java.util.Locale.GERMANY, "%.1f", v)
private fun fmt2(v: Double): String = String.format(java.util.Locale.GERMANY, "%.2f", v)
