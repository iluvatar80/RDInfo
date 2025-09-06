// Zielpfad: app/src/main/java/com/rdinfo/logic/DosingCalculator.kt
// Saubere Minimalversion – Logik liest ausschließlich Regeln aus dem MedicationRepository

package com.rdinfo.logic

import com.rdinfo.data.MedicationRepository

data class DosingResult(
    val mg: Double?,
    val hint: String,
    val recommendedConcMgPerMl: Double?
)

object DosingCalculator {
    fun compute(
        medicationName: String,
        useCaseLabel: String,
        ageYears: Int,
        weightKg: Double
    ): DosingResult {
        val rule = MedicationRepository.findBestDosingRule(
            medicationName = medicationName,
            useCaseLabelOrKey = useCaseLabel,
            ageYears = ageYears,
            weightKg = weightKg
        ) ?: return DosingResult(
            mg = null,
            hint = "Keine Dosisregel für $useCaseLabel bei $medicationName hinterlegt.",
            recommendedConcMgPerMl = null
        )

        val baseMg = when {
            rule.fixedDoseMg != null -> rule.fixedDoseMg
            rule.doseMgPerKg != null -> rule.doseMgPerKg * weightKg
            else -> null
        }
        val mg = baseMg?.let { if (rule.maxDoseMg != null) kotlin.math.min(it, rule.maxDoseMg) else it }

        val hintPrefix = rule.route?.let { "$it: " } ?: ""
        val hint = (hintPrefix + (rule.note ?: "")).ifBlank { "—" }

        return DosingResult(
            mg = mg,
            hint = hint,
            recommendedConcMgPerMl = rule.recommendedConcMgPerMl
        )
    }
}

fun computeDoseFor(
    medication: String,
    useCase: String,
    weightKg: Double,
    ageYears: Int
): DosingResult = DosingCalculator.compute(medication, useCase, ageYears, weightKg)

fun computeVolumeMl(doseMg: Double?, concentrationMgPerMl: Double?): Double? {
    if (doseMg == null || concentrationMgPerMl == null || concentrationMgPerMl <= 0.0) return null
    return doseMg / concentrationMgPerMl
}
