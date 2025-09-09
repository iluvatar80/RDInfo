// Zielpfad: app/src/main/java/com/rdinfo/logic/DosingCalculator.kt
// Vollständige Datei – regelgetrieben, Exceptions bei Fehlern, Route‑Support, 0,1‑ml‑Rundung.

package com.rdinfo.logic

import com.rdinfo.data.MedicationRepository
import kotlin.math.min
import kotlin.math.roundToInt

data class DosingResult(
    val mg: Double?,                      // berechnete Dosis (mg) oder null, wenn nicht berechenbar
    val hint: String,                     // Hinweis-/Regeltext (deutsch)
    val recommendedConcMgPerMl: Double?,  // Arbeitskonzentration (mg/ml), falls aus Regel vorgegeben
    val solutionText: String?,            // z. B. "NaCl 0,9 %"
    val totalPreparedMl: Double?          // z. B. 10.0
)

object DosingCalculator {

    /**
     * Wirft Exceptions mit deutscher Meldung, wenn Daten fehlen/unlogisch sind.
     * Route ist optional (falls nicht übergeben, wird nur nach UseCase gefiltert).
     */
    fun compute(
        medicationName: String,
        useCaseLabel: String,
        routeDisplayName: String?, // z. B. "i.v." / "i.m." / "inhalativ"
        ageYears: Int,
        weightKg: Double
    ): DosingResult {
        val rule = MedicationRepository.findBestDosingRuleWithRoute(
            medicationName = medicationName,
            useCaseLabelOrKey = useCaseLabel,
            routeDisplayName = routeDisplayName,
            ageYears = ageYears,
            weightKg = weightKg
        ) ?: throw IllegalStateException(
            "Keine Dosisregel für $useCaseLabel (${routeDisplayName ?: "ohne Route"}) bei $medicationName hinterlegt."
        )

        val baseMg = when {
            rule.fixedDoseMg != null -> rule.fixedDoseMg
            rule.doseMgPerKg != null -> rule.doseMgPerKg * weightKg
            else -> null
        } ?: throw IllegalStateException(
            "Für $medicationName/$useCaseLabel ist keine Dosis (mg/kg oder fixe mg) angegeben."
        )

        val mg = if (rule.maxDoseMg != null) min(baseMg, rule.maxDoseMg) else baseMg

        val hintPrefix = rule.route?.let { "$it: " } ?: ""
        val hint = (hintPrefix + (rule.note ?: "")).ifBlank { "—" }

        return DosingResult(
            mg = mg,
            hint = hint,
            recommendedConcMgPerMl = rule.recommendedConcMgPerMl,
            solutionText = rule.solutionText,
            totalPreparedMl = rule.totalPreparedMl
        )
    }
}

/**
 * Bequeme UI-Fassade (Kompatibilität). Route kann null sein.
 */
fun computeDoseFor(
    medication: String,
    useCase: String,
    weightKg: Double,
    ageYears: Int,
    routeDisplayName: String? = null
): DosingResult = DosingCalculator.compute(medication, useCase, routeDisplayName, ageYears, weightKg)

/**
 * Gibt das Volumen in ml zurück und rundet intern auf 0,1 ml.
 */
fun computeVolumeMl(doseMg: Double?, concentrationMgPerMl: Double?): Double? {
    if (doseMg == null || concentrationMgPerMl == null || concentrationMgPerMl <= 0.0) return null
    val raw = doseMg / concentrationMgPerMl
    return ((raw * 10.0).roundToInt() / 10.0)
}
